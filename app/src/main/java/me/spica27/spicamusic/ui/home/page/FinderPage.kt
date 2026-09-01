package me.spica27.spicamusic.ui.home.page

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.delay
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.spicamusic.App
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.CatalogQueueItem
import me.spica27.spicamusic.cloud.CloudCatalogPlaylist
import me.spica27.spicamusic.cloud.CloudCatalogSong
import me.spica27.spicamusic.cloud.CloudMusicCatalogViewModel
import me.spica27.spicamusic.cloud.CloudSongSource
import me.spica27.spicamusic.cloud.CloudUserPlaylist
import me.spica27.spicamusic.cloud.CloudUserPlaylistScene
import me.spica27.spicamusic.cloud.NeteasePlaylistScene
import me.spica27.spicamusic.cloud.RemoteMusicProvider
import me.spica27.spicamusic.common.entity.FinderHeroSource
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.common.entity.getAlbumCoverUri
import me.spica27.spicamusic.common.entity.getCoverUri
import me.spica27.spicamusic.ui.cache.ArtworkRenderCache
import me.spica27.spicamusic.ui.cache.PlaylistCoverSnapshotCache
import me.spica27.spicamusic.ui.favorite.FavoriteScene
import me.spica27.spicamusic.ui.home.HomePage
import me.spica27.spicamusic.ui.home.HomeViewModel
import me.spica27.spicamusic.ui.home.LocalBottomBarScrollConnection
import me.spica27.spicamusic.ui.model.PlaylistWithCover
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.playlist.AllPlaylistsScene
import me.spica27.spicamusic.ui.playlistdetail.PlaylistDetailScene
import me.spica27.spicamusic.ui.search.SearchScene
import me.spica27.spicamusic.ui.settings.SettingsScene
import me.spica27.spicamusic.ui.theme.LayoutTokens
import me.spica27.spicamusic.ui.theme.ListItemFadeInSpec
import me.spica27.spicamusic.ui.theme.ListItemFadeOutSpec
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.AudioCover
import me.spica27.spicamusic.ui.widget.PlaylistCoverView
import me.spica27.spicamusic.ui.widget.StableAudioCover
import me.spica27.spicamusic.ui.widget.clickHighlight
import me.spica27.spicamusic.ui.widget.rememberIOSOverScrollEffect
import me.spica27.spicamusic.ui.widget.stableStatusBarTopPadding
import org.koin.compose.viewmodel.koinActivityViewModel

/**
 * 发现页
 */

/** 大标题收缩归一化距离的上限（实际取刊头实测滚出高度，见 mastheadCollapse） */
private val MastheadCollapseDistance = 140.dp

/** 首屏入场交错间隔 */
private const val ENTRANCE_STAGGER_MILLIS = 55L

/** 收藏预览最多展示的歌曲数 */
private const val FavoritePreviewSongCount = 5

private data class FrequentEntry(
    val stableId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUri: Uri?,
    val fallbackArtworkUri: Uri?,
    val queueItem: CatalogQueueItem,
)

private data class NeteaseSceneMusic(
    val title: String,
    val query: String,
    val artworkUri: Uri?,
    val icon: ImageVector,
    val colorIndex: Int,
)

private fun Song.toFrequentEntry(): FrequentEntry =
    FrequentEntry(
        stableId = "local:$mediaStoreId",
        title = displayName,
        artist = artist,
        durationMs = duration,
        artworkUri = getCoverUri(),
        fallbackArtworkUri = getAlbumCoverUri(),
        queueItem = CatalogQueueItem.Local(this),
    )

private fun CloudCatalogSong.toFrequentEntry(): FrequentEntry =
    FrequentEntry(
        stableId = stableId,
        title = title,
        artist = artist,
        durationMs = durationMs,
        artworkUri = artworkUri,
        fallbackArtworkUri = null,
        queueItem = CatalogQueueItem.Cloud(this),
    )

