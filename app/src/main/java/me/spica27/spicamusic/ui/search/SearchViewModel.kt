package me.spica27.spicamusic.ui.search

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.spica27.spicamusic.cloud.CloudAccountStore
import me.spica27.spicamusic.cloud.CloudCatalogSong
import me.spica27.spicamusic.cloud.RemoteMusicClientRegistry
import me.spica27.spicamusic.cloud.RemoteMusicProvider
import me.spica27.spicamusic.cloud.toCatalogSong
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.common.entity.getCoverUri
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.feature.library.domain.SongUseCases
import org.json.JSONArray
import org.json.JSONObject

/**
 * SearchPage 列表项的密封类：分组头 or 歌曲
 */
sealed class SearchListItem {
    data class Header(
        val title: String,
    ) : SearchListItem()

    data class SongItem(
        val song: Song,
    ) : SearchListItem()
}

data class RemoteSearchState(
    val query: String = "",
    val source: SearchSource = SearchSource.ALL,
    val isLoading: Boolean = false,
    val songs: List<CloudCatalogSong> = emptyList(),
    val failedProviders: Set<RemoteMusicProvider> = emptySet(),
)

/** A song the user actually chose from search results (not merely a submitted keyword). */
data class RecentSearchSong(
    val stableId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val source: String,
)

enum class SearchSource(
    val remoteProvider: RemoteMusicProvider?,
) {
    ALL(null),
    LOCAL(null),
    QQ_MUSIC(RemoteMusicProvider.QQ_MUSIC),
    NETEASE(RemoteMusicProvider.NETEASE),
    ;

    val includesLocal: Boolean
        get() = this == ALL || this == LOCAL
}

/**
 * 搜索页面 ViewModel
 * 空关键词不加载数据（WelcomeHolder），有关键词时使用 Paging 3 + InsertSeparators 实现分组
 */
