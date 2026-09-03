@file:Suppress("FunctionName")

package me.spica27.spicamusic.ui.home.page

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.navkit.popup.PopupMenuAnchorState
import me.spica27.navkit.popup.popupMenuAnchor
import me.spica27.navkit.popup.rememberPopupMenuAnchorState
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.CatalogQueueItem
import me.spica27.spicamusic.cloud.CloudCatalogSong
import me.spica27.spicamusic.cloud.CloudMusicCatalogViewModel
import me.spica27.spicamusic.cloud.CloudSongSource
import me.spica27.spicamusic.common.entity.Album
import me.spica27.spicamusic.common.entity.Artist
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.common.entity.getAlbumCoverUri
import me.spica27.spicamusic.common.entity.getCoverUri
import me.spica27.spicamusic.ui.albumdetail.AlbumDetailScene
import me.spica27.spicamusic.ui.artistdetail.ArtistDetailScene
import me.spica27.spicamusic.ui.dialog.CloudSongMenuScene
import me.spica27.spicamusic.ui.dialog.SongMenuScene
import me.spica27.spicamusic.ui.dialog.SortMenuOption
import me.spica27.spicamusic.ui.dialog.SortMenuScene
import me.spica27.spicamusic.ui.home.HomeViewModel
import me.spica27.spicamusic.ui.home.LocalBottomBarScrollConnection
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.scan.ScannerScene
import me.spica27.spicamusic.ui.settings.MediaLibrarySourceViewModel
import me.spica27.spicamusic.ui.settings.ScanState
import me.spica27.spicamusic.ui.theme.EaseOutEmphasized
import me.spica27.spicamusic.ui.theme.LayoutTokens
import me.spica27.spicamusic.ui.theme.ListItemFadeInSpec
import me.spica27.spicamusic.ui.theme.ListItemFadeOutSpec
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.AudioCover
import me.spica27.spicamusic.ui.widget.clickHighlight
import me.spica27.spicamusic.ui.widget.combinedClickHighlight
import me.spica27.spicamusic.ui.widget.rememberIOSOverScrollEffect
import org.koin.compose.viewmodel.koinActivityViewModel
import java.util.concurrent.TimeUnit

private val MastheadCollapseDistance = 140.dp

private const val ENTRANCE_STAGGER_MILLIS = 55L

@Immutable
private enum class MusicBrowserTab(
    val titleRes: Int,
    val countRes: Int,
    val searchHintRes: Int,
    val icon: ImageVector,
) {
    Songs(
        titleRes = R.string.music_tab_songs,
        countRes = R.string.music_tab_songs_count,
        searchHintRes = R.string.music_search_songs_hint,
        icon = Icons.Default.MusicNote,
    ),
    Albums(
        titleRes = R.string.music_tab_albums,
        countRes = R.string.music_tab_albums_count,
        searchHintRes = R.string.music_search_albums_hint,
        icon = Icons.Default.Album,
    ),
    Artists(
        titleRes = R.string.music_tab_artists,
        countRes = R.string.music_tab_artists_count,
        searchHintRes = R.string.music_search_artists_hint,
        icon = Icons.Default.Person,
    ),
    Daily(
        titleRes = R.string.music_tab_daily,
        countRes = R.string.music_tab_daily_count,
        searchHintRes = R.string.music_search_daily_hint,
        icon = Icons.Default.Today,
    ),
}

@Immutable
private enum class SongLibrarySource(
    val titleRes: Int,
    val cloudSource: CloudSongSource? = null,
    val icon: ImageVector,
) {
    All(R.string.music_source_all, icon = Icons.Default.AllInclusive),
    Local(R.string.music_source_local, icon = Icons.Default.Smartphone),
    Telegram(R.string.music_source_telegram, CloudSongSource.TELEGRAM, Icons.Default.Cloud),
    Jellyfin(R.string.music_source_jellyfin, CloudSongSource.JELLYFIN, Icons.Default.Storage),
    Emby(R.string.music_source_emby, CloudSongSource.EMBY, Icons.Default.Storage),
    Subsonic(R.string.music_source_subsonic, CloudSongSource.SUBSONIC, Icons.Default.Storage),
    Netease(R.string.music_source_netease, CloudSongSource.NETEASE, Icons.Default.Cloud),
    QqMusic(R.string.music_source_qq, CloudSongSource.QQ_MUSIC, Icons.Default.Cloud),
}

@Immutable
private sealed interface BrowserSongItem {
    val stableId: String
    val title: String
    val artist: String
    val album: String
    val durationMs: Long
    val artworkUri: Uri?
    val fallbackArtworkUri: Uri?
    val source: CloudSongSource?

    data class Local(
        val song: Song,
    ) : BrowserSongItem {
        override val stableId = "local:${song.mediaStoreId}"
        override val title = song.displayName
        override val artist = song.artist
        override val album = song.album
        override val durationMs = song.duration
        override val artworkUri = song.getCoverUri()
        override val fallbackArtworkUri = song.getAlbumCoverUri()
        override val source: CloudSongSource? = null
    }

    data class Cloud(
        val song: CloudCatalogSong,
    ) : BrowserSongItem {
        override val stableId = song.stableId
        override val title = song.title
        override val artist = song.artist
        override val album = song.album
        override val durationMs = song.durationMs
        override val artworkUri = song.artworkUri
        override val fallbackArtworkUri: Uri? = null
        override val source = song.source
    }
}

private data class SongRowSubtitle(
    val artist: String,
    val album: String,
)

@Immutable
private data class BrowserAlbumItem(
    val stableId: String,
    val title: String,
    val artist: String,
    val numberOfSongs: Int,
    val artworkUri: Uri?,
    val localAlbum: Album? = null,
    val source: CloudSongSource? = null,
)

