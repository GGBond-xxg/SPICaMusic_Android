package me.spica27.spicamusic.ui.widget

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.image.LandscapistImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.spica27.spicamusic.common.entity.DynamicSpectrumBackground
import me.spica27.spicamusic.player.api.IFFTProcessor
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.settings.SettingsViewModel
import me.spica27.spicamusic.utils.blurhash.BlurHashTransformationPlugin
import org.koin.compose.viewmodel.koinViewModel
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
 * @param active 页面当前是否可见；false 时停止动态渲染并保留纯色底。
 * @param isDarkMode 暗色模式（true）或亮色模式（false），null时自动判断
 */
@Composable
fun FluidMusicBackground(
    modifier: Modifier = Modifier,
    coverColor: Color = Color(0xFF2196F3),
    isDarkMode: Boolean? = null,
    coverUri: () -> Uri? = { null },
    active: Boolean = true,
    visibilityProgressProvider: () -> Float = { 1f },
) {
    val playerViewModel = LocalPlayerViewModel.current
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val modeValue by settingsViewModel.dynamicSpectrumBackground.collectAsStateWithLifecycle()
    val backgroundMode = remember(modeValue) { DynamicSpectrumBackground.fromString(modeValue) }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val effectiveDarkMode = isDarkMode ?: (surfaceColor.luminance() < 0.5f)

    // 动态效果暂停时不订阅 FFT。GL 背景本身会保留最后一帧，收起后只停止连续渲染。
    val enableFft =
        active &&
            backgroundMode != DynamicSpectrumBackground.OFF &&
            backgroundMode != DynamicSpectrumBackground.BlurCover
    LaunchedEffect(enableFft) {
        playerViewModel.setFftSamplingEnabled(enableFft)
    }
    val fftSnapshot =
        if (enableFft) {
            val fftDrawData by playerViewModel.fftDrawData.collectAsStateWithLifecycle()
            fftDrawData
        } else {
            remember { FloatArray(IFFTProcessor.BAND_COUNT) }
        }

    var dynamicReady by
        remember(backgroundMode) {
            mutableStateOf(
                backgroundMode == DynamicSpectrumBackground.OFF ||
                    backgroundMode == DynamicSpectrumBackground.BlurCover,
            )
        }

    // TextureView 模式在收起后会释放组合；下一次打开重新等待它的首帧。
    // FluidWarp / EffectShader 则常驻并冻结最后一帧，不重置 ready。
    LaunchedEffect(active, backgroundMode) {
        if (
            active &&
            (
                backgroundMode == DynamicSpectrumBackground.TopGlow ||
                    backgroundMode == DynamicSpectrumBackground.LiquidAurora
            )
        ) {
            dynamicReady = false
        }
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

    Box(modifier = modifier) {
        when (backgroundMode) {
            DynamicSpectrumBackground.OFF -> {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(surfaceColor),
                )
            }

            DynamicSpectrumBackground.BlurCover -> {
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

            else -> {
                // 始终先绘制同色静态底。动态层真正完成首帧后再做 240ms 交叉淡化，
                // 不会再从纯色一帧跳到动态封面。
                StaticPlayerBackdrop(
                    modifier = Modifier.matchParentSize(),
                    coverColor = coverColor,
                    surfaceColor = surfaceColor,
                    isDarkMode = effectiveDarkMode,
                )

                when (backgroundMode) {
                    DynamicSpectrumBackground.TopGlow -> {
                        if (active) {
                            TopGlowBackground(
                                modifier = Modifier.matchParentSize().blur(72.dp),
                                fftDrawData = fftSnapshot,
                                coverColor = coverColor,
                                visualAlpha = dynamicAlphaState.value,
                                onFirstFrame = { dynamicReady = true },
                            )
                        }
                    }

                    DynamicSpectrumBackground.LiquidAurora -> {
                        if (active) {
                            LiquidAuroraBackground(
                                modifier = Modifier.matchParentSize().blur(40.dp),
                                fftDrawData = fftSnapshot,
                                coverColor = coverColor,
                                isDarkMode = effectiveDarkMode,
                                visualAlpha = dynamicAlphaState.value,
                                onFirstFrame = { dynamicReady = true },
                            )
                        }
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

private const val RENDER_FRAME_DELAY_MS = 16L

private class TextureViewRenderLoop(
    threadName: String,
) {
    private val surfaceActive = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val stateLock = Any()
    private val renderDispatcher: ExecutorCoroutineDispatcher =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, threadName).apply {
                    priority = Thread.MIN_PRIORITY
                }
            }.asCoroutineDispatcher()
    private val renderScope = CoroutineScope(renderDispatcher + SupervisorJob())
    private var drawJob: Job? = null
    private var textureView: TextureView? = null
    private var drawFrame: ((android.graphics.Canvas) -> Unit)? = null

    fun start(
        textureView: TextureView,
        drawFrame: (android.graphics.Canvas) -> Unit,
    ) {
        synchronized(stateLock) {
            this.textureView = textureView
            this.drawFrame = drawFrame
        }
        launchLoop()
    }

    private fun launchLoop() {
        stop()
        if (paused.get()) return
        val targetView: TextureView
        val targetDraw: (android.graphics.Canvas) -> Unit
        synchronized(stateLock) {
            targetView = textureView ?: return
            targetDraw = drawFrame ?: return
        }
        surfaceActive.set(true)
        val token = generation.incrementAndGet()

        synchronized(stateLock) {
            drawJob =
                renderScope.launch {
                    while (isActive && surfaceActive.get() && generation.get() == token) {
                        val canvas =
                            try {
                                targetView.lockCanvas(null)
                            } catch (e: IllegalStateException) {
                                Timber
                                    .tag("FluidMusicBackground")
                                    .w(e, "TextureView lockCanvas failed, stopping render loop")
                                break
                            }

                        if (canvas == null) {
                            delay(RENDER_FRAME_DELAY_MS)
                            continue
                        }

                        var shouldContinue = true
                        try {
                            if (!surfaceActive.get() || generation.get() != token) {
                                shouldContinue = false
                            } else {
                                targetDraw(canvas)
                            }
                        } finally {
                            try {
                                targetView.unlockCanvasAndPost(canvas)
                            } catch (e: IllegalStateException) {
                                Timber
                                    .tag("FluidMusicBackground")
                                    .w(
                                        e,
                                        "TextureView unlockCanvasAndPost failed, stopping render loop",
                                    )
                                shouldContinue = false
                            }
                        }

                        if (!shouldContinue) {
                            break
                        }

                        delay(RENDER_FRAME_DELAY_MS)
                    }
                }
        }
    }

    fun stop() {
        surfaceActive.set(false)
        generation.incrementAndGet()
        synchronized(stateLock) {
            drawJob.also { drawJob = null }
        }?.cancel()
    }

    fun pause() {
        paused.set(true)
        stop()
    }

    fun resume() {
        if (!paused.getAndSet(false)) return
        launchLoop()
    }

    fun onSurfaceDestroyed() {
        val job =
            synchronized(stateLock) {
                surfaceActive.set(false)
                generation.incrementAndGet()
                val currentJob = drawJob
                drawJob = null
                textureView = null
                drawFrame = null
                currentJob
            }
        job?.cancel()
        if (job != null) {
            runBlocking { job.join() }
        }
    }

    fun release() {
        stop()
        synchronized(stateLock) {
            textureView = null
            drawFrame = null
        }
        renderScope.coroutineContext.cancel()
        renderDispatcher.close()
    }
}

/** 线程安全数据持有者，供 TopGlowBackground 绘制线程读取 */
private class TopGlowHolder {
    @Volatile
    var fftData: FloatArray = FloatArray(0)

    @Volatile
    var colorA: Int = android.graphics.Color.BLUE

    @Volatile
    var colorB: Int = android.graphics.Color.TRANSPARENT

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rectF = RectF()
}

@Composable
private fun TopGlowBackground(
    modifier: Modifier,
    fftDrawData: FloatArray,
    coverColor: Color,
    visualAlpha: Float,
    onFirstFrame: () -> Unit,
) {
    val holder = remember { TopGlowHolder() }
    val renderLoop = remember { TextureViewRenderLoop("TopGlow-Renderer") }
    val currentOnFirstFrame = rememberUpdatedState(onFirstFrame)
    val firstFrameDispatched = remember { AtomicBoolean(false) }

    SideEffect {
        holder.fftData = fftDrawData
        val luminance = calculateLuminance(coverColor)
        val hueShift = if (luminance < 0.5f) 24f else -24f
        holder.colorA = shiftHue(coverColor, hueShift).copy(alpha = 0.85f).toArgb()
        holder.colorB = shiftHue(coverColor, hueShift * 1.6f).copy(alpha = 0.2f).toArgb()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, renderLoop) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> renderLoop.pause()
                    Lifecycle.Event.ON_START -> renderLoop.resume()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            renderLoop.release()
        }
    }

    AndroidView(
        modifier = modifier.blur(45.dp),
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                tv.isOpaque = false
                tv.alpha = visualAlpha.coerceIn(0f, 1f)
                tv.surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            renderLoop.start(tv) { canvas ->
                                canvas.drawColor(
                                    android.graphics.Color.TRANSPARENT,
                                    PorterDuff.Mode.CLEAR,
                                )
                                val data = holder.fftData
                                if (data.isEmpty()) return@start

                                val w = canvas.width.toFloat()
                                val h = canvas.height.toFloat()
                                val bandWidth = w / data.size
                                val blurRadius = (bandWidth * 1.5f).coerceAtLeast(4f)
                                holder.paint.shader =
                                    LinearGradient(
                                        0f,
                                        0f,
                                        w,
                                        h,
                                        holder.colorA,
                                        holder.colorB,
                                        Shader.TileMode.CLAMP,
                                    )

                                data.forEachIndexed { index, magnitude ->
                                    val energy = magnitude.coerceIn(0f, 1f)
                                    val barHeight = h * 0.8f * energy + h * 0.08f
                                    val left = index * bandWidth
                                    holder.rectF.set(
                                        left,
                                        0f,
                                        left + max(1f, bandWidth * 0.9f),
                                        barHeight,
                                    )
                                    canvas.drawRect(holder.rectF, holder.paint)
                                }
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            renderLoop.onSurfaceDestroyed()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            if (firstFrameDispatched.compareAndSet(false, true)) {
                                currentOnFirstFrame.value.invoke()
                            }
                        }
                    }
            }
        },
        update = { textureView ->
            textureView.alpha = visualAlpha.coerceIn(0f, 1f)
        },
    )
}

