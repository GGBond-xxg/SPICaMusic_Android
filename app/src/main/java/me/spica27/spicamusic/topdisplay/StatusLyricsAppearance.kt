package me.spica27.spicamusic.topdisplay

data class StatusLyricsAppearance(
    val xDp: Int = 0,
    val yDp: Int = 0,
    val widthDp: Int = 240,
    val textSizeSp: Int = 14,
    val opacityPercent: Int = 90,
) {
    fun sanitized(): StatusLyricsAppearance =
        copy(
            xDp = xDp.coerceIn(-180, 180),
            yDp = yDp.coerceIn(0, 240),
            widthDp = widthDp.coerceIn(120, 360),
            textSizeSp = textSizeSp.coerceIn(10, 28),
            opacityPercent = opacityPercent.coerceIn(20, 100),
        )
}

internal data class StatusLyricsBounds(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
)

/** Coordinates are relative to the usable area; x is an offset from its horizontal center. */
internal fun statusLyricsBounds(
    availableWidth: Int,
    availableHeight: Int,
    requestedWidth: Int,
    requestedHeight: Int,
    requestedX: Int,
    requestedY: Int,
): StatusLyricsBounds {
    val width = requestedWidth.coerceIn(1, availableWidth.coerceAtLeast(1))
    val height = requestedHeight.coerceIn(1, availableHeight.coerceAtLeast(1))
    val maxX = (availableWidth - width).coerceAtLeast(0) / 2
    return StatusLyricsBounds(
        width,
        height,
        requestedX.coerceIn(-maxX, maxX),
        requestedY.coerceIn(0, (availableHeight - height).coerceAtLeast(0)),
    )
}
