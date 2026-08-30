package me.spica27.spicamusic.cloud

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.common.entity.getCoverUri
import me.spica27.spicamusic.feature.library.domain.PlaylistUseCases
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
data class CloudCatalogPlaylist(
    val stableId: String,
    val account: RemoteMusicAccount,
    val playlist: RemotePlaylist,
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
    val remotePlaylists: List<CloudCatalogPlaylist> = emptyList(),
    val userPlaylists: List<CloudUserPlaylist> = emptyList(),
    val playlistSongs: Map<String, List<CloudCatalogSong>> = emptyMap(),
    val loadingPlaylists: Set<String> = emptySet(),
    val dailyRecommendations: List<CloudCatalogSong> = emptyList(),
    val isLoadingDailyRecommendations: Boolean = false,
    val dailyRecommendationsError: String? = null,
    val localPlaylistCloudSongs: Map<Long, List<CloudCatalogSong>> = emptyMap(),
    val recentCloudSongs: List<CloudCatalogSong> = emptyList(),
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
    private val playlistRepository: PlaylistUseCases,
    private val userPlaylistStore: CloudUserPlaylistStore,
    private val playlistEntryStore: CloudPlaylistEntryStore,
    private val recentStore: CloudRecentStore,
) : ViewModel() {
    private val _state = MutableStateFlow(CloudMusicCatalogState())
    val state = _state.asStateFlow()

    private val endpoints = linkedMapOf<String, CatalogEndpoint>()
    private val loadingEndpoints = mutableSetOf<String>()
    private var telegramReady = false
    private var catalogGeneration = 0
    private var remotePlaylistRefreshJob: Job? = null
    private var playbackQueueCompletionJob: Job? = null

    init {
        refreshSources()
        publishLocalPlaylistEntries()
        publishRecentCloudSongs()
        viewModelScope.launch {
            userPlaylistStore.revision.drop(1).collect { refreshUserPlaylists() }
        }
        viewModelScope.launch {
            playlistEntryStore.revision.drop(1).collect { publishLocalPlaylistEntries() }
        }
        viewModelScope.launch {
            recentStore.revision.drop(1).collect { publishRecentCloudSongs() }
        }
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
        refreshRemotePlaylists()
        refreshDailyRecommendations()
        refreshUserPlaylists()
        publishLocalPlaylistEntries()
        publishRecentCloudSongs()
    }

    fun refreshRemotePlaylists(forceRefresh: Boolean = false) {
        val accounts =
            accountStore
                .getRemoteAccounts()
                .filter { it.provider == RemoteMusicProvider.NETEASE || it.provider == RemoteMusicProvider.QQ_MUSIC }
                .associateBy(RemoteMusicAccount::id)
        if (accounts.isEmpty()) {
            _state.update {
                it.copy(
                    remotePlaylists = emptyList(),
                    playlistSongs = emptyMap(),
                    loadingPlaylists = emptySet(),
                )
            }
            return
        }
        val cached =
            accounts.values.flatMap { account ->
                remoteClients.cachedPlaylists(account).map { playlist ->
                    CloudCatalogPlaylist(
                        stableId = "${account.provider.name.lowercase()}:${account.id}:${playlist.id}",
                        account = account,
                        playlist = playlist,
                    )
                }
            }
        if (cached.isNotEmpty()) {
            _state.update { current -> current.copy(remotePlaylists = cached) }
        }
        if (remotePlaylistRefreshJob?.isActive == true) {
            if (!forceRefresh) return
            remotePlaylistRefreshJob?.cancel()
        }
        remotePlaylistRefreshJob =
            viewModelScope.launch {
                val refreshedByAccount = mutableMapOf<String, List<CloudCatalogPlaylist>>()
                accounts.values.forEach { account ->
                    // A normal cold start reuses the published disk cache. Only an explicit user
                    // refresh is allowed to replace it with fresh server metadata.
                    runCatching { remoteClients.listPlaylists(account, forceRefresh = forceRefresh) }
                        .onSuccess { playlists ->
                            refreshedByAccount[account.id] =
                                playlists.map { playlist ->
                                    CloudCatalogPlaylist(
                                        stableId = "${account.provider.name.lowercase()}:${account.id}:${playlist.id}",
                                        account = account,
                                        playlist = playlist,
                                    )
                                }
                        }
                }
                _state.update { current ->
                    current.copy(
                        remotePlaylists =
                            accounts.values.flatMap { account ->
                                refreshedByAccount[account.id]
                                    ?: current.remotePlaylists.filter { it.account.id == account.id }
                            },
                    )
                }
            }
    }

    /** Compatibility entry point used by the NetEase account screen. */
    fun refreshNeteasePlaylists(forceRefresh: Boolean = false) = refreshRemotePlaylists(forceRefresh)

    fun refreshDailyRecommendations(forceRefresh: Boolean = false) {
        val account = accountStore.getRemoteAccounts(RemoteMusicProvider.NETEASE).firstOrNull()
        if (account == null) {
            _state.update {
                it.copy(
                    dailyRecommendations = emptyList(),
                    isLoadingDailyRecommendations = false,
                    dailyRecommendationsError = null,
                )
            }
            return
        }
        if (_state.value.isLoadingDailyRecommendations) return
        _state.update { it.copy(isLoadingDailyRecommendations = true, dailyRecommendationsError = null) }
        viewModelScope.launch {
            runCatching { remoteClients.dailyRecommendations(account, forceRefresh) }
                .onSuccess { songs ->
                    _state.update {
                        it.copy(
                            dailyRecommendations = songs.map { song -> account.toCatalogSong(song) },
                            isLoadingDailyRecommendations = false,
                            dailyRecommendationsError = null,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoadingDailyRecommendations = false,
                            dailyRecommendationsError = error.message ?: "无法获取网易云每日推荐",
                        )
                    }
                }
        }
    }

    fun addToLocalPlaylist(
        playlistId: Long,
        song: CloudCatalogSong,
    ) {
        playlistEntryStore.add(playlistId, song)
    }

    fun addRemoteSongToLocalPlaylist(
        playlistId: Long,
        account: RemoteMusicAccount,
        song: RemoteSong,
    ) {
        addToLocalPlaylist(playlistId, account.toCatalogSong(song))
    }

    fun createLocalPlaylistAndAdd(
        name: String,
        song: CloudCatalogSong,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(trimmed)
            playlistEntryStore.add(playlistId, song)
        }
    }

    fun createLocalPlaylistFromQueue(
        name: String,
        items: List<CatalogQueueItem>,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || items.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepository.createPlaylist(trimmed)
            val localIds =
                items.filterIsInstance<CatalogQueueItem.Local>().map { it.song.mediaStoreId }
            if (localIds.isNotEmpty()) {
                playlistRepository.addSongsToPlaylist(playlistId, localIds)
            }
            items.filterIsInstance<CatalogQueueItem.Cloud>().forEach { item ->
                playlistEntryStore.add(playlistId, item.song)
            }
        }
    }

    fun removeLocalPlaylistEntries(playlistId: Long) {
        playlistEntryStore.removePlaylist(playlistId)
    }

    private fun refreshUserPlaylists() {
        val playlists =
            accountStore.getRemoteAccounts().flatMap { account ->
                userPlaylistStore.read(account.provider, account.id)
            }
        _state.update { it.copy(userPlaylists = playlists) }
    }

    private fun publishLocalPlaylistEntries() {
        val resolved =
            playlistEntryStore.readAll().mapValues { (_, songs) ->
                songs.mapNotNull(::resolveStoredPlaylistSong)
            }
        _state.update { it.copy(localPlaylistCloudSongs = resolved) }
    }

    private fun publishRecentCloudSongs() {
        _state.update { current ->
            current.copy(
                recentCloudSongs =
                    recentStore.read().mapNotNull { recent ->
                        resolveStoredPlaylistSong(recent.song)
                    },
            )
        }
    }

    private fun resolveStoredPlaylistSong(value: StoredCloudPlaylistSong): CloudCatalogSong? {
        val payload =
            when (value.payloadType) {
                "remote" -> {
                    val account = accountStore.getRemoteAccounts().firstOrNull { it.id == value.accountId } ?: return null
                    CloudCatalogPayload.Remote(
                        account,
                        RemoteSong(
                            value.itemId,
                            value.title,
                            value.artist,
                            value.album,
                            value.durationMs,
                            value.mimeType,
                            value.artworkUrl,
                        ),
                    )
                }

                "media" -> {
                    val account = accountStore.getAccounts().firstOrNull { it.id == value.accountId } ?: return null
                    CloudCatalogPayload.MediaServer(
                        account,
                        CloudSong(
                            value.itemId,
                            value.title,
                            value.artist,
                            value.album,
                            value.durationMs,
                            value.mimeType,
                            value.imageItemId,
                        ),
                    )
                }

                "telegram" ->
                    if (telegramRepository.savedChannels().any { it.chatId == value.telegramChatId }) {
                        CloudCatalogPayload.Telegram(
                            TelegramSong(
                                value.telegramMessageId,
                                value.telegramChatId,
                                value.telegramFileId,
                                value.telegramFileSize,
                                value.title,
                                value.artist,
                                value.durationMs,
                                value.mimeType,
                                value.telegramCoverFileId,
                            ),
                        )
                    } else {
                        return null
                    }

                else -> return null
            }
        return CloudCatalogSong(
            stableId = value.stableId,
            source = value.source,
            accountName = value.accountName,
            title = value.title,
            artist = value.artist,
            album = value.album,
            durationMs = value.durationMs,
            artworkUri = value.artworkUrl?.let(Uri::parse),
            payload = payload,
        )
    }

    fun loadPlaylist(
        value: CloudCatalogPlaylist,
        forceRefresh: Boolean = false,
    ) {
        if (value.stableId in _state.value.loadingPlaylists) return
        if (!forceRefresh &&
            _state.value.playlistSongs[value.stableId]
                .orEmpty()
                .isNotEmpty()
        ) {
            return
        }
        _state.update { it.copy(loadingPlaylists = it.loadingPlaylists + value.stableId) }
        viewModelScope.launch {
            runCatching {
                remoteClients.listPlaylistSongs(
                    account = value.account,
                    playlistId = value.playlist.id,
                    forceRefresh = forceRefresh,
                )
            }.onSuccess { songs ->
                _state.update { current ->
                    current.copy(
                        playlistSongs =
                            current.playlistSongs +
                                (
                                    value.stableId to
                                        songs.map { song -> value.account.toCatalogSong(song) }
                                ),
                    )
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        errors =
                            current.errors +
                                (
                                    value.account.provider.toCloudSongSource() to
                                        (error.message ?: "Unable to load playlist")
                                ),
                    )
                }
            }
            _state.update { it.copy(loadingPlaylists = it.loadingPlaylists - value.stableId) }
        }
    }

    fun playPlaylistSongs(
        selectedStableId: String,
        songs: List<CloudCatalogSong>,
    ) {
        viewModelScope.launch {
            val items = songs.mapNotNull { CatalogQueueItem.Cloud(it).toMediaItem() }
            if (items.isEmpty()) return@launch
            val index = songs.indexOfFirst { it.stableId == selectedStableId }.coerceAtLeast(0)
            player.doAction(PlayerAction.PlayMediaItems(items, index.coerceIn(items.indices)))
        }
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
        playbackQueueCompletionJob?.cancel()
        playbackQueueCompletionJob =
            viewModelScope.launch {
                // Start from the already-rendered queue immediately. Completing every paged
                // cloud endpoint before handing anything to Media3 made a cloud row feel much
                // slower than a local row, even though the selected item was already available.
                val immediateQueue =
                    visibleQueue
                        .distinctBy(CatalogQueueItem::stableId)
                        .ifEmpty {
                            _state.value.songs
                                .firstOrNull { it.stableId == selectedStableId }
                                ?.let { listOf(CatalogQueueItem.Cloud(it)) }
                                .orEmpty()
                        }
                // Keep the catalog item beside its converted MediaItem. Local catalog IDs use
                // the "local:" prefix while MediaStore media IDs do not, so looking up the
                // selected row through MediaItem.mediaId can incorrectly fall back to index 0.
                val immediateEntries =
                    immediateQueue.mapNotNull { queueItem ->
                        queueItem.toMediaItem()?.let { mediaItem -> queueItem to mediaItem }
                    }
                val immediateItems = immediateEntries.map { it.second }
                if (immediateItems.isEmpty()) return@launch
                val startIndex =
                    immediateEntries
                        .indexOfFirst { (queueItem, _) -> queueItem.stableId == selectedStableId }
                        .coerceAtLeast(0)
                        .coerceIn(immediateItems.indices)
                val selectedMediaId = immediateItems[startIndex].mediaId
                player.doAction(PlayerAction.PlayMediaItems(immediateItems, startIndex))

                // Preserve the previous whole-library queue behaviour, but fill the remaining
                // pages after playback has started. A later tap cancels this job so an obsolete
                // catalog cannot be appended to the user's new queue.
                val visibleCloudSongs =
                    immediateQueue
                        .filterIsInstance<CatalogQueueItem.Cloud>()
                        .map(CatalogQueueItem.Cloud::song)
                val endpointKeys =
                    visibleCloudSongs.mapTo(linkedSetOf(), CloudCatalogSong::endpointKey)
                if (endpointKeys.isEmpty()) return@launch
                val completedSongs = completeEndpointsForPlayback(endpointKeys)
                val currentQueueIds = player.currentTimelineItems.value.mapTo(hashSetOf()) { it.mediaId }
                if (selectedMediaId !in currentQueueIds) return@launch
                val missingItems =
                    completedSongs
                        .asSequence()
                        .filter { it.endpointKey() in endpointKeys }
                        .filterNot { it.stableId in currentQueueIds }
                        .map(CatalogQueueItem::Cloud)
                        .toList()
                        .mapNotNull { it.toMediaItem() }
                if (missingItems.isNotEmpty()) {
                    player.doAction(PlayerAction.AddMediaItemsToQueue(missingItems))
                }
            }
    }

    fun addToNext(song: CloudCatalogSong) {
        viewModelScope.launch {
            CatalogQueueItem
                .Cloud(song)
                .toMediaItem()
                ?.let { player.doAction(PlayerAction.AddMediaItemToNext(it)) }
        }
    }

    fun addToQueue(song: CloudCatalogSong) {
        viewModelScope.launch {
            CatalogQueueItem
                .Cloud(song)
                .toMediaItem()
                ?.let { player.doAction(PlayerAction.AddMediaItemsToQueue(listOf(it))) }
        }
    }

    /**
     * A paged catalog is sufficient for rendering, but a playback queue must represent the whole
     * selected cloud library. Complete only the endpoints visible in the current filter with an
     * independent cursor. The visible catalog must not change merely because a song was clicked:
     * otherwise newly fetched entries are inserted by the active sort and the tapped row jumps.
     */
    private suspend fun completeEndpointsForPlayback(endpointKeys: Set<String>): List<CloudCatalogSong> {
        if (endpointKeys.isEmpty()) return _state.value.songs
        val completedByEndpoint = linkedMapOf<String, List<CloudCatalogSong>>()
        endpointKeys.forEach { key ->
            val endpoint = endpoints[key] ?: return@forEach
            if (!endpoint.hasMore) return@forEach
            val playbackContinuation = endpoint.playbackContinuation()
            runCatching { playbackContinuation.loadRemaining() }
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
        return completedByEndpoint.values.fold(_state.value.songs) { songs, incoming ->
            mergeCatalogSongs(songs, incoming)
        }
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

        protected abstract fun copyForPlaybackContinuation(): CatalogEndpoint

        suspend fun loadNextPage(): CatalogPage =
            loadMutex.withLock {
                load()
            }

        suspend fun playbackContinuation(): CatalogEndpoint =
            loadMutex.withLock {
                copyForPlaybackContinuation()
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

        override fun copyForPlaybackContinuation(): CatalogEndpoint =
            TelegramEndpoint(channel).also { copy ->
                copy.cursor = cursor
                copy.hasMore = hasMore
            }

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

        override fun copyForPlaybackContinuation(): CatalogEndpoint =
            MediaServerEndpoint(account).also { copy ->
                copy.offset = offset
                copy.hasMore = hasMore
            }

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

        override fun copyForPlaybackContinuation(): CatalogEndpoint =
            RemoteEndpoint(account).also { copy ->
                copy.offset = offset
                copy.hasMore = hasMore
            }

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
                songs = page.songs.map { song -> account.toCatalogSong(song) },
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
                                    putLong("telegramFileSize", value.song.fileSize)
                                    value.song.coverFileId?.let { putInt("telegramCoverFileId", it) }
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
                                    value.song.imageItemId?.let { putString("cloudArtworkItemId", it) }
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

internal fun RemoteMusicAccount.toCatalogSong(song: RemoteSong): CloudCatalogSong =
    CloudCatalogSong(
        stableId = "cloud:${provider.name.lowercase()}:$id:${song.id}",
        source =
            when (provider) {
                RemoteMusicProvider.SUBSONIC -> CloudSongSource.SUBSONIC
                RemoteMusicProvider.NETEASE -> CloudSongSource.NETEASE
                RemoteMusicProvider.QQ_MUSIC -> CloudSongSource.QQ_MUSIC
            },
        accountName = displayName,
        title = song.title,
        artist = song.artist,
        album = song.album,
        durationMs = song.durationMs,
        artworkUri = song.artworkUrl?.let(Uri::parse),
        payload = CloudCatalogPayload.Remote(this, song),
    )

internal fun CloudCatalogSong.endpointKey(): String =
    when (val value = payload) {
        is CloudCatalogPayload.Telegram -> "telegram:${value.song.chatId}"
        is CloudCatalogPayload.MediaServer -> "media:${value.account.id}"
        is CloudCatalogPayload.Remote -> "remote:${value.account.id}"
    }

private fun RemoteMusicProvider.toCloudSongSource(): CloudSongSource =
    when (this) {
        RemoteMusicProvider.SUBSONIC -> CloudSongSource.SUBSONIC
        RemoteMusicProvider.NETEASE -> CloudSongSource.NETEASE
        RemoteMusicProvider.QQ_MUSIC -> CloudSongSource.QQ_MUSIC
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