@Composable
private fun LiquidAuroraBackground(
    modifier: Modifier,
    fftDrawData: FloatArray,
    coverColor: Color,
    isDarkMode: Boolean?,
    visualAlpha: Float,
    onFirstFrame: () -> Unit,
) {
    val currentOnFirstFrame = rememberUpdatedState(onFirstFrame)
    val transition = rememberInfiniteTransition(label = "LiquidAuroraPhase")
    val phaseDegrees by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 20_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "LiquidAuroraPhaseValue",
        )
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
    LaunchedEffect(Unit) {
        withFrameNanos { }
        currentOnFirstFrame.value.invoke()
    }

    Canvas(
        modifier =
            modifier.graphicsLayer {
                alpha = visualAlpha.coerceIn(0f, 1f)
            },
    ) {
        if (fftDrawData.isEmpty()) return@Canvas

        val layers = 3
        val chunkSize = (fftDrawData.size / layers).coerceAtLeast(1)
        repeat(layers) { layer ->
            val startIndex = layer * chunkSize
            val endIndex = min(fftDrawData.size, startIndex + chunkSize)
            if (startIndex >= endIndex) return@repeat

            val path = Path().apply { moveTo(0f, 0f) }
            val steps = endIndex - startIndex
            val amplitude = size.height * (0.28f - layer * 0.05f)
            val phaseShift = (phaseDegrees + layer * 45f) * (PI / 180.0)

            for (index in 0 until steps) {
                val progress = if (steps == 1) 0f else index / (steps - 1f)
                val energy = fftDrawData[startIndex + index].coerceIn(0f, 1f)
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
