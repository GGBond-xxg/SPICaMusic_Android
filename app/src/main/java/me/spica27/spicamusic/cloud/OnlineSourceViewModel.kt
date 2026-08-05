package me.spica27.spicamusic.cloud

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.spica27.spicamusic.player.api.IMusicPlayer
import me.spica27.spicamusic.player.api.PlayerAction

data class OnlineSourceUiState(
    val status: OnlineSourceStatus = OnlineSourceStatus(),
    val selectedSource: String? = null,
    val query: String = "",
    val songs: List<OnlineSourceSong> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val importing: Boolean = false,
    val page: Int = 1,
    val endReached: Boolean = true,
    val message: String? = null,
)

class OnlineSourceViewModel(
    private val fileStore: OnlineSourceFileStore,
    private val engine: OnlineSourceEngine,
    private val repository: OnlineSourceRepository,
    private val player: IMusicPlayer,
) : ViewModel() {
    private val _state = MutableStateFlow(OnlineSourceUiState())
    val state: StateFlow<OnlineSourceUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = engine.status(refresh = true)
            val selected = chooseSource(status, _state.value.selectedSource)
            _state.value =
                _state.value.copy(
                    status = status,
                    selectedSource = selected,
                    importing = false,
                    message = status.error,
                )
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, message = null)
            fileStore
                .import(uri)
                .onSuccess {
                    val status = engine.reload()
                    _state.value =
                        OnlineSourceUiState(
                            status = status,
                            selectedSource = chooseSource(status, null),
                            message = status.error ?: "音源已导入",
                        )
                }.onFailure {
                    _state.value =
                        _state.value.copy(
                            importing = false,
                            message = it.message ?: "导入失败",
                        )
                }
        }
    }

    fun import(url: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, message = null)
            fileStore
                .import(url)
                .onSuccess {
                    val status = engine.reload()
                    _state.value =
                        OnlineSourceUiState(
                            status = status,
                            selectedSource = chooseSource(status, null),
                            message = status.error ?: "音源已导入",
                        )
                }.onFailure {
                    _state.value =
                        _state.value.copy(
                            importing = false,
                            message = it.message ?: "下载失败",
                        )
                }
        }
    }

    fun remove() {
        viewModelScope.launch {
            runCatching {
                engine.close()
                fileStore.delete()
            }.onSuccess {
                _state.value = OnlineSourceUiState(message = "音源已移除")
            }.onFailure {
                _state.value = _state.value.copy(message = it.message ?: "移除失败")
            }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun selectSource(key: String) {
        if (_state.value.status.sources
                .none { it.key == key }
        ) {
            return
        }
        _state.value =
            _state.value.copy(
                selectedSource = key,
                songs = emptyList(),
                page = 1,
                endReached = true,
                message = null,
            )
    }

    fun search() {
        val snapshot = _state.value
        val query = snapshot.query.trim()
        val source = snapshot.status.sources.firstOrNull { it.key == snapshot.selectedSource }
        if (query.isBlank() || source == null || snapshot.loading) return
        viewModelScope.launch {
            _state.value =
                _state.value.copy(
                    loading = true,
                    loadingMore = false,
                    songs = emptyList(),
                    page = 1,
                    endReached = false,
                    message = null,
                )
            runCatching { repository.search(source, query, page = 1) }
                .onSuccess { songs ->
                    _state.value =
                        _state.value.copy(
                            loading = false,
                            songs = songs.distinctBy { "${it.source}:${it.id}" },
                            endReached = songs.size < PAGE_SIZE,
                            message = if (songs.isEmpty()) "没有找到歌曲" else null,
                        )
                }.onFailure {
                    _state.value =
                        _state.value.copy(
                            loading = false,
                            endReached = true,
                            message = it.message ?: "搜索失败",
                        )
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val source = snapshot.status.sources.firstOrNull { it.key == snapshot.selectedSource }
        if (source == null ||
            snapshot.loading ||
            snapshot.loadingMore ||
            snapshot.endReached ||
            snapshot.query.isBlank()
        ) {
            return
        }
        val nextPage = snapshot.page + 1
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, message = null)
            runCatching { repository.search(source, snapshot.query.trim(), nextPage) }
                .onSuccess { more ->
                    val merged =
                        (_state.value.songs + more)
                            .distinctBy { "${it.source}:${it.id}" }
                    _state.value =
                        _state.value.copy(
                            loadingMore = false,
                            songs = merged,
                            page = nextPage,
                            endReached = more.size < PAGE_SIZE,
                        )
                }.onFailure {
                    _state.value =
                        _state.value.copy(
                            loadingMore = false,
                            message = it.message ?: "加载更多失败",
                        )
                }
        }
    }

    fun play(song: OnlineSourceSong) {
        val queue = _state.value.songs.ifEmpty { listOf(song) }
        val items = queue.map(::toMediaItem)
        val startIndex = queue.indexOfFirst { it.source == song.source && it.id == song.id }.coerceAtLeast(0)
        player.doAction(PlayerAction.PlayMediaItems(items, startIndex))
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun toMediaItem(song: OnlineSourceSong): MediaItem {
        val extras =
            Bundle().apply {
                putString(EXTRA_ONLINE_SOURCE, song.source)
                putString(EXTRA_ONLINE_SONG_INFO, song.rawInfoJson)
                putString("cloudProvider", "ONLINE_SOURCE")
            }
        val metadata =
            MediaMetadata
                .Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setDurationMs(song.durationMs.takeIf { it > 0 })
                .setArtworkUri(song.artworkUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .setExtras(extras)
                .build()
        return MediaItem
            .Builder()
            .setMediaId("cloud:online:${song.source}:${song.id}")
            .setUri("spica-online://${song.source}/${Uri.encode(song.id)}")
            .setMediaMetadata(metadata)
            .build()
    }

    private fun chooseSource(
        status: OnlineSourceStatus,
        preferred: String?,
    ): String? {
        if (status.sources.any { it.key == preferred }) return preferred
        return status.sources
            .firstOrNull {
                "musicSearch" in it.actions || "search" in it.actions
            }?.key ?: status.sources.firstOrNull { it.key == "wy" }?.key ?: status.sources.firstOrNull()?.key
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val EXTRA_ONLINE_SOURCE = "onlineSource"
        const val EXTRA_ONLINE_SONG_INFO = "onlineSongInfo"
    }
}
