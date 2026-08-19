package me.spica27.spicamusic.cloud

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class StoredCloudRecentSong(
    val song: StoredCloudPlaylistSong,
    val playCount: Int,
    val lastPlayedAt: Long,
)

/** Small persistent history for cloud IDs, which cannot be stored in Room's Long mediaId column. */
class CloudRecentStore(
    context: Context,
    private val accountStore: CloudAccountStore,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    @Synchronized
    fun read(): List<StoredCloudRecentSong> {
        val array =
            runCatching { JSONArray(preferences.getString(KEY_RECENT, "[]")) }.getOrNull()
                ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val source =
                    runCatching { CloudSongSource.valueOf(item.optString("source")) }.getOrNull()
                        ?: continue
                val stableId = item.optString("stableId")
                if (stableId.isBlank()) continue
                add(
                    StoredCloudRecentSong(
                        song =
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
                                telegramCoverFileId = item.optInt("telegramCoverFileId", 0).takeIf { it > 0 },
                            ),
                        playCount = item.optInt("playCount", 1).coerceAtLeast(1),
                        lastPlayedAt = item.optLong("lastPlayedAt"),
                    ),
                )
            }
        }.sortedWith(
            compareByDescending<StoredCloudRecentSong> { it.playCount }
                .thenByDescending { it.lastPlayedAt },
        ).take(MAX_ITEMS)
    }

    @Synchronized
    fun record(item: MediaItem) {
        val identity = parseCloudMediaIdentity(item.mediaId) ?: return
        val source = identity.provider.toRecentSource() ?: return
        val extras = item.mediaMetadata.extras
        val account =
            accountStore.getRemoteAccounts().firstOrNull { it.id == identity.accountOrChatId }
                ?: accountStore.getAccounts().firstOrNull { it.id == identity.accountOrChatId }
        if (identity.provider != "telegram" && account == null) return
        val payloadType =
            when (identity.provider) {
                "telegram" -> "telegram"
                "jellyfin", "emby" -> "media"
                else -> "remote"
            }
        val stored =
            StoredCloudPlaylistSong(
                stableId = item.mediaId,
                source = source,
                accountName =
                    when (account) {
                        is RemoteMusicAccount -> account.displayName
                        is MediaServerAccount -> account.displayName
                        else -> "Telegram"
                    },
                title =
                    item.mediaMetadata.title
                        ?.toString()
                        .orEmpty()
                        .ifBlank { "Unknown title" },
                artist =
                    item.mediaMetadata.artist
                        ?.toString()
                        .orEmpty()
                        .ifBlank { "Unknown artist" },
                album =
                    item.mediaMetadata.albumTitle
                        ?.toString()
                        .orEmpty()
                        .ifBlank { "Unknown album" },
                durationMs = item.mediaMetadata.durationMs?.coerceAtLeast(0L) ?: 0L,
                artworkUrl = item.mediaMetadata.artworkUri?.toString(),
                payloadType = payloadType,
                accountId = identity.accountOrChatId.takeUnless { identity.provider == "telegram" },
                itemId = identity.songId,
                mimeType = item.localConfiguration?.mimeType ?: "audio/mpeg",
                imageItemId = extras?.getString("cloudArtworkItemId"),
                telegramChatId = identity.accountOrChatId.toLongOrNull() ?: 0L,
                telegramMessageId = identity.songId.toLongOrNull() ?: 0L,
                telegramFileId = extras?.getInt("telegramFileId") ?: 0,
                telegramFileSize = extras?.getLong("telegramFileSize") ?: 0L,
                telegramCoverFileId = extras?.getInt("telegramCoverFileId")?.takeIf { it > 0 },
            )
        val current = read().associateBy { it.song.stableId }.toMutableMap()
        val previous = current[item.mediaId]
        current[item.mediaId] =
            StoredCloudRecentSong(
                song = stored,
                playCount = (previous?.playCount ?: 0) + 1,
                lastPlayedAt = System.currentTimeMillis(),
            )
        write(
            current.values
                .sortedWith(
                    compareByDescending<StoredCloudRecentSong> { it.playCount }
                        .thenByDescending { it.lastPlayedAt },
                ).take(MAX_ITEMS),
        )
        _revision.update(Long::inc)
    }

    @Synchronized
    fun removeAccount(accountId: String) {
        val filtered = read().filterNot { it.song.accountId == accountId }
        write(filtered)
        _revision.update(Long::inc)
    }

    @Synchronized
    fun removeTelegramChannel(chatId: Long) {
        val filtered = read().filterNot { it.song.telegramChatId == chatId }
        write(filtered)
        _revision.update(Long::inc)
    }

    private fun write(values: List<StoredCloudRecentSong>) {
        val array = JSONArray()
        values.forEach { value ->
            val song = value.song
            array.put(
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
                    .put("playCount", value.playCount)
                    .put("lastPlayedAt", value.lastPlayedAt),
            )
        }
        preferences.edit { putString(KEY_RECENT, array.toString()) }
    }

    private fun String.toRecentSource(): CloudSongSource? =
        when (lowercase()) {
            "telegram" -> CloudSongSource.TELEGRAM
            "jellyfin" -> CloudSongSource.JELLYFIN
            "emby" -> CloudSongSource.EMBY
            "subsonic" -> CloudSongSource.SUBSONIC
            "netease" -> CloudSongSource.NETEASE
            "qq_music" -> CloudSongSource.QQ_MUSIC
            else -> null
        }

    private companion object {
        const val PREFERENCES_NAME = "cloud_recent_history"
        const val KEY_RECENT = "recent_v1"
        const val MAX_ITEMS = 50
    }
}
