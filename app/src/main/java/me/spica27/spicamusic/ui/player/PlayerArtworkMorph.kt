package me.spica27.spicamusic.ui.player

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.spica27.spicamusic.ui.widget.AudioCover
import me.spica27.spicamusic.ui.widget.MusicCoverPlaceholder
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp as floatLerp

/**
 * 迷你播放器封面与全屏播放器封面之间的几何过渡状态。
 *
 * 源位置记录在底栏宿主坐标中，目标位置记录为全屏播放器根节点内的局部坐标。
 * 过渡开始前会冻结源位置；否则迷你播放条随容器移动时源坐标也会变化，飞行封面
 * 会追着移动的起点计算路径，造成重叠、跳动和“顿一下”的错觉。
 */
@Stable
class PlayerArtworkMorphState internal constructor() {
    private var hostCoordinates: LayoutCoordinates? = null
    private var playerRootCoordinates: LayoutCoordinates? = null
    private var lastSourceCoordinates: LayoutCoordinates? = null
    private var lastTargetCoordinates: LayoutCoordinates? = null
    private var sourceCaptureEnabled = true

    var sourceBounds by mutableStateOf(Rect.Zero)
        private set

    var targetBounds by mutableStateOf(Rect.Zero)
        private set

    internal fun updateHostCoordinates(coordinates: LayoutCoordinates) {
        hostCoordinates = coordinates
        recalculateSourceBounds()
    }

    internal fun updatePlayerRootCoordinates(coordinates: LayoutCoordinates) {
        playerRootCoordinates = coordinates
        recalculateTargetBounds()
    }

    internal fun updateSourceCoordinates(coordinates: LayoutCoordinates) {
        lastSourceCoordinates = coordinates
        recalculateSourceBounds()
    }

    internal fun updateTargetCoordinates(coordinates: LayoutCoordinates) {
        lastTargetCoordinates = coordinates
        recalculateTargetBounds()
    }

    /** 在点击展开或开始上拉前冻结迷你封面的真实收起位置。 */
    fun freezeSourceBounds() {
        sourceCaptureEnabled = false
    }

    /** 完全收起后恢复源坐标采集，以适配底栏模式、旋转和窗口尺寸变化。 */
    fun resumeSourceCapture() {
        sourceCaptureEnabled = true
        recalculateSourceBounds()
    }

    /**
     * Wait until the compact artwork has reached a stable, attached layout position.
     *
     * The bottom bar itself uses shared-element motion when switching out of detail-page inline
     * mode. Querying the live LayoutCoordinates for consecutive frames avoids freezing either an
     * outgoing node or the final intermediate frame on slower cold starts.
     */
    suspend fun awaitStableSourceBounds(timeoutMillis: Long = SOURCE_LAYOUT_TIMEOUT_MS) {
        var previous = Rect.Zero
        var stableFrames = 0
        withTimeoutOrNull(timeoutMillis) {
            while (stableFrames < REQUIRED_STABLE_SOURCE_FRAMES) {
                withFrameNanos { }
                recalculateSourceBounds()
                val current = sourceBounds
                stableFrames =
                    if (current.isUsable() && current.approximatelyEquals(previous)) {
                        stableFrames + 1
                    } else {
                        0
                    }
                previous = current
            }
        }
    }

