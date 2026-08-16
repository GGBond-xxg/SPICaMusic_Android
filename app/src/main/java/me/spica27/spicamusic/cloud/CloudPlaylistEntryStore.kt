package me.spica27.spicamusic.cloud

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persisted cloud entry attached to a regular local playlist. */
data class StoredCloudPlaylistSong(
    val stableId: String,
    val source: CloudSongSource,
    val accountName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val payloadType: String,
    val accountId: String?,
    val itemId: String,
    val mimeType: String,
    val imageItemId: String? = null,
    val telegramChatId: Long = 0L,
    val telegramMessageId: Long = 0L,
    val telegramFileId: Int = 0,
    val telegramFileSize: Long = 0L,
    val telegramCoverFileId: Int? = null,
)

/**
 * Sidecar for cloud songs inside Room-backed playlists.
 *
 * Room's playlist cross-reference intentionally contains MediaStore Long IDs only. Keeping cloud
 * entries here lets an existing local playlist contain every source without pretending remote
 * songs are MediaStore rows (which would be removed by the next local scan).
 */
class CloudPlaylistEntryStore(
    context: Context,
) {
    private val root = File(context.applicationContext.filesDir, "playlist_cloud_entries").apply { mkdirs() }
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    @Synchronized
    fun read(playlistId: Long): List<StoredCloudPlaylistSong> {
        val array = runCatching { JSONArray(file(playlistId).readText()) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val stableId = item.optString("stableId")
                val source = runCatching { CloudSongSource.valueOf(item.optString("source")) }.getOrNull()
                if (stableId.isBlank() || source == null) continue
                add(
                    StoredCloudPlaylistSong(
                        stableId = stableId,
                        source = source,
                        accountName = item.optString("accountName"),
                        title = item.optString("title", "Unknown title"),
                        artist = item.optString("artist", "Unknown artist"),
                        album = item.optString("album", "Unknown album"),
                        durationMs = item.optLong("durationMs").coerceAtLeast(0L),
                        artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
                        payloadType = item.optString("payloadType"),
                        accountId = item.optString("accountId").takeIf(String::isNotBlank),
                        itemId = item.optString("itemId"),
                        mimeType = item.optString("mimeType", "audio/mpeg"),
                        imageItemId = item.optString("imageItemId").takeIf(String::isNotBlank),
                        telegramChatId = item.optLong("telegramChatId"),
                        telegramMessageId = item.optLong("telegramMessageId"),
                        telegramFileId = item.optInt("telegramFileId"),
                        telegramFileSize = item.optLong("telegramFileSize"),
                        telegramCoverFileId =
                            item.optInt("telegramCoverFileId", 0).takeIf { it > 0 },
                    ),
                )
            }
        }
    }

    @Synchronized
    fun readAll(): Map<Long, List<StoredCloudPlaylistSong>> =
        root
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { file ->
                file.nameWithoutExtension.toLongOrNull()?.let { it to read(it) }
            }.filter { (_, songs) -> songs.isNotEmpty() }
            .toMap()

    @Synchronized
    fun add(
        playlistId: Long,
        song: CloudCatalogSong,
    ): List<StoredCloudPlaylistSong> {
        val current = read(playlistId)
        if (current.any { it.stableId == song.stableId }) return current
        val updated = current + song.toStored()
        write(playlistId, updated)
        _revision.update(Long::inc)
        return updated
    }

    @Synchronized
    fun removePlaylist(playlistId: Long) {
        if (file(playlistId).delete()) _revision.update(Long::inc)
    }

    private fun write(
        playlistId: Long,
        songs: List<StoredCloudPlaylistSong>,
    ) {
        val values =
            songs.map { song ->
                JSONObject()
                    .put("stableId", song.stableId)
                    .put("source", song.source.name)
                    .put("accountName", song.accountName)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("durationMs", song.durationMs)
                    .put("artworkUrl", song.artworkUrl.orEmpty())
                    .put("payloadType", song.payloadType)
                    .put("accountId", song.accountId.orEmpty())
                    .put("itemId", song.itemId)
                    .put("mimeType", song.mimeType)
                    .put("imageItemId", song.imageItemId.orEmpty())
                    .put("telegramChatId", song.telegramChatId)
                    .put("telegramMessageId", song.telegramMessageId)
                    .put("telegramFileId", song.telegramFileId)
                    .put("telegramFileSize", song.telegramFileSize)
                    .put("telegramCoverFileId", song.telegramCoverFileId ?: 0)
            }
        val target = file(playlistId)
        val temporary = File(root, "${target.name}.tmp")
        temporary.writeText(JSONArray(values).toString())
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun file(playlistId: Long) = File(root, "$playlistId.json")

    private fun CloudCatalogSong.toStored(): StoredCloudPlaylistSong =
        when (val value = payload) {
            is CloudCatalogPayload.Remote ->
                StoredCloudPlaylistSong(
                    stableId,
                    source,
                    accountName,
                    title,
                    artist,
                    album,
                    durationMs,
                    artworkUri?.toString(),
                    "remote",
                    value.account.id,
                    value.song.id,
                    value.song.mimeType,
                )

            is CloudCatalogPayload.MediaServer ->
                StoredCloudPlaylistSong(
                    stableId,
                    source,
                    accountName,
                    title,
                    artist,
                    album,
                    durationMs,
                    artworkUri?.toString(),
                    "media",
                    value.account.id,
                    value.song.id,
                    value.song.mimeType,
                    imageItemId = value.song.imageItemId,
                )

            is CloudCatalogPayload.Telegram ->
                StoredCloudPlaylistSong(
                    stableId,
                    source,
                    accountName,
                    title,
                    artist,
                    album,
                    durationMs,
                    artworkUri?.toString(),
                    "telegram",
                    null,
                    value.song.messageId.toString(),
                    value.song.mimeType,
                    telegramChatId = value.song.chatId,
                    telegramMessageId = value.song.messageId,
                    telegramFileId = value.song.fileId,
                    telegramFileSize = value.song.fileSize,
                    telegramCoverFileId = value.song.coverFileId,
                )
        }
}
