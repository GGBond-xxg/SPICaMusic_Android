package me.spica27.spicamusic.topdisplay

/** Only the visible settings editor supplies adjustmentText. Playback never shows that sample. */
internal fun statusLyricsText(
    isPlaying: Boolean,
    lyric: String?,
    title: String?,
    adjustmentText: String?,
): String? =
    if (isPlaying) {
        lyric?.takeIf(String::isNotBlank) ?: title?.takeIf(String::isNotBlank)
    } else {
        adjustmentText?.takeIf(String::isNotBlank)
    }
