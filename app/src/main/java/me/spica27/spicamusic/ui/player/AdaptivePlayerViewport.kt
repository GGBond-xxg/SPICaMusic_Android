package me.spica27.spicamusic.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
private fun minimumPlayerHeight() = (640f + 180f * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)).dp

@Composable
internal fun playerNeedsVerticalScroll() = LocalConfiguration.current.screenHeightDp.dp < minimumPlayerHeight()

/** Keep artwork, metadata and every control reachable when the window cannot fit them. */
@Composable
internal fun AdaptivePlayerViewport(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val minimumHeight = minimumPlayerHeight()
    BoxWithConstraints(modifier) {
        val height = maxHeight.coerceAtLeast(minimumHeight)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Box(contentModifier.widthIn(max = 600.dp).fillMaxWidth().height(height), content = content)
        }
    }
}
