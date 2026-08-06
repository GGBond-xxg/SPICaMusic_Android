package me.spica27.spicamusic.ui.widget

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Returns the status-bar height without exposing the transient zero inset reported while the
 * activity window is attaching.
 *
 * The platform dimension is available before the first Compose inset dispatch, so using it as a
 * floor keeps edge-to-edge content in the same position on the first and subsequent frames.
 */
@Composable
fun stableStatusBarTopPadding(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current
    val platformHeightPx =
        remember(context) {
            val resourceId =
                context.resources.getIdentifier(
                    "status_bar_height",
                    "dimen",
                    "android",
                )
            if (resourceId != 0) {
                context.resources.getDimensionPixelSize(resourceId)
            } else {
                0
            }
        }
    val fallbackHeightPx = with(density) { 24.dp.roundToPx() }
    val insetHeightPx = WindowInsets.statusBars.getTop(density)
    return with(density) {
        maxOf(insetHeightPx, platformHeightPx, fallbackHeightPx).toDp()
    }
}
