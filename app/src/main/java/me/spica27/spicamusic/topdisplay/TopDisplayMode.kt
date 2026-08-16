package me.spica27.spicamusic.topdisplay

enum class TopDisplayMode(
    val value: String,
) {
    OFF("off"),
    STATUS_LYRIC("status_lyric"),
    LIVE_UPDATE("live_update"),
    ;

    companion object {
        fun fromString(value: String): TopDisplayMode = entries.firstOrNull { it.value == value } ?: STATUS_LYRIC
    }
}