@Immutable
private data class BrowserArtistItem(
    val stableId: String,
    val name: String,
    val songCount: Int,
    val artworkUri: Uri?,
    val localArtist: Artist? = null,
    val source: CloudSongSource? = null,
)

// ──────────────────────────────────────────────────────────────────────────
// 各 Tab 的排序方式
// ──────────────────────────────────────────────────────────────────────────

@Immutable
private enum class SongSortMode(
    val option: SortMenuOption,
    val comparator: Comparator<Song>,
) {
    TitleAsc(
        SortMenuOption("title_asc", R.string.sort_song_title_az, Icons.Default.SortByAlpha),
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName },
    ),
    TitleDesc(
        SortMenuOption("title_desc", R.string.sort_song_title_za, Icons.Default.SortByAlpha),
        compareBy(String.CASE_INSENSITIVE_ORDER, Song::displayName).reversed(),
    ),
    ArtistAsc(
        SortMenuOption("artist_asc", R.string.sort_song_artist_az, Icons.Default.Person),
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist },
    ),
    ArtistDesc(
        SortMenuOption("artist_desc", R.string.sort_song_artist_za, Icons.Default.Person),
        compareBy(String.CASE_INSENSITIVE_ORDER, Song::artist).reversed(),
    ),
    DurationAsc(
        SortMenuOption("duration_asc", R.string.sort_song_duration_asc, Icons.Default.Schedule),
        compareBy { it.duration },
    ),
    DurationDesc(
        SortMenuOption("duration_desc", R.string.sort_song_duration_desc, Icons.Default.Schedule),
        compareByDescending { it.duration },
    ),
}

private fun SongSortMode.browserComparator(): Comparator<BrowserSongItem> =
    when (this) {
        SongSortMode.TitleAsc ->
            compareBy(String.CASE_INSENSITIVE_ORDER, BrowserSongItem::title)
        SongSortMode.TitleDesc ->
            compareBy(String.CASE_INSENSITIVE_ORDER, BrowserSongItem::title).reversed()
        SongSortMode.ArtistAsc ->
            compareBy(String.CASE_INSENSITIVE_ORDER, BrowserSongItem::artist)
        SongSortMode.ArtistDesc ->
            compareBy(String.CASE_INSENSITIVE_ORDER, BrowserSongItem::artist).reversed()
        SongSortMode.DurationAsc -> compareBy(BrowserSongItem::durationMs)
        SongSortMode.DurationDesc -> compareByDescending(BrowserSongItem::durationMs)
    }

private fun List<BrowserSongItem>.filterBrowserSongsBy(query: String): List<BrowserSongItem> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter {
        it.title.contains(normalized, ignoreCase = true) ||
            it.artist.contains(normalized, ignoreCase = true) ||
            it.album.contains(normalized, ignoreCase = true)
    }
}

private fun CloudSongSource.filter(): SongLibrarySource =
    when (this) {
        CloudSongSource.TELEGRAM -> SongLibrarySource.Telegram
        CloudSongSource.JELLYFIN -> SongLibrarySource.Jellyfin
        CloudSongSource.EMBY -> SongLibrarySource.Emby
        CloudSongSource.SUBSONIC -> SongLibrarySource.Subsonic
        CloudSongSource.NETEASE -> SongLibrarySource.Netease
        CloudSongSource.QQ_MUSIC -> SongLibrarySource.QqMusic
    }

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs.coerceAtLeast(0L))
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0L)) % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Immutable
private enum class AlbumSortMode(
    val option: SortMenuOption,
) {
    TitleAsc(
        SortMenuOption("title_asc", R.string.sort_album_title_az, Icons.Default.SortByAlpha),
    ),
    TitleDesc(
        SortMenuOption("title_desc", R.string.sort_album_title_za, Icons.Default.SortByAlpha),
    ),
    ArtistAsc(
        SortMenuOption("artist_asc", R.string.sort_album_artist_az, Icons.Default.Person),
    ),
    ArtistDesc(
        SortMenuOption("artist_desc", R.string.sort_album_artist_za, Icons.Default.Person),
    ),
    CountDesc(
        SortMenuOption("count_desc", R.string.sort_album_count_desc, Icons.Default.FormatListNumbered),
    ),
    CountAsc(
        SortMenuOption("count_asc", R.string.sort_album_count_asc, Icons.Default.FormatListNumbered),
    ),
}

@Immutable
private enum class ArtistSortMode(
    val option: SortMenuOption,
) {
    NameAsc(
        SortMenuOption("name_asc", R.string.sort_artist_name_az, Icons.Default.SortByAlpha),
    ),
    NameDesc(
        SortMenuOption("name_desc", R.string.sort_artist_name_za, Icons.Default.SortByAlpha),
    ),
    CountDesc(
        SortMenuOption("count_desc", R.string.sort_artist_count_desc, Icons.Default.FormatListNumbered),
    ),
    CountAsc(
        SortMenuOption("count_asc", R.string.sort_artist_count_asc, Icons.Default.FormatListNumbered),
    ),
}

private fun AlbumSortMode.browserComparator(): Comparator<BrowserAlbumItem> =
    when (this) {
        AlbumSortMode.TitleAsc -> compareBy(String.CASE_INSENSITIVE_ORDER, BrowserAlbumItem::title)
        AlbumSortMode.TitleDesc -> compareBy(String.CASE_INSENSITIVE_ORDER, BrowserAlbumItem::title).reversed()
        AlbumSortMode.ArtistAsc -> compareBy(String.CASE_INSENSITIVE_ORDER, BrowserAlbumItem::artist)
        AlbumSortMode.ArtistDesc -> compareBy(String.CASE_INSENSITIVE_ORDER, BrowserAlbumItem::artist).reversed()
        AlbumSortMode.CountDesc -> compareByDescending(BrowserAlbumItem::numberOfSongs)
        AlbumSortMode.CountAsc -> compareBy(BrowserAlbumItem::numberOfSongs)
    }

