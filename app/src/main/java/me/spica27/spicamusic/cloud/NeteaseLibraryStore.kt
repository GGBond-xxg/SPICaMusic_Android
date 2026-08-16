package me.spica27.spicamusic.cloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists non-sensitive NetEase playlist metadata for reliable reload and offline browsing. */
class NeteaseLibraryStore(
    context: Context,
) {
    private val root = File(context.applicationContext.filesDir, "netease_library").apply { mkdirs() }

    fun readPlaylists(accountId: String): List<RemotePlaylist> =
        readArray(file(accountId, PLAYLISTS_FILE)) { item ->
            RemotePlaylist(
                id = item.getString("id"),
                name = item.optString("name"),
                coverUrl = item.optString("coverUrl").takeIf(String::isNotBlank),
                songCount = item.optInt("songCount").coerceAtLeast(0),
            )
        }

    fun writePlaylists(
        accountId: String,
        playlists: List<RemotePlaylist>,
    ) {
        writeArray(
            file(accountId, PLAYLISTS_FILE),
            playlists.map { playlist ->
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("coverUrl", playlist.coverUrl.orEmpty())
                    .put("songCount", playlist.songCount)
            },
        )
    }

    fun readSongs(
        accountId: String,
        playlistId: String,
    ): List<RemoteSong> =
        readArray(file(accountId, "playlist_${safe(playlistId)}.json")) { item ->
            RemoteSong(
                id = item.getString("id"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                durationMs = item.optLong("durationMs").coerceAtLeast(0L),
                mimeType = item.optString("mimeType", "audio/mpeg"),
                artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
            )
        }

    fun writeSongs(
        accountId: String,
        playlistId: String,
        songs: List<RemoteSong>,
    ) {
        writeArray(
            file(accountId, "playlist_${safe(playlistId)}.json"),
            songs.map { song ->
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("durationMs", song.durationMs)
                    .put("mimeType", song.mimeType)
                    .put("artworkUrl", song.artworkUrl.orEmpty())
            },
        )
    }

    fun clear(accountId: String) {
        file(accountId, PLAYLISTS_FILE).parentFile?.deleteRecursively()
    }

    private fun file(
        accountId: String,
        name: String,
    ): File = File(root, safe(accountId)).apply { mkdirs() }.resolve(name)

    private fun safe(value: String): String = value.replace(UNSAFE_FILE_CHARS, "_").take(160)

    private fun <T> readArray(
        file: File,
        mapper: (JSONObject) -> T,
    ): List<T> {
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching { mapper(item) }.getOrNull()?.let(::add)
            }
        }
    }

    private fun writeArray(
        file: File,
        values: List<JSONObject>,
    ) {
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(JSONArray(values).toString())
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    private companion object {
        const val PLAYLISTS_FILE = "playlists.json"
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9_.-]")
    }
}
