package me.spica27.spicamusic.cloud

import androidx.compose.runtime.Immutable

@Immutable
enum class MediaServerType {
    JELLYFIN,
    EMBY,
}

@Immutable
enum class RemoteMusicProvider {
    SUBSONIC,
    NETEASE,
    QQ_MUSIC,
}

@Immutable
enum class NeteaseAudioQuality(
    val value: String,
) {
    AUTO("auto"),
    STANDARD("standard"),
    EXHIGH("exhigh"),
    LOSSLESS("lossless"),
    HIRES("hires"),
    JY_EFFECT("jyeffect"),
    SKY("sky"),
    MASTER("jymaster"),
    ;

    companion object {
        fun fromValue(value: String?): NeteaseAudioQuality = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

@Immutable
enum class QqAudioQuality(
    val value: String,
) {
    AUTO("auto"),
    STANDARD("standard"),
    HIGH("high"),
    LOSSLESS("lossless"),
    ;

    companion object {
        fun fromValue(value: String?): QqAudioQuality = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

@Immutable
data class ResolvedRemoteStream(
    val url: String,
    val isPreview: Boolean = false,
)

@Immutable
data class RemoteMusicAccount(
    val id: String,
    val provider: RemoteMusicProvider,
    val displayName: String,
    val serverUrl: String = "",
    val username: String = "",
    val secret: String,
    val userId: String = "",
) {
    val normalizedServerUrl: String
        get() = serverUrl.trim().trimEnd('/')
}

@Immutable
data class RemoteSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
    val artworkUrl: String?,
)

@Immutable
data class RemoteSongPage(
    val songs: List<RemoteSong>,
    val nextOffset: Int?,
)

@Immutable
data class RemotePlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val songCount: Int,
    val creatorName: String = "",
    val isOwned: Boolean = false,
    val isLikedSongs: Boolean = false,
)

@Immutable
data class MediaServerAccount(
    val id: String,
    val type: MediaServerType,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val userId: String,
    val accessToken: String,
) {
    val normalizedServerUrl: String
        get() = serverUrl.trim().trimEnd('/')
}

@Immutable
data class CloudSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
    val imageItemId: String?,
)

@Immutable
data class CloudSongPage(
    val songs: List<CloudSong>,
    val totalCount: Int,
    val nextStartIndex: Int?,
)

@Immutable
data class TelegramConfig(
    val apiId: Int,
    val apiHash: String,
)

@Immutable
data class TelegramChannel(
    val chatId: Long,
    val title: String,
    val username: String,
)

@Immutable
data class TelegramSong(
    val messageId: Long,
    val chatId: Long,
    val fileId: Int,
    val fileSize: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val mimeType: String,
    val coverFileId: Int?,
)

@Immutable
data class TelegramSongPage(
    val songs: List<TelegramSong>,
    val nextFromMessageId: Long?,
)
