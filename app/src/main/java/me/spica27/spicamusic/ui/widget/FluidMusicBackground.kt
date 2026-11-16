package me.spica27.spicamusic.ui.widget

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.image.LandscapistImage
import me.spica27.spicamusic.common.entity.DynamicSpectrumBackground
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.settings.SettingsViewModel
import me.spica27.spicamusic.utils.blurhash.BlurHashTransformationPlugin
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 流动融合动态背景组件
 * 响应音乐FFT数据和封面颜色
 *
 * @param modifier 修饰符
 * @param coverColor 封面主色，用于色彩调整
 * @param active 页面当前是否可见；false 时停止律动并保留已绘制背景。
 * @param isDarkMode 暗色模式（true）或亮色模式（false），null时自动判断
 */
@Composable
fun FluidMusicBackground(
    modifier: Modifier = Modifier,
    coverColor: Color = Color(0xFF2196F3),
    isDarkMode: Boolean? = null,
    coverUri: () -> Uri? = { null },
    active: Boolean = true,
    foreground: Boolean = true,
    visibilityProgressProvider: () -> Float = { 1f },
    glassBackdrop: LayerBackdrop? = null,
) {
    val playerViewModel = LocalPlayerViewModel.current
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val modeValue by settingsViewModel.dynamicSpectrumBackground.collectAsStateWithLifecycle()
    val backgroundMode = remember(modeValue) { DynamicSpectrumBackground.fromString(modeValue) }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val effectiveDarkMode = isDarkMode ?: (surfaceColor.luminance() < 0.5f)

    // Compose effects are cheap to draw during the opening morph. Native GL modes still
    // wait for the settled container; all modes stop sampling when hidden/backgrounded.
    val canvasMode =
        backgroundMode == DynamicSpectrumBackground.TopGlow ||
            backgroundMode == DynamicSpectrumBackground.LiquidAurora
    val effectActive = active || (canvasMode && foreground && visibilityProgressProvider() > 0f)
    val enableFft =
        effectActive &&
            backgroundMode != DynamicSpectrumBackground.OFF &&
            backgroundMode != DynamicSpectrumBackground.BlurCover
    LaunchedEffect(enableFft) {
        playerViewModel.setFftSamplingEnabled(enableFft)
    }
    // Retain the last spectrum while motion is paused; never replace it with an empty layer.
    var retainedSpectrum by remember { mutableStateOf(FloatArray(31)) }
    val fftSnapshot =
        if (enableFft) {
            val current by playerViewModel.fftDrawData.collectAsStateWithLifecycle()
            SideEffect { retainedSpectrum = current }
            current
        } else {
            retainedSpectrum
        }

    var dynamicReady by
        remember(backgroundMode) {
            mutableStateOf(
                backgroundMode == DynamicSpectrumBackground.OFF ||
                    backgroundMode == DynamicSpectrumBackground.BlurCover,
            )
        }

    val dynamicAlphaState =
        animateFloatAsState(
            targetValue = if (dynamicReady) 1f else 0f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            label = "DynamicBackgroundCrossfade",
        )
    val dynamicSurfaceAlphaProvider =
        remember(dynamicAlphaState, visibilityProgressProvider) {
            {
                dynamicAlphaState.value * visibilityProgressProvider().coerceIn(0f, 1f)
            }
        }

    // SurfaceView display lists must never be replayed by glass in a dialog window:
    // replay moves the native surface to the dialog's transform/clip after the IME closes.
    // Record only the Compose base; the GL/TextureView animation remains a sibling.
    val backdropModifier = glassBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
    Box(modifier = modifier) {
        when (backgroundMode) {
            DynamicSpectrumBackground.OFF -> {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .then(backdropModifier)
                            .background(surfaceColor),
                )
            }

            DynamicSpectrumBackground.BlurCover -> {
                Box(Modifier.matchParentSize().then(backdropModifier)) {
                    // Keep the previous decoded backdrop until the next one is fully prepared. The
                    // image loader's own crossfade clears its old request first, exposing surface for
                    // one or more frames (white in light mode and black in dark mode).
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(surfaceColor),
                    )
                    StableBlurredBackdrop(
                        modifier = Modifier.matchParentSize(),
                        uri = coverUri.invoke(),
                    )
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(surfaceColor.copy(alpha = 0.5f)),
                    )
                }
            }

            else -> {
                // 始终先绘制同色静态底。动态层真正完成首帧后再做 240ms 交叉淡化，
                // 不会再从纯色一帧跳到动态封面。
                StaticPlayerBackdrop(
                    modifier = Modifier.matchParentSize().then(backdropModifier),
                    coverColor = coverColor,
                    surfaceColor = surfaceColor,
                    isDarkMode = effectiveDarkMode,
                )

                when (backgroundMode) {
                    DynamicSpectrumBackground.TopGlow -> {
                        TopGlowBackground(
                            modifier = Modifier.matchParentSize().blur(72.dp),
                            fftDrawData = fftSnapshot,
                            coverColor = coverColor,
                            visualAlpha = visibilityProgressProvider(),
                        )
                    }

                    DynamicSpectrumBackground.LiquidAurora -> {
                        LiquidAuroraBackground(
                            modifier = Modifier.matchParentSize().blur(40.dp),
                            fftDrawData = fftSnapshot,
                            coverColor = coverColor,
                            isDarkMode = effectiveDarkMode,
                            visualAlpha = visibilityProgressProvider(),
                            active = effectActive,
                        )
                    }

                    DynamicSpectrumBackground.EffectShader -> {
                        EffectShaderBackground(
                            modifier = Modifier.matchParentSize(),
                            coverColor = coverColor,
                            fftDrawData = fftSnapshot,
                            isDarkMode = effectiveDarkMode,
                            active = active,
                            visualAlphaProvider = dynamicSurfaceAlphaProvider,
                            onFirstFrame = { dynamicReady = true },
                        )
                    }

                    DynamicSpectrumBackground.FluidWarp -> {
                        FluidWarpBackground(
                            modifier = Modifier.matchParentSize(),
                            coverColor = coverColor,
                            fftDrawData = fftSnapshot,
                            isDarkMode = effectiveDarkMode,
                            coverUri = coverUri,
                            active = active,
                            visualAlphaProvider = dynamicSurfaceAlphaProvider,
                            onFirstFrame = { dynamicReady = true },
                        )
                    }

                    DynamicSpectrumBackground.OFF,
                    DynamicSpectrumBackground.BlurCover,
                    -> Unit
                }
            }
        }
    }
}

