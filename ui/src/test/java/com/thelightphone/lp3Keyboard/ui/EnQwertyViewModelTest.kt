package com.thelightphone.lp3Keyboard.ui

import com.thelightphone.lp3Keyboard.ui.layout.EnQwerty
import com.thelightphone.lp3Keyboard.ui.layout.EnShared
import com.thelightphone.lp3Keyboard.ui.viewmodel.CapsMode
import com.thelightphone.lp3Keyboard.ui.viewmodel.EnQwertyLp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3RepeatableKeyboardCallback
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnQwertyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val callback = mockk<Lp3RepeatableKeyboardCallback>(relaxed = true)
    private val swipeCallback = mockk<Lp3KeyboardSwipeCallback<Unit>>(relaxed = true)

    private val vm = EnQwertyLp3KeyboardViewModel(
        passedCallback = callback,
        swipeCallback = swipeCallback,
    )

    private fun tapShift() = vm.apply{
        onSpecialKeyPressed(SpecialKey.UpCase)
        onSpecialKeyReleased(SpecialKey.UpCase)
    }

    @Test
    fun `onKeyPressed does not swap layout mid-gesture in one-shot caps`() {
        tapShift()
        assertEquals(CapsMode.Single, vm.capsMode)
        assertSame(EnQwerty.UpperCaseLayout, vm.layoutFlow.value)

        vm.onKeyPressed('Q'.code)
        assertSame(
            "onKeyPressed must not swap layoutFlow while a key is held down",
            EnQwerty.UpperCaseLayout,
            vm.layoutFlow.value
        )
    }

    @Test
    fun `single-shift then letter commits the capital and reverts to lowercase`() {
        tapShift()

        // Full press -> release gesture on the capital key.
        vm.onKeyPressed('Q'.code)
        vm.onKeyReleased('Q'.code)

        // The release is what commits the character downstream in the IME.
        verify(exactly = 1) { callback.onKeyReleased('Q'.code) }
        assertEquals(CapsMode.Off, vm.capsMode)
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)
    }

    /**
     * The long press swaps the whole layout, which disposes the key the finger
     * is still resting on. Compose ends that gesture with a cancel, and before
     * this was guarded the cancel dismissed the extended-char layout on the
     * frame after it appeared — the keyboard visibly flashed and no character
     * was ever typed.
     */
    @Test
    fun `long press cancel does not dismiss the extended char layout`() {
        vm.onKeyPressed('i'.code)
        vm.onKeyLongPressed('i'.code)
        assertTrue(
            "long press on a key with accents must open the extended-char layout",
            vm.layoutFlow.value is EnShared.ExtendedCharKeyboard
        )

        vm.onKeyCancelled('i'.code)

        assertTrue(
            "the cancel from the gesture that opened the layout must not dismiss it",
            vm.layoutFlow.value is EnShared.ExtendedCharKeyboard
        )
        verify(exactly = 0) { callback.onKeyReleased('i'.code) }
    }

    @Test
    fun `long press release does not dismiss the extended char layout`() {
        vm.onKeyPressed('i'.code)
        vm.onKeyLongPressed('i'.code)

        vm.onKeyReleased('i'.code)

        assertTrue(
            "the release from the gesture that opened the layout must not dismiss it",
            vm.layoutFlow.value is EnShared.ExtendedCharKeyboard
        )
        // The root letter itself is never committed by the long press.
        verify(exactly = 0) { callback.onKeyReleased('i'.code) }
    }

    @Test
    fun `tapping an accent commits it and returns to the previous layout`() {
        vm.onKeyPressed('i'.code)
        vm.onKeyLongPressed('i'.code)
        vm.onKeyCancelled('i'.code)

        vm.onKeyPressed('î'.code)
        vm.onKeyReleased('î'.code)

        verify(exactly = 1) { callback.onKeyReleased('î'.code) }
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)
    }

    @Test
    fun `ordinary tap on the root letter still types after a long press`() {
        vm.onKeyPressed('i'.code)
        vm.onKeyLongPressed('i'.code)
        // Worst case: the disposed key never reports back at all, so the
        // long-press marker is still set when the user leaves the layout.
        vm.onSpecialKeyReleased(SpecialKey.Close)
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)

        vm.onKeyPressed('i'.code)
        vm.onKeyReleased('i'.code)

        verify(exactly = 1) { callback.onKeyReleased('i'.code) }
    }
}
