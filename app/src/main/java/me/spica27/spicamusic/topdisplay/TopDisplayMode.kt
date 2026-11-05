package me.spica27.spicamusic.topdisplay

enum class TopDisplayMode(
    val value: String,
) {
    OFF("off"),
    SYSTEM_STATUS_LYRIC("system_status_lyric"),
    STATUS_LYRIC("status_lyric"),
    LIVE_UPDATE("live_update"),
    ;

    companion object {
        fun fromString(value: String): TopDisplayMode = entries.firstOrNull { it.value == value } ?: OFF

        /** Preserve the old Lyricon preference; unknown values must not enable a new output. */
        fun resolve(
            saved: String,
            legacy: Boolean,
        ): TopDisplayMode =
            if (saved.isBlank()) {
                if (legacy) STATUS_LYRIC else OFF
            } else {
                fromString(saved)
            }
    }
}
