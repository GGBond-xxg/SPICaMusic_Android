package me.spica27.spicamusic.ui.theme

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

val LocalGlassSettings =
    staticCompositionLocalOf {
        me.spica27.spicamusic.common.entity
            .GlassSettings()
    }

val LocalGlassEnabled = staticCompositionLocalOf { false }
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalGlassOverlay = staticCompositionLocalOf { false }

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
            Canvas(Modifier.fillMaxSize().then(if (LocalGlassSettings.current.smoothMode) Modifier else Modifier.layerBackdrop(backdrop))) {
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
    val settings = LocalGlassSettings.current
    val alpha = (opacity * settings.opacity / 65f * surfaceTint.alpha).coerceIn(0f, 1f)
    // Small controls and repeated list cells use one cheap paint in smooth mode.
    // Rectangular bars have no lens rim, avoiding a box around the whole toolbar.
    if (settings.smoothMode || shape !is CornerBasedShape) {
        val base = if (LocalGlassOverlay.current) background(surfaceTint.copy(alpha = 1f), shape) else this
        val paint =
            base.background(
                Brush.linearGradient(
                    listOf(
                        surfaceTint.copy(alpha = (alpha + 0.08f).coerceAtMost(1f)),
                        surfaceTint.copy(alpha = alpha),
                    ),
                ),
                shape,
            )
        return if (shape is CornerBasedShape) {
            paint.border(
                0.75.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                shape,
            )
        } else {
            paint
        }
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return background(surfaceTint.copy(alpha = 0.9f), shape)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (settings.blur > 0) blur(settings.blur.dp.toPx())
            if (settings.refraction > 0) lens((8f * settings.refraction / 100f).dp.toPx(), (12f * settings.refraction / 100f).dp.toPx())
        },
        shadow = null,
        onDrawSurface = { drawRect(surfaceTint.copy(alpha = alpha)) },
    )
}
