package com.thelightphone.lp3keyboard

import android.content.Context
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** What the corrector needs from a dictionary — split out so the engine is JVM-testable. */
interface SvLexicon {
    val ready: Boolean
    val sorted: Array<String>
    fun isWord(word: String): Boolean
    fun isSafeTarget(word: String): Boolean
    fun freqOf(word: String): Long
}

/**
 * Swedish frequency word list, loaded from `assets/sv_words.txt` on a background
 * thread. Format: one `word<TAB>f<TAB>offensive` entry per line, where f is the
 * AOSP log-scale frequency (0-255). Derived from the HeliBoard experimental
 * Swedish dictionary (Leipzig Corpora Collection, CC BY 4.0). Corrections are
 * simply skipped until [ready].
 *
 * Offensive-flagged words stay in the dictionary (so a deliberately typed word
 * is never "corrected" away) but are never offered as correction targets.
 */
class SvDictionary(context: Context) : SvLexicon {

    private class Data(
        val freq: HashMap<String, Long>,
        val offensive: HashSet<String>,
        val sorted: Array<String>,
    )

    private val data = AtomicReference<Data?>(null)
    private val appContext = context.applicationContext

    override val ready: Boolean get() = data.get() != null
    override val sorted: Array<String> get() = data.get()?.sorted ?: emptyArray()

    override fun isWord(word: String): Boolean = data.get()?.freq?.containsKey(word) == true
    override fun isSafeTarget(word: String): Boolean =
        data.get()?.let { it.freq.containsKey(word) && word !in it.offensive } == true
    override fun freqOf(word: String): Long = data.get()?.freq?.get(word) ?: 0L

    fun prepare() {
        if (data.get() != null) return
        thread(name = "sv-dict-load") {
            val freq = HashMap<String, Long>(140_000)
            val offensive = HashSet<String>()
            appContext.assets.open("sv_words.txt").bufferedReader().forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size < 2) return@forEachLine
                val word = parts[0].lowercase()
                val f = parts[1].toLongOrNull() ?: return@forEachLine
                if (word.any { it !in SvCorrector.ALPHABET && it != '\'' }) return@forEachLine
                val prev = freq[word]
                if (prev == null || f > prev) freq[word] = f
                // A word is a safe target only if no occurrence is flagged.
                if (parts.size > 2 && parts[2] == "1") offensive.add(word)
                else if (prev != null) offensive.remove(word)
            }
            val sorted = freq.keys.toTypedArray().also { it.sort() }
            data.set(Data(freq, offensive, sorted))
        }
    }
}
