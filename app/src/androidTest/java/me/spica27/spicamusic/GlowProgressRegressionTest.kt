package me.spica27.spicamusic

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.common.entity.ProgressBarStyle
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.player.PlayerProgressSection
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import me.spica27.spicamusic.ui.widget.audio_seekbar.GlowParticleSeekBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlowProgressRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun shaderRendersAndAccessibleSeekingWorksInAllThemes() {
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        var progress by mutableStateOf(.25f)
        compose.setContent {
            SPICaMusicTheme(themeColor = Color.Cyan, themeColorStyle = style, animateColors = false) {
                GlowParticleSeekBar(progress, { progress = it }, animationsEnabled = false, modifier = Modifier.width(280.dp))
            }
        }
        for (next in listOf(ThemeColorStyle.Glass, ThemeColorStyle.Flat, ThemeColorStyle.Textured)) {
            compose.runOnIdle {
                style = next
                progress = .25f
            }
            compose
                .onNode(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                ).performSemanticsAction(SemanticsActions.SetProgress) {
                    it(.75f)
                }
            compose.runOnIdle { assertEquals(.75f, progress, .001f) }
            val image = compose.onRoot().captureToImage()
            assertTrue(image.width > 0 && image.height > 0)
        }
    }

    @Test fun sharedPlayerGestureLayerSeeksWithGlowAndLargeText() {
        var progress by mutableStateOf(.2f)
        var finished = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                SPICaMusicTheme(themeColor = Color.Cyan, themeColorStyle = ThemeColorStyle.Glass, animateColors = false) {
                    PlayerProgressSection(
                        progress = progress,
                        currentPosition = 12000,
                        duration = 60000,
                        isPlaying = false,
                        isSeeking = false,
                        progressBarStyle = ProgressBarStyle.GlowParticle,
                        onProgressChange = { progress = it },
                        onProgressChangeFinished = { finished++ },
                        modifier = Modifier.width(280.dp),
                    )
                }
            }
        }
        compose.onRoot().performTouchInput { swipe(Offset(width * .25f, 24.dp.toPx()), Offset(width * .8f, 24.dp.toPx())) }
        compose.runOnIdle {
            assertTrue("Seeking should reach the right side", progress > .65f)
            assertTrue("Seek completion must reach the player", finished > 0)
        }
    }
}