private fun ArtistSortMode.browserComparator(): Comparator<BrowserArtistItem> =
    when (this) {
        ArtistSortMode.NameAsc -> compareBy(String.CASE_INSENSITIVE_ORDER, BrowserArtistItem::name)
        ArtistSortMode.NameDesc -> compareBy(String.CASE_INSENSITIVE_ORDER, BrowserArtistItem::name).reversed()
        ArtistSortMode.CountDesc -> compareByDescending(BrowserArtistItem::songCount)
        ArtistSortMode.CountAsc -> compareBy(BrowserArtistItem::songCount)
    }

@Composable
fun MusicPage() {
    val path = LocalNavigationPath.current
    val homeViewModel: HomeViewModel = koinActivityViewModel()
    val cloudCatalogViewModel: CloudMusicCatalogViewModel = koinActivityViewModel()
    LaunchedEffect(cloudCatalogViewModel) {
        cloudCatalogViewModel.connectTelegramIfConfigured()
    }
    val mediaLibraryViewModel: MediaLibrarySourceViewModel = koinActivityViewModel()
    val playerViewModel = LocalPlayerViewModel.current

    val allSongs by homeViewModel.allSongs.collectAsStateWithLifecycle()
    val cloudCatalog by cloudCatalogViewModel.state.collectAsStateWithLifecycle()
    val localScanState by mediaLibraryViewModel.scanState.collectAsStateWithLifecycle()
    val currentMediaItem by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
    var pendingPlayerMediaId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingPlayerMediaId) {
        val expectedMediaId = pendingPlayerMediaId ?: return@LaunchedEffect
        playerViewModel.currentMediaItem.first { it?.mediaId == expectedMediaId }
        if (pendingPlayerMediaId == expectedMediaId) {
            pendingPlayerMediaId = null
            homeViewModel.expandPlayer()
        }
    }
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)

    val localAlbums =
        remember(allSongs, unknownAlbum, unknownArtist) {
            allSongs.toAlbums(unknownAlbum = unknownAlbum, unknownArtist = unknownArtist)
        }
    val localArtists =
        remember(allSongs, unknownArtist) {
            allSongs.toArtists(unknownArtist = unknownArtist)
        }

    var selectedTab by rememberSaveable { mutableStateOf(MusicBrowserTab.Songs) }
    var selectedSource by rememberSaveable { mutableStateOf(SongLibrarySource.All) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var songSortMode by rememberSaveable { mutableStateOf(SongSortMode.TitleAsc) }
    var albumSortMode by rememberSaveable { mutableStateOf(AlbumSortMode.TitleAsc) }
    var artistSortMode by rememberSaveable { mutableStateOf(ArtistSortMode.NameAsc) }
    var playEntrance by remember { mutableStateOf(true) }
    var playlistEntrance by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (playEntrance) {
            delay(1400)
            playEntrance = false
        }
    }

    LaunchedEffect(playlistEntrance) {
        if (playlistEntrance) {
            delay(55)
            playlistEntrance = false
        }
    }

    val availableSources =
        remember(cloudCatalog.availableSources) {
            buildList {
                add(SongLibrarySource.All)
                add(SongLibrarySource.Local)
                SongLibrarySource.entries
                    .filter { it.cloudSource in cloudCatalog.availableSources }
                    .forEach(::add)
            }
        }
    LaunchedEffect(availableSources) {
        if (selectedSource !in availableSources) selectedSource = SongLibrarySource.All
        if (
            selectedTab == MusicBrowserTab.Daily &&
            CloudSongSource.NETEASE !in cloudCatalog.availableSources
        ) {
            selectedTab = MusicBrowserTab.Songs
        }
    }
    val allBrowserSongs =
        remember(allSongs, cloudCatalog.songs) {
            buildList {
                allSongs.forEach { add(BrowserSongItem.Local(it)) }
                cloudCatalog.songs.forEach { add(BrowserSongItem.Cloud(it)) }
            }
        }
    val browserSongs =
        remember(allBrowserSongs, selectedSource) {
            allBrowserSongs.filter {
                when (selectedSource) {
                    SongLibrarySource.All -> true
                    SongLibrarySource.Local -> it.source == null
                    else -> it.source == selectedSource.cloudSource
                }
            }
        }
    val albums =
        remember(allBrowserSongs, localAlbums, unknownAlbum, unknownArtist) {
            allBrowserSongs.toBrowserAlbums(localAlbums, unknownAlbum, unknownArtist)
        }
    val artists =
        remember(allBrowserSongs, localArtists, unknownArtist) {
            allBrowserSongs.toBrowserArtists(localArtists, unknownArtist)
        }
    val filteredSongs =
        remember(browserSongs, searchQuery, songSortMode) {
            browserSongs
                .filterBrowserSongsBy(searchQuery)
                .sortedWith(songSortMode.browserComparator())
        }
    val filteredDailySongs =
        remember(cloudCatalog.dailyRecommendations, searchQuery, songSortMode) {
            cloudCatalog.dailyRecommendations
                .map(BrowserSongItem::Cloud)
                .filterBrowserSongsBy(searchQuery)
                .sortedWith(songSortMode.browserComparator())
        }
    val displayedBrowserSongs =
        if (selectedTab == MusicBrowserTab.Daily) filteredDailySongs else filteredSongs
    val knownCloudSongCount = cloudCatalog.songCounts.values.sum()
    val knownAllSongCount = allSongs.size + knownCloudSongCount
    val displayedSongCount =
        if (searchQuery.isNotBlank()) {
            filteredSongs.size
        } else {
            when (selectedSource) {
                SongLibrarySource.All -> knownAllSongCount
                SongLibrarySource.Local -> allSongs.size
                else ->
                    selectedSource.cloudSource
                        ?.let(cloudCatalog.songCounts::get)
                        ?: filteredSongs.size
            }
        }
    val visibleQueue =
        remember(displayedBrowserSongs) {
            displayedBrowserSongs.map {
                when (it) {
                    is BrowserSongItem.Local -> CatalogQueueItem.Local(it.song)
                    is BrowserSongItem.Cloud -> CatalogQueueItem.Cloud(it.song)
                }
            }
        }
    val filteredAlbums =
        remember(albums, searchQuery, albumSortMode) {
            albums
                .filterBrowserAlbumsBy(searchQuery)
                .sortedWith(albumSortMode.browserComparator())
        }
    val filteredArtists =
        remember(artists, searchQuery, artistSortMode) {
            artists
                .filterBrowserArtistsBy(searchQuery)
                .sortedWith(artistSortMode.browserComparator())
        }

    val listState = rememberLazyListState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // 排序菜单锚点：挂在页面作用域，锚点图标本身在 Lazy item 内
    val sortAnchor = rememberPopupMenuAnchorState()
    val sourceAnchor = rememberPopupMenuAnchorState()

    fun openSourceMenu() {
        if (sourceAnchor.isOpen) return
        path.push(
            SortMenuScene(
                anchorState = sourceAnchor,
                anchorIcon = selectedSource.icon,
                options =
                    availableSources.map {
                        SortMenuOption(it.name, it.titleRes, it.icon)
                    },
                selectedId = selectedSource.name,
                onSelect = { id ->
                    SongLibrarySource.entries.firstOrNull { it.name == id }?.let {
                        selectedSource = it
                        playlistEntrance = true
                    }
                },
                titleRes = R.string.music_source_selector_cd,
            ),
        )
    }

    fun openSortMenu() {
        if (sortAnchor.isOpen) return
        val scene =
            when (selectedTab) {
                MusicBrowserTab.Songs ->
                    SortMenuScene(
                        anchorState = sortAnchor,
                        anchorIcon = Icons.AutoMirrored.Filled.Sort,
                        options = SongSortMode.entries.map { it.option },
                        selectedId = songSortMode.option.id,
                        onSelect = { id ->
                            SongSortMode.entries
                                .firstOrNull { it.option.id == id }
                                ?.let { songSortMode = it }
                        },
                    )

                MusicBrowserTab.Daily ->
                    SortMenuScene(
                        anchorState = sortAnchor,
                        anchorIcon = Icons.AutoMirrored.Filled.Sort,
                        options = SongSortMode.entries.map { it.option },
                        selectedId = songSortMode.option.id,
                        onSelect = { id ->
                            SongSortMode.entries
                                .firstOrNull { it.option.id == id }
                                ?.let { songSortMode = it }
                        },
                    )

                MusicBrowserTab.Albums ->
                    SortMenuScene(
                        anchorState = sortAnchor,
                        anchorIcon = Icons.AutoMirrored.Filled.Sort,
                        options = AlbumSortMode.entries.map { it.option },
                        selectedId = albumSortMode.option.id,
                        onSelect = { id ->
                            AlbumSortMode.entries
                                .firstOrNull { it.option.id == id }
                                ?.let { albumSortMode = it }
                        },
                    )

                MusicBrowserTab.Artists ->
                    SortMenuScene(
                        anchorState = sortAnchor,
                        anchorIcon = Icons.AutoMirrored.Filled.Sort,
                        options = ArtistSortMode.entries.map { it.option },
                        selectedId = artistSortMode.option.id,
                        onSelect = { id ->
                            ArtistSortMode.entries
                                .firstOrNull { it.option.id == id }
                                ?.let { artistSortMode = it }
                        },
                    )
            }
        path.push(scene)
    }
    // 用户开始滚动结果时自动收起键盘，把屏幕还给内容
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
    }
    LaunchedEffect(listState, selectedTab, selectedSource) {
        if (selectedTab != MusicBrowserTab.Songs || selectedSource == SongLibrarySource.Local) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 10
        }.distinctUntilChanged()
            .filter { it }
            .collect {
                cloudCatalogViewModel.loadMore(selectedSource.cloudSource)
            }
    }
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
            overscrollEffect = rememberIOSOverScrollEffect(Orientation.Vertical),
        ) {
            item(key = "masthead", contentType = "masthead") {
                val entrance = rememberEntrance(order = 0, play = playEntrance)
                MusicMasthead(
                    songsCount = knownAllSongCount,
                    albumsCount = albums.size,
                    artistsCount = artists.size,
                    modifier =
                        Modifier
                            .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                            .padding(top = Spacing.Large)
                            .graphicsLayer {
                                val t = mastheadCollapse(listState)
                                val enter = entrance.value
                                transformOrigin = TransformOrigin(0f, 0f)
                                alpha = (1f - t) * enter
                                translationY = -t * 16.dp.toPx() + (1f - enter) * 28.dp.toPx()
                                scaleX = 1f - 0.18f * t
                                scaleY = 1f - 0.18f * t
                            },
                )
            }
            item(key = "tabs", contentType = "tabs") {
                val entrance = rememberEntrance(order = 1, play = playEntrance)
                MusicTabStrip(
                    selectedTab = selectedTab,
                    songsCount = knownAllSongCount,
                    albumsCount = albums.size,
                    artistsCount = artists.size,
                    dailyCount = cloudCatalog.dailyRecommendations.size,
                    showDaily = CloudSongSource.NETEASE in cloudCatalog.availableSources,
                    onSelect = {
                        selectedTab = it
                        searchQuery = ""
                        playlistEntrance = true
                    },
                    modifier =
                        Modifier
                            .animateItem(
                                fadeInSpec =
                                ListItemFadeInSpec,
                                placementSpec = null,
                                fadeOutSpec = ListItemFadeOutSpec,
                            ).entranceGraphics(entrance),
                )
            }

            item(key = "search", contentType = "search") {
                val entrance = rememberEntrance(order = 2, play = playEntrance)
                MusicSearchBar(
                    query = searchQuery,
                    hint = stringResource(selectedTab.searchHintRes),
                    onQueryChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                    modifier =
                        Modifier
                            .animateItem(
                                fadeInSpec =
                                ListItemFadeInSpec,
                                placementSpec = null,
                                fadeOutSpec = ListItemFadeOutSpec,
                            ).entranceGraphics(entrance),
                )
            }

            item(key = "section_header", contentType = "section_header") {
                MusicSectionHeader(
                    tab = selectedTab,
                    count =
                        when (selectedTab) {
                            MusicBrowserTab.Songs -> displayedSongCount
                            MusicBrowserTab.Albums -> filteredAlbums.size
                            MusicBrowserTab.Artists -> filteredArtists.size
                            MusicBrowserTab.Daily -> filteredDailySongs.size
                        },
                    sortAnchor = sortAnchor,
                    onSortClick = ::openSortMenu,
                    sourceAnchor = sourceAnchor,
                    sourceIcon = selectedSource.icon,
                    showSourceSelector = selectedTab == MusicBrowserTab.Songs,
                    onSourceClick = ::openSourceMenu,
                    showRefresh =
                        selectedTab == MusicBrowserTab.Songs ||
                            selectedTab == MusicBrowserTab.Daily,
                    refreshing =
                        when {
                            selectedTab == MusicBrowserTab.Songs &&
                                selectedSource == SongLibrarySource.All ->
                                localScanState is ScanState.Scanning || cloudCatalog.isRefreshing
                            selectedTab == MusicBrowserTab.Songs &&
                                selectedSource == SongLibrarySource.Local ->
                                localScanState is ScanState.Scanning
                            selectedTab == MusicBrowserTab.Daily ->
                                cloudCatalog.isLoadingDailyRecommendations
                            else -> cloudCatalog.isRefreshing
                        },
                    refreshContentDescription =
                        stringResource(
                            when {
                                selectedTab == MusicBrowserTab.Songs &&
                                    selectedSource == SongLibrarySource.All ->
                                    R.string.music_refresh_all_cd
                                selectedTab == MusicBrowserTab.Songs &&
                                    selectedSource == SongLibrarySource.Local ->
                                    R.string.music_refresh_local_cd
                                else -> R.string.music_refresh_cloud_cd
                            },
                        ),
                    onRefresh = {
                        when {
                            selectedTab == MusicBrowserTab.Songs &&
                                selectedSource == SongLibrarySource.All -> {
                                mediaLibraryViewModel.startFullScan()
                                cloudCatalogViewModel.refreshCatalog()
                            }
                            selectedTab == MusicBrowserTab.Songs &&
                                selectedSource == SongLibrarySource.Local ->
                                mediaLibraryViewModel.startFullScan()
                            selectedTab == MusicBrowserTab.Daily ->
                                cloudCatalogViewModel.refreshDailyRecommendations(forceRefresh = true)
                            else -> cloudCatalogViewModel.refreshCatalog()
                        }
                    },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = ListItemFadeInSpec,
                            placementSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                ),
                            fadeOutSpec = ListItemFadeOutSpec,
                        ),
                )
            }

            when (selectedTab) {
                MusicBrowserTab.Songs,
                MusicBrowserTab.Daily,
                -> {
                    if (displayedBrowserSongs.isEmpty()) {
                        item(key = "songs_empty", contentType = "empty") {
                            if (
                                (selectedTab == MusicBrowserTab.Daily && cloudCatalog.isLoadingDailyRecommendations) ||
                                (
                                    selectedTab == MusicBrowserTab.Songs &&
                                        selectedSource != SongLibrarySource.Local &&
                                        cloudCatalog.loadingSources.isNotEmpty()
                                )
                            ) {
                                MusicCloudLoadingState()
                            } else {
                                MusicEmptyState(
                                    title =
                                        stringResource(
                                            if (selectedTab == MusicBrowserTab.Daily) {
                                                R.string.music_tab_daily
                                            } else if (allSongs.isEmpty() && cloudCatalog.songs.isEmpty()) {
                                                R.string.music_no_songs_title
                                            } else {
                                                R.string.music_empty_songs_title
                                            },
                                        ),
                                    subtitle =
                                        stringResource(
                                            if (selectedTab == MusicBrowserTab.Daily) {
                                                R.string.music_search_daily_hint
                                            } else if (allSongs.isEmpty() && cloudCatalog.songs.isEmpty()) {
                                                R.string.music_no_songs_subtitle
                                            } else {
                                                R.string.music_empty_songs_subtitle
                                            },
                                        ),
                                    actionLabel =
                                        stringResource(R.string.scan_local_music)
                                            .takeIf {
                                                selectedSource == SongLibrarySource.Local &&
                                                    allSongs.isEmpty()
                                            },
                                    onActionClick =
                                        { path.push(ScannerScene()) }
                                            .takeIf {
                                                selectedSource == SongLibrarySource.Local &&
                                                    allSongs.isEmpty()
                                            },
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = displayedBrowserSongs,
                            key = { _, song -> song.stableId },
                            contentType = { _, _ -> "song" },
                        ) { index, item ->
                            val entrance =
                                rememberEntrance(
                                    order = minOf(index + 4, 10),
                                    play = playlistEntrance,
                                )
                            MusicSongRow(
                                index = index,
                                title = item.title,
                                artist = item.artist,
                                album = item.album,
                                durationMs = item.durationMs,
                                artworkUri = item.artworkUri,
                                fallbackArtworkUri = item.fallbackArtworkUri,
                                sourceLabel =
                                    item.source?.let { source ->
                                        stringResource(source.filter().titleRes)
                                    },
                                isPlaying =
                                    currentMediaItem?.mediaId ==
                                        when (item) {
                                            is BrowserSongItem.Local -> item.song.mediaStoreId.toString()
                                            is BrowserSongItem.Cloud -> item.song.stableId
                                        },
                                onLongClick = {
                                    when (item) {
                                        is BrowserSongItem.Local -> path.push(SongMenuScene(item.song))
                                        is BrowserSongItem.Cloud -> path.push(CloudSongMenuScene(item.song))
                                    }
                                },
                                onClick = {
                                    pendingPlayerMediaId =
                                        when (item) {
                                            is BrowserSongItem.Local -> item.song.mediaStoreId.toString()
                                            is BrowserSongItem.Cloud -> item.song.stableId
                                        }
                                    cloudCatalogViewModel.play(
                                        selectedStableId = item.stableId,
                                        visibleQueue = visibleQueue,
                                    )
                                },
                                modifier =
                                    Modifier
                                        .animateItem(
                                            fadeInSpec =
                                            ListItemFadeInSpec,
                                            placementSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                                ),
                                            fadeOutSpec = ListItemFadeOutSpec,
                                        ).graphicsLayer {
                                            val enter = entrance.value
                                            transformOrigin = TransformOrigin(0f, 0f)
                                            alpha = enter
                                            translationY = (1f - enter) * 28.dp.toPx()
                                        },
                            )
                        }
                        if (
                            selectedTab == MusicBrowserTab.Songs &&
                            selectedSource != SongLibrarySource.Local &&
                            cloudCatalog.loadingSources.isNotEmpty()
                        ) {
                            item(key = "cloud_page_loading", contentType = "loading") {
                                MusicCloudLoadingState(compact = true)
                            }
                        }
                    }
                }

                MusicBrowserTab.Albums -> {
                    if (filteredAlbums.isEmpty()) {
                        item(key = "albums_empty", contentType = "empty") {
                            MusicEmptyState(
                                title =
                                    stringResource(
                                        if (albums.isEmpty()) {
                                            R.string.music_no_albums_title
                                        } else {
                                            R.string.music_empty_albums_title
                                        },
                                    ),
                                subtitle = stringResource(R.string.music_empty_albums_subtitle),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredAlbums,
                            key = { _, album -> album.stableId },
                            contentType = { index, _ -> "album" },
                        ) { index, album ->
                            val entrance =
                                rememberEntrance(
                                    order = minOf(index + 4, 10),
                                    play = playlistEntrance,
                                )
                            MusicAlbumRow(
                                album = album,
                                onClick = {
                                    album.localAlbum?.let { path.push(AlbumDetailScene(it)) }
                                        ?: run {
                                            selectedTab = MusicBrowserTab.Songs
                                            selectedSource = album.source?.filter() ?: SongLibrarySource.All
                                            searchQuery = album.title
                                        }
                                },
                                modifier =
                                    Modifier
                                        .animateItem(
                                            fadeInSpec =
                                            ListItemFadeInSpec,
                                            placementSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                                ),
                                            fadeOutSpec = ListItemFadeOutSpec,
                                        ).graphicsLayer {
                                            val enter = entrance.value
                                            transformOrigin = TransformOrigin(0f, 0f)
                                            alpha = enter
                                            translationY = (1f - enter) * 28.dp.toPx()
                                        },
                            )
                        }
                    }
                }

                MusicBrowserTab.Artists -> {
                    if (filteredArtists.isEmpty()) {
                        item(key = "artists_empty", contentType = "empty") {
                            MusicEmptyState(
                                title =
                                    stringResource(
                                        if (artists.isEmpty()) {
                                            R.string.music_no_artists_title
                                        } else {
                                            R.string.music_empty_artists_title
                                        },
                                    ),
                                subtitle = stringResource(R.string.music_empty_artists_subtitle),
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = filteredArtists,
                            key = { _, artist -> artist.stableId },
                            contentType = { index, _ -> "artist" },
                        ) { index, artist ->
                            val entrance =
                                rememberEntrance(
                                    order = minOf(index + 4, 10),
                                    play = playlistEntrance,
                                )
                            MusicArtistRow(
                                artist = artist,
                                onClick = {
                                    artist.localArtist?.let { path.push(ArtistDetailScene(it)) }
                                        ?: run {
                                            selectedTab = MusicBrowserTab.Songs
                                            selectedSource = artist.source?.filter() ?: SongLibrarySource.All
                                            searchQuery = artist.name
                                        }
                                },
                                modifier =
                                    Modifier
                                        .animateItem(
                                            fadeInSpec =
                                            ListItemFadeInSpec,
                                            placementSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    visibilityThreshold = IntOffset.VisibilityThreshold,
                                                ),
                                            fadeOutSpec = ListItemFadeOutSpec,
                                        ).graphicsLayer {
                                            val enter = entrance.value
                                            transformOrigin = TransformOrigin(0f, 0f)
                                            alpha = enter
                                            translationY = (1f - enter) * 28.dp.toPx()
                                        },
                            )
                        }
                    }
                }
            }
        }

        MusicTopBar(
            listState = listState,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

private fun Density.mastheadCollapse(listState: LazyListState): Float {
    if (listState.firstVisibleItemIndex > 0) return 1f
    val layoutInfo = listState.layoutInfo
    val masthead = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    val scrollOutDistance =
        (masthead.size + layoutInfo.mainAxisItemSpacing)
            .toFloat()
            .coerceIn(1f, MastheadCollapseDistance.toPx())
    return (listState.firstVisibleItemScrollOffset / scrollOutDistance).coerceIn(0f, 1f)
}

@Composable
private fun rememberEntrance(
    order: Int,
    play: Boolean,
): Animatable<Float, AnimationVector1D> {
    val entrance = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entrance.value < 1f) {
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

private fun Modifier.entranceGraphics(entrance: Animatable<Float, AnimationVector1D>): Modifier =
    graphicsLayer {
        val enter = entrance.value
        alpha = enter
        translationY = (1f - enter) * 28.dp.toPx()
    }

@Composable
private fun rememberPressScale(interactionSource: MutableInteractionSource): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = 1100f,
            ),
        label = "musicPressScale",
    )
}

