package me.spica27.spicamusic.ui.home.page

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.CloudLibraryScene
import me.spica27.spicamusic.cloud.CloudMusicCatalogViewModel
import me.spica27.spicamusic.cloud.CloudUserPlaylistScene
import me.spica27.spicamusic.cloud.NeteasePlaylistScene
import me.spica27.spicamusic.cloud.RemoteMusicProvider
import me.spica27.spicamusic.cloud.displayName
import me.spica27.spicamusic.common.entity.PlayStats
import me.spica27.spicamusic.feature.library.domain.ScanFolder
import me.spica27.spicamusic.ui.home.LocalBottomBarScrollConnection
import me.spica27.spicamusic.ui.ignoredsongs.IgnoredSongsScene
import me.spica27.spicamusic.ui.library.LibraryPageViewModel
import me.spica27.spicamusic.ui.model.PlaylistWithCover
import me.spica27.spicamusic.ui.playlist.PlaylistCreatorScene
import me.spica27.spicamusic.ui.playlistdetail.PlaylistDetailScene
import me.spica27.spicamusic.ui.scan.ScannerScene
import me.spica27.spicamusic.ui.settings.MediaLibrarySourceViewModel
import me.spica27.spicamusic.ui.theme.CollapsingBarBackground
import me.spica27.spicamusic.ui.theme.LayoutTokens
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.theme.appPageBackground
import me.spica27.spicamusic.ui.theme.glassSurface
import me.spica27.spicamusic.ui.widget.AudioCover
import me.spica27.spicamusic.ui.widget.PlaylistCoverView
import me.spica27.spicamusic.ui.widget.PreloadPlaylistCovers
import me.spica27.spicamusic.ui.widget.clickHighlight
import me.spica27.spicamusic.ui.widget.materialSharedAxisZ
import me.spica27.spicamusic.ui.widget.rememberIOSOverScrollEffect
import org.koin.compose.viewmodel.koinActivityViewModel
import java.util.concurrent.TimeUnit
import me.spica27.spicamusic.ui.theme.GlassIconButton as IconButton

/**
 * 资料库页面
 */

/** 大标题收缩归一化距离的上限（实际取刊头实测滚出高度，见 mastheadCollapse） */
private val MastheadCollapseDistance = 140.dp

