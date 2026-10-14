package me.spica27.spicamusic

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.player.TimerMinutesDialog
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TimerMinutesInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inputRejectsOutOfRangeAndConfirmsTypedMinutes() {
        var result = 0
        var open by mutableStateOf(true)
        val confirm = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.confirm)
        compose.setContent {
            SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = ThemeColorStyle.Glass) {
                if (open) {
                    TimerMinutesDialog(20, {
                        result = it
                        open = false
                    }, { open = false })
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("241")
        compose.onNodeWithText(confirm).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("37")
        compose.onNodeWithText(confirm).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(37, result) }
    }
}