/**
 * Two-phase artwork switch for the full-screen blurred backdrop.
 *
 * Loading and blur-hash conversion happen invisibly. Only after the transformed painter is ready
 * is it faded over the retained painter; rapid consecutive skips therefore keep the last complete
 * frame instead of exposing the theme surface.
 */
@Composable
private fun StableBlurredBackdrop(
    uri: Uri?,
    modifier: Modifier = Modifier,
) {
    // Do not clear the currently visible painter when the media URI changes. The replacement is
    // prepared invisibly first, then Crossfade takes over from its current visual state. Unlike
    // the old two-slot Animatable implementation, an interrupted transition does not jump back to
    // an older retained frame when users skip through several songs quickly.
    var preparedPainter by remember { mutableStateOf<Painter?>(null) }

    LaunchedEffect(uri) {
        if (uri == null) preparedPainter = null
    }

    Box(modifier = modifier) {
        Crossfade(
            targetState = preparedPainter,
            animationSpec =
                tween(
                    durationMillis = BACKDROP_CROSSFADE_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            label = "BlurredCoverBackdropCrossfade",
            modifier = Modifier.matchParentSize(),
        ) { painter ->
            painter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        if (uri != null) {
            val requestedUri = uri
            key(uri) {
                LandscapistImage(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = 0f },
                    requestBuilder = {
                        this
                            .model(requestedUri)
                            // BlurHash only needs a tiny colour sample. Decoding the original
                            // cover here made its CPU encoder run over hundreds of thousands of
                            // pixels on the UI thread whenever the song changed.
                            .size(BACKDROP_SAMPLE_SIZE_PX, BACKDROP_SAMPLE_SIZE_PX)
                            .tag("player-backdrop:$requestedUri")
                            .progressiveEnabled(false)
                            .build()
                    },
                    imageModel = { uri },
                    component =
                        rememberImageComponent {
                            +BlurHashTransformationPlugin()
                        },
                    success = { _, painter ->
                        LaunchedEffect(requestedUri, painter) {
                            if (uri == requestedUri) preparedPainter = painter
                        }
                    },
                )
            }
        }
    }
}

private const val BACKDROP_CROSSFADE_DURATION_MS = 900
private const val BACKDROP_SAMPLE_SIZE_PX = 96

/**
 * 播放器形变容器和全屏播放器共用的静态底色。
 *
 * 亮色主题提高封面色占比，暗色主题保留更多 surface；动态背景尚未绘出首帧时，
 * 画面仍与最终色调接近，并保持文字对比度。
 */
internal fun resolvePlayerBackdropColor(
    coverColor: Color,
    surfaceColor: Color,
): Color {
    val coverWeight = if (surfaceColor.luminance() < 0.5f) 0.48f else 0.66f
    return lerp(surfaceColor, coverColor.copy(alpha = 1f), coverWeight).copy(alpha = 1f)
}

@Composable
private fun StaticPlayerBackdrop(
    modifier: Modifier,
    coverColor: Color,
    surfaceColor: Color,
    isDarkMode: Boolean,
) {
    val base =
        remember(coverColor, surfaceColor) {
            resolvePlayerBackdropColor(coverColor, surfaceColor)
        }
    val top =
        remember(base, isDarkMode) {
            lerp(
                base,
                if (isDarkMode) Color.Black else Color.White,
                if (isDarkMode) 0.12f else 0.16f,
            )
        }
    val bottom =
        remember(base, coverColor, isDarkMode) {
            lerp(
                base,
                if (isDarkMode) Color.Black else coverColor,
                if (isDarkMode) 0.18f else 0.10f,
            )
        }

    Box(
        modifier =
            modifier.background(
                Brush.verticalGradient(
                    colors = listOf(top, base, bottom),
                ),
            ),
    )
}

/** Compose draws a complete first frame during expansion, without waiting for a native surface. */
@Composable
internal fun TopGlowBackground(
    modifier: Modifier,
    fftDrawData: FloatArray,
    coverColor: Color,
    visualAlpha: Float,
) {
    val hueShift = if (calculateLuminance(coverColor) < 0.5f) 24f else -24f
    val colorA = shiftHue(coverColor, hueShift).copy(alpha = 0.85f)
    val colorB = shiftHue(coverColor, hueShift * 1.6f).copy(alpha = 0.2f)
    Canvas(modifier.blur(45.dp).graphicsLayer { alpha = visualAlpha.coerceIn(0f, 1f) }) {
        val count = fftDrawData.size.coerceAtLeast(1)
        val bandWidth = size.width / count
        val brush =
            Brush.linearGradient(
                listOf(colorA, colorB),
                end =
                    androidx.compose.ui.geometry
                        .Offset(size.width, size.height),
            )
        repeat(count) { index ->
            val energy = fftDrawData.getOrElse(index) { 0f }.coerceIn(0f, 1f)
            drawRect(
                brush = brush,
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(index * bandWidth, 0f),
                size =
                    androidx.compose.ui.geometry
                        .Size(max(1f, bandWidth * 0.9f), size.height * (0.8f * energy + 0.08f)),
            )
        }
    }
}

@Composable
internal fun LiquidAuroraBackground(
    modifier: Modifier,
    fftDrawData: FloatArray,
    coverColor: Color,
    isDarkMode: Boolean?,
    visualAlpha: Float,
    active: Boolean,
) {
    var phaseDegrees by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            phaseDegrees = (phaseDegrees + (frame - lastFrame) / 1_000_000_000f * 18f) % 360f
            lastFrame = frame
        }
    }
    val layerColors =
        remember(coverColor, isDarkMode) {
            val alpha = if (isDarkMode == true) 0.9f else 0.75f
            List(3) { layer ->
                val colorA = shiftHue(coverColor, layer * 18f + 120f)
                val colorB = shiftHue(coverColor, layer * -14f - 116f)
                colorA.copy(alpha = alpha - layer * 0.2f) to
                    colorB.copy(alpha = (alpha - layer * 0.3f).coerceAtLeast(0.1f))
            }
        }

    // The old implementation called TextureView.lockCanvas() from a worker thread. On some
    // Android 16 builds (observed on vivo PD2352), translucent drawPath() calls then crashed in
    // libhwui's software SkRasterPipeline while reading the destination buffer. Keep all path
    // recording in Compose so HWUI owns both the canvas and its surface lifecycle.

    Canvas(
        modifier =
            modifier.graphicsLayer {
                alpha = visualAlpha.coerceIn(0f, 1f)
            },
    ) {
        val layers = 3
        val chunkSize = (fftDrawData.size.coerceAtLeast(layers) / layers).coerceAtLeast(1)
        repeat(layers) { layer ->
            val startIndex = layer * chunkSize
            val endIndex = min(fftDrawData.size.coerceAtLeast(layers), startIndex + chunkSize)
            if (startIndex >= endIndex) return@repeat

            val path = Path().apply { moveTo(0f, 0f) }
            val steps = endIndex - startIndex
            val amplitude = size.height * (0.28f - layer * 0.05f)
            val phaseShift = (phaseDegrees + layer * 45f) * (PI / 180.0)

            for (index in 0 until steps) {
                val progress = if (steps == 1) 0f else index / (steps - 1f)
                val energy = fftDrawData.getOrElse(startIndex + index) { 0f }.coerceIn(0f, 1f)
                val wave = sin(progress * 6f + phaseShift).toFloat()
                val y =
                    size.height * 0.35f -
                        amplitude * energy -
                        amplitude * 0.2f * wave
                path.lineTo(progress * size.width, y)
            }

            path.lineTo(size.width, 0f)
            path.close()
            val (colorA, colorB) = layerColors[layer]
            drawPath(
                path = path,
                brush =
                    Brush.verticalGradient(
                        colors = listOf(colorA, colorB, colorB),
                        startY = 0f,
                        endY = size.height * 0.5f,
                    ),
            )
        }
    }
}

/**
 * 计算颜色亮度（感知亮度）
 */
private fun calculateLuminance(color: Color): Float = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue

/**
 * 色相偏移辅助函数
 * 简化版本：通过RGB分量旋转实现
 */
private fun shiftHue(
    color: Color,
    degrees: Float,
): Color {
    val amount = degrees / 360f

    // 简单的颜色偏移算法
    val r = color.red
    val g = color.green
    val b = color.blue

    return when {
        amount > 0 ->
            Color(
                red = (r + amount * (1 - r)).coerceIn(0f, 1f),
                green = (g - amount * g * 0.5f).coerceIn(0f, 1f),
                blue = (b + amount * (1 - b) * 0.5f).coerceIn(0f, 1f),
                alpha = color.alpha,
            )

        else ->
            Color(
                red = (r + amount * r * 0.5f).coerceIn(0f, 1f),
                green = (g - amount * (1 - g)).coerceIn(0f, 1f),
                blue = (b + amount * (1 - b)).coerceIn(0f, 1f),
                alpha = color.alpha,
            )
    }
}