@Composable
fun LibraryPage() {
    val path = LocalNavigationPath.current
    val context = LocalContext.current
    val viewModel: LibraryPageViewModel = koinActivityViewModel()
    val sourceViewModel: MediaLibrarySourceViewModel = koinActivityViewModel()
    val cloudCatalogViewModel: CloudMusicCatalogViewModel = koinActivityViewModel()

    val playlists by viewModel.playlistsWithCover.collectAsStateWithLifecycle()
    PreloadPlaylistCovers(playlists)
    val cloudCatalog by cloudCatalogViewModel.state.collectAsStateWithLifecycle()
    val remotePlaylists = cloudCatalog.remotePlaylists
    val neteasePlaylists =
        remember(remotePlaylists) {
            remotePlaylists.filter { it.account.provider == RemoteMusicProvider.NETEASE }
        }
    val otherRemotePlaylists =
        remember(remotePlaylists) {
            remotePlaylists.filterNot { it.account.provider == RemoteMusicProvider.NETEASE }
        }
    val neteaseAccount = neteasePlaylists.firstOrNull()?.account
    val userCloudPlaylists = cloudCatalog.userPlaylists
    val totalPlaylistCount = playlists.size + remotePlaylists.size + userCloudPlaylists.size
    val weeklyStats by viewModel.weeklyStats.collectAsStateWithLifecycle()
    val extraFolders by viewModel.extraFolders.collectAsStateWithLifecycle()
    val ignoreFolders by viewModel.ignoreFolders.collectAsStateWithLifecycle()
    val ignoredSongsCount by viewModel.ignoredSongsCount.collectAsStateWithLifecycle()

    // weeklyStats 只在 VM init 拉取一次，进程长驻后会陈旧；每次进入页面刷新
    LaunchedEffect(Unit) { viewModel.refreshWeeklyStats() }

    // SAF launcher 必须驻留在页面根部：文件夹行位于 Lazy 作用域内，滚出屏幕即被销毁，
    // launcher 挂在行内会在系统目录选择器返回前丢失回调
    var pendingReauthFolderId by rememberSaveable { mutableLongStateOf(-1L) }
    val addExtraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { sourceViewModel.addExtraFolder(context, it) }
        }
    val addIgnoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { sourceViewModel.addIgnoreFolder(context, it) }
        }
    val reauthLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val id = pendingReauthFolderId
            if (uri != null && id >= 0) {
                sourceViewModel.reAuthorizeFolder(context, id, uri)
            }
            pendingReauthFolderId = -1L
        }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val showStats = (weeklyStats?.totalPlayedDuration ?: 0L) > 0L
    // 只有额外扫描目录会因 SAF 权限被撤销而失效；忽略目录仅存路径做过滤、不参与授权
    val hasInaccessibleFolders =
        remember(extraFolders) {
            extraFolders.any { !it.isAccessible }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .appPageBackground(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(LocalBottomBarScrollConnection.current),
            contentPadding =
                PaddingValues(
                    start = LayoutTokens.MusicHeaderHorizontalPadding,
                    end = LayoutTokens.MusicHeaderHorizontalPadding,
                    top = statusBarTop + 56.dp,
                    bottom = 200.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            overscrollEffect = rememberIOSOverScrollEffect(Orientation.Vertical),
        ) {
            item(key = "masthead", span = { GridItemSpan(maxLineSpan) }, contentType = "masthead") {
                LibraryMasthead(
                    playlistCount = totalPlaylistCount,
                    subtitle =
                        stringResource(R.string.library_netease_subtitle)
                            .takeIf { neteaseAccount != null },
                    onRefresh =
                        {
                            cloudCatalogViewModel.refreshRemotePlaylists(forceRefresh = true)
                            cloudCatalogViewModel.refreshDailyRecommendations(forceRefresh = true)
                        }.takeIf { neteaseAccount != null },
                    modifier =
                        Modifier
                            .padding(top = Spacing.Large)
                            .graphicsLayer {
                                // 跟手收缩：大标题缩小、上移、淡出，直接耦合滚动偏移
                                val t = mastheadCollapse(gridState)
                                transformOrigin = TransformOrigin(0f, 0f)
                                alpha = (1f - t)
                                translationY = -t * 16.dp.toPx()
                                scaleX = 1f - 0.18f * t
                                scaleY = 1f - 0.18f * t
                            },
                )
            }

            item(key = "cloud_library", span = { GridItemSpan(maxLineSpan) }, contentType = "cloud_library") {
                CloudLibraryEntryCard(
                    onClick = { path.push(CloudLibraryScene()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showStats) {
                item(key = "weekly_stats", span = { GridItemSpan(maxLineSpan) }, contentType = "stats") {
                    WeeklyStatsStrip(
                        stats = weeklyStats ?: return@item,
                        modifier =
                            Modifier
                                .animateItem(
                                    fadeInSpec = null,
                                    placementSpec = null,
                                    fadeOutSpec = null,
                                ),
                    )
                }
            }

            if (neteaseAccount != null) {
                item(key = "netease_account", span = { GridItemSpan(maxLineSpan) }, contentType = "account") {
                    NeteaseAccountSummaryCard(
                        accountName = neteaseAccount.displayName,
                        playlistCount = neteasePlaylists.size,
                        onManageClick = { path.push(CloudLibraryScene()) },
                    )
                }

                item(
                    key = "netease_library_header",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "section_header",
                ) {
                    Text(
                        text = stringResource(R.string.library_netease_section),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Spacing.Medium),
                    )
                }

                itemsIndexed(
                    items = neteasePlaylists,
                    key = { _, item -> "netease_library_${item.stableId}" },
                    contentType = { _, _ -> "netease_playlist" },
                ) { _, item ->
                    LibraryCloudPlaylistCard(
                        name = item.playlist.name,
                        subtitle =
                            stringResource(
                                if (item.playlist.isOwned) {
                                    R.string.library_netease_playlist_owned
                                } else {
                                    R.string.library_netease_playlist_collected
                                },
                                item.playlist.songCount,
                            ),
                        coverUrl = item.playlist.coverUrl,
                        onClick = { path.push(NeteasePlaylistScene(item)) },
                    )
                }
            }

            item(key = "actions", span = { GridItemSpan(maxLineSpan) }, contentType = "actions") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.Small),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    LibraryCommandPill(
                        text = stringResource(R.string.create_playlist),
                        icon = Icons.Default.Add,
                        container = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = { path.push(PlaylistCreatorScene()) },
                        modifier = Modifier.weight(1f),
                    )
                    LibraryCommandPill(
                        text = stringResource(R.string.scan_music),
                        icon = Icons.Default.Scanner,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { path.push(ScannerScene()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (hasInaccessibleFolders) {
                item(key = "sources_alert", span = { GridItemSpan(maxLineSpan) }, contentType = "alert") {
                    InaccessibleFoldersNotice(
                        onClick = {
                            val sourcesHeaderIndex =
                                3 + // masthead、cloud_library、actions
                                    (if (showStats) 1 else 0) +
                                    (if (neteaseAccount != null) 2 + neteasePlaylists.size else 0) +
                                    1 + // sources_alert（本回调触发时必然存在）
                                    (if (playlists.isNotEmpty()) 1 + playlists.size else 0) +
                                    otherRemotePlaylists.size +
                                    userCloudPlaylists.size
                            scope.launch { gridState.animateScrollToItem(sourcesHeaderIndex) }
                        },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = null,
                                fadeOutSpec = null,
                            ),
                    )
                }
            }

            if (playlists.isNotEmpty()) {
                item(key = "playlists_header", span = { GridItemSpan(maxLineSpan) }, contentType = "section_header") {
                    Text(
                        text =
                            stringResource(
                                if (neteaseAccount != null) R.string.library_local_playlists else R.string.my_playlists,
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .padding(top = Spacing.Medium),
                    )
                }

                itemsIndexed(
                    items = playlists,
                    key = { _, item ->
                        item.playlist.playlistId ?: item.playlist.playlistName
                            .hashCode()
                            .toLong()
                    },
                    contentType = { _, _ -> "playlist" },
                ) { index, item ->
                    val cardModifier =
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
                            fadeOutSpec = null,
                        )
                    PlaylistCard(
                        item = item,
                        onClick = {
                            path.push(
                                PlaylistDetailScene(
                                    playlist = item.playlist,
                                    coverAlbumIds = item.coverAlbumIds,
                                ),
                            )
                        },
                        modifier = cardModifier,
                    )
                }
            }

            itemsIndexed(
                items = otherRemotePlaylists,
                key = { _, item -> item.stableId },
                contentType = { _, _ -> "remote_playlist" },
            ) { _, item ->
                LibraryCloudPlaylistCard(
                    name = item.playlist.name,
                    subtitle =
                        "${item.account.provider.displayName} · " +
                            "${item.playlist.songCount} 首",
                    coverUrl = item.playlist.coverUrl,
                    onClick = { path.push(NeteasePlaylistScene(item)) },
                )
            }

            itemsIndexed(
                items = userCloudPlaylists,
                key = { _, item -> "user_cloud_${item.id}" },
                contentType = { _, _ -> "user_cloud_playlist" },
            ) { _, item ->
                LibraryCloudPlaylistCard(
                    name = item.name,
                    subtitle = "本地云端歌单 · ${item.songs.size} 首",
                    coverUrl = item.songs.firstOrNull()?.artworkUrl,
                    onClick = { path.push(CloudUserPlaylistScene(item)) },
                )
            }

            item(key = "sources_header", span = { GridItemSpan(maxLineSpan) }, contentType = "section_header") {
                SourcesSectionHeader(
                    showErrorDot = hasInaccessibleFolders,
                    modifier = Modifier.padding(top = Spacing.ExtraLarge),
                )
            }

            item(key = "extra_header", span = { GridItemSpan(maxLineSpan) }, contentType = "sub_header") {
                FolderSubHeader(
                    title = stringResource(R.string.extra_scan_folders),
                    onAddClick = { addExtraLauncher.launch(null) },
                    modifier = Modifier.padding(top = Spacing.Small),
                )
            }

            if (extraFolders.isEmpty()) {
                item(key = "extra_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "folder_empty") {
                    FolderEmptyHint(text = stringResource(R.string.add_extra_folder_hint))
                }
            } else {
                items(
                    count = extraFolders.size,
                    key = { "extra_${extraFolders[it].id}" },
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = { "folder" },
                ) { index ->
                    val folder = extraFolders[index]
                    FolderRow(
                        folder = folder,
                        onRemove = { sourceViewModel.removeFolder(context, folder) },
                        onReAuthorize = {
                            pendingReauthFolderId = folder.id
                            reauthLauncher.launch(null)
                        },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold,
                                    ),
                                fadeOutSpec = null,
                            ),
                    )
                }
            }

            item(key = "ignore_header", span = { GridItemSpan(maxLineSpan) }, contentType = "sub_header") {
                FolderSubHeader(
                    title = stringResource(R.string.ignore_folders),
                    onAddClick = { addIgnoreLauncher.launch(null) },
                    modifier = Modifier.padding(top = Spacing.Small),
                )
            }

            if (ignoreFolders.isEmpty()) {
                item(key = "ignore_empty", span = { GridItemSpan(maxLineSpan) }, contentType = "folder_empty") {
                    FolderEmptyHint(text = stringResource(R.string.add_ignore_folder_hint))
                }
            } else {
                items(
                    count = ignoreFolders.size,
                    key = { "ignore_${ignoreFolders[it].id}" },
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = { "folder" },
                ) { index ->
                    val folder = ignoreFolders[index]
                    FolderRow(
                        folder = folder,
                        onRemove = { sourceViewModel.removeFolder(context, folder) },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold,
                                    ),
                                fadeOutSpec = null,
                            ),
                    )
                }
            }

            item(key = "ignored_songs_entry", span = { GridItemSpan(maxLineSpan) }, contentType = "ignored_songs_entry") {
                IgnoredSongsEntryRow(
                    count = ignoredSongsCount,
                    onClick = { path.push(IgnoredSongsScene()) },
                    modifier =
                        Modifier
                            .padding(top = Spacing.Small)
                            .animateItem(
                                fadeInSpec = null,
                                placementSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold,
                                    ),
                                fadeOutSpec = null,
                            ),
                )
            }
        }

        LibraryTopBar(
            gridState = gridState,
            onCreateClick = { path.push(PlaylistCreatorScene()) },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/**
 * 大标题收缩进度：0f=完全展开 1f=完全收进顶栏（在 Draw 阶段读取，滚动零重组）。
 * 归一化距离取刊头实测滚出高度（含行距），保证进度在 firstVisibleItemIndex 翻转前
 * 自然到达 1f、不产生跳变；[MastheadCollapseDistance] 仅作异常高度的上限防御。
 */
private fun Density.mastheadCollapse(gridState: LazyGridState): Float {
    if (gridState.firstVisibleItemIndex > 0) return 1f
    val layoutInfo = gridState.layoutInfo
    val masthead = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val scrollOutDistance =
        (masthead.size.height + layoutInfo.mainAxisItemSpacing)
            .toFloat()
            .coerceIn(1f, MastheadCollapseDistance.toPx())
    return (gridState.firstVisibleItemScrollOffset / scrollOutDistance).coerceIn(0f, 1f)
}

/** 固定顶栏：背景与标题透明度跟随刊头收缩进度，收起后弹出迷你「新建歌单」药丸 */
@Composable
private fun LibraryTopBar(
    gridState: LazyGridState,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 布尔量化派生状态放在顶栏自身作用域：翻转只重组顶栏，不波及页面根
    val solid by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    // 容器只做绘制不挂任何点击 Modifier：透明态不得拦截刊头与操作行的触摸
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarTop + 56.dp),
    ) {
        CollapsingBarBackground(Modifier.matchParentSize()) { mastheadCollapse(gridState) }
        // 全页唯一分隔线：顶栏收起后出现
        if (solid) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomStart),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = mastheadCollapse(gridState) },
            )
            AnimatedVisibility(
                visible = solid,
                enter =
                    scaleIn(
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        initialScale = 0.6f,
                    ) + fadeIn(tween(durationMillis = 160)),
                exit =
                    scaleOut(
                        animationSpec = tween(durationMillis = 140),
                        targetScale = 0.8f,
                    ) + fadeOut(tween(durationMillis = 140)),
            ) {
                Row(
                    modifier =
                        Modifier
                            .clip(
                                CircleShape,
                            ).glassSurface(
                                CircleShape,
                                MaterialTheme.colorScheme.primary,
                                tint = MaterialTheme.colorScheme.primary,
                                opacity = 0.7f,
                            ).clickHighlight(onClick = onCreateClick)
                            .padding(horizontal = Spacing.Medium, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.create_playlist),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 刊头：大标题 + 歌单计数 meta 行（计数全页唯一，周数据只活在统计条） */
@Composable
private fun LibraryMasthead(
    playlistCount: Int,
    subtitle: String?,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            onRefresh?.let { refresh ->
                IconButton(onClick = refresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(top = 6.dp)) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                RollingPlaylistCount(
                    playlistCount = playlistCount,
                )
            }
        }
    }
}

