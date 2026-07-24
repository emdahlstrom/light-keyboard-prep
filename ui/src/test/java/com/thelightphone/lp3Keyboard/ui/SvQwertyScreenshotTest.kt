package com.thelightphone.lp3Keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.thelightphone.lp3Keyboard.ui.layout.Layout
import com.thelightphone.lp3Keyboard.ui.layout.SvQwerty
import com.thelightphone.lp3Keyboard.ui.viewmodel.defaultEmojis
import org.junit.Rule
import org.junit.Test

// Renders the Swedish layout at exact LP3 resolution (1080x1240, 480dpi) on the
// JVM, no emulator needed. Run: ./gradlew :ui:recordPaparazziDebug
class SvQwertyScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 1080,
            screenHeight = 1240,
            density = Density.XXHIGH,
            xdpi = 480,
            ydpi = 480,
        ),
        maxPercentDifference = 100.0,
    )

    private fun snap(layout: Layout, displayVoice: Boolean) {
        paparazzi.snapshot {
            Lp3KeyboardTheme(DarkKeyboardColors) {
                Column(verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxSize()) {
                    Lp3KeyboardWrapper(
                        layout,
                        KeyboardOptions(
                            defaultEmojis,
                            displayReturn = true,
                            displayVoice = displayVoice,
                            enableKeyAnimation = false,
                            swipeEnabled = false
                        ),
                        LayoutOptions(displayCloseButton = true),
                        previewCallback,
                        null
                    )
                }
            }
        }
    }

    @Test
    fun svQwertyLower() = snap(SvQwerty.LowerCaseLayout, displayVoice = false)

    @Test
    fun svQwertyUpper() = snap(SvQwerty.UpperCaseLayout, displayVoice = false)

    @Test
    fun svQwertyLowerWithVoice() = snap(SvQwerty.LowerCaseLayout, displayVoice = true)
}
