package me.spica27.spicamusic.ui.widget.audio_seekbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A fixed, continuous progress curve derived from the complete song amplitude envelope.
 * Playback only reveals the active colour and moves the thumb; the curve itself never scrolls.
 */
@Composable
fun SongStructureWaveSlider(
    curveKey: Any?,
    progress: Float,
    amplitudes: List<Int>,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 5.dp,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { 7.5.dp.toPx() }
    var fromPoints by remember { mutableStateOf(neutralStructure()) }
    var toPoints by remember { mutableStateOf(neutralStructure()) }
    val morph = remember { Animatable(1f) }

    LaunchedEffect(curveKey, amplitudes) {
        val current = interpolateStructures(fromPoints, toPoints, morph.value)
        val next = amplitudes.toSongStructure()
        if (next == toPoints) return@LaunchedEffect
        fromPoints = current
        toPoints = next
        morph.snapTo(0f)
        morph.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        )
    }

    Canvas(modifier = modifier) {
        val points = interpolateStructures(fromPoints, toPoints, morph.value)
        val offsets =
            points.mapIndexed { index, normalizedY ->
                Offset(
                    x = (index.toFloat() / (points.lastIndex.coerceAtLeast(1))) * size.width,
                    y = normalizedY * size.height,
                )
            }
        val path = offsets.toSmoothPath()
        val style =
            Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        drawPath(path = path, color = inactiveColor, style = style)

        val safeProgress = progress.coerceIn(0f, 1f)
        clipRect(right = size.width * safeProgress) {
            drawPath(path = path, color = activeColor, style = style)
        }
        val position = safeProgress * points.lastIndex
        val leftIndex = position.toInt().coerceIn(0, points.lastIndex)
        val rightIndex = (leftIndex + 1).coerceAtMost(points.lastIndex)
        val fraction = position - leftIndex
        val thumbY =
            (points[leftIndex] + (points[rightIndex] - points[leftIndex]) * fraction) * size.height
        drawCircle(
            color = activeColor,
            radius = thumbRadiusPx,
            center = Offset(size.width * safeProgress, thumbY),
        )
    }
}

private const val STRUCTURE_POINT_COUNT = 52

private fun neutralStructure(): List<Float> = List(STRUCTURE_POINT_COUNT) { 0.5f }

private fun List<Int>.toSongStructure(): List<Float> {
    if (isEmpty()) return neutralStructure()
    val magnitudes = map { abs(it.toLong()).toDouble() }
    val sorted = magnitudes.sorted()
    val robustPeak = sorted[((sorted.lastIndex) * 0.92f).toInt()].coerceAtLeast(1.0)
    val binned =
        List(STRUCTURE_POINT_COUNT) { bin ->
            val start = (bin * magnitudes.size / STRUCTURE_POINT_COUNT)
            val end = ((bin + 1) * magnitudes.size / STRUCTURE_POINT_COUNT).coerceAtLeast(start + 1)
            val average = magnitudes.subList(start, end.coerceAtMost(magnitudes.size)).average()
            sqrt((average / robustPeak).coerceIn(0.0, 1.0)).toFloat()
        }
    var smooth = binned
    repeat(2) {
        smooth =
            smooth.mapIndexed { index, value ->
                val before = smooth[(index - 1).coerceAtLeast(0)]
                val after = smooth[(index + 1).coerceAtMost(smooth.lastIndex)]
                before * 0.2f + value * 0.6f + after * 0.2f
            }
    }
    return smooth.map { energy -> (0.76f - energy * 0.52f).coerceIn(0.18f, 0.82f) }
}

private fun interpolateStructures(
    from: List<Float>,
    to: List<Float>,
    fraction: Float,
): List<Float> =
    List(STRUCTURE_POINT_COUNT) { index ->
        val start = from.getOrElse(index) { 0.5f }
        val end = to.getOrElse(index) { 0.5f }
        start + (end - start) * fraction.coerceIn(0f, 1f)
    }

private fun List<Offset>.toSmoothPath(): Path =
    Path().apply {
        if (this@toSmoothPath.isEmpty()) return@apply
        moveTo(first().x, first().y)
        for (index in 0 until lastIndex) {
            val p0 = getOrElse(index - 1) { this@toSmoothPath[index] }
            val p1 = this@toSmoothPath[index]
            val p2 = this@toSmoothPath[index + 1]
            val p3 = getOrElse(index + 2) { p2 }
            cubicTo(
                p1.x + (p2.x - p0.x) / 6f,
                p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f,
                p2.y - (p3.y - p1.y) / 6f,
                p2.x,
                p2.y,
            )
        }
    }
