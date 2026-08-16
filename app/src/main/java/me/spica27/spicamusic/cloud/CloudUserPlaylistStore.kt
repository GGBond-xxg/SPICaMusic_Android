package me.spica27.spicamusic.cloud

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A provider-scoped playlist that stays on this device. */
@Immutable
data class CloudUserPlaylist(
    val id: String,
    val provider: RemoteMusicProvider,
    val accountId: String,
    val name: String,
    val songs: List<RemoteSong>,
)

/**
 * Persists playlists made from NetEase/QQ search results.
 *
 * Neither provider exposes a stable playlist-write API in the current client, so these playlists
 * are deliberately local and are kept separate per account.
 */
class CloudUserPlaylistStore(
    context: Context,
) {
    private val root = File(context.applicationContext.filesDir, "cloud_user_playlists").apply { mkdirs() }
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    @Synchronized
    fun read(
        provider: RemoteMusicProvider,
        accountId: String,
    ): List<CloudUserPlaylist> {
        val file = file(provider, accountId)
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val songs =
                    buildList {
                        val songArray = item.optJSONArray("songs") ?: JSONArray()
                        for (songIndex in 0 until songArray.length()) {
                            val song = songArray.optJSONObject(songIndex) ?: continue
                            val id = song.optString("id")
                            if (id.isBlank()) continue
                            add(
                                RemoteSong(
                                    id = id,
                                    title = song.optString("title"),
                                    artist = song.optString("artist"),
                                    album = song.optString("album"),
                                    durationMs = song.optLong("durationMs").coerceAtLeast(0L),
                                    mimeType = song.optString("mimeType", "audio/mpeg"),
                                    artworkUrl = song.optString("artworkUrl").takeIf(String::isNotBlank),
                                ),
                            )
                        }
                    }
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    add(CloudUserPlaylist(id, provider, accountId, name, songs))
                }
            }
        }
    }

    @Synchronized
    fun create(
        provider: RemoteMusicProvider,
        accountId: String,
        name: String,
        initialSong: RemoteSong? = null,
    ): List<CloudUserPlaylist> {
        val current = read(provider, accountId)
        val playlist =
            CloudUserPlaylist(
                id = UUID.randomUUID().toString(),
                provider = provider,
                accountId = accountId,
                name = name.trim(),
                songs = listOfNotNull(initialSong),
            )
        return (current + playlist).also {
            write(it)
            _revision.update(Long::inc)
        }
    }

    @Synchronized
    fun addSong(
        provider: RemoteMusicProvider,
        accountId: String,
        playlistId: String,
        song: RemoteSong,
    ): List<CloudUserPlaylist> {
        val updated =
            read(provider, accountId).map { playlist ->
                if (playlist.id != playlistId || playlist.songs.any { it.id == song.id }) {
                    playlist
                } else {
                    playlist.copy(songs = playlist.songs + song)
                }
            }
        write(updated)
        _revision.update(Long::inc)
        return updated
    }

    private fun write(playlists: List<CloudUserPlaylist>) {
        val first = playlists.firstOrNull() ?: return
        val target = file(first.provider, first.accountId)
        val values =
            playlists.map { playlist ->
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put(
                        "songs",
                        JSONArray(
                            playlist.songs.map { song ->
                                JSONObject()
                                    .put("id", song.id)
                                    .put("title", song.title)
                                    .put("artist", song.artist)
                                    .put("album", song.album)
                                    .put("durationMs", song.durationMs)
                                    .put("mimeType", song.mimeType)
                                    .put("artworkUrl", song.artworkUrl.orEmpty())
                            },
                        ),
                    )
            }
        runCatching {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(JSONArray(values).toString())
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun file(
        provider: RemoteMusicProvider,
        accountId: String,
    ): File =
        File(root, provider.name.lowercase())
            .apply { mkdirs() }
            .resolve("${safe(accountId)}.json")

    private fun safe(value: String): String = value.replace(UNSAFE_FILE_CHARS, "_").take(160)

    private companion object {
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9_.-]")
    }
}
