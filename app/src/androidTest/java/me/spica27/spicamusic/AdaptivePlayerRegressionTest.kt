package me.spica27.spicamusic

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.player.api.PlayMode
import me.spica27.spicamusic.ui.home.page.FinderActionPill
import me.spica27.spicamusic.ui.player.AdaptivePlayerViewport
import me.spica27.spicamusic.ui.player.PlayerPlaybackBottomSection
import me.spica27.spicamusic.ui.theme.EqualHeightActionRow
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class AdaptivePlayerRegressionTest {
    @get:Rule(order = 0)
    val englishLocale = EnglishAppLocaleRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun wrappedPairHasEqualHeightAndGapAtEveryFontScale() {
        var scale by mutableFloatStateOf(1f)
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        compose.setContent {
            English(scale) {
                SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = style, animateColors = false) {
                    EqualHeightActionRow(Modifier.width(280.dp)) {
                        FinderActionPill("Play All", Icons.Default.Add, {}, Modifier.weight(1f).fillMaxHeight().testTag("first"))
                        FinderActionPill("Save as Playlist", Icons.Default.Add, {}, Modifier.weight(1f).fillMaxHeight().testTag("second"))
                    }
                }
            }
        }
        for (theme in listOf(ThemeColorStyle.Glass, ThemeColorStyle.Flat)) {
            for (font in listOf(1f, 1.5f, 2f)) {
                compose.runOnIdle {
                    style = theme
                    scale = font
                }
                val first = compose.onNodeWithTag("first").getUnclippedBoundsInRoot()
                val second = compose.onNodeWithTag("second").getUnclippedBoundsInRoot()
                assertEquals(first.bottom - first.top, second.bottom - second.top)
                assertEquals(first.top, second.top)
                assertTrue(second.left - first.right >= 8.dp)
                compose.onNodeWithText("Save as Playlist").assertIsDisplayed()
            }
        }
    }

    @Test fun landscapeLargeFontPlayerControlsAndTimerActionsRemainReachable() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        // ComponentActivity does not implement MainActivity's CustomAdapt contract.
        // Match the production short-edge density after its orientation recreation.
        compose.activityRule.scenario.onActivity {
            me.jessyan.autosize.AutoSize
                .autoConvertDensity(it, 375f, false)
        }
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        var generation by mutableIntStateOf(0)
        var nextClicks = 0
        var timerMinutes = 0
        compose.setContent {
            English(2f) {
                SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = style, animateColors = false) {
                    key(generation) {
                        AdaptivePlayerViewport(Modifier.fillMaxSize()) {
                            PlayerPlaybackBottomSection(
                                progress = .2f,
                                currentPosition = 12000,
                                duration = 60000,
                                isPlaying = false,
                                isSeeking = false,
                                playMode = PlayMode.LOOP,
                                isLike = false,
                                onProgressChange = {},
                                onProgressChangeFinished = {},
                                onAudioQualityClick = {},
                                onPlayPauseClick = {},
                                onPreviousClick = {},
                                onNextClick = { nextClicks++ },
                                onPlayModeClick = {},
                                onFavoriteClick = {},
                                onSleepTimerSet = { timerMinutes = it },
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                }
            }
        }
        for (theme in listOf(ThemeColorStyle.Glass, ThemeColorStyle.Flat)) {
            compose.runOnIdle {
                style = theme
                generation++
                timerMinutes = 0
            }
            compose
                .onNodeWithContentDescription("Next track")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            compose.onNodeWithText("Quality & effects").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Sleep timer").performScrollTo().performClick()
            compose.onNodeWithText("Custom duration").performScrollTo().performClick()
            compose.onNode(hasText("20 minutes") and hasClickAction() and !hasText("Custom duration")).performScrollTo().performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("37")
            // Insets are delivered by Android outside the Compose test clock. Verify the
            // real IME stays open instead of passing after a layout switch loses focus.
            compose.waitUntil(5000) {
                val descriptor =
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                        "dumpsys input_method",
                    )
                android.os.ParcelFileDescriptor
                    .AutoCloseInputStream(
                        descriptor,
                    ).bufferedReader()
                    .use { it.readText().contains("mIsInputViewShown=true") }
            }
            // No scroll-to on actions: they must be visible while the keyboard is open.
            compose.waitForIdle()
            val fieldBounds = compose.onNode(hasSetTextAction()).getBoundsInRoot()
            assertTrue("The numeric field must retain usable height above the IME", fieldBounds.bottom - fieldBounds.top >= 48.dp)
            compose.onNodeWithText("Cancel").assertIsDisplayed()
            compose.onNodeWithText("Confirm").assertIsDisplayed().performClick()
            compose.onNodeWithText("Start timer").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(37, timerMinutes) }
        }
        compose.runOnIdle { assertEquals(2, nextClicks) }
    }
}

@Composable
private fun English(
    scale: Float,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val config = remember(context) { Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) } }
    val english = remember(context) { context.createConfigurationContext(config) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalContext provides english,
        LocalConfiguration provides config,
        LocalDensity provides Density(density.density, scale),
        content = content,
    )
}
