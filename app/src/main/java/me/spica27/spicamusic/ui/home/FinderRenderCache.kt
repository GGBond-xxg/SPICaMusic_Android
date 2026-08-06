package me.spica27.spicamusic.ui.home

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Base64
import androidx.core.content.edit
import me.spica27.spicamusic.common.entity.Playlist
import me.spica27.spicamusic.common.entity.Song

/**
 * A small synchronous first-frame cache for the Finder page.
 *
 * Room remains the source of truth. This snapshot prevents previously rendered frequent,
 * favorite, and playlist content from starting at an artificial empty/loading state after process
 * death.
 */
internal class FinderRenderCache(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun getFrequentSongs(): List<Song>? = readParcelableList(KEY_FREQUENT_SONGS, MAX_FREQUENT_SONGS)

    fun setFrequentSongs(songs: List<Song>) {
        writeParcelableList(KEY_FREQUENT_SONGS, songs.take(MAX_FREQUENT_SONGS))
    }

    fun getFavoriteSongs(): List<Song>? = readParcelableList(KEY_FAVORITE_SONGS, MAX_FAVORITE_SONGS)

    fun setFavoriteSongs(songs: List<Song>) {
        writeParcelableList(KEY_FAVORITE_SONGS, songs.take(MAX_FAVORITE_SONGS))
    }

    fun getPlaylists(): List<Playlist>? = readParcelableList(KEY_PLAYLISTS, MAX_PLAYLISTS)

    fun setPlaylists(playlists: List<Playlist>) {
        writeParcelableList(KEY_PLAYLISTS, playlists.take(MAX_PLAYLISTS))
    }

    private inline fun <reified T : Parcelable> readParcelableList(
        key: String,
        maxSize: Int,
    ): List<T>? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                val size = parcel.readInt()
                require(size in 0..maxSize)
                buildList(size) {
                    repeat(size) {
                        val item = parcel.readParcelable<T>(T::class.java.classLoader)
                        requireNotNull(item)
                        add(item)
                    }
                }
            } finally {
                parcel.recycle()
            }
        }.getOrElse {
            preferences.edit { remove(key) }
            null
        }
    }

    private fun <T : Parcelable> writeParcelableList(
        key: String,
        items: List<T>,
    ) {
        val encoded =
            runCatching {
                val parcel = Parcel.obtain()
                try {
                    parcel.writeInt(items.size)
                    items.forEach { parcel.writeParcelable(it, 0) }
                    Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
                } finally {
                    parcel.recycle()
                }
            }.getOrNull() ?: return

        if (preferences.getString(key, null) == encoded) return
        preferences.edit { putString(key, encoded) }
    }

    private companion object {
        const val PREFERENCES_NAME = "finder_render_cache"
        const val KEY_FREQUENT_SONGS = "frequent_songs_v1"
        const val KEY_FAVORITE_SONGS = "favorite_songs_v1"
        const val KEY_PLAYLISTS = "playlists_v1"
        const val MAX_FREQUENT_SONGS = 10
        const val MAX_FAVORITE_SONGS = 1_000
        const val MAX_PLAYLISTS = 1_000
    }
}
