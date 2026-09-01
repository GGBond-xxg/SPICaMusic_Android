package me.spica27.spicamusic.cloud

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.first
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.common.entity.Playlist
import me.spica27.spicamusic.ui.home.HomeViewModel
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.playlist.PlaylistViewModel
import me.spica27.spicamusic.ui.widget.AudioCover
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private fun RemoteSong.remoteMediaId(
    provider: RemoteMusicProvider,
    accountId: String,
): String = "cloud:${provider.name.lowercase()}:$accountId:$id"

class RemoteMusicScene(
    private val provider: RemoteMusicProvider,
) : StackScene() {
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val viewModel: RemoteMusicViewModel =
            koinViewModel(key = "remote_music_${provider.name}") {
                parametersOf(provider)
            }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val catalogViewModel: CloudMusicCatalogViewModel = koinActivityViewModel()
        val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
        val playlistViewModel: PlaylistViewModel = koinActivityViewModel()
        val homePlaylists by playlistViewModel.playlists.collectAsStateWithLifecycle()
        val songs = viewModel.songs.collectAsLazyPagingItems()
        var showLogin by rememberSaveable(provider) { mutableStateOf(state.accounts.isEmpty()) }
        var searchText by rememberSaveable(provider) { mutableStateOf("") }
        var hasSubmittedSearch by rememberSaveable(provider) { mutableStateOf(false) }
        var showCreatePlaylist by remember { mutableStateOf(false) }
        var showPlaylistPicker by remember { mutableStateOf(false) }
        var pendingPlaylistSong by remember { mutableStateOf<RemoteSong?>(null) }
        val showsRemotePlaylistLanding =
            !hasSubmittedSearch &&
                (provider == RemoteMusicProvider.NETEASE || provider == RemoteMusicProvider.QQ_MUSIC)

        if (showCreatePlaylist) {
            CloudPlaylistNameDialog(
                provider = provider,
                onDismiss = {
                    showCreatePlaylist = false
                    pendingPlaylistSong = null
                },
                onConfirm = { name ->
                    viewModel.createLocalPlaylist(name, pendingPlaylistSong)
                    showCreatePlaylist = false
                    pendingPlaylistSong = null
                },
            )
        }
        if (showPlaylistPicker) {
            CloudPlaylistPickerDialog(
                cloudPlaylists = state.localPlaylists,
                homePlaylists = homePlaylists,
                onDismiss = {
                    showPlaylistPicker = false
                    pendingPlaylistSong = null
                },
                onCreate = {
                    showPlaylistPicker = false
                    showCreatePlaylist = true
                },
                onSelect = { playlistId ->
                    pendingPlaylistSong?.let { viewModel.addSongToLocalPlaylist(playlistId, it) }
                    showPlaylistPicker = false
                    pendingPlaylistSong = null
                },
                onSelectHome = { playlistId ->
                    val account = state.selectedAccount
                    val song = pendingPlaylistSong
                    if (account != null && song != null) {
                        catalogViewModel.addRemoteSongToLocalPlaylist(playlistId, account, song)
                    }
                    showPlaylistPicker = false
                    pendingPlaylistSong = null
                },
            )
        }

        LaunchedEffect(state.accounts.size) {
            if (state.accounts.isEmpty()) showLogin = true
            if (state.accounts.isNotEmpty() && !state.isConnecting && state.error == null) {
                showLogin = false
            }
        }
        LaunchedEffect(provider, state.selectedAccount?.id) {
            if (provider == RemoteMusicProvider.QQ_MUSIC && state.selectedAccount != null) {
                viewModel.refreshRemotePlaylists()
            }
        }

        RemoteSceneScaffold(
            title = provider.displayName,
            onBack = { path.popTop() },
            actions = {
                if (state.selectedAccount != null && !showLogin) {
                    IconButton(
                        onClick = {
                            when (provider) {
                                RemoteMusicProvider.NETEASE -> {
                                    catalogViewModel.refreshNeteasePlaylists(forceRefresh = true)
                                    catalogViewModel.refreshDailyRecommendations(forceRefresh = true)
                                }
                                RemoteMusicProvider.QQ_MUSIC ->
                                    viewModel.refreshRemotePlaylists(forceRefresh = true)
                                RemoteMusicProvider.SUBSONIC -> songs.refresh()
                            }
                        },
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            },
        ) { padding ->
            if (showLogin) {
                when (provider) {
                    RemoteMusicProvider.SUBSONIC ->
                        SubsonicLogin(
                            state = state,
                            hasExistingAccount = state.accounts.isNotEmpty(),
                            modifier = Modifier.fillMaxSize().padding(padding),
                            onCancel = {
                                viewModel.clearError()
                                showLogin = false
                            },
                            onLogin = viewModel::loginSubsonic,
                        )
                    RemoteMusicProvider.NETEASE,
                    RemoteMusicProvider.QQ_MUSIC,
                    ->
                        CookieWebLogin(
                            provider = provider,
                            state = state,
                            hasExistingAccount = state.accounts.isNotEmpty(),
                            modifier = Modifier.fillMaxSize().padding(padding),
                            onCancel = {
                                viewModel.clearError()
                                showLogin = false
                            },
                            onCookiesCaptured = viewModel::loginWithCookies,
                        )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 10.dp,
                            bottom = 36.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "account", contentType = "account") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.selectedAccount?.let { account ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            account.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            provider.accountSubtitle(account),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = viewModel::removeSelectedAccount) {
                                        Icon(Icons.Default.DeleteOutline, "Remove account")
                                    }
                                    if (provider != RemoteMusicProvider.SUBSONIC) {
                                        IconButton(onClick = { showCreatePlaylist = true }) {
                                            Icon(Icons.Default.PlaylistPlay, "创建本地歌单")
                                        }
                                    }
                                    IconButton(onClick = { showLogin = true }) {
                                        Icon(Icons.Default.Add, "Add account")
                                    }
                                }
                            }
                            if (state.accounts.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.accounts.take(3).forEach { account ->
                                        if (account.id == state.selectedAccount?.id) {
                                            FilledTonalButton(
                                                onClick = { viewModel.selectAccount(account.id) },
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                            ) {
                                                Text(account.displayName, maxLines = 1)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { viewModel.selectAccount(account.id) },
                                                contentPadding = PaddingValues(horizontal = 12.dp),
                                            ) {
                                                Text(account.displayName, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    modifier = Modifier.weight(1f).height(58.dp),
                                    singleLine = true,
                                    placeholder = { Text("搜索${provider.displayName}歌曲") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    shape = RoundedCornerShape(20.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions =
                                        KeyboardActions(
                                            onSearch = {
                                                hasSubmittedSearch = searchText.isNotBlank()
                                                viewModel.search(searchText)
                                            },
                                        ),
                                )
                                FilledTonalButton(
                                    onClick = {
                                        hasSubmittedSearch = searchText.isNotBlank()
                                        viewModel.search(searchText)
                                    },
                                    modifier = Modifier.height(58.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                ) {
                                    Text("搜索")
                                }
                            }
                        }
                    }

                    if (provider != RemoteMusicProvider.SUBSONIC && state.localPlaylists.isNotEmpty()) {
                        item(key = "local_playlist_title", contentType = "section_title") {
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                                Text("本地歌单", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "仅保存在本机，不会同步到${provider.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            count = state.localPlaylists.size,
                            key = { index -> "local:${state.localPlaylists[index].id}" },
                            contentType = { "local_cloud_playlist" },
                        ) { index ->
                            val playlist = state.localPlaylists[index]
                            CloudUserPlaylistRow(playlist) {
                                path.push(CloudUserPlaylistScene(playlist))
                            }
                        }
                    }

                    if (provider == RemoteMusicProvider.NETEASE && !hasSubmittedSearch) {
                        val accountId = state.selectedAccount?.id
                        val dailySongs =
                            catalogState.dailyRecommendations.mapNotNull { catalogSong ->
                                val payload = catalogSong.payload as? CloudCatalogPayload.Remote
                                payload
                                    ?.takeIf { it.account.id == accountId }
                                    ?.song
                            }
                        val playlists =
                            catalogState.remotePlaylists.filter {
                                it.account.provider == RemoteMusicProvider.NETEASE &&
                                    it.account.id == accountId
                            }
                        item(key = "netease_daily_title", contentType = "section_title") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("每日推荐", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "来自你的网易云账号",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        catalogViewModel.refreshDailyRecommendations(forceRefresh = true)
                                    },
                                ) {
                                    Icon(Icons.Default.Refresh, "刷新每日推荐")
                                }
                            }
                        }
                        when {
                            catalogState.isLoadingDailyRecommendations && dailySongs.isEmpty() -> {
                                item(key = "netease_daily_loading", contentType = "loading") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            catalogState.dailyRecommendationsError != null && dailySongs.isEmpty() -> {
                                item(key = "netease_daily_error", contentType = "error") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            catalogState.dailyRecommendationsError.orEmpty(),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                catalogViewModel.refreshDailyRecommendations(forceRefresh = true)
                                            },
                                        ) {
                                            Text("重试")
                                        }
                                    }
                                }
                            }
                            else -> {
                                items(
                                    count = dailySongs.size,
                                    key = { index -> "daily:${dailySongs[index].id}" },
                                    contentType = { "netease_daily_song" },
                                ) { index ->
                                    val song = dailySongs[index]
                                    RemoteSongRow(
                                        song = song,
                                        onClick = { viewModel.play(song, dailySongs) },
                                        onAddToPlaylist = {
                                            pendingPlaylistSong = song
                                            showPlaylistPicker = true
                                        },
                                    )
                                }
                            }
                        }
                        item(key = "netease_collection_title", contentType = "section_title") {
                            Text(
                                "网易云收藏歌单",
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (playlists.isEmpty()) {
                            item(key = "netease_playlists_empty", contentType = "empty") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (catalogState.loadingSources.contains(CloudSongSource.NETEASE)) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                    Text("还没有获取到收藏歌单")
                                    Text(
                                        "点击右上角刷新；网络异常时会继续显示上次成功获取的歌单。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            items(
                                count = playlists.size,
                                key = { index -> playlists[index].stableId },
                                contentType = { "netease_playlist" },
                            ) { index ->
                                val playlist = playlists[index]
                                NeteasePlaylistRow(playlist) {
                                    path.push(NeteasePlaylistScene(playlist))
                                }
                            }
                        }
                    }

                    if (provider == RemoteMusicProvider.QQ_MUSIC && !hasSubmittedSearch) {
                        item(key = "qq_collection_title", contentType = "section_title") {
                            Text(
                                "QQ 音乐歌单",
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        when {
                            state.loadingRemotePlaylists -> {
                                item(key = "qq_playlists_loading", contentType = "loading") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            state.remotePlaylistError != null -> {
                                item(key = "qq_playlists_error", contentType = "error") {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            state.remotePlaylistError.orEmpty(),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        OutlinedButton(
                                            onClick = { viewModel.refreshRemotePlaylists(forceRefresh = true) },
                                        ) {
                                            Text("重试")
                                        }
                                    }
                                }
                            }
                            state.remotePlaylists.isEmpty() -> {
                                item(key = "qq_playlists_empty", contentType = "empty") {
                                    Text(
                                        "还没有获取到 QQ 音乐歌单",
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                items(
                                    count = state.remotePlaylists.size,
                                    key = { index -> "qq:${state.remotePlaylists[index].id}" },
                                    contentType = { "qq_playlist" },
                                ) { index ->
                                    val playlist = state.remotePlaylists[index]
                                    RemotePlaylistRow(playlist) {
                                        path.push(QqPlaylistScene(playlist))
                                    }
                                }
                            }
                        }
                    }

                    items(
                        count =
                            if (showsRemotePlaylistLanding) {
                                0
                            } else {
                                songs.itemCount
                            },
                        key = songs.itemKey(RemoteSong::id),
                        contentType = songs.itemContentType { "remote_song" },
                    ) { index ->
                        songs[index]?.let { song ->
                            RemoteSongRow(
                                song = song,
                                onClick = { viewModel.play(song, songs.itemSnapshotList.items) },
                                onAddToPlaylist =
                                    if (provider == RemoteMusicProvider.SUBSONIC) {
                                        null
                                    } else {
                                        {
                                            pendingPlaylistSong = song
                                            showPlaylistPicker = true
                                        }
                                    },
                            )
                        }
                    }

                    if (!showsRemotePlaylistLanding) {
                        when {
                            songs.loadState.refresh is LoadState.Loading ||
                                songs.loadState.append is LoadState.Loading -> {
                                item(key = "loading", contentType = "paging") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            songs.loadState.refresh is LoadState.Error ||
                                songs.loadState.append is LoadState.Error -> {
                                item(key = "error", contentType = "paging") {
                                    val error =
                                        (songs.loadState.refresh as? LoadState.Error)?.error
                                            ?: (songs.loadState.append as? LoadState.Error)?.error
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            error?.message ?: "加载云端音乐失败",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        OutlinedButton(onClick = songs::retry) {
                                            Text("重试")
                                        }
                                    }
                                }
                            }
                            hasSubmittedSearch && songs.itemCount == 0 -> {
                                item(key = "search_empty", contentType = "empty") {
                                    Text(
                                        "没有找到相关歌曲",
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class NeteasePlaylistScene(
    private val value: CloudCatalogPlaylist,
) : StackScene() {
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val viewModel: CloudMusicCatalogViewModel = koinActivityViewModel()
        val homeViewModel: HomeViewModel = koinActivityViewModel()
        val playerViewModel = LocalPlayerViewModel.current
        var pendingPlayerMediaId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(pendingPlayerMediaId) {
            val expectedMediaId = pendingPlayerMediaId ?: return@LaunchedEffect
            playerViewModel.currentMediaItem.first { it?.mediaId == expectedMediaId }
            if (pendingPlayerMediaId == expectedMediaId) {
                pendingPlayerMediaId = null
                homeViewModel.expandPlayer()
                path.popTop()
            }
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val songs = state.playlistSongs[value.stableId].orEmpty()
        val loading = value.stableId in state.loadingPlaylists

        LaunchedEffect(value.stableId) {
            viewModel.loadPlaylist(value)
        }
        RemoteSceneScaffold(
            title = value.playlist.name,
            onBack = { path.popTop() },
            actions = {
                IconButton(onClick = { viewModel.loadPlaylist(value, forceRefresh = true) }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 10.dp,
                        bottom = 36.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "playlist_header", contentType = "header") {
                    NeteasePlaylistRow(value, onClick = null)
                }
                items(
                    count = songs.size,
                    key = { index -> songs[index].stableId },
                    contentType = { "netease_song" },
                ) { index ->
                    val song = songs[index]
                    CloudPlaylistSongRow(song) {
                        pendingPlayerMediaId = song.stableId
                        viewModel.playPlaylistSongs(song.stableId, songs)
                    }
                }
                if (loading) {
                    item(key = "loading", contentType = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (songs.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = "歌单中暂无可播放歌曲",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

class QqPlaylistScene(
    private val playlist: RemotePlaylist,
) : StackScene() {
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val homeViewModel: HomeViewModel = koinActivityViewModel()
        val playerViewModel = LocalPlayerViewModel.current
        var pendingPlayerMediaId by remember { mutableStateOf<String?>(null) }
        val viewModel: RemoteMusicViewModel =
            koinViewModel(key = "remote_music_${RemoteMusicProvider.QQ_MUSIC.name}") {
                parametersOf(RemoteMusicProvider.QQ_MUSIC)
            }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val songs = state.remotePlaylistSongs[playlist.id].orEmpty()
        val loading = playlist.id in state.loadingRemotePlaylistIds
        LaunchedEffect(pendingPlayerMediaId) {
            val expectedMediaId = pendingPlayerMediaId ?: return@LaunchedEffect
            playerViewModel.currentMediaItem.first { it?.mediaId == expectedMediaId }
            if (pendingPlayerMediaId == expectedMediaId) {
                pendingPlayerMediaId = null
                homeViewModel.expandPlayer()
                path.popTop()
            }
        }

        LaunchedEffect(playlist.id) {
            viewModel.loadRemotePlaylist(playlist.id)
        }
        RemoteSceneScaffold(
            title = playlist.name,
            onBack = { path.popTop() },
            actions = {
                IconButton(
                    onClick = { viewModel.loadRemotePlaylist(playlist.id, forceRefresh = true) },
                ) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 10.dp,
                        bottom = 36.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "playlist_header", contentType = "header") {
                    RemotePlaylistRow(playlist, onClick = null)
                }
                items(
                    count = songs.size,
                    key = { index -> songs[index].id },
                    contentType = { "qq_playlist_song" },
                ) { index ->
                    val song = songs[index]
                    RemoteSongRow(
                        song = song,
                        onClick = {
                            pendingPlayerMediaId =
                                state.selectedAccount?.let { account ->
                                    song.remoteMediaId(account.provider, account.id)
                                }
                            viewModel.play(song, songs)
                        },
                    )
                }
                if (loading) {
                    item(key = "loading", contentType = "loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (state.remotePlaylistError != null) {
                    item(key = "error", contentType = "error") {
                        Text(
                            state.remotePlaylistError.orEmpty(),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else if (songs.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            "歌单中暂无可播放歌曲",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

class CloudUserPlaylistScene(
    private val playlist: CloudUserPlaylist,
) : StackScene() {
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val homeViewModel: HomeViewModel = koinActivityViewModel()
        val playerViewModel = LocalPlayerViewModel.current
        var pendingPlayerMediaId by remember { mutableStateOf<String?>(null) }
        val viewModel: RemoteMusicViewModel =
            koinViewModel(key = "remote_music_${playlist.provider.name}") {
                parametersOf(playlist.provider)
            }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val currentPlaylist = state.localPlaylists.firstOrNull { it.id == playlist.id } ?: playlist
        var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(pendingPlayerMediaId) {
            val expectedMediaId = pendingPlayerMediaId ?: return@LaunchedEffect
            playerViewModel.currentMediaItem.first { it?.mediaId == expectedMediaId }
            if (pendingPlayerMediaId == expectedMediaId) {
                pendingPlayerMediaId = null
                homeViewModel.expandPlayer()
                path.popTop()
            }
        }
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除歌单？") },
                text = { Text("将删除“${currentPlaylist.name}”。歌曲本身不会被删除。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteLocalPlaylist(currentPlaylist.accountId, currentPlaylist.id)
                            path.popTop()
                        },
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                },
            )
        }
        RemoteSceneScaffold(
            title = currentPlaylist.name,
            onBack = { path.popTop() },
            actions = {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.DeleteOutline, "删除歌单")
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 10.dp,
                        bottom = 36.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "playlist_header", contentType = "header") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AudioCover(
                            uri =
                                currentPlaylist.songs
                                    .firstOrNull()
                                    ?.artworkUrl
                                    ?.let(Uri::parse),
                            modifier = Modifier.size(196.dp),
                            placeHolder = {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        RoundedCornerShape(28.dp),
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Default.PlaylistPlay, null, modifier = Modifier.size(64.dp)) }
                            },
                        )
                        Text(currentPlaylist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "本地歌单 · ${currentPlaylist.songs.size} 首 · 不同步到云端",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = {
                                currentPlaylist.songs.firstOrNull()?.let { first ->
                                    pendingPlayerMediaId =
                                        first.remoteMediaId(
                                            currentPlaylist.provider,
                                            currentPlaylist.accountId,
                                        )
                                    viewModel.play(first, currentPlaylist.songs)
                                }
                            },
                            enabled = currentPlaylist.songs.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PlaylistPlay, null)
                            Text("播放全部")
                        }
                    }
                }
                items(
                    count = currentPlaylist.songs.size,
                    key = { index -> currentPlaylist.songs[index].id },
                    contentType = { "local_playlist_song" },
                ) { index ->
                    val song = currentPlaylist.songs[index]
                    RemoteSongRow(
                        song = song,
                        onClick = {
                            pendingPlayerMediaId =
                                song.remoteMediaId(
                                    currentPlaylist.provider,
                                    currentPlaylist.accountId,
                                )
                            viewModel.play(song, currentPlaylist.songs)
                        },
                        onRemoveFromPlaylist = {
                            viewModel.removeSongFromLocalPlaylist(
                                currentPlaylist.accountId,
                                currentPlaylist.id,
                                song.id,
                            )
                        },
                    )
                }
                if (currentPlaylist.songs.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            "歌单还是空的，请从搜索结果右侧的 + 添加歌曲。",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeteasePlaylistRow(
    value: CloudCatalogPlaylist,
    onClick: (() -> Unit)?,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = Modifier.fillMaxWidth().then(clickableModifier),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AudioCover(
                uri = value.playlist.coverUrl?.let(Uri::parse),
                modifier = Modifier.size(64.dp),
                placeHolder = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlaylistPlay, null)
                    }
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    value.playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${value.playlist.songCount} 首 · ${value.account.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RemotePlaylistRow(
    playlist: RemotePlaylist,
    onClick: (() -> Unit)?,
) {
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = Modifier.fillMaxWidth().then(clickableModifier),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AudioCover(
                uri = playlist.coverUrl?.let(Uri::parse),
                modifier = Modifier.size(64.dp),
                placeHolder = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlaylistPlay, null)
                    }
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.songCount} 首 · QQ 音乐",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CloudUserPlaylistRow(
    playlist: CloudUserPlaylist,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AudioCover(
                uri =
                    playlist.songs
                        .firstOrNull()
                        ?.artworkUrl
                        ?.let(Uri::parse),
                modifier = Modifier.size(64.dp),
                placeHolder = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlaylistPlay, null)
                    }
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.songs.size} 首 · 仅保存在本机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CloudPlaylistSongRow(
    song: CloudCatalogSong,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AudioCover(
            uri = song.artworkUri,
            modifier = Modifier.size(48.dp),
            placeHolder = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null)
                }
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatRemoteDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubsonicLogin(
    state: RemoteMusicUiState,
    hasExistingAccount: Boolean,
    modifier: Modifier,
    onCancel: () -> Unit,
    onLogin: (String, String, String) -> Unit,
) {
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LoginIntro(
                title = "连接 Subsonic",
                body = "兼容 Navidrome、Airsonic、Gonic 及其他 Subsonic/OpenSubsonic 服务器。",
            )
        }
        state.error?.let { message ->
            item { LoginError(message) }
        }
        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("服务器地址") },
                placeholder = { Text("https://music.example.com") },
                keyboardOptions = KeyboardOptions.Default,
            )
        }
        item {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("用户名") },
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasExistingAccount) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !state.isConnecting,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        Text("取消")
                    }
                }
                Button(
                    onClick = { onLogin(serverUrl, username, password) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    enabled =
                        !state.isConnecting &&
                            serverUrl.isNotBlank() &&
                            username.isNotBlank() &&
                            password.isNotBlank(),
                ) {
                    if (state.isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("连接")
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CookieWebLogin(
    provider: RemoteMusicProvider,
    state: RemoteMusicUiState,
    hasExistingAccount: Boolean,
    modifier: Modifier,
    onCancel: () -> Unit,
    onCookiesCaptured: (String) -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LoginIntro(
            title = "登录 ${provider.displayName}",
            body = "请在下方官方网页完成登录。应用只读取登录 Cookie，不会读取或保存你的密码。",
        )
        state.error?.let { LoginError(it) }
        AndroidView(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)),
            factory = { context ->
                WebView(context).apply {
                    val loginWebView = this
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.userAgentString =
                        if (provider == RemoteMusicProvider.QQ_MUSIC) {
                            DESKTOP_USER_AGENT
                        } else {
                            settings.userAgentString
                        }
                    settings.useWideViewPort = provider == RemoteMusicProvider.QQ_MUSIC
                    settings.loadWithOverviewMode = provider == RemoteMusicProvider.QQ_MUSIC
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(loginWebView, true)
                    }
                    loadUrl(provider.loginUrl)
                }
            },
            onRelease = WebView::releaseAfterLayout,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            if (hasExistingAccount) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.isConnecting,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text("取消")
                }
            }
            Button(
                enabled = !state.isConnecting,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                onClick = {
                    CookieManager.getInstance().flush()
                    val cookies = collectCookies(provider.cookieUrls)
                    onCookiesCaptured(cookies)
                },
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("我已完成登录")
                }
            }
        }
    }
}

/**
 * AndroidView can leave composition while Compose is still completing a layout pass.
 * WebView.destroy() may request another layout synchronously, so release it on the next
 * main-loop turn instead of re-entering Compose's active measure.
 */
private fun WebView.releaseAfterLayout() {
    Handler(Looper.getMainLooper()).post {
        stopLoading()
        webChromeClient = null
        webViewClient = WebViewClient()
        removeAllViews()
        destroy()
    }
}

@Composable
private fun LoginIntro(
    title: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoginError(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun CloudPlaylistNameDialog(
    provider: RemoteMusicProvider,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建本地歌单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "当前没有稳定的${provider.displayName}歌单写入接口，新歌单只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("歌单名称") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun CloudPlaylistPickerDialog(
    cloudPlaylists: List<CloudUserPlaylist>,
    homePlaylists: List<Playlist>,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectHome: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cloudPlaylists.isEmpty() && homePlaylists.isEmpty()) {
                    Text(
                        "还没有本地歌单，请先创建一个。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    homePlaylists.take(8).forEach { playlist ->
                        playlist.playlistId?.let { playlistId ->
                            TextButton(
                                onClick = { onSelectHome(playlistId) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(playlist.playlistName, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "首页/资料库歌单 · 可添加所有音乐源",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    cloudPlaylists.take(8).forEach { playlist ->
                        TextButton(
                            onClick = { onSelect(playlist.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(playlist.name, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${playlist.songs.size} 首 · 仅保存在本机",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Text("新建歌单")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RemoteSongRow(
    song: RemoteSong,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AudioCover(
            uri = song.artworkUrl?.let(Uri::parse),
            modifier = Modifier.size(46.dp),
            placeHolder = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(13.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                }
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatRemoteDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        onAddToPlaylist?.let { add ->
            IconButton(onClick = add) {
                Icon(Icons.Default.Add, "添加到歌单")
            }
        }
        onRemoveFromPlaylist?.let { remove ->
            IconButton(onClick = remove) {
                Icon(Icons.Default.DeleteOutline, "从歌单移除")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteSceneScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = actions,
            )
        },
        content = content,
    )
}

private val RemoteMusicProvider.displayName: String
    get() =
        when (this) {
            RemoteMusicProvider.SUBSONIC -> "Subsonic"
            RemoteMusicProvider.NETEASE -> "网易云音乐"
            RemoteMusicProvider.QQ_MUSIC -> "QQ 音乐"
        }

private val RemoteMusicProvider.loginUrl: String
    get() =
        when (this) {
            RemoteMusicProvider.NETEASE -> "https://music.163.com/m/login"
            RemoteMusicProvider.QQ_MUSIC -> "https://y.qq.com/"
            RemoteMusicProvider.SUBSONIC -> error("Subsonic does not use web login")
        }

private val RemoteMusicProvider.cookieUrls: List<String>
    get() =
        when (this) {
            RemoteMusicProvider.NETEASE ->
                listOf(
                    "https://music.163.com/",
                    "https://interface.music.163.com/",
                )
            RemoteMusicProvider.QQ_MUSIC ->
                listOf(
                    "https://y.qq.com/",
                    "https://u.y.qq.com/",
                    "https://u6.y.qq.com/",
                    "https://c.y.qq.com/",
                )
            RemoteMusicProvider.SUBSONIC -> emptyList()
        }

private fun RemoteMusicProvider.accountSubtitle(account: RemoteMusicAccount): String =
    when (this) {
        RemoteMusicProvider.SUBSONIC -> account.normalizedServerUrl
        RemoteMusicProvider.NETEASE,
        RemoteMusicProvider.QQ_MUSIC,
        -> "网页登录会话已加密保存在本机"
    }

private fun collectCookies(urls: List<String>): String {
    val values = LinkedHashMap<String, String>()
    val manager = CookieManager.getInstance()
    urls.forEach { url ->
        manager
            .getCookie(url)
            .orEmpty()
            .split(';')
            .map(String::trim)
            .filter { '=' in it }
            .forEach { entry ->
                val name = entry.substringBefore('=').trim()
                val value = entry.substringAfter('=', "")
                if (name.isNotBlank()) values[name] = value
            }
    }
    return values.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}

private fun formatRemoteDuration(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"
