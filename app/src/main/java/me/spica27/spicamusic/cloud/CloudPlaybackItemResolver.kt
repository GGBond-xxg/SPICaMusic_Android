package me.spica27.spicamusic.cloud

import android.net.Uri
import androidx.media3.common.MediaItem
import org.json.JSONObject

/**
 * Rebuilds process-local and credential-bearing cloud URLs after process death.
 *
 * The player persists the queue's MediaItems so its UI state can be restored,
 * but Telegram and remote providers use a loopback proxy with a new port in
 * every process. Media-server URLs are also regenerated from the encrypted
 * account store so restored items never depend on an old access token.
 */
class CloudPlaybackItemResolver(
    private val accountStore: CloudAccountStore,
    private val mediaServerClient: MediaServerClient,
    private val telegramProxy: TelegramStreamProxy,
    private val remoteProxy: RemoteMusicStreamProxy,
    private val onlineSourceProxy: OnlineSourceStreamProxy,
    private val onlineSourceEngine: OnlineSourceEngine,
) {
    suspend fun resolve(item: MediaItem): MediaItem {
        if (!item.mediaId.startsWith(CLOUD_ID_PREFIX)) return item

        val identity = parseCloudMediaIdentity(item.mediaId) ?: return item
        val provider = identity.provider
        val accountOrChatId = identity.accountOrChatId
        val songId = identity.songId
        val extras = item.mediaMetadata.extras

        val resolved =
            runCatching {
                when (provider) {
                    TELEGRAM_PROVIDER -> {
                        val chatId = accountOrChatId.toLongOrNull() ?: return item
                        val messageId = songId.toLongOrNull() ?: return item
                        val hasArtwork =
                            extras?.containsKey(EXTRA_TELEGRAM_COVER_FILE_ID) == true ||
                                item.mediaMetadata.artworkUri != null

                        ResolvedUrls(
                            streamUrl = telegramProxy.streamUrl(chatId, messageId),
                            artworkUrl =
                                if (hasArtwork) {
                                    telegramProxy.restoredArtworkUrl(chatId, messageId)
                                } else {
                                    null
                                },
                        )
                    }

                    EMBY_PROVIDER,
                    JELLYFIN_PROVIDER,
                    -> {
                        val account =
                            accountStore
                                .getAccounts()
                                .firstOrNull { it.id == accountOrChatId }
                                ?: return item
                        val artworkItemId =
                            extras?.getString(EXTRA_CLOUD_ARTWORK_ITEM_ID)
                                ?: mediaServerArtworkItemId(item.mediaMetadata.artworkUri)
                        ResolvedUrls(
                            streamUrl = mediaServerClient.streamUrl(account, songId),
                            artworkUrl = artworkItemId?.let { mediaServerClient.imageUrl(account, it) },
                        )
                    }

                    ONLINE_PROVIDER -> {
                        val source =
                            extras
                                ?.getString(EXTRA_ONLINE_SOURCE)
                                ?.takeIf(String::isNotBlank)
                                ?: accountOrChatId
                        val songInfo =
                            extras
                                ?.getString(EXTRA_ONLINE_SONG_INFO)
                                ?.takeIf(String::isNotBlank)
                                ?: return item
                        ResolvedUrls(
                            streamUrl = onlineSourceProxy.streamUrl(source, songInfo),
                            artworkUrl = null,
                        )
                    }

                    else -> {
                        val accountExists =
                            accountStore
                                .getRemoteAccounts()
                                .any { it.id == accountOrChatId }
                        if (!accountExists) return item
                        val fallbackUrl = remoteProxy.streamUrl(accountOrChatId, songId)
                        val sourceKey =
                            when (provider) {
                                NETEASE_PROVIDER -> "wy"
                                QQ_MUSIC_PROVIDER -> "tx"
                                else -> null
                            }
                        val usableSourceKey =
                            sourceKey?.takeIf { key ->
                                runCatching {
                                    onlineSourceEngine
                                        .status()
                                        .takeIf(OnlineSourceStatus::ready)
                                        ?.sources
                                        ?.firstOrNull { it.key == key }
                                        ?.actions
                                        ?.contains("musicUrl") == true
                                }.getOrDefault(false)
                            }
                        ResolvedUrls(
                            streamUrl =
                                if (usableSourceKey != null) {
                                    onlineSourceProxy.streamUrl(
                                        source = usableSourceKey,
                                        songInfoJson = onlineSongInfo(item, songId),
                                        fallbackUrl = fallbackUrl,
                                    )
                                } else {
                                    fallbackUrl
                                },
                            artworkUrl = null,
                        )
                    }
                }
            }.getOrNull() ?: return item

        val builder = item.buildUpon().setUri(resolved.streamUrl)
        resolved.artworkUrl?.let { artworkUrl ->
            builder.setMediaMetadata(
                item.mediaMetadata
                    .buildUpon()
                    .setArtworkUri(Uri.parse(artworkUrl))
                    .build(),
            )
        }
        return builder.build()
    }

    private fun mediaServerArtworkItemId(uri: Uri?): String? {
        val segments = uri?.pathSegments ?: return null
        val itemsIndex = segments.indexOf("Items")
        return segments.getOrNull(itemsIndex + 1).takeIf { itemsIndex >= 0 && !it.isNullOrBlank() }
    }

    private fun onlineSongInfo(
        item: MediaItem,
        songId: String,
    ): String =
        JSONObject()
            .put("id", songId)
            .put("songmid", songId)
            .put("name", item.mediaMetadata.title?.toString().orEmpty())
            .put("singer", item.mediaMetadata.artist?.toString().orEmpty())
            .put("artist", item.mediaMetadata.artist?.toString().orEmpty())
            .put("albumName", item.mediaMetadata.albumTitle?.toString().orEmpty())
            .put("duration", item.mediaMetadata.durationMs ?: 0L)
            .put("pic", item.mediaMetadata.artworkUri?.toString().orEmpty())
            .toString()

    private data class ResolvedUrls(
        val streamUrl: String,
        val artworkUrl: String?,
    )

    private companion object {
        const val CLOUD_ID_PREFIX = "cloud:"
        const val TELEGRAM_PROVIDER = "telegram"
        const val EMBY_PROVIDER = "emby"
        const val JELLYFIN_PROVIDER = "jellyfin"
        const val ONLINE_PROVIDER = "online"
        const val NETEASE_PROVIDER = "netease"
        const val QQ_MUSIC_PROVIDER = "qq_music"
        const val EXTRA_TELEGRAM_COVER_FILE_ID = "telegramCoverFileId"
        const val EXTRA_CLOUD_ARTWORK_ITEM_ID = "cloudArtworkItemId"
        const val EXTRA_ONLINE_SOURCE = "onlineSource"
        const val EXTRA_ONLINE_SONG_INFO = "onlineSongInfo"
    }
}

internal data class CloudMediaIdentity(
    val provider: String,
    val accountOrChatId: String,
    val songId: String,
)

internal fun parseCloudMediaIdentity(mediaId: String): CloudMediaIdentity? {
    val parts = mediaId.split(':', limit = 4)
    if (parts.size != 4 || parts[0] != "cloud") return null
    val provider = parts[1].lowercase().takeIf(String::isNotBlank) ?: return null
    val accountOrChatId = parts[2].takeIf(String::isNotBlank) ?: return null
    val songId = parts[3].takeIf(String::isNotBlank) ?: return null
    return CloudMediaIdentity(provider, accountOrChatId, songId)
}
