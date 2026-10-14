package me.spica27.spicamusic.ui.theme

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import me.spica27.navkit.scene.StackScene
import me.spica27.navkit.stack.LocalSceneContainer

val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun GlassSceneContainers(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSceneContainer provides { scene, body ->
            if (scene is StackScene) GlassBackdropHost(content = body) else body()
        },
        content = content,
    )
}

/** Opaque page wallpaper prevents a pushed scene from revealing the previous page. */
@Composable
fun Modifier.appPageBackground(color: Color = MaterialTheme.colorScheme.background): Modifier {
    if (!LocalGlassEnabled.current) return background(color)
    val colors = MaterialTheme.colorScheme
    return drawBehind {
        drawRect(colors.background)
        drawRect(
            Brush.radialGradient(
                listOf(colors.primaryContainer, Color.Transparent),
                Offset(size.width * 0.05f, size.height * 0.2f),
                size.width * 0.95f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(colors.tertiaryContainer, Color.Transparent),
                Offset(size.width * 0.95f, size.height * 0.65f),
                size.width * 0.9f,
            ),
        )
    }
}

/** The recorded wallpaper is a sibling of the glass surfaces, avoiding recursive sampling. */
@Composable
internal fun GlassBackdropHost(content: @Composable () -> Unit) {
    val enabled = LocalGlassEnabled.current
    val backdrop = rememberLayerBackdrop()
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        if (enabled) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                drawRect(colors.background)
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.primaryContainer, Color.Transparent),
                        Offset(size.width * 0.05f, size.height * 0.2f),
                        size.width * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.tertiaryContainer, Color.Transparent),
                        Offset(
                            size.width * 0.95f,
                            size.height * 0.65f,
                        ),
                        size.width * 0.9f,
                    ),
                )
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides (if (enabled) backdrop else null), content = content)
    }
}

/** Real background blur and edge refraction; old Android versions retain a readable tint. */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    color: Color,
    tint: Color? = null,
    opacity: Float = 0.48f,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (!LocalGlassEnabled.current || backdrop == null) return background(color, shape)
    val surfaceTint = tint ?: MaterialTheme.colorScheme.surface
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return background(surfaceTint.copy(alpha = 0.9f), shape)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(6.dp.toPx())
            if (shape is CornerBasedShape) lens(16.dp.toPx(), 24.dp.toPx())
        },
        shadow = null,
        onDrawSurface = { drawRect(surfaceTint.copy(alpha = opacity * surfaceTint.alpha)) },
    )
}
