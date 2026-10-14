package me.spica27.spicamusic.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Fade only the material; scrolling reads stay in the drawing layer. */
@Composable
internal fun CollapsingBarBackground(
    modifier: Modifier = Modifier,
    collapse: Density.() -> Float,
) {
    Box(
        modifier
            .graphicsLayer { alpha = collapse().coerceIn(0f, 1f) }
            .glassSurface(
                RoundedCornerShape(0.dp),
                MaterialTheme.colorScheme.background,
                tint = MaterialTheme.colorScheme.surfaceContainer,
                opacity = 0.64f,
                frosted = true,
                edgeHighlight = false,
            ),
    )
}
