package com.thelightphone.lp3keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Offline, on-device dictation via Vosk. The model is downloaded on demand
 * (see [VoiceModel]) and loaded from internal storage — nothing is bundled in
 * the APK, and audio never leaves the phone. Ported from KEZO555/Type (MIT).
 */
class VoiceDictation(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var modelCode: String? = null
    private var loading = false
    private var speech: SpeechService? = null
    private var recognizer: Recognizer? = null

    fun ready(code: String): Boolean = model != null && modelCode == code

    /** Load language [code]'s downloaded model into memory (background). */
    fun prepare(code: String) {
        if (loading || (model != null && modelCode == code)) return
        if (!VoiceModel.isInstalled(context, code)) return
        loading = true
        Thread {
            try {
                val m = Model(VoiceModel.dir(context, code).absolutePath)
                main.post {
                    destroy()
                    model?.let { runCatching { it.close() } }
                    model = m; modelCode = code; loading = false
                }
            } catch (e: Throwable) {
                main.post { loading = false }
            }
        }.start()
    }

    /**
     * Continuous dictation: a pause ends one *segment* (delivered via [onSegment])
     * but keeps listening. Call [stop] when done; trailing words arrive through
     * [onSegment] too.
     */
    fun listen(
        code: String,
        onPartial: (String) -> Unit,
        onSegment: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val m = if (modelCode == code) model else null
        if (m == null) {
            prepare(code)
            onError(if (VoiceModel.isInstalled(context, code)) "loading" else "missing")
            return
        }
        destroy()
        try {
            val rec = Recognizer(m, SAMPLE_RATE)
            recognizer = rec
            val s = SpeechService(rec, SAMPLE_RATE)
            speech = s
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    field(hypothesis, "partial")?.let { if (it.isNotBlank()) onPartial(it) }
                }
                override fun onResult(hypothesis: String?) {
                    field(hypothesis, "text")?.let { if (it.isNotBlank()) onSegment(it) }
                }
                override fun onFinalResult(hypothesis: String?) {
                    field(hypothesis, "text")?.let { if (it.isNotBlank()) onSegment(it) }
                }
                override fun onError(e: Exception?) { onError("error") }
                override fun onTimeout() {}
            })
        } catch (e: Throwable) {
            onError("error"); destroy()
        }
    }

    /** Finish dictating: flush the words since the last pause, then tear down. */
    fun stop() {
        val s = speech ?: return destroy()
        runCatching { s.stop() }
        main.postDelayed({ if (speech === s) destroy() }, 350)
    }

    fun destroy() {
        speech?.let { runCatching { it.stop() }; runCatching { it.shutdown() } }
        speech = null
        recognizer?.let { runCatching { it.close() } }
        recognizer = null
    }

    private fun field(json: String?, key: String): String? =
        json?.let { runCatching { JSONObject(it).optString(key) }.getOrNull() }

    companion object {
        private const val SAMPLE_RATE = 16000.0f
    }
}
