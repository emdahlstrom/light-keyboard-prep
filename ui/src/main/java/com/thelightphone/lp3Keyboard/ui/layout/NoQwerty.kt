package com.thelightphone.lp3Keyboard.ui.layout

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.thelightphone.lp3Keyboard.ui.DefaultRow
import com.thelightphone.lp3Keyboard.ui.FinalRow
import com.thelightphone.lp3Keyboard.ui.FirstRow
import com.thelightphone.lp3Keyboard.ui.ICON_KEY_WIDTH_DP
import com.thelightphone.lp3Keyboard.ui.IconKey
import com.thelightphone.lp3Keyboard.ui.Key
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardCallback
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardLayoutCapture
import com.thelightphone.lp3Keyboard.ui.MultiLabelKey
import com.thelightphone.lp3Keyboard.ui.NARROW_KEY_WIDTH_DP
import com.thelightphone.lp3Keyboard.ui.R
import com.thelightphone.lp3Keyboard.ui.SecondRow
import com.thelightphone.lp3Keyboard.ui.SpecialKey

private val NoQwertySwipeConfig: SwipeConfig by lazy {
    object : Lp3KeyboardLayoutCapture("abcdefghijklmnopqrstuvwxyzåøæ") {
        override fun report(code: Int, bounds: Rect) {
            val lower = code.toChar().lowercaseChar()
            if (lower !in letters) return
            // onGloballyPositioned fires on every layout pass; skip identical
            // writes so we don't churn the snapshot or re-fire boundsFlow.
            if (letterBounds[lower.code] == bounds) return
            letterBounds[lower.code] = bounds
        }
    }
}


/**
 * Third row at the same 32dp key pitch as the 11-key rows above, so the whole
 * grid keeps one rhythm (Gboard/iOS/AOSP shrink all Swedish rows uniformly).
 */
@Composable
private fun ColumnScope.NoThirdRow(
    characters: String,
    callback: Lp3KeyboardCallback,
    swipeConfig: SwipeConfig?,
    options: KeyboardOptions,
    leftButton: @Composable RowScope.() -> Unit
) {
    DefaultRow {
        leftButton()
        for (char in characters) {
            Key(char.code, callback, swipeConfig, options.enableKeyAnimation, width = NARROW_KEY_WIDTH_DP.dp)
        }
        IconKey(
            R.drawable.back_lp3,
            SpecialKey.Backspace,
            callback,
            options.enableKeyAnimation,
            width = ICON_KEY_WIDTH_DP.dp,
            modifier = Modifier.padding(10.dp).padding(start = 8.dp, bottom = 6.dp)
        )
    }
}


/** The layouts for Norwegian QWERTY. */
object NoQwerty {
    object LowerCaseLayout : Layout {
        override val isRootLayout: Boolean
            get() = true

        override val swipeConfig: SwipeConfig
            get() = NoQwertySwipeConfig

        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            FirstRow("qwertyuiopå", callback, swipeConfig, options.enableKeyAnimation)
            SecondRow("asdfghjkløæ", callback, swipeConfig, options.enableKeyAnimation)
            NoThirdRow("zxcvbnm", callback, swipeConfig, options) {
                IconKey(
                    R.drawable.up_lp3,
                    SpecialKey.UpCase,
                    callback,
                    options.enableKeyAnimation,
                    width = ICON_KEY_WIDTH_DP.dp,
                    modifier = Modifier.padding(12.dp).padding(bottom = 6.dp, end = 8.dp)
                )
            }
            FinalRow(options, callback) {
                MultiLabelKey("123", SpecialKey.Numbers, callback, options.enableKeyAnimation)
            }
        }
    }

    object CapsLockedLayout : Layout {
        override val isRootLayout: Boolean
            get() = true
        override val swipeConfig: SwipeConfig
            get() = NoQwertySwipeConfig

        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            FirstRow("QWERTYUIOPÅ", callback, swipeConfig, options.enableKeyAnimation)
            SecondRow("ASDFGHJKLØÆ", callback, swipeConfig, options.enableKeyAnimation)
            NoThirdRow("ZXCVBNM", callback, swipeConfig, options) {
                IconKey(
                    R.drawable.caps_lp3,
                    SpecialKey.DownCase,
                    callback,
                    options.enableKeyAnimation,
                    width = ICON_KEY_WIDTH_DP.dp,
                    modifier = Modifier.padding(9.dp).padding(bottom = 2.dp, end = 4.dp)
                )
            }
            FinalRow(options, callback) {
                MultiLabelKey("123", SpecialKey.Numbers, callback, options.enableKeyAnimation)
            }
        }
    }

    object UpperCaseLayout : Layout {
        override val isRootLayout: Boolean
            get() = true
        override val swipeConfig: SwipeConfig
            get() = NoQwertySwipeConfig

        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            FirstRow("QWERTYUIOPÅ", callback, swipeConfig, options.enableKeyAnimation)
            SecondRow("ASDFGHJKLØÆ", callback, swipeConfig, options.enableKeyAnimation)
            NoThirdRow("ZXCVBNM", callback, swipeConfig, options) {
                IconKey(
                    R.drawable.down_lp3,
                    SpecialKey.DownCase,
                    callback,
                    options.enableKeyAnimation,
                    width = ICON_KEY_WIDTH_DP.dp,
                    modifier = Modifier.padding(12.dp).padding(bottom = 6.dp, end = 8.dp)
                )
            }
            FinalRow(options, callback) {
                MultiLabelKey("123", SpecialKey.Numbers, callback, options.enableKeyAnimation)
            }
        }
    }
}