    /**
     * Wait for the lazily composed full-player artwork to complete a real layout pass.
     *
     * On the first expansion after a cold start the target does not exist yet. Starting the
     * progress animation after an arbitrary single frame lets progress advance before targetBounds
     * becomes usable: the compact artwork disappears first, then the overlay mounts partway along
     * the path at a wrong position. Waiting on the actual geometry makes ownership atomic.
     */
    suspend fun awaitUsableBounds(timeoutMillis: Long = TARGET_LAYOUT_TIMEOUT_MS) {
        if (hasUsableBounds) return
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { hasUsableBounds }.first { it }
        }
    }

    private fun recalculateSourceBounds() {
        if (!sourceCaptureEnabled) return
        val host = hostCoordinates?.takeIf { it.isAttached } ?: return
        val source = lastSourceCoordinates?.takeIf { it.isAttached } ?: return
        val topLeft = host.localPositionOf(source, Offset.Zero)
        updateSourceBounds(Rect(offset = topLeft, size = source.size.toSize()))
    }

    private fun recalculateTargetBounds() {
        val playerRoot = playerRootCoordinates?.takeIf { it.isAttached } ?: return
        val target = lastTargetCoordinates?.takeIf { it.isAttached } ?: return
        val topLeft = playerRoot.localPositionOf(target, Offset.Zero)
        updateTargetBounds(Rect(offset = topLeft, size = target.size.toSize()))
    }

    private fun updateSourceBounds(value: Rect) {
        if (!sourceBounds.approximatelyEquals(value)) sourceBounds = value
    }

    private fun updateTargetBounds(value: Rect) {
        if (!targetBounds.approximatelyEquals(value)) targetBounds = value
    }

    val hasUsableBounds: Boolean
        get() = sourceBounds.isUsable() && targetBounds.isUsable()
}

@Composable
fun rememberPlayerArtworkMorphState(): PlayerArtworkMorphState = remember { PlayerArtworkMorphState() }

/** 记录整个底部播放器宿主的坐标，供迷你封面计算源位置。 */
fun Modifier.playerArtworkMorphHost(state: PlayerArtworkMorphState): Modifier = onGloballyPositioned(state::updateHostCoordinates)

/** 记录迷你播放器封面的源位置。 */
fun Modifier.playerArtworkMorphSource(state: PlayerArtworkMorphState): Modifier = onGloballyPositioned(state::updateSourceCoordinates)

/** 记录全屏播放器根节点，目标封面会相对于此根节点保存坐标。 */
fun Modifier.playerArtworkMorphRoot(state: PlayerArtworkMorphState): Modifier = onGloballyPositioned(state::updatePlayerRootCoordinates)

/** 记录全屏播放器封面的目标位置。 */
fun Modifier.playerArtworkMorphTarget(state: PlayerArtworkMorphState): Modifier = onGloballyPositioned(state::updateTargetCoordinates)

/**
 * M3 风格的共享封面浮层。
 *
 * 过渡期间只绘制这一份封面，并在源/目标矩形间连续插值。
 * 源封面、共享浮层和目标封面使用互斥可见性，在几何完全一致的端点同帧交接，
 * 避免两张不同采样清晰度的封面交叉淡化形成重影。
 * 浮层不参与触摸命中。
 */
@Composable
fun PlayerArtworkMorphOverlay(
    state: PlayerArtworkMorphState,
    artworkUri: Uri?,
    artworkPainter: Painter?,
    progressProvider: () -> Float,
    inFlightProvider: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val source = state.sourceBounds
    val target = state.targetBounds
    if (!state.hasUsableBounds) return

    val targetWidth = target.width.coerceAtLeast(1f)
    val targetHeight = target.height.coerceAtLeast(1f)
    val density = LocalDensity.current
    val hasArtwork = artworkPainter != null || artworkUri != null
    val artworkContainerColor =
        if (hasArtwork) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    val placeholderContentColor =
        if (hasArtwork) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }
    val renderScaleProvider = {
        val progress = progressProvider().coerceIn(0f, 1f)
        val currentWidth = floatLerp(source.width, target.width, progress)
        val currentHeight = floatLerp(source.height, target.height, progress)
        minOf(currentWidth / targetWidth, currentHeight / targetHeight).coerceAtLeast(0.01f)
    }
    val transitionShape =
        remember(progressProvider) {
            PlayerArtworkTransitionShape(progressProvider)
        }

    val artwork: @Composable () -> Unit = {
        if (artworkPainter != null) {
            Image(
                painter = artworkPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AudioCover(
                uri = artworkUri,
                modifier = Modifier.fillMaxSize(),
                placeHolder = {
                    MusicCoverPlaceholder(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = placeholderContentColor,
                        renderScaleProvider = renderScaleProvider,
                    )
                },
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            x = target.left.roundToInt(),
                            y = target.top.roundToInt(),
                        )
                    }.size(
                        width = with(density) { targetWidth.toDp() },
                        height = with(density) { targetHeight.toDp() },
                    ).zIndex(30f)
                    .graphicsLayer {
                        val progress = progressProvider().coerceIn(0f, 1f)
                        // 几何位置直接使用原始进度，手势拖动时封面与手指严格同步；
                        // 只有形状交接使用平滑曲线。
                        val currentLeft = floatLerp(source.left, target.left, progress)
                        val currentTop = floatLerp(source.top, target.top, progress)
                        val currentWidth = floatLerp(source.width, target.width, progress)
                        val currentHeight = floatLerp(source.height, target.height, progress)

                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = currentLeft - target.left
                        translationY = currentTop - target.top
                        scaleX = (currentWidth / targetWidth).coerceAtLeast(0.01f)
                        scaleY = (currentHeight / targetHeight).coerceAtLeast(0.01f)
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha =
                            artworkOverlayAlpha(
                                inFlight = inFlightProvider(),
                            )
                    },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            // The compact cover is circular and the full player cover is a rounded
                            // square. One outline follows the same geometric progress in both
                            // directions, so neither endpoint flashes a mismatched corner radius.
                            shape = transitionShape
                            clip = true
                        }.background(artworkContainerColor),
            ) {
                artwork()
            }
        }
    }
}

