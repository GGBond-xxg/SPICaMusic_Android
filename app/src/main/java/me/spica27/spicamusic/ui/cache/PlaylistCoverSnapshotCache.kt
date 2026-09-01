package me.spica27.spicamusic.ui.cache

import me.spica27.spicamusic.App

/** Persists the last non-empty local playlist mosaic so a cold launch has a real first frame. */
object PlaylistCoverSnapshotCache {
    fun read(key: String): List<Long> =
        preferences()
            .getString(key, null)
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.take(4)
            .orEmpty()

    fun write(
        key: String,
        albumIds: List<Long>,
    ) {
        val encoded = albumIds.take(4).joinToString(",")
        if (encoded.isBlank()) {
            clear(key)
            return
        }
        val preferences = preferences()
        if (preferences.getString(key, null) == encoded) return
        preferences.edit().putString(key, encoded).apply()
    }

    fun clear(key: String) {
        val preferences = preferences()
        if (!preferences.contains(key)) return
        preferences.edit().remove(key).apply()
    }

    private fun preferences() = App.getInstance().getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "playlist_cover_snapshots"
}
