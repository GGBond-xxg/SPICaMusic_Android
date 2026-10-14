@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.spica27.navkit.geometry.GeometryTransition
import me.spica27.navkit.geometry.geometryTarget
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.common.entity.Playlist
import me.spica27.spicamusic.ui.dialog.CloudSongMenuScene
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.playlist.PlaylistViewModel
import me.spica27.spicamusic.ui.playlistdetail.PlaylistCloudSongRow
import me.spica27.spicamusic.ui.theme.EqualHeightActionRow
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.appPageBackground
import me.spica27.spicamusic.ui.theme.glassSurface
import me.spica27.spicamusic.ui.widget.AudioCover
import me.spica27.spicamusic.ui.widget.StableAudioCover
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import me.spica27.spicamusic.ui.theme.GlassAlertDialog as AlertDialog
import me.spica27.spicamusic.ui.theme.GlassButton as Button
import me.spica27.spicamusic.ui.theme.GlassDropdownMenu as DropdownMenu
import me.spica27.spicamusic.ui.theme.GlassFilledTonalButton as FilledTonalButton
import me.spica27.spicamusic.ui.theme.GlassIconButton as IconButton
import me.spica27.spicamusic.ui.theme.GlassOutlinedButton as OutlinedButton
import me.spica27.spicamusic.ui.theme.GlassOutlinedTextField as OutlinedTextField
import me.spica27.spicamusic.ui.theme.GlassScaffold as Scaffold
import me.spica27.spicamusic.ui.theme.GlassSurface as Surface
import me.spica27.spicamusic.ui.theme.GlassTextButton as TextButton
import me.spica27.spicamusic.ui.theme.GlassTopAppBar as TopAppBar

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
                (provider in setOf(RemoteMusicProvider.NETEASE, RemoteMusicProvider.QQ_MUSIC, RemoteMusicProvider.KUGOU))

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

        LaunchedEffect(state.accounts, state.isConnecting) {
            if (state.accounts.isEmpty()) showLogin = true
            if (state.accounts.isNotEmpty() && !state.isConnecting && state.error == null) {
                showLogin = false
            }
        }
        LaunchedEffect(provider, state.selectedAccount) {
            if (provider in setOf(RemoteMusicProvider.QQ_MUSIC, RemoteMusicProvider.BILIBILI, RemoteMusicProvider.KUGOU) &&
                state.selectedAccount != null
            ) {
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
                                RemoteMusicProvider.BILIBILI -> {
                                    viewModel.refreshRemotePlaylists(forceRefresh = true)
                                    songs.refresh()
                                }
                                RemoteMusicProvider.KUGOU, RemoteMusicProvider.QQ_MUSIC ->
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
                    RemoteMusicProvider.KUGOU ->
                        KugouLogin(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            onLogin = viewModel::loginKugou,
                            onCancel = if (state.accounts.isNotEmpty()) ({ showLogin = false }) else null,
                        )
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
                    RemoteMusicProvider.BILIBILI,
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
                            onBrowse = if (provider == RemoteMusicProvider.BILIBILI) viewModel::browseBilibili else null,
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
                                            Icon(
                                                Icons.Default.PlaylistPlay,
                                                stringResource(R.string.cloud_create_local_playlist),
                                            )
                                        }
                                    }
                                    IconButton(onClick = { showLogin = true }) {
                                        Icon(Icons.Default.Add, "Add account")
                                    }
                                }
                            }
                            if (state.accounts.size > 1) {
                                FlowRow(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.accounts.take(3).forEach { account ->
                                        if (account.id == state.selectedAccount?.id) {
                                            FilledTonalButton(
                                                onClick = { viewModel.selectAccount(account.id) },
                                                modifier = Modifier.widthIn(max = 240.dp).fillMaxRowHeight(),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            ) {
                                                Text(account.displayName)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { viewModel.selectAccount(account.id) },
                                                modifier = Modifier.widthIn(max = 240.dp).fillMaxRowHeight(),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            ) {
                                                Text(account.displayName)
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
                                    placeholder = {
                                        Text(
                                            if (provider ==
                                                RemoteMusicProvider.BILIBILI
                                            ) {
                                                stringResource(R.string.bilibili_search_hint)
                                            } else {
                                                stringResource(R.string.cloud_search_provider, provider.displayName)
                                            },
                                        )
                                    },
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
                                    modifier = Modifier.heightIn(min = 58.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                ) {
                                    Text(stringResource(R.string.cloud_search))
                                }
                            }
                        }
                    }

                    if (provider == RemoteMusicProvider.KUGOU && state.selectedAccount != null) {
                        item(key = "kugou_rewards", contentType = "rewards") {
                            KugouRewardsCard(state.selectedAccount!!)
                        }
                    }
                    if (provider != RemoteMusicProvider.SUBSONIC && state.localPlaylists.isNotEmpty()) {
                        item(key = "local_playlist_title", contentType = "section_title") {
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
                                Text(stringResource(R.string.cloud_local_playlists), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.cloud_local_only_provider, provider.displayName),
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
                                            Text(stringResource(R.string.cloud_retry))
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
                                NeteasePlaylistRow(
                                    value = playlist,
                                    onClick = { path.push(NeteasePlaylistScene(playlist)) },
                                )
                            }
                        }
                    }

                    if (provider in setOf(RemoteMusicProvider.QQ_MUSIC, RemoteMusicProvider.BILIBILI, RemoteMusicProvider.KUGOU) &&
                        !hasSubmittedSearch
                    ) {
                        item(key = "qq_collection_title", contentType = "section_title") {
                            Text(
                                if (provider ==
                                    RemoteMusicProvider.BILIBILI
                                ) {
                                    stringResource(R.string.bilibili_favorites)
                                } else {
                                    stringResource(R.string.cloud_provider_playlists, provider.displayName)
                                },
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
                                            Text(stringResource(R.string.cloud_retry))
                                        }
                                    }
                                }
                            }
                            state.remotePlaylists.isEmpty() -> {
                                item(key = "qq_playlists_empty", contentType = "empty") {
                                    Text(
                                        if (provider ==
                                            RemoteMusicProvider.BILIBILI
                                        ) {
                                            stringResource(R.string.bilibili_guest_help)
                                        } else {
                                            stringResource(R.string.cloud_no_playlists, provider.displayName)
                                        },
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
                                    RemotePlaylistRow(playlist, provider = provider) {
                                        path.push(QqPlaylistScene(playlist, provider))
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
                                            error?.message ?: stringResource(R.string.cloud_load_error),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        OutlinedButton(onClick = songs::retry) {
                                            Text(stringResource(R.string.cloud_retry))
                                        }
                                    }
                                }
                            }
                            hasSubmittedSearch && songs.itemCount == 0 -> {
                                item(key = "search_empty", contentType = "empty") {
                                    Text(
                                        stringResource(R.string.cloud_no_songs),
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
    private val transition: GeometryTransition? = null,
    private val transitionCoverPainter: Painter? = null,
) : StackScene() {
    override val geometryTransition: GeometryTransition? = transition
    override val transitionShadowEnabled: Boolean = transition == null
    override val transitionScaleEnabled: Boolean = transition == null
    override val transitionSlideEnabled: Boolean = transition == null
    override val transitionFadeEnabled: Boolean = transition != null
    override val compressesPreviousScene: Boolean = transition == null

    @Composable
    override fun FloatingContent() {
        if (transitionCoverPainter != null) {
            Image(
                painter = transitionCoverPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AudioCover(
                uri = value.playlist.coverUrl?.let(Uri::parse),
                modifier = Modifier.fillMaxSize(),
                placeHolder = {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                },
            )
        }
    }

    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val viewModel: CloudMusicCatalogViewModel = koinActivityViewModel()
        val playlistListState = rememberLazyListState()
        val playerViewModel = LocalPlayerViewModel.current
        val currentMediaItem by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
        var pendingPlayerMediaId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(pendingPlayerMediaId) {
            val expectedMediaId = pendingPlayerMediaId ?: return@LaunchedEffect
            withTimeoutOrNull(8_000L) {
                playerViewModel.currentMediaItem.first { it?.mediaId == expectedMediaId }
            }
            if (pendingPlayerMediaId == expectedMediaId) {
                pendingPlayerMediaId = null
            }
        }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val songs = state.playlistSongs[value.stableId].orEmpty()
        val loading = value.stableId in state.loadingPlaylists

        LaunchedEffect(value.stableId) {
            viewModel.loadPlaylist(value)
        }
        RemoteSceneScaffold(
            title = if (playlistListState.firstVisibleItemIndex > 0) value.playlist.name else "",
            playlistAppearance = true,
            headerVisible = playlistListState.firstVisibleItemIndex == 0,
            onBack = { path.popTop() },
            actions = {
                IconButton(onClick = { viewModel.loadPlaylist(value, forceRefresh = true) }) {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            },
        ) { padding ->
            LazyColumn(
                state = playlistListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = padding.calculateTopPadding(),
                        bottom = 200.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "playlist_header", contentType = "header") {
                    OnlinePlaylistHeader(
                        name = value.playlist.name,
                        songCount = songs.size.takeIf { it > 0 } ?: value.playlist.songCount,
                        coverUri = value.playlist.coverUrl?.let(Uri::parse),
                        transition = transition,
                        retainedPainter = transitionCoverPainter,
                        playEnabled = songs.isNotEmpty(),
                        onPlayAll = {
                            songs.firstOrNull()?.let { first ->
                                pendingPlayerMediaId = first.stableId
                                viewModel.playPlaylistSongs(first.stableId, songs)
                            }
                        },
                    )
                }
                items(
                    count = songs.size,
                    key = { index -> songs[index].stableId },
                    contentType = { "netease_song" },
                ) { index ->
                    val song = songs[index]
                    PlaylistCloudSongRow(
                        song = song,
                        isPlaying = currentMediaItem?.mediaId == song.stableId,
                        isPending = pendingPlayerMediaId == song.stableId,
                        isMultiSelectMode = false,
                        isSelected = false,
                        onClick = {
                            pendingPlayerMediaId = song.stableId
                            viewModel.playPlaylistSongs(song.stableId, songs)
                        },
                        onLongClick = { path.push(CloudSongMenuScene(song)) },
                        onMore = { path.push(CloudSongMenuScene(song)) },
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
                } else if (songs.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(
                            text = stringResource(R.string.cloud_empty_playlist),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    override suspend fun onPush() {
        super.onPush()
        geometryTransition?.reset()
    }

    override suspend fun onAppear() {
        coroutineScope {
            launch { super.onAppear() }
            launch { geometryTransition?.animateForwardWhenTargetReady() }
        }
    }

    override suspend fun onDisappear() {
        coroutineScope {
            launch { super.onDisappear() }
            launch { geometryTransition?.animateReverse() }
        }
    }
}

class QqPlaylistScene(
    private val playlist: RemotePlaylist,
    private val provider: RemoteMusicProvider = RemoteMusicProvider.QQ_MUSIC,
) : StackScene() {
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val playlistListState = rememberLazyListState()
        val playerViewModel = LocalPlayerViewModel.current
        val currentMediaItem by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
        var pendingPlayerMediaId by remember { mutableStateOf<String?>(null) }
        val viewModel: RemoteMusicViewModel =
            koinViewModel(key = "remote_music_${provider.name}") {
                parametersOf(provider)
            }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val songs = state.remotePlaylistSongs[playlist.id].orEmpty()
        val loading = playlist.id in state.loadingRemotePlaylistIds
        LaunchedEffect(pendingPlayerMediaId) {
            val expectedMediaId = pendingPlayerMediaId ?: return@LaunchedEffect
            withTimeoutOrNull(8_000L) {
                playerViewModel.currentMediaItem.first { it?.mediaId == expectedMediaId }
            }
            if (pendingPlayerMediaId == expectedMediaId) {
                pendingPlayerMediaId = null
            }
        }

        LaunchedEffect(playlist.id) {
            viewModel.loadRemotePlaylist(playlist.id)
        }
        RemoteSceneScaffold(
            title = if (playlistListState.firstVisibleItemIndex > 0) playlist.name else "",
            playlistAppearance = true,
            headerVisible = playlistListState.firstVisibleItemIndex == 0,
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
                state = playlistListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = padding.calculateTopPadding(),
                        bottom = 200.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "playlist_header", contentType = "header") {
                    OnlinePlaylistHeader(
                        name = playlist.name,
                        songCount = songs.size.takeIf { it > 0 } ?: playlist.songCount,
                        coverUri = playlist.coverUrl?.let(Uri::parse),
                        playEnabled = songs.isNotEmpty(),
                        onPlayAll = {
                            songs.firstOrNull()?.let { first ->
                                pendingPlayerMediaId = state.selectedAccount?.let { first.remoteMediaId(it.provider, it.id) }
                                viewModel.play(first, songs)
                            }
                        },
                    )
                }
                items(
                    count = songs.size,
                    key = { index -> songs[index].id },
                    contentType = { "qq_playlist_song" },
                ) { index ->
                    val song = songs[index]
                    RemoteSongRow(
                        song = song,
                        isPlaying =
                            state.selectedAccount?.let { currentMediaItem?.mediaId == song.remoteMediaId(it.provider, it.id) } == true,
                        isPending = state.selectedAccount?.let { pendingPlayerMediaId == song.remoteMediaId(it.provider, it.id) } == true,
                        playlistStyle = true,
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
                            stringResource(R.string.cloud_empty_playlist),
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
    private val transition: GeometryTransition? = null,
    private val transitionCoverPainter: Painter? = null,
) : StackScene() {
    private var skipGeometryOnExit = false
    override val geometryTransition: GeometryTransition? = transition
    override val transitionShadowEnabled: Boolean = transition == null
    override val transitionScaleEnabled: Boolean = transition == null
    override val transitionSlideEnabled: Boolean = transition == null
    override val transitionFadeEnabled: Boolean = transition != null
    override val compressesPreviousScene: Boolean = transition == null

    @Composable
    override fun FloatingContent() {
        if (transitionCoverPainter != null) {
            Image(
                painter = transitionCoverPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AudioCover(
                uri =
                    playlist.songs
                        .firstOrNull()
                        ?.artworkUrl
                        ?.let(Uri::parse),
                modifier = Modifier.fillMaxSize(),
                placeHolder = {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                },
            )
        }
    }

    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val playlistListState = rememberLazyListState()
        val playerViewModel = LocalPlayerViewModel.current
        val currentMediaItem by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
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
            withTimeoutOrNull(8_000L) {
                playerViewModel.currentMediaItem.first { it?.mediaId == expectedMediaId }
            }
            if (pendingPlayerMediaId == expectedMediaId) {
                pendingPlayerMediaId = null
            }
        }
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.cloud_delete_playlist_question)) },
                text = { Text(stringResource(R.string.cloud_delete_playlist_help, currentPlaylist.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            // The source card disappears as soon as the store publishes deletion,
                            // so there is no valid shared-element destination to fly back to.
                            skipGeometryOnExit = true
                            viewModel.deleteLocalPlaylist(currentPlaylist.accountId, currentPlaylist.id)
                            path.popTop()
                        },
                    ) { Text(stringResource(R.string.cloud_delete)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = false },
                    ) { Text(stringResource(R.string.cloud_action_cancel)) }
                },
            )
        }
        RemoteSceneScaffold(
            title = if (playlistListState.firstVisibleItemIndex > 0) currentPlaylist.name else "",
            playlistAppearance = true,
            headerVisible = playlistListState.firstVisibleItemIndex == 0,
            onBack = { path.popTop() },
            actions = {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.cloud_delete_playlist))
                }
            },
        ) { padding ->
            LazyColumn(
                state = playlistListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 0.dp,
                        end = 0.dp,
                        top = padding.calculateTopPadding(),
                        bottom = 200.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "playlist_header", contentType = "header") {
                    OnlinePlaylistHeader(
                        name = currentPlaylist.name,
                        songCount = currentPlaylist.songs.size,
                        coverUri =
                            currentPlaylist.songs
                                .firstOrNull()
                                ?.artworkUrl
                                ?.let(Uri::parse),
                        transition = transition,
                        retainedPainter = transitionCoverPainter,
                        playEnabled = currentPlaylist.songs.isNotEmpty(),
                        onPlayAll = {
                            currentPlaylist.songs.firstOrNull()?.let { first ->
                                pendingPlayerMediaId = first.remoteMediaId(currentPlaylist.provider, currentPlaylist.accountId)
                                viewModel.play(first, currentPlaylist.songs)
                            }
                        },
                    )
                }
                items(
                    count = currentPlaylist.songs.size,
                    key = { index -> currentPlaylist.songs[index].id },
                    contentType = { "local_playlist_song" },
                ) { index ->
                    val song = currentPlaylist.songs[index]
                    RemoteSongRow(
                        song = song,
                        isPlaying = currentMediaItem?.mediaId == song.remoteMediaId(currentPlaylist.provider, currentPlaylist.accountId),
                        isPending = pendingPlayerMediaId == song.remoteMediaId(currentPlaylist.provider, currentPlaylist.accountId),
                        playlistStyle = true,
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
                            stringResource(R.string.cloud_empty_playlist_help),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    override suspend fun onPush() {
        super.onPush()
        geometryTransition?.reset()
    }

    override suspend fun onAppear() {
        coroutineScope {
            launch { super.onAppear() }
            launch { geometryTransition?.animateForwardWhenTargetReady() }
        }
    }

    override suspend fun onDisappear() {
        coroutineScope {
            launch { super.onDisappear() }
            if (!skipGeometryOnExit) {
                launch { geometryTransition?.animateReverse() }
            }
        }
    }
}

@Composable
private fun OnlinePlaylistHeader(
    name: String,
    songCount: Int,
    coverUri: Uri?,
    playEnabled: Boolean,
    onPlayAll: () -> Unit,
    transition: GeometryTransition? = null,
    retainedPainter: Painter? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StableAudioCover(
            uri = coverUri,
            retainedPainter = retainedPainter,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(180.dp)
                    .clip(Shapes.LargeCornerBasedShape)
                    .then(if (transition != null) Modifier.geometryTarget(transition) else Modifier)
                    .graphicsLayer { alpha = if (transition?.shouldShowTarget() != false) 1f else 0f },
            placeHolder = {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(48.dp))
                }
            },
        )
        Column {
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.songs_count, songCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onPlayAll, enabled = playEnabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Text(stringResource(R.string.play_all_songs))
        }
    }
}

@Composable
private fun NeteasePlaylistRow(
    value: CloudCatalogPlaylist,
    onClick: (() -> Unit)?,
    geometryTransition: GeometryTransition? = null,
    retainedPainter: Painter? = null,
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
            StableAudioCover(
                uri = value.playlist.coverUrl?.let(Uri::parse),
                retainedPainter = retainedPainter,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (geometryTransition != null) {
                                Modifier.geometryTarget(geometryTransition)
                            } else {
                                Modifier
                            },
                        ).graphicsLayer {
                            alpha = if (geometryTransition?.shouldShowTarget() != false) 1f else 0f
                        },
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
                    stringResource(
                        R.string.cloud_song_count_provider,
                        value.playlist.songCount,
                        value.account.displayName,
                    ),
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
    provider: RemoteMusicProvider = RemoteMusicProvider.QQ_MUSIC,
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
                    stringResource(R.string.cloud_song_count_provider, playlist.songCount, provider.displayName),
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
                    stringResource(R.string.cloud_song_count_local, playlist.songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            EqualHeightActionRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasExistingAccount) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !state.isConnecting,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                    ) {
                        Text(stringResource(R.string.cloud_action_cancel))
                    }
                }
                Button(
                    onClick = { onLogin(serverUrl, username, password) },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
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
    onBrowse: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LoginIntro(
            title = stringResource(R.string.cloud_web_login_title, provider.displayName),
            body = stringResource(R.string.cloud_web_login_help),
        )
        state.error?.let { LoginError(it) }
        onBrowse?.let { browse ->
            OutlinedButton(onClick = browse, enabled = !state.isConnecting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bilibili_guest_action))
            }
        }
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
        EqualHeightActionRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            if (hasExistingAccount) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !state.isConnecting,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.cloud_action_cancel))
                }
            }
            Button(
                enabled = !state.isConnecting,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                onClick = {
                    CookieManager.getInstance().flush()
                    val cookies = collectCookies(provider.cookieUrls)
                    onCookiesCaptured(cookies)
                },
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.cloud_login_done))
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
        title = { Text(stringResource(R.string.cloud_create_local_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.cloud_create_local_help, provider.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.cloud_playlist_name)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.cloud_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_action_cancel)) }
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
        title = { Text(stringResource(R.string.cloud_add_to_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cloudPlaylists.isEmpty() && homePlaylists.isEmpty()) {
                    Text(
                        stringResource(R.string.cloud_no_local_playlists),
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
                                        stringResource(R.string.cloud_local_playlist_hint),
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
                                    stringResource(R.string.cloud_song_count_local, playlist.songs.size),
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
                Text(stringResource(R.string.cloud_new_playlist))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_action_cancel)) }
        },
    )
}