@Composable
private fun NeteaseAccountSummaryCard(
    accountName: String,
    playlistCount: Int,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(
                    Shapes.ExtraLargeCornerBasedShape,
                ).glassSurface(
                    Shapes.ExtraLargeCornerBasedShape,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    tint = MaterialTheme.colorScheme.surfaceContainerLow,
                    opacity = 0.7f,
                ).padding(Spacing.Large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(
                        Shapes.LargeCornerBasedShape,
                    ).glassSurface(
                        Shapes.LargeCornerBasedShape,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        tint = MaterialTheme.colorScheme.tertiaryContainer,
                        opacity = 0.7f,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = accountName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.library_netease_account_summary, playlistCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier =
                Modifier
                    .clip(
                        CircleShape,
                    ).glassSurface(
                        CircleShape,
                        MaterialTheme.colorScheme.secondaryContainer,
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        opacity = 0.7f,
                    ).clickHighlight(onClick = onManageClick)
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.library_account_management),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

/** 歌单计数：数字变化时上下滚动切换 */
@Composable
private fun RollingPlaylistCount(
    playlistCount: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = playlistCount,
        transitionSpec = {
            val direction = if (targetState >= initialState) 1 else -1
            (
                slideInVertically { height -> direction * height / 2 } +
                    fadeIn(tween(durationMillis = 240))
            ) togetherWith
                (
                    slideOutVertically { height -> -direction * height / 2 } +
                        fadeOut(tween(durationMillis = 160))
                ) using SizeTransform(clip = false)
        },
        modifier = modifier,
        label = "libraryPlaylistCountRoll",
    ) { count ->
        Text(
            text = stringResource(R.string.library_summary_playlists, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 命令药丸：52dp 圆胶囊 + 按压回弹（收藏页 HeroCommandPill 同款，无心跳脉冲） */
@Composable
internal fun LibraryCommandPill(
    text: String,
    icon: ImageVector,
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = 1100f,
            ),
        label = "libraryCommandPillPressScale",
    )
    Box(
        modifier =
            modifier
                .heightIn(min = 52.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(CircleShape)
                .glassSurface(CircleShape, container, tint = container, opacity = 0.7f)
                .clickHighlight(interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}

/** 目录权限失效警示条：仅在存在失效目录时出现，点按直达「媒体库来源」分区 */
@Composable
private fun InaccessibleFoldersNotice(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(
                    Shapes.ExtraLargeCornerBasedShape,
                ).glassSurface(
                    Shapes.ExtraLargeCornerBasedShape,
                    MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.errorContainer,
                    opacity = 0.7f,
                ).clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.library_folders_inaccessible_notice),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** 本周统计条：眉题 + 三格明细，纯展示不可点击 */
@Composable
private fun WeeklyStatsStrip(
    stats: PlayStats,
    modifier: Modifier = Modifier,
) {
    val hoursMinutesFmt = stringResource(R.string.hours_minutes)
    val minutesFmt = stringResource(R.string.minutes)
    val lessThan1MinText = stringResource(R.string.less_than_1_minute)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(
                    Shapes.ExtraLargeCornerBasedShape,
                ).glassSurface(
                    Shapes.ExtraLargeCornerBasedShape,
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    tint = MaterialTheme.colorScheme.surfaceContainerLow,
                    opacity = 0.7f,
                ).padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = stringResource(R.string.weekly_listening_overview),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell(
                value = formatPlayDuration(stats.totalPlayedDuration, hoursMinutesFmt, minutesFmt, lessThan1MinText),
                label = stringResource(R.string.play_duration),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                value = "${stats.playEventCount}",
                label = stringResource(R.string.play_count),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                value = "${stats.uniqueSongCount}",
                label = stringResource(R.string.unique_songs),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 歌单卡：马赛克封面 + 名称 + 歌曲数 */
@Composable
private fun PlaylistCard(
    item: PlaylistWithCover,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .clickHighlight(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        PlaylistCoverView(
            albumIds = item.coverAlbumIds,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Shapes.ExtraLargeCornerBasedShape),
        )
        Column(
            modifier = Modifier.padding(bottom = Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.playlist.playlistName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.songs_count, item.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryCloudPlaylistCard(
    name: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .clickHighlight(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        AudioCover(
            uri = coverUrl?.let(Uri::parse),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(
                        Shapes.ExtraLargeCornerBasedShape,
                    ).glassSurface(
                        Shapes.ExtraLargeCornerBasedShape,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                        opacity = 0.7f,
                    ),
            placeHolder = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary)
                }
            },
        )
        Column(
            modifier = Modifier.padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 歌单空态：开放排版无卡片，音符轻盈浮动（收藏页配方） */
@Composable
private fun PlaylistsEmptyState(modifier: Modifier = Modifier) {
    val floatTransition = rememberInfiniteTransition(label = "libraryEmptyFloat")
    val bob by floatTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "libraryEmptyBob",
    )
    Column(
        modifier = modifier.padding(top = Spacing.ExtraLarge, bottom = Spacing.Huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        translationY = bob * 5.dp.toPx()
                        rotationZ = bob * 6f
                    }.clip(
                        CircleShape,
                    ).glassSurface(
                        CircleShape,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        opacity = 0.7f,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.no_playlists_yet),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.create_first_playlist_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 「媒体库来源」总分区头：标题 + 失效红点 + 副文案 */
@Composable
private fun SourcesSectionHeader(
    showErrorDot: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = stringResource(R.string.media_library_source_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (showErrorDot) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(
                                CircleShape,
                            ).glassSurface(
                                CircleShape,
                                MaterialTheme.colorScheme.error,
                                tint = MaterialTheme.colorScheme.error,
                                opacity = 0.7f,
                            ),
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_media_library_source_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 目录子分区头：标题 + 「添加」胶囊动作 */
@Composable
private fun FolderSubHeader(
    title: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier =
                Modifier
                    .clip(
                        CircleShape,
                    ).glassSurface(
                        CircleShape,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                        opacity = 0.7f,
                    ).clickHighlight(onClick = onAddClick)
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.add_folder),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 目录空提示：开放式单行文案，无卡片 */
@Composable
private fun FolderEmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = Spacing.ExtraSmall),
    )
}

/**
 * 目录行：通栏无卡片，44dp 圆形图标徽章按可达性着色，
 * 失效时尾随槽内弹出「重新授权」胶囊（materialSharedAxisZ 交换），移除有预缩放微反馈
 */
@Composable
private fun FolderRow(
    folder: ScanFolder,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onReAuthorize: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // 移除微反馈：图标先蓄力放大、再收缩消失，随后条目在列表中退场（收藏页取消收藏配方）
    val removeScale = remember(folder.id) { Animatable(1f) }
    val badgeColor =
        if (folder.isAccessible) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(
                        CircleShape,
                    ).glassSurface(CircleShape, badgeColor.copy(alpha = 0.12f), tint = badgeColor.copy(alpha = 0.12f), opacity = 0.7f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (folder.isAccessible) Icons.Default.Folder else Icons.Default.FolderOff,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = folder.pathPrefix ?: folder.uriString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 状态位固定尾随槽：可达性翻转时在槽内做 Z 轴交换，行宽不跳
        AnimatedContent(
            targetState = !folder.isAccessible && onReAuthorize != null,
            transitionSpec = { materialSharedAxisZ(forward = true) },
            label = "folderTrailingState",
        ) { needsReauth ->
            if (needsReauth && onReAuthorize != null) {
                Text(
                    text = stringResource(R.string.reauthorize),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier =
                        Modifier
                            .clip(
                                CircleShape,
                            ).glassSurface(
                                CircleShape,
                                MaterialTheme.colorScheme.errorContainer,
                                tint = MaterialTheme.colorScheme.errorContainer,
                                opacity = 0.7f,
                            ).clickHighlight(onClick = onReAuthorize)
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                )
            } else {
                // 可达状态由徽章底色编码，不再显示文字标签
                Box(Modifier)
            }
        }
        IconButton(
            onClick = {
                if (removeScale.isRunning) return@IconButton
                scope.launch {
                    removeScale.animateTo(
                        targetValue = 1.28f,
                        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                    )
                    removeScale.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing),
                    )
                    onRemove()
                }
            },
            modifier =
                Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = removeScale.value
                        scaleY = removeScale.value
                    },
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.remove_folder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 已忽略歌曲
 */
@Composable
private fun IgnoredSongsEntryRow(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeColor = MaterialTheme.colorScheme.primary
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(
                        CircleShape,
                    ).glassSurface(CircleShape, badgeColor.copy(alpha = 0.12f), tint = badgeColor.copy(alpha = 0.12f), opacity = 0.7f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MusicOff,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_ignore_music),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.songs_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun formatPlayDuration(
    durationMs: Long,
    hoursMinutesFormat: String,
    minutesFormat: String,
    lessThan1Min: String,
): String {
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    return when {
        hours > 0 -> hoursMinutesFormat.format(hours, minutes)
        minutes > 0 -> minutesFormat.format(minutes)
        else -> lessThan1Min
    }
}