@Stable
class SearchViewModel(
    private val songRepository: SongUseCases,
    private val accountStore: CloudAccountStore,
    private val remoteClients: RemoteMusicClientRegistry,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {
    // 搜索关键词
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _selectedSource = MutableStateFlow(SearchSource.ALL)
    val selectedSource: StateFlow<SearchSource> = _selectedSource.asStateFlow()

    private val _recentSearchSongs = MutableStateFlow<List<RecentSearchSong>>(emptyList())
    val recentSearchSongs: StateFlow<List<RecentSearchSong>> = _recentSearchSongs.asStateFlow()

    private val _remoteSearchState = MutableStateFlow(RemoteSearchState())
    val remoteSearchState: StateFlow<RemoteSearchState> = _remoteSearchState.asStateFlow()

    /**
     * 分页搜索结果（带分组头）
     * 空关键词时返回空的 PagingData，不做任何查询
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchPagingResults: Flow<PagingData<SearchListItem>> =
        combine(_searchKeyword, _selectedSource) { keyword, source -> keyword to source }
            .debounce(300)
            .flatMapLatest { (keyword, source) ->
                if (keyword.isBlank()) {
                    flowOf(
                        PagingData.empty(
                            sourceLoadStates =
                                LoadStates(
                                    refresh = LoadState.Loading,
                                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                                    append = LoadState.NotLoading(endOfPaginationReached = true),
                                ),
                        ),
                    )
                } else if (!source.includesLocal) {
                    flowOf(PagingData.empty())
                } else {
                    songRepository
                        .getSongsBySortNamePagingFlow(keyword)
                        .map { pagingData ->
                            pagingData
                                .map<Song, SearchListItem> { song -> SearchListItem.SongItem(song) }
                                .insertSeparators { before, after ->
                                    val beforeSort = (before as? SearchListItem.SongItem)?.song?.sortName
                                    val afterSort = (after as? SearchListItem.SongItem)?.song?.sortName
                                    if (afterSort != null && beforeSort != afterSort) {
                                        SearchListItem.Header(afterSort)
                                    } else {
                                        null
                                    }
                                }
                        }
                }
            }.cachedIn(viewModelScope)

    init {
        observeSearchHistory()
        observeRemoteSearch()
    }

    /**
     * 更新搜索关键词
     */
    fun updateSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
        _remoteSearchState.value =
            RemoteSearchState(
                query = keyword,
                source = _selectedSource.value,
                isLoading =
                    keyword.isNotBlank() &&
                        searchableAccounts(_selectedSource.value).isNotEmpty(),
            )
    }

    fun selectSource(source: SearchSource) {
        if (_selectedSource.value == source) return
        _selectedSource.value = source
        _remoteSearchState.value =
            RemoteSearchState(
                query = _searchKeyword.value,
                source = source,
                isLoading =
                    _searchKeyword.value.isNotBlank() && searchableAccounts(source).isNotEmpty(),
            )
    }

    fun submitSearch() {
        // Search submission intentionally does not write history. The product history is a list
        // of songs the user selected, so abandoned/typo queries never displace useful entries.
    }

    fun recordRecentSong(song: Song) {
        recordRecentSong(
            RecentSearchSong(
                stableId = "local:${song.mediaStoreId}",
                title = song.displayName,
                artist = song.artist,
                artworkUri = song.getCoverUri()?.toString(),
                source = RECENT_SOURCE_LOCAL,
            ),
        )
    }

    fun recordRecentSong(song: CloudCatalogSong) {
        recordRecentSong(
            RecentSearchSong(
                stableId = song.stableId,
                title = song.title,
                artist = song.artist,
                artworkUri = song.artworkUri?.toString(),
                source = song.source.name,
            ),
        )
    }

    private fun recordRecentSong(song: RecentSearchSong) {
        val updated =
            (listOf(song) + _recentSearchSongs.value.filterNot { it.stableId == song.stableId })
                .take(MAX_SEARCH_HISTORY)
        _recentSearchSongs.value = updated
        viewModelScope.launch {
            preferencesManager.setString(
                PreferencesManager.Keys.SEARCH_HISTORY,
                encodeRecentSongs(updated),
            )
        }
    }

    fun clearSearchHistory() {
        _recentSearchSongs.value = emptyList()
        viewModelScope.launch {
            preferencesManager.setString(PreferencesManager.Keys.SEARCH_HISTORY, "")
        }
    }

    /**
     * 清空搜索关键词
     */
    fun clearSearch() {
        _searchKeyword.value = ""
        _remoteSearchState.value =
            RemoteSearchState(source = _selectedSource.value)
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            preferencesManager
                .getString(PreferencesManager.Keys.SEARCH_HISTORY)
                .collectLatest { stored ->
                    _recentSearchSongs.value = decodeRecentSongs(stored)
                }
        }
    }

    private fun encodeRecentSongs(songs: List<RecentSearchSong>): String =
        JSONArray()
            .apply {
                songs.forEach { song ->
                    put(
                        JSONObject()
                            .put("id", song.stableId)
                            .put("title", song.title)
                            .put("artist", song.artist)
                            .put("artwork", song.artworkUri)
                            .put("source", song.source),
                    )
                }
            }.toString()

    private fun decodeRecentSongs(raw: String): List<RecentSearchSong> =
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until minOf(array.length(), MAX_SEARCH_HISTORY)) {
                    val value = array.optJSONObject(index) ?: continue
                    val stableId = value.optString("id").trim()
                    val title = value.optString("title").trim()
                    if (stableId.isEmpty() || title.isEmpty()) continue
                    add(
                        RecentSearchSong(
                            stableId = stableId,
                            title = title,
                            artist = value.optString("artist"),
                            artworkUri = value.optString("artwork").takeIf(String::isNotBlank),
                            source = value.optString("source", RECENT_SOURCE_LOCAL),
                        ),
                    )
                }
            }.distinctBy(RecentSearchSong::stableId)
        }.getOrDefault(emptyList())

    @OptIn(FlowPreview::class)
    private fun observeRemoteSearch() {
        viewModelScope.launch {
            combine(_searchKeyword, _selectedSource) { query, source ->
                query to source
            }.debounce(300)
                .distinctUntilChanged()
                .collectLatest { (rawQuery, selectedSource) ->
                    val query = rawQuery.trim()
                    val accounts = searchableAccounts(selectedSource)
                    if (query.isEmpty() || accounts.isEmpty()) {
                        _remoteSearchState.value =
                            RemoteSearchState(
                                query = rawQuery,
                                source = selectedSource,
                            )
                        return@collectLatest
                    }

                    _remoteSearchState.update {
                        RemoteSearchState(
                            query = rawQuery,
                            source = selectedSource,
                            isLoading = true,
                        )
                    }
                    val responses =
                        coroutineScope {
                            accounts
                                .map { account ->
                                    async {
                                        account to
                                            runCatching {
                                                remoteClients.listSongs(
                                                    account = account,
                                                    query = query,
                                                    offset = 0,
                                                    limit = REMOTE_SEARCH_LIMIT,
                                                )
                                            }
                                    }
                                }.awaitAll()
                        }
                    if (
                        _searchKeyword.value != rawQuery ||
                        _selectedSource.value != selectedSource
                    ) {
                        return@collectLatest
                    }

                    val failedProviders =
                        responses
                            .filter { (_, result) -> result.isFailure }
                            .mapTo(linkedSetOf()) { (account, _) -> account.provider }
                    val songs =
                        responses
                            .flatMap { (account, result) ->
                                result
                                    .getOrNull()
                                    ?.songs
                                    .orEmpty()
                                    .map(account::toCatalogSong)
                            }.distinctBy(CloudCatalogSong::stableId)
                    _remoteSearchState.value =
                        RemoteSearchState(
                            query = rawQuery,
                            source = selectedSource,
                            isLoading = false,
                            songs = songs,
                            failedProviders = failedProviders,
                        )
                }
        }
    }

    private fun searchableAccounts(source: SearchSource) =
        accountStore.getRemoteAccounts().filter { account ->
            when (source) {
                SearchSource.ALL ->
                    account.provider == RemoteMusicProvider.QQ_MUSIC ||
                        account.provider == RemoteMusicProvider.NETEASE
                SearchSource.LOCAL -> false
                SearchSource.QQ_MUSIC,
                SearchSource.NETEASE,
                -> account.provider == source.remoteProvider
            }
        }

    private companion object {
        const val REMOTE_SEARCH_LIMIT = 30
        const val MAX_SEARCH_HISTORY = 10
        const val RECENT_SOURCE_LOCAL = "LOCAL"
    }
}
