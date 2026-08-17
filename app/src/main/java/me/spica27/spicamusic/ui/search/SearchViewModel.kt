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
import me.spica27.spicamusic.feature.library.domain.SongUseCases

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
    val isLoading: Boolean = false,
    val songs: List<CloudCatalogSong> = emptyList(),
    val failedProviders: Set<RemoteMusicProvider> = emptySet(),
)

/**
 * 搜索页面 ViewModel
 * 空关键词不加载数据（WelcomeHolder），有关键词时使用 Paging 3 + InsertSeparators 实现分组
 */
@Stable
class SearchViewModel(
    private val songRepository: SongUseCases,
    private val accountStore: CloudAccountStore,
    private val remoteClients: RemoteMusicClientRegistry,
) : ViewModel() {
    // 搜索关键词
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    private val _remoteSearchState = MutableStateFlow(RemoteSearchState())
    val remoteSearchState: StateFlow<RemoteSearchState> = _remoteSearchState.asStateFlow()

    /**
     * 分页搜索结果（带分组头）
     * 空关键词时返回空的 PagingData，不做任何查询
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchPagingResults: Flow<PagingData<SearchListItem>> =
        _searchKeyword
            .debounce(300)
            .flatMapLatest { keyword ->
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
                isLoading = keyword.isNotBlank() && searchableAccounts().isNotEmpty(),
            )
    }

    /**
     * 清空搜索关键词
     */
    fun clearSearch() {
        _searchKeyword.value = ""
        _remoteSearchState.value = RemoteSearchState()
    }

    @OptIn(FlowPreview::class)
    private fun observeRemoteSearch() {
        viewModelScope.launch {
            _searchKeyword
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { rawQuery ->
                    val query = rawQuery.trim()
                    val accounts = searchableAccounts()
                    if (query.isEmpty() || accounts.isEmpty()) {
                        _remoteSearchState.value = RemoteSearchState(query = rawQuery)
                        return@collectLatest
                    }

                    _remoteSearchState.update {
                        RemoteSearchState(query = rawQuery, isLoading = true)
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
                    if (_searchKeyword.value != rawQuery) return@collectLatest

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
                            isLoading = false,
                            songs = songs,
                            failedProviders = failedProviders,
                        )
                }
        }
    }

    private fun searchableAccounts() =
        accountStore
            .getRemoteAccounts()
            .filter { account ->
                account.provider == RemoteMusicProvider.NETEASE ||
                    account.provider == RemoteMusicProvider.QQ_MUSIC
            }

    private companion object {
        const val REMOTE_SEARCH_LIMIT = 30
    }
}
