package me.spica27.spicamusic

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.albumdetail.PlayButtons
import me.spica27.spicamusic.ui.artistdetail.ArtistPlayButtons
import me.spica27.spicamusic.ui.dialog.ControlButton
import me.spica27.spicamusic.ui.player.FeaturedQualityCard
import me.spica27.spicamusic.ui.playlistdetail.ActionRow
import me.spica27.spicamusic.ui.theme.AdaptiveActionGrid
import me.spica27.spicamusic.ui.theme.EqualHeightActionRow
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalLayoutApi::class)
class ActionRowsRegressionTest {
    @get:Rule(order = 0)
    val english = EnglishAppLocaleRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun portraitActions() = verifyActions(false)

    @Test fun landscapeActions() = verifyActions(true)

    private fun verifyActions(landscape: Boolean) {
        val orientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        compose.activityRule.scenario.onActivity { it.requestedOrientation = orientation }
        compose.waitUntil(10000) {
            compose.activity.resources.configuration.orientation ==
                if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        compose.activityRule.scenario.onActivity {
            me.jessyan.autosize.AutoSize
                .autoConvertDensity(it, 375f, !landscape)
        }
        var font by mutableFloatStateOf(1f)
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        var panel by mutableIntStateOf(0)
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font)) {
                SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = style, animateColors = false) {
                    Box(Modifier.fillMaxWidth()) {
                        when (panel) {
                            0 -> PlayButtons({ clicks++ }, { clicks++ })
                            1 -> ArtistPlayButtons({ clicks++ }, { clicks++ })
                            2 -> ActionRow(false, true, { clicks++ }, { clicks++ })
                            3 ->
                                AdaptiveActionGrid(
                                    listOf("Play Next", "Add", "Remove from favorites").map { label ->
                                        { modifier -> ControlButton(modifier, label, Icons.Default.Add, { clicks++ }) }
                                    },
                                )
                            else ->
                                EqualHeightActionRow(Modifier.fillMaxWidth().padding(16.dp)) {
                                    FeaturedQualityCard(
                                        "Immersive sound",
                                        "Audio Vivid",
                                        "Surround sound provided by the source",
                                        Icons.Default.Add,
                                        false,
                                        "SVIP",
                                        { clicks++ },
                                        Modifier.weight(1f).fillMaxHeight(),
                                    )
                                    FeaturedQualityCard(
                                        "Dolby Atmos",
                                        "Surround Audio",
                                        "Immersive multichannel audio provided by the source",
                                        Icons.Default.Add,
                                        true,
                                        "SVIP",
                                        { clicks++ },
                                        Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                        }
                    }
                }
            }
        }
        for (theme in listOf(ThemeColorStyle.Glass, ThemeColorStyle.Flat)) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                for (screen in 0..4) {
                    compose.runOnIdle {
                        style = theme
                        font = scale
                        panel = screen
                    }
                    val actions = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                    assertEquals(if (screen == 3) 3 else 2, actions.size)
                    val bounds = actions.map { it.boundsInRoot }.sortedWith(compareBy({ it.top }, { it.left }))
                    bounds.zipWithNext().forEach { (a, b) ->
                        if (kotlin.math.abs(a.top - b.top) < 1f) {
                            assertEquals("$theme/$scale/$screen height", a.height, b.height, 1f)
                            assertTrue("Actions must remain separated", b.left > a.right)
                        } else {
                            assertTrue("Wrapped rows must remain separated", b.top > a.bottom)
                        }
                    }
                    compose
                        .onAllNodes(hasClickAction())
                        .onFirst()
                        .assertIsDisplayed()
                        .performClick()
                    if (scale == 2f) {
                        val context =
                            androidx.test.platform.app.InstrumentationRegistry
                                .getInstrumentation()
                                .targetContext
                        val file = java.io.File(context.externalCacheDir, "actions-$landscape-$theme-$screen.png")
                        file.outputStream().use {
                            compose.onRoot().captureToImage().asAndroidBitmap().compress(
                                android.graphics.Bitmap.CompressFormat.PNG,
                                100,
                                it,
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(30, clicks) }
    }
}