@Composable
private fun RemoteSongRow(
    song: RemoteSong,
    onClick: () -> Unit,
    isPlaying: Boolean = false,
    isPending: Boolean = false,
    playlistStyle: Boolean = false,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (playlistStyle) 12.dp else 0.dp)
                .clip(Shapes.MediumCornerBasedShape)
                .glassSurface(
                    Shapes.MediumCornerBasedShape,
                    if (isPlaying || isPending) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    tint =
                        if (isPlaying || isPending) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    opacity = 0.7f,
                ).clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AudioCover(
            uri = song.artworkUrl?.let(Uri::parse),
            modifier = Modifier.size(48.dp).clip(Shapes.SmallCornerBasedShape),
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
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (playlistStyle) song.artist else "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPending) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else if (playlistStyle) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(song.title) }, onClick = {}, enabled = false)
                    DropdownMenuItem(text = {
                        Text("${song.artist} · ${song.album} · ${formatRemoteDuration(song.durationMs)}")
                    }, onClick = {}, enabled = false)
                    onRemoveFromPlaylist?.let { remove ->
                        DropdownMenuItem(text = {
                            Text(
                                stringResource(R.string.cloud_remove_from_playlist),
                            )
                        }, onClick = {
                            showMenu = false
                            remove()
                        })
                    }
                }
            }
        } else {
            Text(
                formatRemoteDuration(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onAddToPlaylist?.let { add ->
                IconButton(onClick = add) {
                    Icon(Icons.Default.Add, stringResource(R.string.cloud_add_to_playlist))
                }
            }
            onRemoveFromPlaylist?.let { remove ->
                IconButton(onClick = remove) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.cloud_remove_from_playlist))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteSceneScaffold(
    title: String,
    onBack: () -> Unit,
    playlistAppearance: Boolean = false,
    headerVisible: Boolean = false,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(Modifier.fillMaxSize().appPageBackground()) {
        if (playlistAppearance && headerVisible) {
            Box(
                Modifier.fillMaxWidth().height(400.dp).background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    ),
                ),
            )
        }
        Scaffold(
            containerColor = if (playlistAppearance) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = if (playlistAppearance) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
                        ),
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
}

internal val RemoteMusicProvider.displayName: String
    get() =
        when (this) {
            RemoteMusicProvider.SUBSONIC -> "Subsonic"
            RemoteMusicProvider.NETEASE -> "网易云音乐"
            RemoteMusicProvider.QQ_MUSIC -> "QQ 音乐"
            RemoteMusicProvider.BILIBILI -> "Bilibili"
            RemoteMusicProvider.KUGOU -> cloudText(R.string.kugou_provider_name)
        }

private val RemoteMusicProvider.loginUrl: String
    get() =
        when (this) {
            RemoteMusicProvider.NETEASE -> "https://music.163.com/m/login"
            RemoteMusicProvider.QQ_MUSIC -> "https://y.qq.com/"
            RemoteMusicProvider.BILIBILI -> "https://passport.bilibili.com/login"
            RemoteMusicProvider.SUBSONIC, RemoteMusicProvider.KUGOU -> error("Provider does not use web login")
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
            RemoteMusicProvider.BILIBILI ->
                listOf(
                    "https://www.bilibili.com/",
                    "https://passport.bilibili.com/",
                    "https://api.bilibili.com/",
                )
            RemoteMusicProvider.SUBSONIC, RemoteMusicProvider.KUGOU -> emptyList()
        }

private fun RemoteMusicProvider.accountSubtitle(account: RemoteMusicAccount): String =
    when (this) {
        RemoteMusicProvider.SUBSONIC -> account.normalizedServerUrl
        RemoteMusicProvider.KUGOU -> cloudText(R.string.cloud_credentials_saved)
        RemoteMusicProvider.NETEASE,
        RemoteMusicProvider.QQ_MUSIC,
        -> cloudText(R.string.cloud_session_saved)
        RemoteMusicProvider.BILIBILI ->
            if (account.secret.isBlank()) {
                cloudText(
                    R.string.bilibili_guest_mode,
                )
            } else {
                cloudText(R.string.cloud_session_saved)
            }
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
