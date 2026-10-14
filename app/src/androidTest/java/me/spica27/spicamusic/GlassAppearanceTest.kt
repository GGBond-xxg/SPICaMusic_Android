package me.spica27.spicamusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.theme.LocalGlassBackdrop
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import me.spica27.spicamusic.ui.theme.glassSurface
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingGlassKeepsContentStateAndSamplesChangingBackdrop() {
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Textured)
        var source by mutableStateOf(Color.Red)
        var dark by mutableStateOf(false)
        var creations = 0
        compose.setContent {
            SPICaMusicTheme(darkTheme = dark, themeColor = Color.Blue, themeColorStyle = style, animateColors = false) {
                val identity = remember { ++creations }
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(source))
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        Box(Modifier.size(160.dp).testTag("glass").glassSurface(RoundedCornerShape(24.dp), Color.Gray))
                    }
                    Text("identity=$identity")
                }
            }
        }
        compose.runOnIdle { style = ThemeColorStyle.Glass }
        compose.onNodeWithText("identity=1").assertExists()

        fun centerColor(): Color {
            val image = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
            return image[image.width / 2, image.height / 2]
        }
        val red = centerColor()
        assertTrue("Glass must sample the red source", red.red > red.blue + 0.1f)
        compose.runOnIdle {
            source = Color.Blue
            dark = true
        }
        val blue = centerColor()
        assertTrue("Glass must update when the source changes", blue.blue > blue.red + 0.1f)
        compose.runOnIdle { style = ThemeColorStyle.Textured }
        compose.onNodeWithText("identity=1").assertExists()
        assertEquals(1, creations)
    }
}
