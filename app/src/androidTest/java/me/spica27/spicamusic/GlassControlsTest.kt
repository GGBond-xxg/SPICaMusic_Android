package me.spica27.spicamusic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.player.SleepTimerSheet
import me.spica27.spicamusic.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class GlassControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassSheetDoesNotExposePageWhenBackdropSampleIsTransparent() {
        var pageColor by mutableStateOf(Color.Red)
        var paintSource by mutableStateOf(false)
        compose.setContent {
            SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = ThemeColorStyle.Glass, animateColors = false) {
                val emptyBackdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize().background(pageColor)) {
                    Canvas(Modifier.fillMaxSize().layerBackdrop(emptyBackdrop)) {
                        if (paintSource) drawRect(pageColor)
                    }
                    CompositionLocalProvider(LocalGlassBackdrop provides emptyBackdrop) {
                        GlassModalBottomSheet(
                            onDismissRequest = {},
                            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = Color.Blue,
                            dragHandle = null,
                        ) {
                            Box(Modifier.fillMaxWidth().height(200.dp).testTag("sheetBody"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("sheetBody").assertIsDisplayed()
        val pixels = compose.onNodeWithTag("sheetBody").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue("The sheet must retain its blue tint: $center", center.blue > center.red + 0.5f)
        compose.runOnIdle {
            pageColor = Color.Green
            paintSource = true
        }
        val changed = compose.onNodeWithTag("sheetBody").captureToImage().toPixelMap()
        for (fraction in listOf(.1f, .5f, .9f)) {
            val x = (pixels.width * fraction).toInt()
            val y = pixels.height / 2
            val before = pixels[x, y]
            val after = changed[x, y]
            assertEquals("Activity backdrop must not leak across windows", before.red, after.red, .01f)
            assertEquals(before.green, after.green, .01f)
            assertEquals(before.blue, after.blue, .01f)
        }
    }

    @Test fun glassSleepTimerKeepsBottomActionVisibleAndClickable() {
        var cancelled = false
        var dismissed by mutableStateOf(false)
        val cancelLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.sleep_timer_cancel)
        compose.setContent {
            SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = ThemeColorStyle.Glass, animateColors = false) {
                if (!dismissed) {
                    SleepTimerSheet(
                        durationMs = 1_200_000L,
                        remainingMs = 600_000L,
                        onSetTimer = {},
                        onCancelTimer = { cancelled = true },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }
        val closeLabel = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.close)
        compose.onNodeWithContentDescription(closeLabel).assertDoesNotExist()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { dismissed }
        compose.runOnIdle { assertTrue(cancelled) }
        compose.onNodeWithText(cancelLabel).assertDoesNotExist()
    }

    @Test fun glassControlsKeepToggleRangeEditingAndDisabledBehaviorAcrossThemeChanges() {
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        var checked by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var value by mutableFloatStateOf(0.25f)
        var text by mutableStateOf("")
        var clicks = 0
        var commits = 0
        compose.setContent {
            SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = style, animateColors = false) {
                Column {
                    GlassSwitch(checked, { checked = it }, enabled = enabled)
                    GlassSlider(
                        value,
                        { value = it },
                        Modifier.testTag("strength"),
                        enabled = enabled,
                        onValueChangeFinished = { commits++ },
                    )
                    GlassOutlinedTextField(text, { text = it }, Modifier.testTag("search"), singleLine = true)
                    GlassButton({ clicks++ }, enabled = enabled) { Text("Action") }
                }
            }
        }
        compose
            .onNode(isToggleable())
            .assertIsOff()
            .performClick()
            .assertIsOn()
        compose.onNodeWithTag("strength").performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        compose.runOnIdle {
            assertEquals(0.8f, value, 0.001f)
            assertEquals(1, commits)
        }
        compose.onNodeWithTag("search").performTextInput("Glass search")
        compose.onNodeWithText("Action").performClick()
        compose.runOnIdle { enabled = false }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithTag("strength").assertIsNotEnabled()
        compose.onNodeWithText("Action").assertIsNotEnabled()
        compose.runOnIdle { style = ThemeColorStyle.Textured }
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithTag("search").assertTextEquals("Glass search")
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(0.8f, value, 0.001f)
        }
    }
}
