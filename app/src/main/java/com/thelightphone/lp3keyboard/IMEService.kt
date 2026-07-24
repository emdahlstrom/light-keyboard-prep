package com.thelightphone.lp3keyboard

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardSwipeCallback
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardView
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.layout.LayoutRegistryItem
import com.thelightphone.lp3Keyboard.ui.layout.buildRootViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3RepeatableKeyboardCallback
import com.thelightphone.lp3Keyboard.ui.viewmodel.SvQwertyLp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.defaultEmojis
import kotlinx.coroutines.flow.MutableStateFlow

class IMEService : LifecycleInputMethodService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    Lp3RepeatableKeyboardCallback {

    private var renderedLayout: LayoutRegistryItem? = null
    private var viewModel: Lp3KeyboardViewModel<*>? = null

    private val svDictionary by lazy { SvDictionary(this) }
    private var undoFrom: String? = null
    private var undoTo: String? = null
    private val autocorrectActive: Boolean
        get() = renderedLayout == LayoutRegistryItem.SvQwerty

    private val dictation by lazy { VoiceDictation(this) }
    private var micActive = false

    private var layoutPrefs: SharedPreferences? = null
    private val layoutChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LayoutPreferences.KEY_ACTIVE_LAYOUT) {
                refreshLayoutIfNeeded()
            }
        }

    private fun refreshLayoutIfNeeded() {
        if (LayoutPreferences.getActiveLayout(this) != renderedLayout) {
            setInputView(onCreateInputView())
        }
    }

    private fun buildViewModel(layout: LayoutRegistryItem): Lp3KeyboardViewModel<*> {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dummySwipeCallback = object : Lp3KeyboardSwipeCallback<Unit> {}
                // The Swedish layout hides the mic by default (LightOS dictation
                // is English-only), but this build has its own offline Swedish
                // dictation, so show the mic key here.
                if (layout == LayoutRegistryItem.SvQwerty) {
                    return SvQwertyLp3KeyboardViewModel<Unit>(
                        passedCallback = this@IMEService,
                        swipeCallback = dummySwipeCallback,
                        haptic = ::tick,
                        keyboardOptionsFlow = MutableStateFlow(
                            KeyboardOptions(
                                defaultEmojis,
                                displayReturn = true,
                                displayVoice = true,
                                enableKeyAnimation = true,
                                swipeEnabled = false
                            )
                        )
                    ) as T
                }
                return layout.buildRootViewModel(
                    this@IMEService,
                    dummySwipeCallback,
                    haptic = ::tick
                ) as T
            }
        }
        // Key by the layout's uniqueId so each layout gets its own retained ViewModel instance.
        return ViewModelProvider(store, factory)[layout.uniqueId, ViewModel::class.java]
                as Lp3KeyboardViewModel<*>
    }

    override fun onCreateInputView(): View {
        val layout = LayoutPreferences.getActiveLayout(this)
        val vm = buildViewModel(layout)
        renderedLayout = layout
        viewModel = vm

        val view = Lp3KeyboardView(
            context = this,
            viewModel = vm,
            // don't need to remap since no external keyboard
            remapKeyCode = null
        ).apply {
            // don't need the keyboard view itself ot handle external keys, Android inputs will do it
            handleHardwareKeyboardInput = false
        }
        setCandidatesViewShown(false)
        window?.window?.let {
            it.decorView.apply {
                setViewTreeLifecycleOwner(this@IMEService)
                setViewTreeViewModelStoreOwner(this@IMEService)
                setViewTreeSavedStateRegistryOwner(this@IMEService)
            }
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshLayoutIfNeeded()
        // Warm the Swedish voice model so the first mic press doesn't stall.
        if (renderedLayout == LayoutRegistryItem.SvQwerty &&
            VoiceModel.isInstalled(this, VoiceModel.SV_CODE)
        ) {
            dictation.prepare(VoiceModel.SV_CODE)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        layoutPrefs = LayoutPreferences.registerOnChange(this, layoutChangeListener)
        svDictionary.prepare()
    }

    /**
     * Autocorrect the word before the cursor (if the Swedish layout is active and
     * a confident fix exists), then commit [terminator]. One backspace immediately
     * after a correction restores the typed word (see Backspace handling).
     */
    private fun autocorrectThenCommit(terminator: String) {
        val ic = currentInputConnection ?: return
        if (autocorrectActive) {
            val before = ic.getTextBeforeCursor(48, 0) ?: ""
            val word = SvCorrector.trailingWord(before)
            val fix = if (word.isNotEmpty()) SvCorrector.correct(word, svDictionary) else null
            if (fix != null) {
                val cased = SvCorrector.applyCase(word, fix)
                ic.beginBatchEdit()
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(cased, 1)
                ic.commitText(terminator, 1)
                ic.endBatchEdit()
                undoFrom = cased + terminator
                undoTo = word + terminator
                updateCapsMode()
                return
            }
        }
        undoFrom = null
        ic.commitText(terminator, 1)
        updateCapsMode()
    }

    override fun onDestroy() {
        layoutPrefs?.unregisterOnSharedPreferenceChangeListener(layoutChangeListener)
        dictation.destroy()
        store.clear()
        super.onDestroy()
    }

    override val viewModelStore: ViewModelStore
        get() = store
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private val store = ViewModelStore()
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    private fun tick() {
        // 50ms feels good on LP3, other device motors may allow faster buzz
        vibrator.vibrate(50)
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onWindowHidden() {
        super.onWindowHidden()
        viewModel?.cancelHeldKeys()
        if (micActive) {
            micActive = false
            dictation.stop()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        undoFrom = null
        updateCapsMode()
    }

    private fun updateCapsMode() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        // might be set if the TextField is set to capitalize sentence starts, for example
        val caps = ic.getCursorCapsMode(ei.inputType)
        viewModel?.setCapsMode(caps != 0)
    }

    override fun onKeyPressed(code: Int) {
    }

    override fun onSubmitWord(word: CharSequence) {
        undoFrom = null
        currentInputConnection?.commitText("$word ", 1)
    }

    override fun onSpecialKeyPressed(key: SpecialKey) {
        when (key) {
            SpecialKey.Space -> {
                autocorrectThenCommit(" ")
            }

            else -> {}
        }
    }

    override fun onKeyReleased(code: Int) {
        val text = buildString { appendCodePoint(code) }
        if (text.length == 1 && SvCorrector.isCorrectTrigger(text[0])) {
            autocorrectThenCommit(text)
            return
        }
        undoFrom = null
        currentInputConnection?.commitText(text, 1)
        updateCapsMode()
    }

    override fun onSpecialKeyReleased(key: SpecialKey) {
        when (key) {
            SpecialKey.Backspace -> {
                val ic = currentInputConnection ?: return
                // A single backspace right after an autocorrection rejects it.
                val from = undoFrom
                if (from != null && ic.getTextBeforeCursor(from.length, 0)?.toString() == from) {
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(from.length, 0)
                    ic.commitText(undoTo, 1)
                    ic.endBatchEdit()
                    undoFrom = null
                    updateCapsMode()
                    return
                }
                undoFrom = null
                val before = ic.getTextBeforeCursor(1, 0)
                val charsToDelete =
                    if (!before.isNullOrEmpty() && Character.isLowSurrogate(before[0])) 2 else 1
                ic.deleteSurroundingText(charsToDelete, 0)
                updateCapsMode()
            }

            SpecialKey.Return -> {
                undoFrom = null
                currentInputConnection?.commitText("\n", 1)
            }

            SpecialKey.Close -> {
                requestHideSelf(0)
            }

            SpecialKey.Voice -> {
                toggleDictation()
            }

            else -> {}
        }
    }

    /**
     * Mic key: first press starts offline Swedish dictation, second press stops
     * it. Segments commit as you pause, so text appears while you speak. Without
     * the RECORD_AUDIO grant this pops the permission dialog (via the shim
     * activity); without the model it opens the setup app to download it.
     */
    private fun toggleDictation() {
        if (micActive) {
            micActive = false
            dictation.stop()
            tick()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(
                Intent(this, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (!VoiceModel.isInstalled(this, VoiceModel.SV_CODE)) {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        micActive = true
        tick()
        startDictationWhenReady(attempts = 0)
    }

    /** Wait (briefly) for the model to finish loading on first use, then listen. */
    private fun startDictationWhenReady(attempts: Int) {
        if (!micActive) return
        if (dictation.ready(VoiceModel.SV_CODE)) {
            dictation.listen(
                VoiceModel.SV_CODE,
                onPartial = {},
                onSegment = { text ->
                    undoFrom = null
                    currentInputConnection?.commitText(spacedDictation(text), 1)
                },
                onError = { micActive = false },
            )
            return
        }
        dictation.prepare(VoiceModel.SV_CODE)
        if (attempts > 40) {   // ~12s; first-run model load should finish well before this
            micActive = false
            return
        }
        window?.window?.decorView?.postDelayed({ startDictationWhenReady(attempts + 1) }, 300)
    }

    /** Insert a leading space if the cursor isn't at a boundary, so dictated text doesn't fuse. */
    private fun spacedDictation(text: String): String {
        val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        return if (before.isNotEmpty() && !before.last().isWhitespace()) " $text" else text
    }

    override fun onKeyLongPressed(code: Int) {
    }

    private fun deletePrecedingWord() {
        val ic = currentInputConnection ?: return
        // Get text before cursor to find the word boundary (max 100 chars long)
        val before = ic.getTextBeforeCursor(100, 0) ?: return
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.indexOfLast { it.isWhitespace() }
        // Delete from cursor back to start of word (including trailing spaces)
        val charsToDelete = before.length - (if (lastSpace >= 0) lastSpace + 1 else 0)
        ic.deleteSurroundingText(charsToDelete, 0)
        updateCapsMode()
    }

    override fun onSpecialKeyLongPressed(key: SpecialKey) {
        when (key) {
            SpecialKey.Backspace -> {
                deletePrecedingWord()
            }

            else -> {}
        }
    }

    override fun onKeyRepeated(code: Int) {
        onKeyReleased(code)
    }

    override fun onSpecialKeyRepeated(specialKey: SpecialKey) {
        when (specialKey) {
            SpecialKey.Space -> {
                undoFrom = null
                currentInputConnection?.commitText(" ", 1)
                updateCapsMode()
            }

            SpecialKey.Backspace -> {
                deletePrecedingWord()
            }

            else -> {}
        }
    }
}
