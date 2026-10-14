package me.spica27.spicamusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.home.page.FinderActionPill
import me.spica27.spicamusic.ui.home.page.LibraryCommandPill
import me.spica27.spicamusic.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class EnglishControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longEnglishActionsWrapAndGrowInGlassAndFlatThemes() {
        var style by mutableStateOf<ThemeColorStyle>(ThemeColorStyle.Glass)
        val labels = listOf("Save as a new playlist", "Manage library sources", "Create a new playlist")
        var clicks = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                SPICaMusicTheme(themeColor = Color.Blue, themeColorStyle = style, animateColors = false) {
                    Column(Modifier.width(200.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FinderActionPill(labels[0], Icons.Default.Add, { clicks++ }, Modifier.fillMaxWidth())
                        LibraryCommandPill(labels[1], Icons.Default.Add, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, {
                            clicks++
                        }, Modifier.fillMaxWidth())
                        GlassButton({ clicks++ }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(labels[2]) }
                    }
                }
            }
        }
        for (theme in listOf(ThemeColorStyle.Glass, ThemeColorStyle.Flat)) {
            compose.runOnIdle { style = theme }
            labels.forEach { label ->
                val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                val results = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertTrue("$theme: long label should wrap: $label", layout.lineCount > 1)
                assertFalse("$theme: label must not be clipped or ellipsized: $label", layout.hasVisualOverflow)
                node.performClick()
            }
        }
        compose.runOnIdle { assertEquals(6, clicks) }
    }

    @Test fun backArrowVisiblePixelsStayCenteredInBothDirections() {
        var direction by mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.size(48.dp).background(Color.Black).testTag("back"), contentAlignment = Alignment.Center) {
                    Icon(AppBackArrow, contentDescription = "Back", modifier = Modifier.size(24.dp), tint = Color.White)
                }
            }
        }
        for (layoutDirection in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            compose.runOnIdle { direction = layoutDirection }
            val pixels = compose.onNodeWithTag("back").captureToImage().toPixelMap()
            val points =
                (0 until pixels.height).flatMap { y ->
                    (0 until pixels.width).mapNotNull { x ->
                        if (pixels[x, y].red >
                            0.8f
                        ) {
                            x to y
                        } else {
                            null
                        }
                    }
                }
            assertTrue(points.isNotEmpty())
            val centerX = (points.minOf { it.first } + points.maxOf { it.first }) / 2f
            val centerY = (points.minOf { it.second } + points.maxOf { it.second }) / 2f
            assertTrue("Arrow horizontal offset in $layoutDirection", abs(centerX - (pixels.width - 1) / 2f) <= 1.5f)
            assertTrue("Arrow vertical offset in $layoutDirection", abs(centerY - (pixels.height - 1) / 2f) <= 1.5f)
        }
    }
}
