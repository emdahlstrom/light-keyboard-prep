package com.thelightphone.lp3Keyboard.ui

import com.thelightphone.lp3Keyboard.ui.layout.EnQwerty
import com.thelightphone.lp3Keyboard.ui.layout.EnShared
import com.thelightphone.lp3Keyboard.ui.viewmodel.CapsMode
import com.thelightphone.lp3Keyboard.ui.viewmodel.EnQwertyLp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3RepeatableKeyboardCallback
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    /** Opens the extended-char layout the way a real long press does. */
    private fun longPressForAccents(code: Int) = vm.apply {
        onKeyPressed(code)
        onKeyLongPressed(code)
        // The layout swap disposes the key the finger is still resting on, so
        // Compose ends that in-flight gesture with a cancel rather than a release.
        onKeyCancelled(code)
    }

    @Test
    fun `the cancel from the long press does not dismiss the extended char layout`() {
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
        // The long press never commits the root letter either.
        verify(exactly = 0) { callback.onKeyReleased('i'.code) }
    }

    @Test
    fun `tapping an accent commits it and returns to the previous layout`() {
        longPressForAccents('i'.code)

        vm.onKeyPressed('î'.code)
        vm.onKeyReleased('î'.code)

        verify(exactly = 1) { callback.onKeyReleased('î'.code) }
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)
    }

    @Test
    fun `sliding off a key does not commit it`() {
        vm.onKeyPressed('q'.code)
        vm.onKeyCancelled('q'.code)

        verify(exactly = 0) { callback.onKeyReleased('q'.code) }
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)
    }

    @Test
    fun `sliding off a key inside the extended char layout dismisses it`() {
        longPressForAccents('i'.code)

        vm.onKeyPressed('î'.code)
        vm.onKeyCancelled('î'.code)

        verify(exactly = 0) { callback.onKeyReleased('î'.code) }
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)
    }

    /**
     * A slow swipe rests on the first key past the long-press timeout, so a
     * repeat job is parked in `heldKeys` when the finger moves off and the
     * gesture ends in a cancel. Nothing may be committed: `onKeyCancelled`
     * exists precisely so a drag does not type the keys it crosses.
     */
    @Test
    fun `a swipe that dwells on the first key commits nothing`() {
        vm.onKeyPressed('q'.code)
        vm.onKeyLongPressed('q'.code)

        vm.onKeyCancelled('q'.code)

        verify(exactly = 0) { callback.onKeyReleased('q'.code) }
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)
    }

    /**
     * The layout swap disposes every key on screen, not just the one the long
     * press started on. A second key already held for repeats therefore also
     * reports a cancel while the extended-char layout is showing — and it must
     * not tear the layout down either.
     */
    @Test
    fun `a second held key disposed by the layout swap does not dismiss the popup`() {
        vm.onKeyPressed('q'.code)
        vm.onKeyLongPressed('q'.code)
        vm.onKeyPressed('a'.code)
        vm.onKeyLongPressed('a'.code)
        assertTrue(vm.layoutFlow.value is EnShared.ExtendedCharKeyboard)

        vm.onKeyCancelled('q'.code)

        assertTrue(
            "the disposed second key's cancel must not dismiss the layout either",
            vm.layoutFlow.value is EnShared.ExtendedCharKeyboard
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `sliding off a key held for repeats stops the repeats`() {
        val scheduler = mainDispatcherRule.dispatcher.scheduler
        // 'q' has no accents, so the long press takes the key-repeat path.
        vm.onKeyPressed('q'.code)
        vm.onKeyLongPressed('q'.code)
        scheduler.advanceTimeBy(1_000)
        verify(atLeast = 1) { callback.onKeyRepeated('q'.code) }
        clearMocks(callback, answers = false)

        vm.onKeyCancelled('q'.code)
        scheduler.advanceTimeBy(10_000)

        // The repeat job is cancelled and the release is still swallowed.
        verify(exactly = 0) { callback.onKeyRepeated('q'.code) }
        verify(exactly = 0) { callback.onKeyReleased('q'.code) }
        assertSame(EnQwerty.LowerCaseLayout, vm.layoutFlow.value)
    }
}