private fun Long.asDurationLabel(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/** 列表项增删的统一动画配方（同资料库页） */
private val ItemPlacementSpec: FiniteAnimationSpec<IntOffset> =
    spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

@Composable
fun FinderPage(playEntrance: Boolean = true) {
    val path = LocalNavigationPath.current
    val homeViewModel: HomeViewModel = koinActivityViewModel()
    val cloudCatalogViewModel: CloudMusicCatalogViewModel = koinActivityViewModel()
    val playerViewModel = LocalPlayerViewModel.current

    val frequentSongs by homeViewModel.frequentSongs.collectAsStateWithLifecycle()
    val finderHeroSourceValue by homeViewModel.finderHeroSource.collectAsStateWithLifecycle()
    val finderHeroSource = FinderHeroSource.fromString(finderHeroSourceValue)
    val frequentSongsInitialized by homeViewModel.frequentSongsInitialized.collectAsStateWithLifecycle()
    val favoriteSongs by homeViewModel.favoriteSongs.collectAsStateWithLifecycle()
    val playlists by homeViewModel.playlists.collectAsStateWithLifecycle()
    val playlistsWithCover by homeViewModel.playlistsWithCover.collectAsStateWithLifecycle()
    val snackbarMessage by homeViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val cloudCatalog by cloudCatalogViewModel.state.collectAsStateWithLifecycle()
    val dailyPlaylistName = stringResource(R.string.finder_daily_playlist_name)
    val cloudPlaylists = cloudCatalog.remotePlaylists
    val userCloudPlaylists = cloudCatalog.userPlaylists
    val overviewCloudPlaylists =
        remember(cloudPlaylists) {
            cloudPlaylists.filterNot { it.account.provider == RemoteMusicProvider.NETEASE }
        }
    val hasNeteaseAccount = CloudSongSource.NETEASE in cloudCatalog.availableSources
    val neteasePlaylists =
        remember(cloudPlaylists) {
            cloudPlaylists.filter { it.account.provider == RemoteMusicProvider.NETEASE }
        }
    val orderedNeteasePlaylists =
        remember(neteasePlaylists) {
            neteasePlaylists
                .filterNot { it.playlist.name.contains("我喜欢") }
                .sortedWith(
                    compareByDescending<CloudCatalogPlaylist> {
                        it.playlist.name.contains("雷达") || it.playlist.name.contains("推荐")
                    }.thenByDescending { it.playlist.songCount },
                ).distinctBy { it.playlist.id }
        }
    val radarPlaylistCount =
        when (orderedNeteasePlaylists.size) {
            0 -> 0
            1 -> 1
            2 -> 1
            else -> ((orderedNeteasePlaylists.size + 1) / 2).coerceAtMost(6)
        }
    val radarPlaylists =
        remember(orderedNeteasePlaylists, radarPlaylistCount) {
            orderedNeteasePlaylists.take(radarPlaylistCount)
        }
    val moreNeteasePlaylists =
        remember(orderedNeteasePlaylists, radarPlaylistCount) {
            orderedNeteasePlaylists
                .drop(radarPlaylistCount)
                .take(12)
        }
    val neteaseSceneArtwork =
        remember(cloudCatalog.dailyRecommendations) {
            cloudCatalog.dailyRecommendations
                .mapNotNull(CloudCatalogSong::artworkUri)
                .distinctBy(Uri::toString)
                .take(6)
        }
    val sceneTitles =
        listOf(
            stringResource(R.string.netease_scene_commute),
            stringResource(R.string.netease_scene_fun),
            stringResource(R.string.netease_scene_relax),
            stringResource(R.string.netease_scene_focus),
            stringResource(R.string.netease_scene_sleep),
            stringResource(R.string.netease_scene_classics),
        )
    val sceneQueries = listOf("通勤 节奏", "运动 欢快", "放松 治愈", "专注 学习 纯音乐", "助眠 轻音乐", "经典 怀旧")
    val sceneIcons =
        listOf(
            Icons.Default.DirectionsCar,
            Icons.Default.FitnessCenter,
            Icons.Default.Spa,
            Icons.Default.MenuBook,
            Icons.Default.Bedtime,
            Icons.Default.History,
        )
    val neteaseScenes =
        remember(sceneTitles, neteaseSceneArtwork) {
            sceneTitles.mapIndexed { index, title ->
                NeteaseSceneMusic(
                    title = title,
                    query = sceneQueries[index],
                    artworkUri = neteaseSceneArtwork.getOrNull(index),
                    icon = sceneIcons[index],
                    colorIndex = index,
                )
            }
        }
    val overviewPlaylistsWithCover =
        remember(playlistsWithCover, dailyPlaylistName) {
            playlistsWithCover.filterNot { it.playlist.playlistName == dailyPlaylistName }
        }
    val overviewLocalPlaylistCount =
        remember(playlists, dailyPlaylistName) {
            playlists.count { it.playlistName != dailyPlaylistName }
        }
    val totalPlaylistCount =
        overviewLocalPlaylistCount + overviewCloudPlaylists.size + userCloudPlaylists.size
    val frequentEntries =
        remember(frequentSongs, cloudCatalog.recentCloudSongs) {
            (
                cloudCatalog.recentCloudSongs.map(CloudCatalogSong::toFrequentEntry) +
                    frequentSongs.map(Song::toFrequentEntry)
            ).distinctBy(FrequentEntry::stableId)
                .take(10)
        }
    val heroEntries =
        remember(finderHeroSource, frequentEntries, cloudCatalog.dailyRecommendations) {
            when (finderHeroSource) {
                FinderHeroSource.RECENT_FREQUENT -> frequentEntries
                FinderHeroSource.NETEASE_DAILY ->
                    cloudCatalog.dailyRecommendations
                        .map(CloudCatalogSong::toFrequentEntry)
                        .distinctBy(FrequentEntry::stableId)
                        .take(10)
            }
        }
    val heroSongs = remember(heroEntries) { ImmutableList.copyOf(heroEntries) }
    val heroInitialized =
        when (finderHeroSource) {
            FinderHeroSource.RECENT_FREQUENT -> frequentSongsInitialized
            FinderHeroSource.NETEASE_DAILY -> !cloudCatalog.isLoadingDailyRecommendations
        }
    val favoritePreviewSongs =
        remember(favoriteSongs) {
            ImmutableList.copyOf(favoriteSongs.take(FavoritePreviewSongCount))
        }

    val frequentPlaylistName = stringResource(R.string.finder_frequent_playlist_name)
    val heroTitle =
        stringResource(
            when (finderHeroSource) {
                FinderHeroSource.RECENT_FREQUENT -> R.string.finder_frequent_title
                FinderHeroSource.NETEASE_DAILY -> R.string.finder_daily_title
            },
        )
    val favoritePlaylistName = stringResource(R.string.finder_favorites_playlist_name)

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        Toast.makeText(App.getInstance(), message, Toast.LENGTH_SHORT).show()
        homeViewModel.clearSnackbar()
    }

    val listState = rememberLazyListState()
    val statusBarTop = stableStatusBarTopPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(LocalBottomBarScrollConnection.current),
            contentPadding =
                PaddingValues(
                    top = statusBarTop + 56.dp,
                    bottom = 200.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            item(key = "masthead", contentType = "masthead") {
                val entrance = rememberEntrance(order = 0, play = playEntrance)
                FinderMasthead(
                    frequentCount = frequentEntries.size,
                    favoriteCount = favoriteSongs.size,
                    playlistCount = totalPlaylistCount,
                    summaryReady = frequentSongsInitialized,
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .padding(top = Spacing.Large)
                            .graphicsLayer {
                                // 不再缩放文字。滚动时对 Text 做位图缩放会产生重影和字形变形；
                                // 这里只做互斥淡出，位置由 LazyColumn 自然滚动，保证字形始终清晰。
                                val t = mastheadCollapse(listState)
                                val enter = entrance.value
                                alpha = expandedTitleAlpha(t) * enter
                                translationY = (1f - enter) * 28.dp.toPx()
                            },
                )
            }

            item(key = "search", contentType = "search") {
                val entrance = rememberEntrance(order = 1, play = playEntrance)
                SearchCapsule(
                    onClick = { path.push(SearchScene()) },
                    modifier = Modifier.entranceGraphics(entrance),
                )
            }

            // 常听榜单：冷启动查询完成后直接呈现，避免 Lazy 条目首次插入淡入导致整卡闪黑。
            // 从其他 Tab 切回发现页时仍由 entranceGraphics 负责统一的控件入场动画。
            if (!heroInitialized) {
                item(key = "frequent_loading", contentType = "loading") {
                    FrequentHeroPlaceholder()
                }
            } else if (heroEntries.isEmpty()) {
                item(key = "frequent_empty", contentType = "empty") {
                    val entrance = rememberEntrance(order = 3, play = playEntrance)
                    FinderEmptyRow(
                        title =
                            stringResource(
                                if (finderHeroSource == FinderHeroSource.NETEASE_DAILY) {
                                    R.string.finder_no_daily_title
                                } else {
                                    R.string.finder_no_frequent_title
                                },
                            ),
                        subtitle =
                            stringResource(
                                if (finderHeroSource == FinderHeroSource.NETEASE_DAILY) {
                                    R.string.finder_no_daily_subtitle
                                } else {
                                    R.string.finder_no_frequent_subtitle
                                },
                            ),
                        modifier = Modifier.entranceGraphics(entrance),
                    )
                }
            } else {
                item(key = "frequent_hero", contentType = "hero") {
                    val entrance = rememberEntrance(order = 3, play = playEntrance)
                    FrequentHeroCard(
                        title = heroTitle,
                        songs = heroSongs,
                        onPlayAll = {
                            heroEntries.firstOrNull()?.let { first ->
                                cloudCatalogViewModel.play(
                                    first.stableId,
                                    heroEntries.map(FrequentEntry::queueItem),
                                )
                            }
                        },
                        onSongClick = { entry ->
                            cloudCatalogViewModel.play(
                                entry.stableId,
                                heroEntries.map(FrequentEntry::queueItem),
                            )
                        },
                        onSaveAsPlaylist = {
                            cloudCatalogViewModel.createLocalPlaylistFromQueue(
                                name =
                                    if (finderHeroSource == FinderHeroSource.NETEASE_DAILY) {
                                        dailyPlaylistName
                                    } else {
                                        frequentPlaylistName
                                    },
                                items = heroEntries.map(FrequentEntry::queueItem),
                            )
                        },
                        modifier = Modifier.entranceGraphics(entrance),
                    )
                }
            }

            if (favoriteSongs.isNotEmpty()) {
                item(key = "favorites_header", contentType = "section_header") {
                    val entrance = rememberEntrance(order = 4, play = playEntrance)
                    SectionHeader(
                        title = stringResource(R.string.my_favorites),
                        subtitle = stringResource(R.string.songs_count_format, favoriteSongs.size),
                        actionLabel = stringResource(R.string.finder_more),
                        onActionClick = { path.push(FavoriteScene()) },
                        modifier =
                            Modifier
                                .animateItem(
                                    fadeInSpec = ListItemFadeInSpec,
                                    placementSpec = ItemPlacementSpec,
                                    fadeOutSpec = ListItemFadeOutSpec,
                                ).padding(top = Spacing.Medium)
                                .entranceGraphics(entrance),
                    )
                }
                item(key = "favorites_card", contentType = "favorites") {
                    val entrance = rememberEntrance(order = 5, play = playEntrance)
                    FavoritesCard(
                        songs = favoritePreviewSongs,
                        onPlayAll = {
                            playerViewModel.updatePlaylistWithSongs(
                                songs = favoriteSongs,
                                startSong = favoriteSongs.firstOrNull(),
                                autoStart = true,
                            )
                        },
                        onSongClick = { song ->
                            playerViewModel.updatePlaylistWithSongs(
                                songs = favoriteSongs,
                                startSong = song,
                                autoStart = true,
                            )
                        },
                        onSaveAsPlaylist = {
                            homeViewModel.createPlaylistFromSongs(
                                songs = favoriteSongs,
                                playlistName = favoritePlaylistName,
                            )
                        },
                        modifier =
                            Modifier
                                .animateItem(
                                    fadeInSpec = ListItemFadeInSpec,
                                    placementSpec = null,
                                    fadeOutSpec = ListItemFadeOutSpec,
                                ).entranceGraphics(entrance),
                    )
                }
            }

            if (totalPlaylistCount > 0) {
                item(key = "playlists_header", contentType = "section_header") {
                    val entrance = rememberEntrance(order = 6, play = playEntrance)
                    SectionHeader(
                        title = stringResource(R.string.finder_playlists_overview_title),
                        subtitle = stringResource(R.string.library_summary_playlists, totalPlaylistCount),
                        actionLabel =
                            stringResource(R.string.finder_more).takeIf {
                                overviewLocalPlaylistCount >= 2
                            },
                        onActionClick =
                            { path.push(AllPlaylistsScene()) }.takeIf {
                                overviewLocalPlaylistCount >= 2
                            },
                        modifier =
                            Modifier
                                .animateItem(
                                    fadeInSpec = ListItemFadeInSpec,
                                    placementSpec = ItemPlacementSpec,
                                    fadeOutSpec = ListItemFadeOutSpec,
                                ).padding(top = Spacing.Medium)
                                .entranceGraphics(entrance),
                    )
                }
                if (overviewPlaylistsWithCover.isNotEmpty()) {
                    item(key = "playlists_rail", contentType = "rail") {
                        val entrance = rememberEntrance(order = 6, play = playEntrance)
                        PlaylistRail(
                            playlists = overviewPlaylistsWithCover,
                            onPlaylistClick = { item -> path.push(PlaylistDetailScene(item.playlist)) },
                            modifier =
                                Modifier
                                    .animateItem(
                                        fadeInSpec = ListItemFadeInSpec,
                                        placementSpec = null,
                                        fadeOutSpec = ListItemFadeOutSpec,
                                    ).entranceGraphics(entrance),
                        )
                    }
                }
                if (overviewCloudPlaylists.isNotEmpty()) {
                    item(key = "cloud_playlists_rail", contentType = "rail") {
                        val entrance = rememberEntrance(order = 6, play = playEntrance)
                        CloudPlaylistRail(
                            playlists = overviewCloudPlaylists,
                            onPlaylistClick = { item -> path.push(NeteasePlaylistScene(item)) },
                            modifier = Modifier.entranceGraphics(entrance),
                        )
                    }
                }
                if (userCloudPlaylists.isNotEmpty()) {
                    item(key = "user_cloud_playlists_rail", contentType = "rail") {
                        val entrance = rememberEntrance(order = 6, play = playEntrance)
                        CloudUserPlaylistRail(
                            playlists = userCloudPlaylists,
                            onPlaylistClick = { item -> path.push(CloudUserPlaylistScene(item)) },
                            modifier = Modifier.entranceGraphics(entrance),
                        )
                    }
                }
            }

            if (hasNeteaseAccount) {
                item(key = "netease_radar_header", contentType = "section_header") {
                    SectionHeader(
                        title = stringResource(R.string.netease_radar_playlists),
                        subtitle = stringResource(R.string.netease_logged_in_recommendations),
                        modifier = Modifier.padding(top = Spacing.ExtraLarge),
                    )
                }
                if (radarPlaylists.isNotEmpty()) {
                    item(key = "netease_radar", contentType = "rail") {
                        CloudPlaylistRail(
                            playlists = radarPlaylists,
                            onPlaylistClick = { path.push(NeteasePlaylistScene(it)) },
                            placeholderIcon = Icons.Default.Radar,
                        )
                    }
                }

                item(key = "netease_scenes_header", contentType = "section_header") {
                    SectionHeader(
                        title = stringResource(R.string.netease_scene_music),
                        subtitle = stringResource(R.string.netease_scene_music_subtitle),
                    )
                }
                item(key = "netease_scenes", contentType = "rail") {
                    NeteaseSceneMusicRail(
                        scenes = neteaseScenes,
                        onSceneClick = { scene -> path.push(SearchScene(scene.query)) },
                    )
                }

                if (moreNeteasePlaylists.isNotEmpty()) {
                    item(key = "netease_more_header", contentType = "section_header") {
                        SectionHeader(
                            title = stringResource(R.string.netease_more_selected),
                            subtitle = stringResource(R.string.netease_more_selected_subtitle),
                        )
                    }
                    item(key = "netease_more", contentType = "rail") {
                        CloudPlaylistRail(
                            playlists = moreNeteasePlaylists,
                            onPlaylistClick = { path.push(NeteasePlaylistScene(it)) },
                            placeholderIcon = Icons.Default.AutoAwesome,
                        )
                    }
                }
            }

            // 导航性质的快捷入口沉底为页尾分区（对应资料库页的「媒体库来源」处理），不参与入场编舞
            item(key = "entries_header", contentType = "section_header") {
                Text(
                    text = stringResource(R.string.finder_quick_entries_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .animateItem(
                                fadeInSpec = ListItemFadeInSpec,
                                placementSpec = ItemPlacementSpec,
                                fadeOutSpec = ListItemFadeOutSpec,
                            ).padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .padding(top = Spacing.ExtraLarge),
                )
            }

            item(key = "entry_library", contentType = "utility_row") {
                UtilityEntryRow(
                    title = stringResource(R.string.finder_library_title),
                    subtitle = stringResource(R.string.finder_library_subtitle),
                    icon = Icons.Default.LibraryMusic,
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = { homeViewModel.navigateToPage(HomePage.Library) },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = ListItemFadeInSpec,
                            placementSpec = ItemPlacementSpec,
                            fadeOutSpec = ListItemFadeOutSpec,
                        ),
                )
            }

            item(key = "entry_music", contentType = "utility_row") {
                UtilityEntryRow(
                    title = stringResource(R.string.finder_all_music_title),
                    subtitle = stringResource(R.string.finder_all_music_subtitle),
                    icon = Icons.Default.MusicNote,
                    badgeColor = MaterialTheme.colorScheme.tertiary,
                    onClick = { homeViewModel.navigateToPage(HomePage.Music) },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = ListItemFadeInSpec,
                            placementSpec = ItemPlacementSpec,
                            fadeOutSpec = ListItemFadeOutSpec,
                        ),
                )
            }

            item(key = "entry_settings", contentType = "utility_row") {
                UtilityEntryRow(
                    title = stringResource(R.string.finder_settings_title),
                    subtitle = stringResource(R.string.finder_settings_subtitle),
                    icon = Icons.Default.Settings,
                    badgeColor = MaterialTheme.colorScheme.secondary,
                    onClick = { path.push(SettingsScene()) },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = ListItemFadeInSpec,
                            placementSpec = ItemPlacementSpec,
                            fadeOutSpec = ListItemFadeOutSpec,
                        ),
                )
            }
        }

        FinderTopBar(
            listState = listState,
            onSearchClick = { path.push(SearchScene()) },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

/**
 * 大标题收缩进度：0f=完全展开 1f=完全收进顶栏（在 Draw 阶段读取，滚动零重组）。
 * 使用固定折叠距离，避免滚动帧内反复读取 LazyList layoutInfo。
 */
private fun Density.mastheadCollapse(listState: LazyListState): Float {
    if (listState.firstVisibleItemIndex > 0) return 1f
    val scrollOutDistance = MastheadCollapseDistance.toPx().coerceAtLeast(1f)
    return (listState.firstVisibleItemScrollOffset / scrollOutDistance).coerceIn(0f, 1f)
}

/** 大标题在 72% 折叠进度前完全淡出，固定标题随后接管，两个标题不交叠。 */
private fun expandedTitleAlpha(progress: Float): Float = (1f - progress / 0.72f).coerceIn(0f, 1f)

/** 固定标题从 72% 折叠进度开始淡入。 */
private fun collapsedTitleAlpha(progress: Float): Float = ((progress - 0.72f) / 0.28f).coerceIn(0f, 1f)

/** 首屏入场：延迟 [order] 个节拍后弹入，[play] 为 false 时直接呈现（配方同资料库页） */
@Composable
private fun rememberEntrance(
    order: Int,
    play: Boolean,
): Animatable<Float, AnimationVector1D> {
    val entrance = remember { Animatable(if (play) 0f else 1f) }
    if (play) {
        LaunchedEffect(Unit) {
            delay(order * ENTRANCE_STAGGER_MILLIS)
            entrance.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = 380f,
                    ),
            )
        }
    }
    return entrance
}

