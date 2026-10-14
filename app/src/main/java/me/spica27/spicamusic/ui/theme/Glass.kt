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
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
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

/** Dialog windows cannot safely sample a LayerBackdrop owned by the activity window.
 * Use a local, coordinate-independent wallpaper during sheet translation and predictive back.
 */
@Composable
internal fun GlassWindowBackdrop(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val backdrop =
        rememberCanvasBackdrop {
            drawRect(colors.background)
            drawRect(
                Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)),
            )
        }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
}

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
    val smooth = LocalGlassSettings.current.smoothMode
    // A coordinate-independent source lets Android reuse each panel's filtered display list
    // while scrolling, instead of recapturing a full-screen layer at every new position.
    val smoothBackdrop =
        rememberCanvasBackdrop {
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
    Box(Modifier.fillMaxSize()) {
        if (enabled) {
            Canvas(Modifier.fillMaxSize().then(if (smooth) Modifier else Modifier.layerBackdrop(backdrop))) {
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
        CompositionLocalProvider(
            LocalGlassBackdrop provides (
                if (!enabled) {
                    null
                } else if (smooth) {
                    smoothBackdrop
                } else {
                    backdrop
                }
            ),
            content = content,
        )
    }
}

/** Real background blur and edge refraction; old Android versions retain a readable tint. */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    color: Color,
    tint: Color? = null,
    opacity: Float = 0.48f,
    frosted: Boolean = false,
    edgeHighlight: Boolean = true,
): Modifier {
    val backdrop = LocalGlassBackdrop.current
    if (!LocalGlassEnabled.current || backdrop == null) return background(color, shape)
    val surfaceTint = tint ?: MaterialTheme.colorScheme.surface
    val settings = LocalGlassSettings.current
    val alpha = (opacity * settings.opacity / 65f * surfaceTint.alpha).coerceIn(0f, 1f)
    // Only major panels blur in smooth mode. Small controls share a light reflective finish.
    val rim =
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.38f),
                Color.White.copy(alpha = 0.06f),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            ),
        )
    if ((settings.smoothMode && !frosted) || shape !is CornerBasedShape) {
        val base = if (LocalGlassOverlay.current) background(surfaceTint.copy(alpha = 1f), shape) else this
        val paint =
            base.background(
                Brush.linearGradient(
                    listOf(
                        androidx.compose.ui.graphics
                            .lerp(surfaceTint, Color.White, 0.12f)
                            .copy(alpha = (alpha + 0.08f).coerceAtMost(1f)),
                        surfaceTint.copy(alpha = alpha),
                    ),
                ),
                shape,
            )
        return if (edgeHighlight && shape is CornerBasedShape) {
            paint.border(
                0.75.dp,
                rim,
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
            val radius = if (settings.smoothMode) settings.blur.coerceAtMost(4) else settings.blur
            if (radius > 0) blur(radius.dp.toPx())
            if (!settings.smoothMode &&
                settings.refraction > 0
            ) {
                lens((8f * settings.refraction / 100f).dp.toPx(), (12f * settings.refraction / 100f).dp.toPx())
            }
        },
        shadow = null,
        highlight = if (settings.smoothMode || !edgeHighlight) null else ({ com.kyant.backdrop.highlight.Highlight.Default }),
        onDrawSurface = {
            drawRect(surfaceTint.copy(alpha = if (settings.smoothMode) alpha * 0.8f else alpha))
            if (settings.smoothMode) drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)))
        },
    ).then(if (settings.smoothMode && edgeHighlight) Modifier.border(0.75.dp, rim, shape) else Modifier)
}
