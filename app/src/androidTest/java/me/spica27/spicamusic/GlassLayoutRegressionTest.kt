package me.spica27.spicamusic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.spica27.spicamusic.common.entity.GlassSettings
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassLayoutRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun updateActionsWrapWithGapsAtLargeFontAndRemainClickable() {
        val clicks = mutableSetOf<String>()
        val labels = listOf("Cancel", "Ignore", "Upgrade")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = ThemeColorStyle.Glass, animateColors = false) {
                    GlassAlertDialog(onDismissRequest = {}, title = { Text("New version available") }, confirmButton = {
                        Box(Modifier.width(240.dp)) {
                            GlassDialogActions {
                                labels.forEach { label ->
                                    GlassTextButton({ clicks += label }, Modifier.testTag(label)) { Text(label) }
                                }
                            }
                        }
                    })
                }
            }
        }
        val bounds = labels.map { compose.onNodeWithTag(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val a = bounds[i]
                val b = bounds[j]
                assertTrue(
                    "Actions must have horizontal or vertical space: $a / $b",
                    a.right + 7.dp <= b.left || b.right + 7.dp <= a.left || a.bottom + 7.dp <= b.top || b.bottom + 7.dp <= a.top,
                )
            }
        }
        assertTrue("Large labels must wrap on a narrow dialog", bounds.map { it.top }.distinct().size > 1)
        labels.forEach { compose.onNodeWithTag(it).performClick() }
        compose.runOnIdle { assertEquals(labels.toSet(), clicks) }
    }

    @Test fun smoothPanelsReallyBlurBackdropInsteadOfOnlyTintingIt() {
        var radius by mutableIntStateOf(0)
        compose.setContent {
            SPICaMusicTheme(
                themeColor = Color.Blue,
                themeColorStyle = ThemeColorStyle.Glass,
                glassSettings = GlassSettings(blur = radius),
                animateColors = false,
            ) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.size(160.dp)) {
                    Canvas(Modifier.matchParentSize().layerBackdrop(backdrop)) {
                        drawRect(Color.Red)
                        drawRect(Color.Blue, topLeft = Offset(size.width / 2, 0f), size = Size(size.width / 2, size.height))
                    }
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .testTag(
                                    "frost",
                                ).glassSurface(
                                    RoundedCornerShape(16.dp),
                                    Color.Transparent,
                                    tint = Color.Transparent,
                                    opacity = 0f,
                                    frosted = true,
                                ),
                        )
                    }
                }
            }
        }
        val sharp = compose.onNodeWithTag("frost").captureToImage().toPixelMap()
        compose.runOnIdle { radius = 4 }
        val blurred = compose.onNodeWithTag("frost").captureToImage().toPixelMap()
        val x = blurred.width / 2 - 3
        val y = blurred.height / 2
        assertTrue("Blur must mix the blue side into the red side", blurred[x, y].blue > sharp[x, y].blue + 0.05f)
        assertTrue("Blur must preserve the red source too", blurred[x, y].red > 0.2f)
    }
}
