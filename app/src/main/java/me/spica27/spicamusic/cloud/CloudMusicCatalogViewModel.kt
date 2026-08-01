package me.spica27.spicamusic.cloud

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.common.entity.getCoverUri
import me.spica27.spicamusic.feature.player.domain.PlayerUseCases
import me.spica27.spicamusic.player.api.PlayerAction
import org.drinkless.tdlib.TdApi

@Immutable
enum class CloudSongSource {
    TELEGRAM,
    JELLYFIN,
    EMBY,
    SUBSONIC,
    NETEASE,
    QQ_MUSIC,
}

@Immutable
data class CloudCatalogSong(
    val stableId: String,
    val source: CloudSongSource,
    val accountName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: Uri?,
    val payload: CloudCatalogPayload,
)

@Immutable
sealed interface CloudCatalogPayload {
    data class Telegram(
        val song: TelegramSong,
    ) : CloudCatalogPayload

    data class MediaServer(
        val account: MediaServerAccount,
        val song: CloudSong,
    ) : CloudCatalogPayload

    data class Remote(
        val account: RemoteMusicAccount,
        val song: RemoteSong,
    ) : CloudCatalogPayload
}

@Immutable
sealed interface CatalogQueueItem {
    val stableId: String

    data class Local(
        val song: Song,
    ) : CatalogQueueItem {
        override val stableId: String = "local:${song.mediaStoreId}"
    }

    data class Cloud(
        val song: CloudCatalogSong,
    ) : CatalogQueueItem {
        override val stableId: String = song.stableId
    }
}

@Immutable
data class CloudMusicCatalogState(
    val songs: List<CloudCatalogSong> = emptyList(),
    val songCounts: Map<CloudSongSource, Int> = emptyMap(),
    val availableSources: Set<CloudSongSource> = emptySet(),
    val loadingSources: Set<CloudSongSource> = emptySet(),
    val errors: Map<CloudSongSource, String> = emptyMap(),
    val sourcesWithMore: Set<CloudSongSource> = emptySet(),
    val isRefreshing: Boolean = false,
)

/**
 * Main music-page catalog. It keeps small, independently paged cursors for every configured
 * account/channel and only materializes MediaItems when the user starts playback.
 */