@Composable
private fun MusicTopBar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backgroundColor = MaterialTheme.colorScheme.background
    val solid by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val scope = rememberCoroutineScope()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarTop + 56.dp)
                .drawBehind {
                    drawRect(color = backgroundColor.copy(alpha = mastheadCollapse(listState)))
                },
    ) {
        if (solid) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomStart),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
        ) {
            Text(
                text = stringResource(R.string.music_page_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { alpha = mastheadCollapse(listState) },
            )
            AnimatedVisibility(
                modifier = Modifier.align(Alignment.CenterEnd),
                visible = solid,
                // 高频触发（滚动过阈值即出现）：短时长强 ease-out，不带弹性；
                // 淡入与缩放同时长，时间轴对齐
                enter =
                    scaleIn(
                        animationSpec = tween(durationMillis = 180, easing = EaseOutEmphasized),
                        initialScale = 0.92f,
                    ) + fadeIn(tween(durationMillis = 180, easing = EaseOutEmphasized)),
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
                            .clickHighlight(onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            })
                            .padding(horizontal = Spacing.Medium, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.scroll_to_top_hint),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicMasthead(
    songsCount: Int,
    albumsCount: Int,
    artistsCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.music_page_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AnimatedContent(
            targetState = Triple(songsCount, albumsCount, artistsCount),
            transitionSpec = {
                val targetSum = targetState.first + targetState.second + targetState.third
                val initialSum = initialState.first + initialState.second + initialState.third
                val direction = if (targetSum >= initialSum) 1 else -1
                (
                    slideInVertically { height -> direction * height / 2 } +
                        fadeIn(
                            tween(
                                durationMillis = 240,
                            ),
                        )
                ) togetherWith (
                    slideOutVertically { height -> -direction * height / 2 } +
                        fadeOut(
                            tween(durationMillis = 160),
                        )
                ) using SizeTransform(clip = false)
            },
            modifier = Modifier.padding(top = 6.dp),
            label = "musicSummaryRoll",
        ) { (songs, albums, artists) ->
            Text(
                text = stringResource(R.string.music_summary_format, songs, albums, artists),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MusicTabStrip(
    selectedTab: MusicBrowserTab,
    songsCount: Int,
    albumsCount: Int,
    artistsCount: Int,
    dailyCount: Int,
    showDaily: Boolean,
    onSelect: (MusicBrowserTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        MusicBrowserTab.entries.filter { it != MusicBrowserTab.Daily || showDaily }.forEach { tab ->
            val count =
                when (tab) {
                    MusicBrowserTab.Songs -> songsCount
                    MusicBrowserTab.Albums -> albumsCount
                    MusicBrowserTab.Artists -> artistsCount
                    MusicBrowserTab.Daily -> dailyCount
                }
            MusicTabChip(
                tab = tab,
                count = count,
                selected = tab == selectedTab,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MusicTabChip(
    tab: MusicBrowserTab,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScale by rememberPressScale(interactionSource)
    val container =
        if (selected) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val content =
        if (selected) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier =
            modifier
                .height(LayoutTokens.MusicTabHeight)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(Shapes.LargeCornerBasedShape)
                .background(container)
                .clickHighlight(interactionSource = interactionSource, onClick = onClick)
                .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(tab.countRes, count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun MusicSearchBar(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .height(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = Spacing.Large, end = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MusicCloudLoadingState(compact: Boolean = false) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = LayoutTokens.MusicHeaderHorizontalPadding,
                    vertical = if (compact) Spacing.Small else Spacing.Large,
                ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(if (compact) 20.dp else 28.dp))
        Text(
            text = stringResource(R.string.music_cloud_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.Small),
        )
    }
}

@Composable
private fun MusicSectionHeader(
    tab: MusicBrowserTab,
    count: Int,
    sortAnchor: PopupMenuAnchorState,
    onSortClick: () -> Unit,
    sourceAnchor: PopupMenuAnchorState,
    sourceIcon: ImageVector,
    showSourceSelector: Boolean,
    onSourceClick: () -> Unit,
    showRefresh: Boolean,
    refreshing: Boolean,
    refreshContentDescription: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .padding(top = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(tab.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(tab.countRes, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRefresh) {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickHighlight(
                                onClickLabel = refreshContentDescription,
                                onClick = {
                                    if (!refreshing) onRefresh()
                                },
                            ).padding(Spacing.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = refreshContentDescription,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (showSourceSelector) {
                Box(
                    modifier =
                        Modifier
                            .popupMenuAnchor(sourceAnchor)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickHighlight(
                                onClickLabel = stringResource(R.string.music_source_selector_cd),
                                onClick = onSourceClick,
                            ).padding(Spacing.Small),
                ) {
                    Icon(
                        imageVector = sourceIcon,
                        contentDescription = stringResource(R.string.music_source_selector_cd),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // 排序锚点：点击后图标原地过渡成排序菜单（SortMenuScene）
            Box(
                modifier =
                    Modifier
                        .popupMenuAnchor(sortAnchor)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickHighlight(
                            onClickLabel = stringResource(R.string.music_sort_cd),
                            onClick = onSortClick,
                        ).padding(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.music_sort_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MusicSongRow(
    modifier: Modifier = Modifier,
    index: Int,
    title: String,
    artist: String,
    album: String,
    durationMs: Long,
    artworkUri: Uri?,
    fallbackArtworkUri: Uri?,
    sourceLabel: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val song =
        remember(artist, album) {
            SongRowSubtitle(
                artist = artist,
                album = album,
            )
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .background(
                    if (isPlaying) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ).combinedClickHighlight(
                    onClick = {
                        onClick()
                    },
                    onLongClick = onLongClick,
                ).padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color =
                if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )
        AudioCover(
            uri = artworkUri,
            fallbackUri = fallbackArtworkUri,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(Shapes.LargeCornerBasedShape),
            placeHolder = { MusicCoverPlaceholder() },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedContent(isPlaying) { playing ->
            Column(
                modifier = Modifier.width(64.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (playing) stringResource(R.string.playing) else formatDuration(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (playing) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    textAlign = TextAlign.End,
                )
                sourceLabel?.let { source ->
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelSmall,
                        // Album-derived primary colors can be almost black in a dark flat palette.
                        // Provider labels are information, not decoration, so keep them on a
                        // guaranteed high-contrast surface role.
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicAlbumRow(
    album: BrowserAlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickHighlight(onClick = onClick)
                .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        AudioCover(
            uri = album.artworkUri,
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(Shapes.LargeCornerBasedShape),
            placeHolder = { MusicCoverPlaceholder(Icons.Default.Album) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.songs_count_format, album.numberOfSongs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MusicArtistRow(
    artist: BrowserArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickHighlight(onClick = onClick)
                .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        AudioCover(
            uri = artist.artworkUri,
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            placeHolder = { MusicCoverPlaceholder(Icons.Default.Person) },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.songs_count_format, artist.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(Spacing.Small)
                    .size(18.dp),
        )
    }
}

@Composable
private fun MusicEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = LayoutTokens.MusicHeaderHorizontalPadding)
                .clip(Shapes.ExtraLarge1CornerBasedShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(Spacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onActionClick != null) {
            Row(
                modifier =
                    Modifier
                        .padding(top = Spacing.Small)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickHighlight(onClick = onActionClick)
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    imageVector = Icons.Default.Scanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun MusicCoverPlaceholder(
    icon: ImageVector = Icons.Default.MusicNote,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun rememberTotalDurationText(totalDuration: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(totalDuration)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalDuration) % 60
    return when {
        hours > 0 -> stringResource(R.string.hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.minutes, minutes)
        else -> stringResource(R.string.less_than_1_minute)
    }
}

private fun List<Song>.toAlbums(
    unknownAlbum: String,
    unknownArtist: String,
): List<Album> =
    groupBy { it.albumId }
        .map { (albumId, songs) ->
            val first = songs.first()
            Album(
                id = albumId.toString(),
                title = first.album.ifBlank { unknownAlbum },
                artist = first.artist.ifBlank { unknownArtist },
                numberOfSongs = songs.size,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

private fun List<Song>.toArtists(unknownArtist: String): List<Artist> =
    groupBy { it.artist.ifBlank { unknownArtist } }
        .map { (name, songs) ->
            Artist(
                name = name,
                songCount = songs.size,
                coverAlbumId = songs.first().albumId,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

private fun List<BrowserSongItem>.toBrowserAlbums(
    localAlbums: List<Album>,
    unknownAlbum: String,
    unknownArtist: String,
): List<BrowserAlbumItem> {
    val localById = localAlbums.associateBy(Album::id)
    return groupBy { song ->
        when (song) {
            is BrowserSongItem.Local -> "local:${song.song.albumId}"
            is BrowserSongItem.Cloud ->
                "${song.source}:${song.album.trim().lowercase()}:${song.artist.trim().lowercase()}"
        }
    }.map { (stableId, songs) ->
        val first = songs.first()
        val localAlbum =
            (first as? BrowserSongItem.Local)
                ?.song
                ?.albumId
                ?.toString()
                ?.let(localById::get)
        BrowserAlbumItem(
            stableId = stableId,
            title = first.album.ifBlank { unknownAlbum },
            artist = first.artist.ifBlank { unknownArtist },
            numberOfSongs = songs.size,
            artworkUri = first.artworkUri ?: first.fallbackArtworkUri,
            localAlbum = localAlbum,
            source = first.source,
        )
    }
}

private fun List<BrowserSongItem>.toBrowserArtists(
    localArtists: List<Artist>,
    unknownArtist: String,
): List<BrowserArtistItem> {
    val localByName = localArtists.associateBy(Artist::name)
    return groupBy { song ->
        val name = song.artist.ifBlank { unknownArtist }
        "${song.source ?: "local"}:${name.trim().lowercase()}"
    }.map { (stableId, songs) ->
        val first = songs.first()
        val name = first.artist.ifBlank { unknownArtist }
        BrowserArtistItem(
            stableId = stableId,
            name = name,
            songCount = songs.size,
            artworkUri = first.artworkUri ?: first.fallbackArtworkUri,
            localArtist = if (first.source == null) localByName[name] else null,
            source = first.source,
        )
    }
}

private fun List<Song>.filterSongsBy(query: String): List<Song> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter { song ->
        song.displayName.contains(normalized, ignoreCase = true) ||
            song.artist.contains(
                normalized,
                ignoreCase = true,
            ) ||
            song.album.contains(normalized, ignoreCase = true)
    }
}

private fun List<Album>.filterAlbumsBy(query: String): List<Album> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter { album ->
        album.title.contains(normalized, ignoreCase = true) ||
            album.artist.contains(
                normalized,
                ignoreCase = true,
            )
    }
}

private fun List<BrowserAlbumItem>.filterBrowserAlbumsBy(query: String): List<BrowserAlbumItem> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter { album ->
        album.title.contains(normalized, ignoreCase = true) ||
            album.artist.contains(normalized, ignoreCase = true)
    }
}

private fun List<Artist>.filterArtistsBy(query: String): List<Artist> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter { artist ->
        artist.name.contains(normalized, ignoreCase = true)
    }
}

private fun List<BrowserArtistItem>.filterBrowserArtistsBy(query: String): List<BrowserArtistItem> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return this
    return filter { it.name.contains(normalized, ignoreCase = true) }
}