/** 入场位移+淡入，全部在 Draw 阶段读取动画值 */
private fun Modifier.entranceGraphics(entrance: Animatable<Float, AnimationVector1D>): Modifier =
    graphicsLayer {
        val enter = entrance.value
        alpha = enter
        translationY = (1f - enter) * 28.dp.toPx()
    }

/** 按压回弹缩放值：0.95f 硬弹簧（资料库命令药丸同款） */
@Composable
private fun rememberPressScale(interactionSource: MutableInteractionSource): androidx.compose.runtime.State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = 1100f,
            ),
        label = "finderPressScale",
    )
}

/** 固定顶栏：背景与标题透明度跟随刊头收缩进度，收起后弹出迷你「搜索」药丸 */
@Composable
private fun FinderTopBar(
    listState: LazyListState,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = stableStatusBarTopPadding()
    val backgroundColor = MaterialTheme.colorScheme.background
    // 布尔量化派生状态放在顶栏自身作用域：翻转只重组顶栏，不波及页面根
    val solid by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // 容器只做绘制不挂任何点击 Modifier：透明态不得拦截刊头与搜索胶囊的触摸
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarTop + 56.dp)
                .drawBehind {
                    drawRect(color = backgroundColor.copy(alpha = mastheadCollapse(listState)))
                },
    ) {
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
                text = stringResource(R.string.finder_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            // 等大标题基本消失后再显示固定标题，避免两个“发现”叠在一起。
                            alpha = collapsedTitleAlpha(mastheadCollapse(listState))
                        },
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
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickHighlight(onClick = onSearchClick)
                            .padding(horizontal = Spacing.Medium, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.finder_search_action),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/** 刊头：大标题 + 三维统计 meta 行（数字变化时上下滚动切换） */
@Composable
private fun FinderMasthead(
    frequentCount: Int,
    favoriteCount: Int,
    playlistCount: Int,
    summaryReady: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.finder_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier.padding(top = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (summaryReady) {
                AnimatedContent(
                    targetState = Triple(frequentCount, favoriteCount, playlistCount),
                    transitionSpec = {
                        val targetSum = targetState.first + targetState.second + targetState.third
                        val initialSum = initialState.first + initialState.second + initialState.third
                        val direction = if (targetSum >= initialSum) 1 else -1
                        (
                            slideInVertically { height -> direction * height / 2 } +
                                fadeIn(tween(durationMillis = 240))
                        ) togetherWith
                            (
                                slideOutVertically { height -> -direction * height / 2 } +
                                    fadeOut(tween(durationMillis = 160))
                            ) using SizeTransform(clip = false)
                    },
                    label = "finderSummaryRoll",
                ) { (frequent, favorite, playlist) ->
                    Text(
                        text =
                            stringResource(
                                R.string.finder_summary_format,
                                frequent,
                                favorite,
                                playlist,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 冷启动查询常听记录时保留主卡的最终高度，避免先画“还没有常听歌曲”，
 * 紧接着又把下方所有分区推开，造成整页闪动。
 */
@Composable
private fun FrequentHeroPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(344.dp)
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.ExtraLarge1CornerBasedShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                        1f to Color.Transparent,
                    ),
                ).padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.finder_frequent_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " ",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            )
        }
        FrequentHeroRowsPlaceholder()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(Shapes.MediumCornerBasedShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
        )
    }
}

/** 搜索胶囊：52dp 圆胶囊 + 按压回弹，页面的首要动作 */
@Composable
private fun SearchCapsule(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }.clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickHighlight(interactionSource = interactionSource, onClick = onClick)
                    .padding(horizontal = Spacing.Large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.finder_search_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 常听榜单主卡：默认展示前三首，点按卡片空白区域展开全部歌曲，再次点按收起。
 * 播放键、歌曲行和存为歌单按钮保留各自独立动作。
 */
@Composable
private fun FrequentHeroCard(
    title: String,
    songs: ImmutableList<FrequentEntry>,
    onPlayAll: () -> Unit,
    onSongClick: (FrequentEntry) -> Unit,
    onSaveAsPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable(title) { mutableStateOf(false) }
    val visibleSongs = if (isExpanded) songs else songs.take(3)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.ExtraLarge1CornerBasedShape)
                .animateContentSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ).clickHighlight(onClick = { isExpanded = !isExpanded })
                .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.songs_count_format, songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val interactionSource = remember { MutableInteractionSource() }
            val pressScale by rememberPressScale(interactionSource)
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickHighlight(interactionSource = interactionSource, onClick = onPlayAll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.play_all),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column {
            visibleSongs.forEachIndexed { index, song ->
                HeroSongRow(
                    index = index,
                    song = song,
                    onClick = { onSongClick(song) },
                    retainedPainter =
                        remember(song.stableId) {
                            ArtworkRenderCache.read(song.artworkUri?.toString())
                        },
                    onArtworkReady = {
                        ArtworkRenderCache.write(
                            key = song.artworkUri?.toString(),
                            fallbackKey = song.fallbackArtworkUri?.toString(),
                        )
                    },
                    onArtworkFailed = {},
                )
            }
        }
        FinderActionPill(
            text = stringResource(R.string.finder_save_as_playlist),
            icon = Icons.Default.Add,
            onClick = onSaveAsPlaylist,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 榜单行：名次 + 封面 + 歌名/歌手 + 时长 */
@Composable
private fun HeroSongRow(
    index: Int,
    song: FrequentEntry,
    onClick: () -> Unit,
    retainedPainter: Painter?,
    onArtworkReady: (Painter) -> Unit,
    onArtworkFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Shapes.LargeCornerBasedShape)
                .clickHighlight(onClick = onClick)
                .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color =
                if (index == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.width(24.dp),
            textAlign = TextAlign.Center,
        )
        StableAudioCover(
            uri = song.artworkUri,
            fallbackUri = song.fallbackArtworkUri,
            retainedPainter = retainedPainter,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(Shapes.MediumCornerBasedShape),
            placeHolder = { CoverPlaceholder() },
            onPainterReady = onArtworkReady,
            onPainterFailed = onArtworkFailed,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = song.durationMs.asDurationLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FrequentHeroRowsPlaceholder(
    rowCount: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        repeat(rowCount) { index ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.width(24.dp),
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(Shapes.MediumCornerBasedShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.62f)
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.42f)
                                .height(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                    )
                }
            }
        }
    }
}

