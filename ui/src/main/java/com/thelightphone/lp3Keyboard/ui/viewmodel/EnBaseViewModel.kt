package com.thelightphone.lp3Keyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardSwipeCallback
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.SpecialKey.Close
import com.thelightphone.lp3Keyboard.ui.layout.EnShared
import com.thelightphone.lp3Keyboard.ui.layout.Layout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * An abstract view model for the base, shared logic for English keyboards.
 *
 * Typically, setting initial, lower, upper, and capslock layouts is enough to define a standard
 * English keyboard.
 */
abstract class EnBaseViewModel<SwipeResult>(
    private val passedCallback: Lp3RepeatableKeyboardCallback,
    private val swipeCallback: Lp3KeyboardSwipeCallback<SwipeResult>?,
    private val haptic: () -> Unit = {},
    private val optionsForLayout: (Layout) -> LayoutOptions = {
        LayoutOptions(
            displayCloseButton = true
        )
    },
    override val keyboardOptionsFlow: StateFlow<KeyboardOptions> = MutableStateFlow(
        KeyboardOptions(
            defaultEmojis,
            displayReturn = true,
            displayVoice = true,
            enableKeyAnimation = true,
            swipeEnabled = false
        )
    ),
    val initialLayout: Layout,
    val lowerCaseLayout: Layout,
    val upperCaseLayout: Layout,
    val capsLockedLayout: Layout,
) : ViewModel(), Lp3KeyboardViewModel<SwipeResult> {

    var previousLayout: Layout? = null
        private set

    private var swipeActive = false

    private val delegateCallback: Lp3RepeatableKeyboardCallback?
        get() = passedCallback.takeUnless { swipeActive }

    override val layoutFlow: MutableStateFlow<Layout> = MutableStateFlow(initialLayout)

    private fun setLayout(layout: Layout) {
        previousLayout = layoutFlow.value
        layoutOptionsFlow.value = optionsForLayout(layout)
        layoutFlow.value = layout
    }

    override val layoutOptionsFlow = MutableStateFlow(optionsForLayout(initialLayout))

    companion object {
        private const val REPEAT_INTERVAL_MS = 350L
    }

    private val heldSpecialKeys = mutableMapOf<SpecialKey, Job>()
    private val heldKeys = mutableMapOf<Int, Job>()

    /**
     * Code of the key whose long press opened the currently showing
     * [EnShared.ExtendedCharKeyboard], or null.
     *
     * The gesture that opened the layout is still in flight when the swap
     * happens: the finger is down on a key that the swap removes from the
     * composition, so Compose ends that gesture with a cancel rather than a
     * release. Both endings belong to the long press we already handled — they
     * must neither type the root letter nor tear down the layout the long press
     * just opened.
     */
    private var extendedCharRoot: Int? = null

    override fun cancelHeldKeys() {
        heldSpecialKeys.values.forEach { it.cancel() }
        heldSpecialKeys.clear()
        heldKeys.values.forEach { it.cancel() }
        heldKeys.clear()
        extendedCharRoot = null
    }

    /**
     * Swallow the trailing release/cancel of the long press that opened the
     * extended-char layout. Returns true when [code] is that root key and the
     * layout is still showing; otherwise clears the stale marker and lets the
     * caller handle the event normally, so a later ordinary tap on the root
     * letter still types.
     */
    private fun consumeExtendedCharRootGesture(code: Int): Boolean {
        if (extendedCharRoot != code) return false
        val stillShowing = layoutFlow.value is EnShared.ExtendedCharKeyboard
        extendedCharRoot = null
        return stillShowing
    }

    var capsMode: CapsMode = CapsMode.Off
        private set

    private fun showAlphabetLayout() {
        setLayout(
            when (capsMode) {
                CapsMode.Off -> lowerCaseLayout
                CapsMode.Single -> upperCaseLayout
                CapsMode.Locked -> capsLockedLayout
            }
        )
    }

    override fun onKeyPressed(code: Int) {
        haptic()
        delegateCallback?.onKeyPressed(code)
    }

    override fun onSpecialKeyPressed(key: SpecialKey) {
        haptic()
        delegateCallback?.onSpecialKeyPressed(key)
    }

    override fun onKeyReleased(code: Int) {
        if (consumeExtendedCharRootGesture(code)) return
        heldKeys.remove(code)?.apply {
            cancel()
            return // swallow on key released if held
        }
        // eagerly drop single-caps so fast typists see lowercase before the IME round-trip
        if (capsMode == CapsMode.Single) {
            capsMode = CapsMode.Off
            showAlphabetLayout()
        }
        // auto-dismiss when a special key is typed
        if (layoutFlow.value is EnShared.ExtendedCharKeyboard) {
            setLayout(previousLayout ?: lowerCaseLayout)
        }
        delegateCallback?.onKeyReleased(code)
    }

    override fun onKeyCancelled(code: Int) {
        // The long press that opened the extended-char layout also disposes the
        // key it started on, which arrives here as a cancel. Swallow it, or the
        // layout is dismissed on the frame after it appears.
        if (consumeExtendedCharRootGesture(code)) return
        // Finger left the key bounds — treat as the start of a swipe (or a
        // deliberate tap-cancel). Clean up press state but don't fire the IME
        // release, which is where text actually gets committed.
        heldKeys.remove(code)?.apply {
            cancel()
            return // swallow on key cancelled if held, as onKeyReleased does
        }
        if (layoutFlow.value is EnShared.ExtendedCharKeyboard) {
            setLayout(previousLayout ?: lowerCaseLayout)
        }
    }

    override fun onSpecialKeyReleased(key: SpecialKey) {
        val repeatJob = heldSpecialKeys.remove(key)
        // if we were long-pressing, swallow the release
        repeatJob?.apply {
            cancel()
            return
        }
        var consumed = true
        when (key) {
            SpecialKey.UpCase, SpecialKey.DownCase -> {
                capsMode = when (capsMode) {
                    CapsMode.Off -> CapsMode.Single
                    CapsMode.Single, CapsMode.Locked -> CapsMode.Off
                }
                showAlphabetLayout()
            }

            SpecialKey.Numbers -> {
                setLayout(EnShared.NumberLayout)
            }

            SpecialKey.Letters -> {
                showAlphabetLayout()
            }

            SpecialKey.Symbols -> {
                setLayout(EnShared.SymbolsLayout)
            }

            SpecialKey.Emojis -> {
                setLayout(EnShared.EmojiLayout)
            }

            Close -> {
                if (!layoutFlow.value.isRootLayout) {
                    showAlphabetLayout()
                } else {
                    consumed = false
                }
            }

            else -> {
                consumed = false
            }
        }
        if (!consumed) {
            delegateCallback?.onSpecialKeyReleased(key)
        }
    }

    /** Called by IME after each character to handle system-requested caps. */
    override fun setCapsMode(enabled: Boolean) {
        if (capsMode == CapsMode.Locked) return
        capsMode = if (enabled) CapsMode.Single else CapsMode.Off
        when (layoutFlow.value) {
            // only update the layout if we were already showing letters
            lowerCaseLayout, upperCaseLayout, capsLockedLayout -> showAlphabetLayout()
            else -> {}
        }
    }

    override fun onKeyLongPressed(code: Int) {
        heldKeys.remove(code)?.cancel()
        if (EnShared.extendedCharMapping.containsKey(code)) {
            haptic()
            setLayout(EnShared.ExtendedCharKeyboard(code))
            // Mark the in-flight gesture as spent rather than parking a job in
            // heldKeys: the key that owns the gesture is gone with the layout
            // swap, so its release may never arrive to clear the entry, and a
            // stale entry would swallow the next ordinary tap on this letter.
            extendedCharRoot = code
            return
        }
        delegateCallback?.onKeyLongPressed(code)
        heldKeys[code] = viewModelScope.launch {
            while (isActive) {
                delay(REPEAT_INTERVAL_MS)
                delegateCallback?.onKeyRepeated(code)
            }
        }
    }

    override fun onSpecialKeyLongPressed(key: SpecialKey) {
        heldSpecialKeys[key]?.cancel()
        val allowRepeats = when (key) {
            SpecialKey.UpCase, SpecialKey.DownCase -> {
                capsMode = if (capsMode == CapsMode.Locked) CapsMode.Off else CapsMode.Locked
                heldSpecialKeys[key] = viewModelScope.launch { }
                showAlphabetLayout()
                // don't allow repeats since we switched layouts and the original button is gone
                false
            }

            else -> true
        }
        haptic()
        delegateCallback?.onSpecialKeyLongPressed(key)
        if (allowRepeats) {
            heldSpecialKeys[key] = viewModelScope.launch {
                while (isActive) {
                    delay(REPEAT_INTERVAL_MS)
                    delegateCallback?.onSpecialKeyRepeated(key)
                }
            }
        }
    }

    override fun onSubmitWord(word: CharSequence) {
        delegateCallback?.onSubmitWord("$word ")
    }

    override fun onSwipeStarted() {
        if (keyboardOptionsFlow.value.swipeEnabled) {
            swipeActive = true
        }
    }

    override fun onSwipeLayoutReady(
        letters: String,
        cx: FloatArray,
        cy: FloatArray
    ) {
        swipeCallback?.onSwipeLayoutReady(letters, cx, cy)
    }

    override fun onSwipeCompleted(
        x: FloatArray,
        y: FloatArray,
        t: FloatArray
    ): List<SwipeResult> {
        val results = swipeCallback?.onSwipeCompleted(x,y,t) ?: emptyList()
        swipeActive = false
        if (results.isNotEmpty()) {
            swipeCallback?.getWordForResult(results[0])
                ?.let(this::onSubmitWord)
        }
        return results
    }

    override fun getWordForResult(swipeResult: SwipeResult) = swipeCallback?.getWordForResult(swipeResult)

    override fun onCleared() {
        super.onCleared()
        cancelHeldKeys()
    }
}