/**
 * 迷你封面的可见度。
 *
 * 有可用的共享元素坐标时，形变开始后立即由共享浮层独占绘制；只有完全收起的
 * 稳定帧才显示源封面。
 */
fun sourceArtworkAlpha(
    progress: Float,
    inFlight: Boolean,
    hasUsableBounds: Boolean,
): Float {
    if (!hasUsableBounds) return 1f
    return if (!inFlight && progress <= STABLE_COLLAPSED_EPSILON) 1f else 0f
}

/**
 * 全屏目标封面的可见度。
 *
 * 共享浮层飞行期间始终为 0；只有形变完全结束后才在同一位置一次性交给目标封面。
 */
fun targetArtworkAlpha(
    progress: Float,
    inFlight: Boolean,
    hasUsableBounds: Boolean,
): Float {
    if (!hasUsableBounds) return if (progress >= STABLE_EXPANDED_THRESHOLD) 1f else 0f
    return if (!inFlight && progress >= STABLE_EXPANDED_THRESHOLD) 1f else 0f
}

private fun artworkOverlayAlpha(inFlight: Boolean): Float = if (inFlight) 1f else 0f

private fun Rect.isUsable(): Boolean = this != Rect.Zero && left.isFinite() && top.isFinite() && width > 1f && height > 1f

private fun Rect.approximatelyEquals(other: Rect): Boolean =
    abs(left - other.left) < 0.5f &&
        abs(top - other.top) < 0.5f &&
        abs(right - other.right) < 0.5f &&
        abs(bottom - other.bottom) < 0.5f

private class PlayerArtworkTransitionShape(
    private val progressProvider: () -> Float,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val progress = progressProvider().coerceIn(0f, 1f)
        val circleRadius = minOf(size.width, size.height) / 2f
        val roundedSquareRadius = with(density) { PLAYER_ARTWORK_CORNER_RADIUS.toPx() }
        val radius =
            floatLerp(circleRadius, roundedSquareRadius, progress)
                .coerceIn(0f, circleRadius)
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                radiusX = radius,
                radiusY = radius,
            ),
        )
    }
}

private const val STABLE_COLLAPSED_EPSILON = 0.001f
private const val STABLE_EXPANDED_THRESHOLD = 0.999f
private const val TARGET_LAYOUT_TIMEOUT_MS = 750L
private const val SOURCE_LAYOUT_TIMEOUT_MS = 750L
private const val REQUIRED_STABLE_SOURCE_FRAMES = 3

// ExpandedPlayerScreen clips the real target with Shapes.LargeCornerBasedShape (16.dp).
// Keeping the overlay endpoint identical prevents a one-frame corner snap at hand-off.
private val PLAYER_ARTWORK_CORNER_RADIUS = 16.dp
