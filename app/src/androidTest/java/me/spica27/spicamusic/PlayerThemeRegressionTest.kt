package me.spica27.spicamusic

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.common.entity.ProgressBarStyle
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.player.PlayerProgressSection
import me.spica27.spicamusic.ui.player.SleepTimerSheet
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class PlayerThemeRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paletteChangesPreserveRememberedPageAndScrollState() {
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Textured)
        var identity: Any? = null
        var scroll: androidx.compose.foundation.ScrollState? = null
        compose.setContent {
            SPICaMusicTheme(themeColor = Color.Cyan, themeColorStyle = style, animateColors = false) {
                val token = remember { Any() }
                val state = rememberScrollState(120)
                SideEffect {
                    identity = token
                    scroll = state
                }
                Column(Modifier.height(200.dp).verticalScroll(state)) {
                    Text("Settings")
                    Spacer(Modifier.height(1000.dp))
                }
            }
        }
        compose.waitForIdle()
        val original = identity
        for (next in listOf(
            ThemeColorStyle.Flat,
            ThemeColorStyle.Glass,
            ThemeColorStyle.Textured,
            ThemeColorStyle.Glass,
            ThemeColorStyle.Flat,
        )) {
            compose.runOnIdle { style = next }
            compose.runOnIdle {
                assertSame("Changing $next must not recreate the page", original, identity)
                assertEquals(120, scroll!!.value)
            }
        }
    }

    @Test fun englishQualityActionDoesNotOverlapTimeOrClipAtLargeFont() {
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        compose.setContent {
            EnglishLargeFont {
                SPICaMusicTheme(themeColor = Color.Cyan, themeColorStyle = style, animateColors = false) {
                    PlayerProgressSection(
                        progress = .2f,
                        currentPosition = 12000,
                        duration = 60000,
                        isPlaying = false,
                        isSeeking = false,
                        progressBarStyle = ProgressBarStyle.TimeDomainWaveform,
                        onProgressChange = {},
                        onProgressChangeFinished = {},
                        onAudioQualityClick = {},
                        modifier = Modifier.width(210.dp),
                    )
                }
            }
        }
        for (next in listOf(ThemeColorStyle.Glass, ThemeColorStyle.Flat, ThemeColorStyle.Textured)) {
            compose.runOnIdle { style = next }
            val action = compose.onNodeWithText("Quality & effects")
            assertFullText(action)
            val actionBounds = action.getUnclippedBoundsInRoot()
            val timeBounds = compose.onNodeWithText("0:12").getUnclippedBoundsInRoot()
            assertTrue(actionBounds.top >= timeBounds.bottom + 8.dp)
            action.performClick()
        }
    }

    @Test fun timerEditorStaysInSheetAndCommitsWithoutStartingUntilRequested() {
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        var generation by mutableIntStateOf(0)
        var minutes = 0
        compose.setContent {
            EnglishLargeFont {
                SPICaMusicTheme(themeColor = Color.Cyan, themeColorStyle = style, animateColors = false) {
                    key(generation) { SleepTimerSheet(null, null, { minutes = it }, {}, {}) }
                }
            }
        }
        for (next in listOf(ThemeColorStyle.Glass, ThemeColorStyle.Flat)) {
            compose.runOnIdle {
                style = next
                generation++
                minutes = 0
            }
            compose.onNodeWithText("Custom duration").performScrollTo().performClick()
            compose
                .onNode(hasText("20 minutes") and hasClickAction() and !hasText("Custom duration"))
                .performScrollTo()
                .performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("37")
            compose.onNodeWithText("Confirm").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(0, minutes) }
            compose
                .onNodeWithText("Start timer")
                .performScrollTo()
                .also { assertFullText(it) }
                .performClick()
            compose.runOnIdle { assertEquals(37, minutes) }
        }
    }

    private fun assertFullText(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("Text must fit vertically", layout.didOverflowHeight)
            assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line))
                // Compose rounds its IntSize, while line extents retain fractional pixels.
                assertTrue("Line must fit within a pixel of its measured width", layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
    }
}

@Composable
private fun EnglishLargeFont(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val config = remember { Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) } }
    val english = remember { context.createConfigurationContext(config) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalContext provides english,
        LocalConfiguration provides config,
        LocalDensity provides Density(density.density, 1.5f),
        content = content,
    )
}
