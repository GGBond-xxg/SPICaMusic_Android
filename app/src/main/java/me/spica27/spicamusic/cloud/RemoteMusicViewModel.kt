package me.spica27.spicamusic.cloud

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.spica27.spicamusic.feature.player.domain.PlayerUseCases
import me.spica27.spicamusic.player.api.PlayerAction

data class RemoteMusicUiState(
    val accounts: List<RemoteMusicAccount> = emptyList(),
    val selectedAccount: RemoteMusicAccount? = null,
    val localPlaylists: List<CloudUserPlaylist> = emptyList(),
    val remotePlaylists: List<RemotePlaylist> = emptyList(),
    val remotePlaylistSongs: Map<String, List<RemoteSong>> = emptyMap(),
    val loadingRemotePlaylists: Boolean = false,
    val loadingRemotePlaylistIds: Set<String> = emptySet(),
    val remotePlaylistError: String? = null,
    val isConnecting: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteMusicViewModel(
    val provider: RemoteMusicProvider,
    private val accountStore: CloudAccountStore,
    private val clients: RemoteMusicClientRegistry,
    private val proxy: RemoteMusicStreamProxy,
    private val player: PlayerUseCases,
    private val playlistStore: CloudUserPlaylistStore,
    private val playlistEntryStore: CloudPlaylistEntryStore,
    private val recentStore: CloudRecentStore,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteMusicUiState())
    val state = _state.asStateFlow()
    private val query = MutableStateFlow("")

    val songs: Flow<PagingData<RemoteSong>> =
        combine(state, query) { uiState, currentQuery ->
            uiState.selectedAccount to currentQuery
        }.flatMapLatest { (account, currentQuery) ->
            if (account == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(
                    PagingConfig(
                        pageSize = RemoteMusicPagingSource.PAGE_SIZE,
                        initialLoadSize = RemoteMusicPagingSource.PAGE_SIZE,
                        prefetchDistance = 12,
                        enablePlaceholders = false,
                        maxSize = RemoteMusicPagingSource.PAGE_SIZE * 6,
                    ),
                ) {
                    RemoteMusicPagingSource(clients, account, currentQuery)
                }.flow
            }
        }.cachedIn(viewModelScope)

    init {
        refreshAccounts()
    }

    fun loginSubsonic(
        serverUrl: String,
        username: String,
        password: String,
    ) {
        if (provider != RemoteMusicProvider.SUBSONIC || _state.value.isConnecting) return
        connect {
            clients.authenticateSubsonic(serverUrl, username, password)
        }
    }

    fun loginWithCookies(cookieHeader: String) {
        if (provider == RemoteMusicProvider.SUBSONIC || _state.value.isConnecting) return
        connect {
            clients.authenticateCookies(provider, cookieHeader)
        }
    }

    fun selectAccount(id: String) {
        val account = _state.value.accounts.firstOrNull { it.id == id }
        _state.update { current ->
            current.copy(
                selectedAccount = account,
                localPlaylists = account?.let { playlistStore.read(provider, it.id) }.orEmpty(),
                remotePlaylists = account?.let(clients::cachedPlaylists).orEmpty(),
                remotePlaylistSongs = emptyMap(),
                loadingRemotePlaylistIds = emptySet(),
                remotePlaylistError = null,
                error = null,
            )
        }
    }

    fun removeSelectedAccount() {
        _state.value.selectedAccount?.let {
            clients.clearCache(it.id)
            playlistEntryStore.removeAccount(it.id)
            recentStore.removeAccount(it.id)
            accountStore.removeRemoteAccount(it.id)
        }
        refreshAccounts()
    }

    fun search(value: String) {
        query.value = value.trim()
    }

    fun refreshRemotePlaylists(forceRefresh: Boolean = false) {
        if (provider != RemoteMusicProvider.QQ_MUSIC || _state.value.loadingRemotePlaylists) return
        val account = _state.value.selectedAccount ?: return
        val cached = clients.cachedPlaylists(account)
        if (cached.isNotEmpty()) {
            _state.update { it.copy(remotePlaylists = cached) }
        }
        _state.update { it.copy(loadingRemotePlaylists = true, remotePlaylistError = null) }
        viewModelScope.launch {
            runCatching { clients.listPlaylists(account, forceRefresh) }
                .onSuccess { playlists ->
                    _state.update {
                        it.copy(
                            remotePlaylists = playlists,
                            loadingRemotePlaylists = false,
                            remotePlaylistError = null,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            loadingRemotePlaylists = false,
                            remotePlaylistError = error.message ?: "无法获取 QQ 音乐歌单",
                        )
                    }
                }
        }
    }

    fun loadRemotePlaylist(
        playlistId: String,
        forceRefresh: Boolean = false,
    ) {
        val account = _state.value.selectedAccount ?: return
        if (provider != RemoteMusicProvider.QQ_MUSIC || playlistId in _state.value.loadingRemotePlaylistIds) return
        if (!forceRefresh && _state.value.remotePlaylistSongs.containsKey(playlistId)) return
        _state.update {
            it.copy(
                loadingRemotePlaylistIds = it.loadingRemotePlaylistIds + playlistId,
                remotePlaylistError = null,
            )
        }
        viewModelScope.launch {
            runCatching { clients.listPlaylistSongs(account, playlistId, forceRefresh) }
                .onSuccess { songs ->
                    _state.update {
                        it.copy(
                            remotePlaylistSongs = it.remotePlaylistSongs + (playlistId to songs),
                            loadingRemotePlaylistIds = it.loadingRemotePlaylistIds - playlistId,
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            loadingRemotePlaylistIds = it.loadingRemotePlaylistIds - playlistId,
                            remotePlaylistError = error.message ?: "无法获取 QQ 音乐歌单内容",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun createLocalPlaylist(
        name: String,
        initialSong: RemoteSong? = null,
    ) {
        val account = _state.value.selectedAccount ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val updated = playlistStore.create(provider, account.id, trimmed, initialSong)
        _state.update { it.copy(localPlaylists = updated) }
    }

    fun addSongToLocalPlaylist(
        playlistId: String,
        song: RemoteSong,
    ) {
        val account = _state.value.selectedAccount ?: return
        val updated = playlistStore.addSong(provider, account.id, playlistId, song)
        _state.update { it.copy(localPlaylists = updated) }
    }

    fun removeSongFromLocalPlaylist(
        accountId: String,
        playlistId: String,
        songId: String,
    ) {
        val updated = playlistStore.removeSong(provider, accountId, playlistId, songId)
        _state.update { current ->
            if (current.selectedAccount?.id == accountId) current.copy(localPlaylists = updated) else current
        }
    }

    fun deleteLocalPlaylist(
        accountId: String,
        playlistId: String,
    ) {
        val updated = playlistStore.delete(provider, accountId, playlistId)
        _state.update { current ->
            if (current.selectedAccount?.id == accountId) current.copy(localPlaylists = updated) else current
        }
    }

    fun play(
        selectedSong: RemoteSong,
        visibleSnapshot: List<RemoteSong>,
    ) {
        val account = _state.value.selectedAccount ?: return
        viewModelScope.launch {
            val visible =
                visibleSnapshot
                    .distinctBy(RemoteSong::id)
                    .ifEmpty { listOf(selectedSong) }
            val items =
                visible.map { song ->
                    MediaItem
                        .Builder()
                        .setMediaId(
                            "cloud:${account.provider.name.lowercase()}:${account.id}:${song.id}",
                        ).setUri(proxy.streamUrl(account, song))
                        .setMimeType(song.mimeType)
                        .setMediaMetadata(
                            MediaMetadata
                                .Builder()
                                .setTitle(song.title)
                                .setDisplayTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .setArtworkUri(song.artworkUrl?.let(Uri::parse))
                                .setDurationMs(song.durationMs)
                                .setIsPlayable(true)
                                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                .setExtras(
                                    Bundle().apply {
                                        putString("cloudProvider", account.provider.name)
                                        putString("cloudAccountId", account.id)
                                    },
                                ).build(),
                        ).build()
                }
            val startIndex =
                visible.indexOfFirst { it.id == selectedSong.id }.coerceAtLeast(0)
            player.doAction(PlayerAction.PlayMediaItems(items, startIndex))
        }
    }

    private fun connect(block: suspend () -> Result<RemoteMusicAccount>) {
        viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, error = null) }
            block().fold(
                onSuccess = { authenticated ->
                    val account = authenticated.copy(id = accountStore.newAccountId())
                    accountStore.saveRemoteAccount(account)
                    refreshAccounts(account.id)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            error = error.message ?: "Unable to connect cloud music account",
                        )
                    }
                },
            )
        }
    }

    private fun refreshAccounts(preferredId: String? = null) {
        val accounts = accountStore.getRemoteAccounts(provider)
        val currentId = preferredId ?: _state.value.selectedAccount?.id
        val selected =
            accounts.firstOrNull { it.id == currentId }
                ?: accounts.firstOrNull()
        _state.value =
            RemoteMusicUiState(
                accounts = accounts,
                selectedAccount = selected,
                localPlaylists = selected?.let { playlistStore.read(provider, it.id) }.orEmpty(),
                remotePlaylists = selected?.let(clients::cachedPlaylists).orEmpty(),
            )
    }
}