class CloudMusicCatalogViewModel(
    private val accountStore: CloudAccountStore,
    private val countStore: CloudCatalogCountStore,
    private val mediaServerClient: MediaServerClient,
    private val remoteClients: RemoteMusicClientRegistry,
    private val remoteProxy: RemoteMusicStreamProxy,
    private val telegramRepository: TelegramRepository,
    private val telegramProxy: TelegramStreamProxy,
    private val player: PlayerUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(CloudMusicCatalogState())
    val state = _state.asStateFlow()

    private val endpoints = linkedMapOf<String, CatalogEndpoint>()
    private val loadingEndpoints = mutableSetOf<String>()
    private var telegramReady = false
    private var catalogGeneration = 0

    init {
        refreshSources()
        viewModelScope.launch {
            telegramRepository.authorizationState.collect { authorization ->
                val ready = authorization is TdApi.AuthorizationStateReady
                if (ready && !telegramReady) {
                    telegramReady = true
                    refreshSources()
                    loadMore(CloudSongSource.TELEGRAM)
                } else {
                    telegramReady = ready
                }
            }
        }
    }

    fun refreshSources() {
        val desired = buildEndpoints()
        if (desired.keys != endpoints.keys) {
            catalogGeneration += 1
            endpoints.clear()
            endpoints.putAll(desired)
            loadingEndpoints.clear()
            countStore.retain(desired.keys)
            _state.update { current ->
                current.copy(
                    songs =
                        current.songs.filter { song ->
                            song.endpointKey() in desired
                        },
                    availableSources = desired.values.mapTo(linkedSetOf()) { endpoint -> endpoint.source },
                    loadingSources = emptySet(),
                    errors = emptyMap(),
                    sourcesWithMore = emptySet(),
                    isRefreshing = false,
                )
            }
        }
        publishStatus()
        loadMore()
    }

    /**
     * Fully enumerates every configured endpoint in the background. The currently visible
     * catalog and its last confirmed counts stay in place until the refresh succeeds, so a
     * 94-song library never temporarily regresses to Telegram's 60-song page size.
     */
    fun refreshCatalog() {
        if (_state.value.isRefreshing) return
        val desired = buildEndpoints()
        catalogGeneration += 1
        val generation = catalogGeneration
        endpoints.clear()
        endpoints.putAll(desired)
        loadingEndpoints.clear()
        countStore.retain(desired.keys)
        loadingEndpoints +=
            desired.values
                .filter { endpoint ->
                    endpoint.source != CloudSongSource.TELEGRAM || telegramReady
                }.map(CatalogEndpoint::key)
        _state.update { current ->
            current.copy(
                songs = current.songs.filter { it.endpointKey() in desired },
                availableSources = desired.values.mapTo(linkedSetOf()) { endpoint -> endpoint.source },
                errors = emptyMap(),
                isRefreshing = loadingEndpoints.isNotEmpty(),
            )
        }
        publishStatus()
        if (loadingEndpoints.isEmpty()) return

        viewModelScope.launch {
            val refreshed = linkedMapOf<String, List<CloudCatalogSong>>()
            val refreshErrors = linkedMapOf<CloudSongSource, String>()
            desired.values.forEach { endpoint ->
                if (endpoint.key !in loadingEndpoints) return@forEach
                runCatching { endpoint.loadAll() }
                    .onSuccess { songs ->
                        refreshed[endpoint.key] = songs
                        countStore.put(endpoint.key, songs.size)
                    }.onFailure { error ->
                        endpoint.hasMore = false
                        refreshErrors[endpoint.source] =
                            error.message ?: "Unable to refresh cloud songs"
                    }
            }
            if (generation != catalogGeneration) return@launch

            loadingEndpoints.clear()
            _state.update { current ->
                val currentByEndpoint = current.songs.groupBy(CloudCatalogSong::endpointKey)
                current.copy(
                    songs =
                        desired.values
                            .flatMap { endpoint ->
                                refreshed[endpoint.key]
                                    ?: currentByEndpoint[endpoint.key].orEmpty()
                            }.distinctBy(CloudCatalogSong::stableId),
                    errors = refreshErrors,
                    isRefreshing = false,
                )
            }
            publishStatus()
        }
    }

    fun loadMore(source: CloudSongSource? = null) {
        endpoints.values
            .filter { endpoint ->
                endpoint.hasMore &&
                    endpoint.key !in loadingEndpoints &&
                    (source == null || endpoint.source == source) &&
                    (endpoint.source != CloudSongSource.TELEGRAM || telegramReady)
            }.forEach(::loadEndpoint)
        if (_state.value.isRefreshing && loadingEndpoints.isEmpty()) {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun play(
        selectedStableId: String,
        visibleQueue: List<CatalogQueueItem>,
    ) {
        viewModelScope.launch {
            val visibleCloudSongs =
                visibleQueue
                    .filterIsInstance<CatalogQueueItem.Cloud>()
                    .map(CatalogQueueItem.Cloud::song)
            val endpointKeys = visibleCloudSongs.mapTo(linkedSetOf(), CloudCatalogSong::endpointKey)
            val completedSongs = completeEndpointsForPlayback(endpointKeys)
            val visibleIds = visibleQueue.mapTo(hashSetOf(), CatalogQueueItem::stableId)
            val queue =
                (
                    visibleQueue +
                        completedSongs
                            .asSequence()
                            .filter { it.endpointKey() in endpointKeys }
                            .filterNot { it.stableId in visibleIds }
                            .map(CatalogQueueItem::Cloud)
                            .toList()
                ).distinctBy(CatalogQueueItem::stableId)
                    .ifEmpty {
                        _state.value.songs
                            .firstOrNull { it.stableId == selectedStableId }
                            ?.let { listOf(CatalogQueueItem.Cloud(it)) }
                            .orEmpty()
                    }
            val mediaItems = queue.mapNotNull { it.toMediaItem() }
            if (mediaItems.isEmpty()) return@launch
            val startIndex =
                queue
                    .indexOfFirst { it.stableId == selectedStableId }
                    .coerceIn(mediaItems.indices)
            player.doAction(PlayerAction.PlayMediaItems(mediaItems, startIndex))
        }
    }

    /**
     * A paged catalog is sufficient for rendering, but a playback queue must represent the whole
     * selected cloud library. Complete only the endpoints visible in the current filter, merge the
     * result into the catalog cache, and keep the already-rendered order at the head of the queue.
     */
    private suspend fun completeEndpointsForPlayback(endpointKeys: Set<String>): List<CloudCatalogSong> {
        if (endpointKeys.isEmpty()) return _state.value.songs
        val completedByEndpoint = linkedMapOf<String, List<CloudCatalogSong>>()
        endpointKeys.forEach { key ->
            val endpoint = endpoints[key] ?: return@forEach
            if (!endpoint.hasMore) return@forEach
            runCatching { endpoint.loadRemaining() }
                .onSuccess { songs ->
                    completedByEndpoint[key] = songs
                    val total =
                        mergeCatalogSongs(
                            _state.value.songs.filter { it.endpointKey() == key },
                            songs,
                        ).size
                    countStore.put(key, total)
                }.onFailure { error ->
                    _state.update { current ->
                        current.copy(
                            errors =
                                current.errors +
                                    (
                                        endpoint.source to
                                            (error.message ?: "Unable to complete cloud queue")
                                    ),
                        )
                    }
                }
        }
        if (completedByEndpoint.isNotEmpty()) {
            _state.update { current ->
                current.copy(
                    songs =
                        completedByEndpoint.values.fold(current.songs) { songs, incoming ->
                            mergeCatalogSongs(songs, incoming)
                        },
                )
            }
            publishStatus()
        }
        return _state.value.songs
    }

    private fun loadEndpoint(endpoint: CatalogEndpoint) {
        val generation = catalogGeneration
        loadingEndpoints += endpoint.key
        publishStatus()
        viewModelScope.launch {
            runCatching { endpoint.loadNextPage() }
                .onSuccess { page ->
                    if (generation != catalogGeneration) return@onSuccess
                    endpoint.hasMore = page.hasMore
                    _state.update { current ->
                        current.copy(
                            songs = mergeCatalogSongs(current.songs, page.songs),
                            errors = current.errors - endpoint.source,
                        )
                    }
                    if (!page.hasMore) {
                        val endpointCount =
                            _state.value.songs.count { song ->
                                song.endpointKey() == endpoint.key
                            }
                        countStore.put(endpoint.key, endpointCount)
                    }
                }.onFailure { error ->
                    if (generation != catalogGeneration) return@onFailure
                    endpoint.hasMore = false
                    _state.update { current ->
                        current.copy(
                            errors =
                                current.errors +
                                    (
                                        endpoint.source to
                                            (error.message ?: "Unable to load cloud songs")
                                    ),
                        )
                    }
                }
            if (generation == catalogGeneration) loadingEndpoints -= endpoint.key
            publishStatus()
        }
    }

    private fun buildEndpoints(): LinkedHashMap<String, CatalogEndpoint> =
        linkedMapOf<String, CatalogEndpoint>().apply {
            telegramRepository.savedChannels().forEach { channel ->
                val endpoint = TelegramEndpoint(channel)
                put(endpoint.key, endpoint)
            }
            accountStore.getAccounts().forEach { account ->
                val endpoint = MediaServerEndpoint(account)
                put(endpoint.key, endpoint)
            }
            accountStore.getRemoteAccounts().forEach { account ->
                val endpoint = RemoteEndpoint(account)
                put(endpoint.key, endpoint)
            }
        }

    private fun publishStatus() {
        val available = endpoints.values.mapTo(linkedSetOf()) { it.source }
        val loading =
            endpoints.values
                .filter { it.key in loadingEndpoints }
                .mapTo(linkedSetOf()) { it.source }
        val withMore =
            endpoints.values
                .filter(CatalogEndpoint::hasMore)
                .mapTo(linkedSetOf()) { it.source }
        val loadedCounts =
            _state.value.songs
                .groupingBy(CloudCatalogSong::endpointKey)
                .eachCount()
        val knownCounts =
            endpoints.values
                .groupBy(CatalogEndpoint::source)
                .mapValues { (_, sourceEndpoints) ->
                    sourceEndpoints.sumOf { endpoint ->
                        maxOf(
                            countStore.get(endpoint.key) ?: 0,
                            loadedCounts[endpoint.key] ?: 0,
                        )
                    }
                }
        _state.update {
            it.copy(
                songCounts = knownCounts,
                availableSources = available,
                loadingSources = loading,
                sourcesWithMore = withMore,
                isRefreshing = it.isRefreshing && loading.isNotEmpty(),
            )
        }
    }

    private abstract inner class CatalogEndpoint(
        val key: String,
        val source: CloudSongSource,
    ) {
        var hasMore: Boolean = true
        private val loadMutex = Mutex()

        abstract suspend fun load(): CatalogPage

        suspend fun loadNextPage(): CatalogPage =
            loadMutex.withLock {
                load()
            }

        suspend fun loadAll(): List<CloudCatalogSong> =
            loadMutex.withLock {
                loadPages()
            }

        suspend fun loadRemaining(): List<CloudCatalogSong> =
            loadMutex.withLock {
                if (!hasMore) emptyList() else loadPages()
            }

        private suspend fun loadPages(): List<CloudCatalogSong> {
            val collected = linkedMapOf<String, CloudCatalogSong>()
            var pagesLoaded = 0
            do {
                val page = load()
                page.songs.forEach { song -> collected[song.stableId] = song }
                hasMore = page.hasMore
                pagesLoaded += 1
            } while (hasMore && page.songs.isNotEmpty() && pagesLoaded < MAX_REFRESH_PAGES)
            check(!hasMore) { "Cloud catalog refresh exceeded its safe page limit" }
            return collected.values.toList()
        }
    }

    private inner class TelegramEndpoint(
        private val channel: TelegramChannel,
    ) : CatalogEndpoint(
            key = "telegram:${channel.chatId}",
            source = CloudSongSource.TELEGRAM,
        ) {
        private var cursor = 0L

        override suspend fun load(): CatalogPage {
            val page =
                telegramRepository.getAudioPage(
                    chatId = channel.chatId,
                    fromMessageId = cursor,
                )
            cursor = page.nextFromMessageId ?: 0L
            return CatalogPage(
                songs =
                    page.songs.map { song ->
                        CloudCatalogSong(
                            stableId = "cloud:telegram:${song.chatId}:${song.messageId}",
                            source = source,
                            accountName = channel.title,
                            title = song.title,
                            artist = song.artist,
                            album = channel.title.ifBlank { "Telegram" },
                            durationMs = song.durationMs,
                            artworkUri = telegramProxy.artworkUrl(song)?.let(Uri::parse),
                            payload = CloudCatalogPayload.Telegram(song),
                        )
                    },
                hasMore = page.nextFromMessageId != null && page.songs.isNotEmpty(),
            )
        }
    }

    private inner class MediaServerEndpoint(
        private val account: MediaServerAccount,
    ) : CatalogEndpoint(
            key = "media:${account.id}",
            source =
                when (account.type) {
                    MediaServerType.JELLYFIN -> CloudSongSource.JELLYFIN
                    MediaServerType.EMBY -> CloudSongSource.EMBY
                },
        ) {
        private var offset = 0

        override suspend fun load(): CatalogPage {
            val page = mediaServerClient.getSongs(account, offset).getOrThrow()
            offset = page.nextStartIndex ?: offset
            return CatalogPage(
                songs =
                    page.songs.map { song ->
                        CloudCatalogSong(
                            stableId = "cloud:${account.type.name.lowercase()}:${account.id}:${song.id}",
                            source = source,
                            accountName = account.displayName,
                            title = song.title,
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.durationMs,
                            artworkUri =
                                song.imageItemId
                                    ?.let { mediaServerClient.imageUrl(account, it) }
                                    ?.let(Uri::parse),
                            payload = CloudCatalogPayload.MediaServer(account, song),
                        )
                    },
                hasMore = page.nextStartIndex != null && page.songs.isNotEmpty(),
            )
        }
    }

    private inner class RemoteEndpoint(
        private val account: RemoteMusicAccount,
    ) : CatalogEndpoint(
            key = "remote:${account.id}",
            source =
                when (account.provider) {
                    RemoteMusicProvider.SUBSONIC -> CloudSongSource.SUBSONIC
                    RemoteMusicProvider.NETEASE -> CloudSongSource.NETEASE
                    RemoteMusicProvider.QQ_MUSIC -> CloudSongSource.QQ_MUSIC
                },
        ) {
        private var offset = 0

        override suspend fun load(): CatalogPage {
            val page =
                remoteClients.listSongs(
                    account = account,
                    query = "",
                    offset = offset,
                    limit = REMOTE_PAGE_SIZE,
                )
            offset = page.nextOffset ?: offset
            return CatalogPage(
                songs =
                    page.songs.map { song ->
                        CloudCatalogSong(
                            stableId = "cloud:${account.provider.name.lowercase()}:${account.id}:${song.id}",
                            source = source,
                            accountName = account.displayName,
                            title = song.title,
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.durationMs,
                            artworkUri = song.artworkUrl?.let(Uri::parse),
                            payload = CloudCatalogPayload.Remote(account, song),
                        )
                    },
                hasMore = page.nextOffset != null && page.songs.isNotEmpty(),
            )
        }
    }

    private suspend fun CatalogQueueItem.toMediaItem(): MediaItem? =
        when (this) {
            is CatalogQueueItem.Local -> song.toLocalMediaItem()
            is CatalogQueueItem.Cloud ->
                when (val value = song.payload) {
                    is CloudCatalogPayload.Telegram ->
                        cloudMediaItem(
                            mediaId = song.stableId,
                            uri = telegramProxy.streamUrl(value.song),
                            mimeType = value.song.mimeType,
                            metadata = song,
                            extras =
                                Bundle().apply {
                                    putString("cloudProvider", "TELEGRAM")
                                    putLong("telegramChatId", value.song.chatId)
                                    putInt("telegramFileId", value.song.fileId)
                                },
                        )

                    is CloudCatalogPayload.MediaServer ->
                        cloudMediaItem(
                            mediaId = song.stableId,
                            uri = mediaServerClient.streamUrl(value.account, value.song.id),
                            mimeType = value.song.mimeType,
                            metadata = song,
                            extras =
                                Bundle().apply {
                                    putString("cloudProvider", value.account.type.name)
                                    putString("cloudAccountId", value.account.id)
                                },
                        )

                    is CloudCatalogPayload.Remote ->
                        cloudMediaItem(
                            mediaId = song.stableId,
                            uri = remoteProxy.streamUrl(value.account, value.song),
                            mimeType = value.song.mimeType,
                            metadata = song,
                            extras =
                                Bundle().apply {
                                    putString("cloudProvider", value.account.provider.name)
                                    putString("cloudAccountId", value.account.id)
                                },
                        )
                }
        }

    private fun Song.toLocalMediaItem(): MediaItem {
        val songUri = "content://media/external/audio/media/$mediaStoreId".toUri()
        return MediaItem
            .Builder()
            .setMediaId(mediaStoreId.toString())
            .setUri(songUri)
            .setMimeType(mimeType)
            .setRequestMetadata(
                MediaItem.RequestMetadata
                    .Builder()
                    .setMediaUri(songUri)
                    .build(),
            ).setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(displayName)
                    .setDisplayTitle(displayName)
                    .setArtist(artist)
                    .setSubtitle(artist)
                    .setArtworkUri(getCoverUri())
                    .setDurationMs(duration)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsPlayable(true)
                    .setIsBrowsable(true)
                    .setExtras(
                        Bundle().apply {
                            putLong("mediaStoreId", mediaStoreId)
                            putLong("albumId", albumId)
                            putInt("sampleRate", sampleRate)
                            putInt("bitRate", bitRate)
                            putInt("channels", channels)
                            putInt("digit", digit)
                            putString("waveformData", waveformData)
                        },
                    ).build(),
            ).build()
    }

    private fun cloudMediaItem(
        mediaId: String,
        uri: String,
        mimeType: String,
        metadata: CloudCatalogSong,
        extras: Bundle,
    ): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(metadata.title)
                    .setDisplayTitle(metadata.title)
                    .setArtist(metadata.artist)
                    .setAlbumTitle(metadata.album)
                    .setArtworkUri(metadata.artworkUri)
                    .setDurationMs(metadata.durationMs)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(extras)
                    .build(),
            ).build()

    private data class CatalogPage(
        val songs: List<CloudCatalogSong>,
        val hasMore: Boolean,
    )

    private companion object {
        const val REMOTE_PAGE_SIZE = 80
        const val MAX_REFRESH_PAGES = 1_000
    }
}

internal fun CloudCatalogSong.endpointKey(): String =
    when (val value = payload) {
        is CloudCatalogPayload.Telegram -> "telegram:${value.song.chatId}"
        is CloudCatalogPayload.MediaServer -> "media:${value.account.id}"
        is CloudCatalogPayload.Remote -> "remote:${value.account.id}"
    }

internal fun mergeCatalogSongs(
    current: List<CloudCatalogSong>,
    incoming: List<CloudCatalogSong>,
): List<CloudCatalogSong> {
    if (incoming.isEmpty()) return current
    val incomingById = incoming.associateBy(CloudCatalogSong::stableId)
    val merged =
        current
            .map { song ->
                incomingById[song.stableId] ?: song
            }.toMutableList()
    val existingIds = current.mapTo(hashSetOf(), CloudCatalogSong::stableId)
    incoming.filterTo(merged) { song -> song.stableId !in existingIds }
    return merged
}