/** 收藏预览卡：静置容器色，视觉上从属于常听主卡 */
@Composable
private fun FavoritesCard(
    songs: ImmutableList<Song>,
    onPlayAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSaveAsPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            songs.forEach { song ->
                FavoriteSongRow(
                    song = song,
                    onClick = { onSongClick(song) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            FinderActionPill(
                text = stringResource(R.string.play_all),
                icon = Icons.Default.PlayArrow,
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
            )
            FinderActionPill(
                text = stringResource(R.string.finder_save_as_playlist),
                icon = Icons.Default.Add,
                onClick = onSaveAsPlaylist,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 收藏行：封面 + 歌名/歌手 + 时长 */
@Composable
private fun FavoriteSongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Shapes.LargeCornerBasedShape)
                .clickHighlight(onClick = onClick)
                .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        AudioCover(
            uri = song.getCoverUri(),
            fallbackUri = song.getAlbumCoverUri(),
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(Shapes.MediumCornerBasedShape),
            placeHolder = { CoverPlaceholder() },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = song.getFormattedDuration(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End,
        )
    }
}

/** 动作药丸：次级容器色 + 按压回弹（收藏/常听卡内的成对动作） */
@Composable
private fun FinderActionPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(Shapes.MediumCornerBasedShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickHighlight(interactionSource = interactionSource, onClick = onClick)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                Spacing.ExtraSmall,
                Alignment.CenterHorizontally,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 歌单横滑列：通栏出血，卡片可越过屏幕边缘 */
@Composable
private fun PlaylistRail(
    playlists: List<PlaylistWithCover>,
    onPlaylistClick: (PlaylistWithCover) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        overscrollEffect = rememberIOSOverScrollEffect(orientation = Orientation.Horizontal),
    ) {
        items(
            items = playlists,
            key = {
                it.playlist.playlistId ?: it.playlist.playlistName
                    .hashCode()
                    .toLong()
            },
            contentType = { "playlist" },
        ) { item ->
            FinderPlaylistCard(
                item = item,
                onClick = { onPlaylistClick(item) },
                modifier =
                    Modifier.animateItem(
                        fadeInSpec = ListItemFadeInSpec,
                        placementSpec = ItemPlacementSpec,
                        fadeOutSpec = ListItemFadeOutSpec,
                    ),
            )
        }
    }
}

/**
 * Shows the last confirmed cover synchronously across process restarts, then refreshes the real
 * image above it. A changed URL crossfades in and replaces the snapshot; an unchanged URL is not
 * written again.
 */
@Composable
private fun PersistentFinderArtwork(
    cacheKey: String,
    uri: Uri?,
    modifier: Modifier = Modifier,
    fallbackUri: Uri? = null,
    placeHolder: @Composable () -> Unit = {},
) {
    val retainedPainter = remember(cacheKey) { ArtworkRenderCache.read(cacheKey) }
    StableAudioCover(
        uri = uri,
        fallbackUri = fallbackUri,
        retainedPainter = retainedPainter,
        modifier = modifier,
        placeHolder = placeHolder,
        onPainterReady = {
            ArtworkRenderCache.writeSnapshot(
                cacheKey = cacheKey,
                sourceKey = uri?.toString(),
                fallbackSourceKey = fallbackUri?.toString(),
            )
        },
    )
}

@Composable
private fun CloudPlaylistRail(
    playlists: List<CloudCatalogPlaylist>,
    onPlaylistClick: (CloudCatalogPlaylist) -> Unit,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Default.LibraryMusic,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        overscrollEffect = rememberIOSOverScrollEffect(orientation = Orientation.Horizontal),
    ) {
        items(
            items = playlists,
            key = CloudCatalogPlaylist::stableId,
            contentType = { "cloud_playlist" },
        ) { item ->
            Column(
                modifier =
                    Modifier
                        .width(148.dp)
                        .clip(Shapes.ExtraLargeCornerBasedShape)
                        .clickHighlight(onClick = { onPlaylistClick(item) }),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                PersistentFinderArtwork(
                    cacheKey = "finder_cloud_playlist:${item.stableId}",
                    uri = item.playlist.coverUrl?.let(Uri::parse),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(Shapes.ExtraLargeCornerBasedShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    placeHolder = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = placeholderIcon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
                Column(
                    modifier = Modifier.padding(bottom = Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            "${if (item.account.provider == RemoteMusicProvider.NETEASE) "网易云" else "QQ 音乐"} · " +
                                "${item.playlist.songCount} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun NeteaseSceneMusicRail(
    scenes: List<NeteaseSceneMusic>,
    onSceneClick: (NeteaseSceneMusic) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        overscrollEffect = rememberIOSOverScrollEffect(orientation = Orientation.Horizontal),
    ) {
        items(
            items = scenes,
            key = NeteaseSceneMusic::query,
            contentType = { "netease_scene" },
        ) { scene ->
            val containerColor =
                when (scene.colorIndex) {
                    0 -> MaterialTheme.colorScheme.primaryContainer
                    1 -> MaterialTheme.colorScheme.secondaryContainer
                    2 -> MaterialTheme.colorScheme.tertiaryContainer
                    3 -> MaterialTheme.colorScheme.surfaceContainerHighest
                    4 -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                }
            val contentColor =
                when (scene.colorIndex) {
                    0 -> MaterialTheme.colorScheme.onPrimaryContainer
                    1 -> MaterialTheme.colorScheme.onSecondaryContainer
                    2 -> MaterialTheme.colorScheme.onTertiaryContainer
                    3 -> MaterialTheme.colorScheme.onSurface
                    4 -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.primary
                }
            Column(
                modifier =
                    Modifier
                        .width(148.dp)
                        .clip(Shapes.ExtraLargeCornerBasedShape)
                        .clickHighlight(onClick = { onSceneClick(scene) }),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                PersistentFinderArtwork(
                    cacheKey = "finder_netease_scene:${scene.query}",
                    uri = scene.artworkUri,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(Shapes.ExtraLargeCornerBasedShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(containerColor, containerColor.copy(alpha = 0.58f)),
                                ),
                            ),
                    placeHolder = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = scene.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(46.dp),
                            )
                        }
                    },
                )
                Column(
                    modifier = Modifier.padding(bottom = Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = scene.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.netease_scene_recommendation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudUserPlaylistRail(
    playlists: List<CloudUserPlaylist>,
    onPlaylistClick: (CloudUserPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        overscrollEffect = rememberIOSOverScrollEffect(orientation = Orientation.Horizontal),
    ) {
        items(
            items = playlists,
            key = CloudUserPlaylist::id,
            contentType = { "user_cloud_playlist" },
        ) { item ->
            Column(
                modifier =
                    Modifier
                        .width(148.dp)
                        .clip(Shapes.ExtraLargeCornerBasedShape)
                        .clickHighlight(onClick = { onPlaylistClick(item) }),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                val artworkUri =
                    item.songs
                        .firstOrNull()
                        ?.artworkUrl
                        ?.let(Uri::parse)
                PersistentFinderArtwork(
                    cacheKey = "finder_user_cloud_playlist:${item.id}",
                    uri =
                    artworkUri,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(Shapes.ExtraLargeCornerBasedShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    placeHolder = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LibraryMusic, null)
                        }
                    },
                )
                Column(
                    modifier = Modifier.padding(bottom = Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "本地云端歌单 · ${item.songs.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 歌单卡：马赛克封面 + 名称 + 歌曲数，无容器底色（资料库歌单卡同款） */
@Composable
private fun FinderPlaylistCard(
    item: PlaylistWithCover,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlistCacheKey =
        remember(item.playlist.playlistId, item.playlist.playlistName) {
            "finder_local_playlist:${item.playlist.playlistId ?: item.playlist.playlistName}"
        }
    var retainedAlbumIds by remember(playlistCacheKey) {
        mutableStateOf(PlaylistCoverSnapshotCache.read(playlistCacheKey))
    }
    val freshAlbumIds = item.coverAlbumIds.toList()
    LaunchedEffect(freshAlbumIds, item.songCount, playlistCacheKey) {
        when {
            freshAlbumIds.isNotEmpty() -> {
                if (freshAlbumIds != retainedAlbumIds) retainedAlbumIds = freshAlbumIds
                PlaylistCoverSnapshotCache.write(playlistCacheKey, freshAlbumIds)
            }

            item.songCount == 0 -> {
                retainedAlbumIds = emptyList()
                PlaylistCoverSnapshotCache.clear(playlistCacheKey)
            }
        }
    }
    val displayedAlbumIds =
        when {
            freshAlbumIds.isNotEmpty() -> freshAlbumIds
            item.songCount > 0 -> retainedAlbumIds
            else -> emptyList()
        }
    Column(
        modifier =
            modifier
                .width(148.dp)
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .clickHighlight(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Crossfade(
            targetState = displayedAlbumIds,
            animationSpec = tween(durationMillis = 180),
            label = "playlistCoverSnapshot",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(Shapes.ExtraLargeCornerBasedShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) { albumIds ->
            PlaylistCoverView(
                albumIds = albumIds,
                modifier = Modifier.fillMaxSize(),
            )
        }
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

/** 空态行：开放排版无卡片（资料库空态同款，本页不加浮动动画保持克制） */
@Composable
private fun FinderEmptyRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.LargeCornerBasedShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickHighlight(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 分区头：标题 + 计数 meta + 可选「更多」胶囊 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onActionClick != null) {
            Row(
                modifier =
                    Modifier
                        .padding(start = Spacing.Small)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickHighlight(onClick = onActionClick)
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 快捷入口行：通栏无卡片，44dp 圆形徽章按分区着色（资料库目录行同款） */
@Composable
private fun UtilityEntryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Medium)
                .clip(Shapes.LargeCornerBasedShape)
                .clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
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
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
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

/** 封面加载失败的占位：容器高色 + 音符图标 */
@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = stringResource(R.string.cover_placeholder),
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
