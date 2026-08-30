package me.spica27.spicamusic.ui.player

import android.net.Uri
import android.os.FileUtils
import android.text.TextUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.linc.amplituda.Amplituda
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spica27.navkit.geometry.GeometryTransition
import me.spica27.navkit.geometry.geometrySource
import me.spica27.spicamusic.App
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.NeteaseAudioQuality
import me.spica27.spicamusic.cloud.QqAudioQuality
import me.spica27.spicamusic.cloud.RemoteMusicProvider
import me.spica27.spicamusic.common.entity.DynamicCoverType
import me.spica27.spicamusic.common.entity.ProgressBarStyle
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.feature.library.domain.SongUseCases
import me.spica27.spicamusic.player.api.PlayMode
import me.spica27.spicamusic.ui.player.pages.CurrentPlaylistPage
import me.spica27.spicamusic.ui.player.scene.LyricsPlayerPage
import me.spica27.spicamusic.ui.settings.SettingsViewModel
import me.spica27.spicamusic.ui.theme.LocalThemeRevealController
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.FluidMusicBackground
import me.spica27.spicamusic.ui.widget.MusicCoverPlaceholder
import me.spica27.spicamusic.ui.widget.ShowOnIdleContent
import me.spica27.spicamusic.ui.widget.StableAudioCover
import me.spica27.spicamusic.ui.widget.audio_seekbar.AudioDynamicWaveSlider
import me.spica27.spicamusic.ui.widget.audio_seekbar.AudioWaveSlider
import me.spica27.spicamusic.ui.widget.clickHighlight
import me.spica27.spicamusic.ui.widget.materialSharedAxisYIn
import me.spica27.spicamusic.ui.widget.materialSharedAxisYOut
import me.spica27.spicamusic.ui.widget.rememberIOSOverScrollEffect
import me.spica27.spicamusic.utils.albumCoverFallbackUri
import me.spica27.spicamusic.utils.rememberRetainedDominantColorFromUri
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import timber.log.Timber
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import androidx.compose.ui.util.lerp as floatLerp

// ============================================
// 常量定义
// ============================================

// 展开动画透明度阈值常量
private const val PAGE_COUNT = 2
const val DEFAULT_PAGE = 0
private const val HERO_REVEAL_THRESHOLD = 0.08f
private const val META_REVEAL_THRESHOLD = 0.18f
private const val MINI_LYRIC_REVEAL_THRESHOLD = 0.24f
private const val TAGS_REVEAL_THRESHOLD = 0.28f
private const val SEEKBAR_REVEAL_THRESHOLD = 0.34f
private const val PLAYER_CONTROLS_REVEAL_THRESHOLD = 0.48f
private const val COLLAPSED_HERO_SCALE = 0.82f
private const val SHARED_PLAYBACK_TRAVEL_DP = 136f
private const val SHARED_CONTROLS_EXIT_DP = 72f
private const val LYRICS_ARTWORK_SOURCE_HANDOFF = 0.001f
private const val LYRICS_ARTWORK_TARGET_HANDOFF = 0.999f
private const val EMPTY_WAVEFORM_CACHE = "EMPTY"
private val EmptyFftDrawData = FloatArray(0)

// ============================================
// 主屏幕组件
// ============================================

/**
 * 全屏播放器页面
 */
@Composable
fun ExpandedPlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = LocalPlayerViewModel.current,
    onCollapse: () -> Unit,
    progressProvider: () -> Float = { 1f }, // 前景内容透明度，避免整棵树每帧重组
    morphProgressProvider: () -> Float = { 1f }, // 胶囊到全屏的几何形变进度
    initialPage: Int = DEFAULT_PAGE, // 初始页面索引
    isActive: Boolean = true, // 内容是否可交互/可见
    dynamicEffectsActive: Boolean = isActive, // TextureView、FFT 与波形分析延后到内容稳定后再启用
    preloadContent: Boolean = false, // 首次打开后保留组合树，收起时不在动画尾帧销毁
    artworkMorphState: PlayerArtworkMorphState? = null,
    artworkMorphInFlightProvider: () -> Boolean = { false },
    dragToCollapseModifier: Modifier = Modifier,
) {
    val themeRevealController = LocalThemeRevealController.current
    DisposableEffect(themeRevealController, isActive) {
        if (isActive) themeRevealController.acquirePlayerSurface()
        onDispose {
            if (isActive) themeRevealController.releasePlayerSurface()
        }
    }

    // 在播放器展开后提前启动本地歌词读取，点击歌词时无需再等待 ViewModel 初始化。
    koinActivityViewModel<LyricsViewModel>()

    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playMode by viewModel.playMode.collectAsStateWithLifecycle()
    val currentMediaItem by viewModel.currentMediaItem.collectAsStateWithLifecycle()
    val duration by viewModel.currentDuration.collectAsStateWithLifecycle()
    val audioQualityInfo =
        remember(currentMediaItem?.mediaId, currentMediaItem?.mediaMetadata?.extras) {
            currentMediaItem.toAudioQualityInfo()
        }

    // 监听应用生命周期状态
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

    // 判断应用是否在前台（可见状态）
    val isAppInForeground =
        remember(lifecycleState) {
            lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        }

    // 当前播放位置（定时更新）
    // 使用 currentMediaItem?.mediaId 作为 key，切歌时自动重置 seek 状态
    val mediaId = currentMediaItem?.mediaId
    // 注意：保留 State 引用而非用 by 解包，避免每秒的播放位置更新触发整个播放器树重组。
    // 位置读取下沉到 SeekBarSection 叶子组件，通过 provider lambda 局部消费。
    val seekValueState = remember(mediaId) { mutableFloatStateOf(0f) }
    var isSeekingState by remember(mediaId) { mutableStateOf(false) }

    val positionState = viewModel.currentPosition.collectAsStateWithLifecycle()

    val songLikeState by viewModel.currentSongIsLike.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val sleepTimerDurationMs by viewModel.sleepTimerDurationMs.collectAsStateWithLifecycle()
    var lyricsArtworkPainter by remember { mutableStateOf<Painter?>(null) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var showAudioQualitySheet by rememberSaveable(mediaId) { mutableStateOf(false) }

    if (showAudioQualitySheet) {
        PlayerAudioQualitySheet(
            audioQualityInfo = audioQualityInfo,
            playerViewModel = viewModel,
            onDismiss = { showAudioQualitySheet = false },
        )
    }

    LaunchedEffect(songLikeState) {
        Timber.tag("ExpandedPlayerScreen").d("当前歌曲收藏状态: $songLikeState")
    }

    BackHandler(enabled = isActive) {
        onCollapse.invoke()
    }

    // 将播放位置同步到 seekbar：用 snapshotFlow 在协程中观察位置变化，
    // 避免在组合作用域读取高频 state 而导致重组。
    LaunchedEffect(mediaId) {
        snapshotFlow { positionState.value }
            .collect { position ->
                if (!isSeekingState) {
                    seekValueState.floatValue = position.toFloat()
                }
            }
    }

    val coroutineScope = rememberCoroutineScope()

    // Pager 状态会在收起时保留；每次重新展开按入口恢复到主页或播放列表页。
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGE_COUNT })
    val lyricsPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val lyricsMorphState = rememberPlayerLyricsMorphState()
    val lyricsTransitionProgressProvider =
        remember(lyricsPagerState) {
            {
                (
                    lyricsPagerState.currentPage +
                        lyricsPagerState.currentPageOffsetFraction
                ).coerceIn(0f, 1f)
            }
        }
    var lyricsProgressBarStyle by remember {
        mutableStateOf<ProgressBarStyle>(ProgressBarStyle.ExpressiveWavy)
    }
    var lyricsAmplitudes by remember { mutableStateOf(emptyList<Int>()) }
    var sharedPlaybackHeightPx by remember { mutableIntStateOf(0) }
    val sharedPlaybackHeight =
        with(LocalDensity.current) {
            sharedPlaybackHeightPx.toDp()
        }

    LaunchedEffect(initialPage, isActive) {
        if (isActive && pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
        if (isActive && initialPage == DEFAULT_PAGE && lyricsPagerState.currentPage != 0) {
            lyricsPagerState.scrollToPage(0)
        }
    }

    BackHandler(isActive && pagerState.currentPage == 0 && lyricsPagerState.currentPage == 1) {
        coroutineScope.launch {
            lyricsPagerState.animateScrollToPage(
                0,
                animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
            )
        }
    }

    BackHandler(isActive && pagerState.currentPage == 1) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                0,
                animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
            )
        }
    }

    // 兜底吸附：快速向下滑动 / 手指滑出屏幕边缘导致手势被取消、未触发正常 fling 时，
    // Pager 可能停在两页之间（offsetFraction != 0）。这里监听滚动结束，
    // 若仍处于中间态则强制吸附到最近的页面，避免卡死在中间态。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && abs(pagerState.currentPageOffsetFraction) > 0.01f) {
                    pagerState.animateScrollToPage(
                        pagerState.currentPage,
                        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
                    )
                }
            }
    }

    // Follow upstream's player-local artwork color. ColorUtils shares an in-memory cache with
    // the app theme, while this state remains keyed to the artwork actually rendered here.
    val playerArtworkUri =
        currentMediaItem?.mediaMetadata?.artworkUri
            ?: currentMediaItem?.mediaMetadata?.albumCoverFallbackUri()
    val preparedCoverColor =
        rememberRetainedDominantColorFromUri(
            uri = playerArtworkUri,
            fallbackColor = MaterialTheme.colorScheme.primary,
        )
    val coverColor by
        animateColorAsState(
            targetValue = preparedCoverColor,
            animationSpec =
                tween(
                    durationMillis = 420,
                    easing = EaseOutCubic,
                ),
            label = "PreparedPlayerBackdropColor",
        )

    Box(
        modifier =
            modifier
                // Keep the same stable base layer as upstream. FluidMusicBackground owns the
                // artwork/color transition; a second cover-colored layer here changes earlier
                // than the artwork and is visible as a white/solid-color flash between songs.
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxSize()
                .then(
                    if (artworkMorphState != null) {
                        Modifier.playerArtworkMorphRoot(artworkMorphState)
                    } else {
                        Modifier
                    },
                ),
    ) {
        // 流动背景（仅前台时启用，节省电量）
        FluidMusicBackground(
            modifier =
                Modifier
                    .fillMaxSize(),
            coverColor = coverColor,
            isDarkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
            coverUri = { playerArtworkUri },
            active = dynamicEffectsActive && isAppInForeground,
            visibilityProgressProvider = {
                val morph = morphProgressProvider()
                ((morph - 0.04f) / 0.46f).coerceIn(0f, 1f)
            },
        )

        // 内容层
        VerticalPager(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (
                            pagerState.currentPage == 0 &&
                            lyricsPagerState.currentPage == 0 &&
                            !lyricsPagerState.isScrollInProgress
                        ) {
                            dragToCollapseModifier
                        } else {
                            Modifier
                        },
                    ).graphicsLayer {
                        val morph = morphProgressProvider()
                        // 所有前景控件共用一个 fade-through 进度，不再逐项错峰出现。
                        alpha = unifiedPlayerContentAlpha(morph)
                    },
            state = pagerState,
            key = { it },
            overscrollEffect = rememberIOSOverScrollEffect(orientation = Orientation.Vertical),
            userScrollEnabled = lyricsPagerState.currentPage == 0,
            flingBehavior =
                PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapPositionalThreshold = .2f,
                ),
        ) {
            if (it == 0) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .playerLyricsMorphRoot(lyricsMorphState),
                ) {
                    HorizontalPager(
                        state = lyricsPagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { page -> page },
                        beyondViewportPageCount = 1,
                        overscrollEffect = null,
                        flingBehavior =
                            PagerDefaults.flingBehavior(
                                state = lyricsPagerState,
                                snapPositionalThreshold = .24f,
                            ),
                    ) { horizontalPage ->
                        if (horizontalPage == 0) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // 顶部工具栏
                                TopBar(
                                    modifier = Modifier,
                                    onCollapse = onCollapse,
                                    progressProvider = { 1f },
                                    showNavigationButton = false,
                                )

                                // 水平 Pager 内容区域
                                // 播放器页面
                                ShowOnIdleContent(
                                    modifier = Modifier.weight(1f),
                                    visible = preloadContent || isActive,
                                ) {
                                    PlayerPage(
                                        playerViewModel = viewModel,
                                        isSeekingState = isSeekingState,
                                        currentMediaItem = { currentMediaItem },
                                        audioQualityInfo = audioQualityInfo,
                                        onAudioQualityClick = { showAudioQualitySheet = true },
                                        realPositionProvider = { positionState.value.toFloat() },
                                        seekPositionProvider = { seekValueState.floatValue },
                                        duration = duration,
                                        isPlaying = isPlaying,
                                        isLike = songLikeState,
                                        playMode = playMode,
                                        onValueChange = {
                                            isSeekingState = true
                                            seekValueState.floatValue = it * duration
                                        },
                                        onValueChangeFinished = {
                                            viewModel.seekTo(seekValueState.floatValue.toLong())
                                            isSeekingState = false
                                        },
                                        onPlayPauseClick = { viewModel.togglePlayPause() },
                                        onPreviousClick = { viewModel.skipToPrevious() },
                                        onNextClick = { viewModel.skipToNext() },
                                        onPlayModeClick = { viewModel.togglePlayMode() },
                                        onFavoriteClick = {
                                            viewModel.toggleLikeCurrentSong()
                                        },
                                        onOpenLyrics = {
                                            coroutineScope.launch {
                                                lyricsPagerState.animateScrollToPage(
                                                    1,
                                                    animationSpec =
                                                        tween(
                                                            durationMillis = 300,
                                                            easing = EaseOutCubic,
                                                        ),
                                                )
                                            }
                                        },
                                        onPlaybackVisualsChanged = { style, amplitudes ->
                                            lyricsProgressBarStyle = style
                                            lyricsAmplitudes = amplitudes
                                        },
                                        onArtworkPainterReady = { painter ->
                                            lyricsArtworkPainter = painter
                                        },
                                        onArtworkPainterFailed = {
                                            lyricsArtworkPainter = null
                                        },
                                        artworkPainter = lyricsArtworkPainter,
                                        progressProvider = { 1f },
                                        morphProgressProvider = morphProgressProvider,
                                        artworkMorphState = artworkMorphState,
                                        artworkMorphInFlightProvider = artworkMorphInFlightProvider,
                                        lyricsMorphState = lyricsMorphState,
                                        lyricsTransitionProgressProvider = lyricsTransitionProgressProvider,
                                        playbackSectionHeight = sharedPlaybackHeight,
                                        isAppInForeground = isAppInForeground,
                                        enableHeavyEffects = dynamicEffectsActive,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        } else {
                            LyricsPlayerPage(
                                modifier = Modifier.fillMaxSize(),
                                heroArtworkUri = currentMediaItem?.mediaMetadata?.artworkUri,
                                artworkPainter = lyricsArtworkPainter,
                                transitionProgressProvider = lyricsTransitionProgressProvider,
                                artworkModifier =
                                    Modifier
                                        .playerLyricsMorphTarget(
                                            lyricsMorphState,
                                            lyricsTransitionProgressProvider,
                                        ).graphicsLayer {
                                            val transitionProgress = lyricsTransitionProgressProvider()
                                            alpha =
                                                if (
                                                    !lyricsMorphState.isReady ||
                                                    transitionProgress >=
                                                    LYRICS_ARTWORK_TARGET_HANDOFF
                                                ) {
                                                    1f
                                                } else {
                                                    0f
                                                }
                                        },
                                showNavigationButton = false,
                                showLyricsEditor = showLyricsEditor,
                                onLyricsEditorDismiss = { showLyricsEditor = false },
                                onBack = {
                                    coroutineScope.launch {
                                        lyricsPagerState.animateScrollToPage(
                                            0,
                                            animationSpec =
                                                tween(
                                                    durationMillis = 300,
                                                    easing = EaseOutCubic,
                                                ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = Spacing.ExtraLarge)
                                .padding(bottom = Spacing.Large)
                                .graphicsLayer {
                                    val progress = lyricsTransitionProgressProvider()
                                    val travel = smoothStepProgress(progress)
                                    translationY = travel * SHARED_PLAYBACK_TRAVEL_DP.dp.toPx()
                                },
                    ) {
                        PlayerPlaybackBottomSection(
                            progress =
                                if (duration > 0L) {
                                    (seekValueState.floatValue / duration).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                            currentPosition = positionState.value,
                            duration = duration,
                            isPlaying = isPlaying,
                            isSeeking = isSeekingState,
                            playMode = playMode,
                            isLike = songLikeState,
                            progressBarStyle = lyricsProgressBarStyle,
                            fftDrawData = viewModel.fftDrawData,
                            amplitudes = lyricsAmplitudes,
                            onProgressChange = {
                                isSeekingState = true
                                seekValueState.floatValue = it * duration
                            },
                            onProgressChangeFinished = {
                                viewModel.seekTo(seekValueState.floatValue.toLong())
                                isSeekingState = false
                            },
                            onPlayPauseClick = viewModel::togglePlayPause,
                            onPreviousClick = viewModel::skipToPrevious,
                            onNextClick = viewModel::skipToNext,
                            onPlayModeClick = viewModel::togglePlayMode,
                            onFavoriteClick = viewModel::toggleLikeCurrentSong,
                            sleepTimerRemainingMs = sleepTimerRemainingMs,
                            sleepTimerDurationMs = sleepTimerDurationMs,
                            onSleepTimerSet = viewModel::setSleepTimer,
                            onSleepTimerCancel = viewModel::cancelSleepTimer,
                            showControls = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coordinates ->
                                        if (sharedPlaybackHeightPx != coordinates.size.height) {
                                            sharedPlaybackHeightPx = coordinates.size.height
                                        }
                                    },
                            controlsModifier =
                                Modifier.graphicsLayer {
                                    val progress = lyricsTransitionProgressProvider()
                                    val exit = smoothStepProgress(progress)
                                    alpha = 1f - exit
                                    translationY = exit * SHARED_CONTROLS_EXIT_DP.dp.toPx()
                                    scaleX = 1f - exit * 0.035f
                                    scaleY = 1f - exit * 0.035f
                                },
                        )
                    }
                    PlayerLyricsArtworkMorphOverlay(
                        state = lyricsMorphState,
                        artworkUri = currentMediaItem?.mediaMetadata?.artworkUri,
                        artworkPainter = lyricsArtworkPainter,
                        progressProvider = lyricsTransitionProgressProvider,
                    )
                    IconButton(
                        onClick = {
                            if (lyricsTransitionProgressProvider() > 0.5f) {
                                coroutineScope.launch {
                                    lyricsPagerState.animateScrollToPage(
                                        0,
                                        animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
                                    )
                                }
                            } else {
                                onCollapse()
                            }
                        },
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(start = Spacing.Large, top = 12.dp)
                                .size(48.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors().copy(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.back),
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .graphicsLayer {
                                            rotationZ = 90f * lyricsTransitionProgressProvider()
                                        },
                            )
                        }
                    }
                    MorphingPlayerTopAction(
                        transitionProgressProvider = lyricsTransitionProgressProvider,
                        onPlaylistClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    1,
                                    animationSpec = tween(durationMillis = 300, easing = EaseOutCubic),
                                )
                            }
                        },
                        onLyricsEditorClick = { showLyricsEditor = true },
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(end = Spacing.Large, top = 12.dp),
                    )
                }
            } else {
                // 播放列表
                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            colors =
                                TopAppBarDefaults.topAppBarColors().copy(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    subtitleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(
                                                0,
                                                animationSpec =
                                                    tween(
                                                        durationMillis = 550,
                                                        easing = EaseOutCubic,
                                                    ),
                                            )
                                        }
                                    },
                                    colors =
                                        IconButtonDefaults.iconButtonColors().copy(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                            title = {
                                Text(stringResource(R.string.now_playinglist))
                            },
                        )
                    },
                ) {
                    ShowOnIdleContent(true) {
                        Box(
                            modifier = Modifier.padding(it),
                        ) {
                            CurrentPlaylistPage(
                                modifier = Modifier.fillMaxSize(),
//                        scrollBehavior = scrollBehavior,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// 导航栏组件
// ============================================

/**
 * 顶部工具栏
 */
@Composable
private fun TopBar(
    onCollapse: () -> Unit,
    progressProvider: () -> Float,
    modifier: Modifier,
    showNavigationButton: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val progress = progressProvider()
                        val barAlpha = calculateFadeAlpha(progress, HERO_REVEAL_THRESHOLD)
                        alpha = barAlpha
                        translationY = (1f - barAlpha) * -20f
                    }.padding(horizontal = Spacing.Large)
                    .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                modifier =
                    Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            alpha = if (showNavigationButton) 1f else 0f
                        },
                enabled = showNavigationButton,
                onClick = {
                    onCollapse.invoke()
                },
                colors =
                    IconButtonDefaults.iconButtonColors().copy(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.collapse),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** 播放列表胶囊随横向 Pager 连续收束为歌词编辑圆形按钮。 */
@Composable
private fun MorphingPlayerTopAction(
    transitionProgressProvider: () -> Float,
    onPlaylistClick: () -> Unit,
    onLyricsEditorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = smoothStepProgress(transitionProgressProvider().coerceIn(0f, 1f))
    val width = floatLerp(104f, 42f, progress).dp
    Box(
        modifier =
            modifier
                .width(width)
                .height(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable {
                    if (progress >= 0.5f) onLyricsEditorClick() else onPlaylistClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.graphicsLayer {
                    alpha = (1f - progress * 1.8f).coerceIn(0f, 1f)
                    translationX = -progress * 10.dp.toPx()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.PlaylistPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Rounded.EditNote,
            contentDescription = stringResource(R.string.lyrics_editor),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        alpha = ((progress - 0.42f) / 0.58f).coerceIn(0f, 1f)
                        rotationZ = (1f - progress) * -24f
                        scaleX = 0.72f + progress * 0.28f
                        scaleY = 0.72f + progress * 0.28f
                    },
        )
    }
}

// ---------- 音频信息组件 ----------

@Immutable
private data class AudioQualityInfo(
    val sampleRate: Int,
    val bitRate: Int,
    val isLossless: Boolean,
    val cloudProvider: String?,
)

private fun MediaItem?.toAudioQualityInfo(): AudioQualityInfo {
    val extras = this?.mediaMetadata?.extras
    val sampleRate = extras?.getInt("sampleRate") ?: 0
    val bitRate = extras?.getInt("bitRate") ?: 0
    val mimeType = extras?.getString("mimeType").orEmpty()
    val cloudProvider = extras?.getString("cloudProvider")?.takeIf(String::isNotBlank)
    val isLossless =
        mimeType.contains("flac", ignoreCase = true) ||
            mimeType.contains("alac", ignoreCase = true) ||
            mimeType.contains("wav", ignoreCase = true)

    return AudioQualityInfo(
        sampleRate = sampleRate,
        bitRate = bitRate,
        isLossless = isLossless,
        cloudProvider = cloudProvider,
    )
}

// ---------- 播放器页面 ----------

/**
 * 播放器页面（原有的播放器内容）
 */
@Composable
private fun PlayerPage(
    playerViewModel: PlayerViewModel,
    currentMediaItem: () -> MediaItem?,
    audioQualityInfo: AudioQualityInfo,
    onAudioQualityClick: () -> Unit,
    seekPositionProvider: () -> Float,
    realPositionProvider: () -> Float,
    duration: Long,
    isPlaying: Boolean,
    isLike: Boolean,
    playMode: PlayMode,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPlayModeClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOpenLyrics: () -> Unit,
    onPlaybackVisualsChanged: (ProgressBarStyle, List<Int>) -> Unit,
    onArtworkPainterReady: (Painter) -> Unit,
    onArtworkPainterFailed: () -> Unit,
    artworkPainter: Painter?,
    progressProvider: () -> Float,
    morphProgressProvider: () -> Float,
    artworkMorphState: PlayerArtworkMorphState?,
    artworkMorphInFlightProvider: () -> Boolean,
    lyricsMorphState: PlayerLyricsMorphState,
    lyricsTransitionProgressProvider: () -> Float,
    playbackSectionHeight: Dp,
    isAppInForeground: Boolean,
    enableHeavyEffects: Boolean,
    modifier: Modifier = Modifier,
    isSeekingState: Boolean = false,
) {
    // 读取动态封面设置
    val preferencesManager: PreferencesManager = koinInject()
    val dynamicCoverTypeValue by preferencesManager
        .getString(PreferencesManager.Keys.DYNAMIC_COVER_TYPE, DynamicCoverType.ShiningStars.value)
        .collectAsStateWithLifecycle(initialValue = DynamicCoverType.ShiningStars.value)
    val dynamicCoverType =
        remember(dynamicCoverTypeValue) {
            DynamicCoverType.fromString(dynamicCoverTypeValue)
        }
    val progressBarStyleValue by preferencesManager
        .getString(PreferencesManager.Keys.PROGRESS_BAR_STYLE, ProgressBarStyle.ExpressiveWavy.value)
        .collectAsStateWithLifecycle(initialValue = ProgressBarStyle.ExpressiveWavy.value)
    val progressBarStyle =
        remember(progressBarStyleValue) {
            ProgressBarStyle.fromString(progressBarStyleValue)
        }
    val songUseCases = koinInject<SongUseCases>()

    val title =
        currentMediaItem
            .invoke()
            ?.mediaMetadata
            ?.title
            ?.toString()
            ?: stringResource(R.string.unknown_song)
    val artist =
        currentMediaItem
            .invoke()
            ?.mediaMetadata
            ?.artist
            ?.toString()
            ?: stringResource(R.string.unknown_artist)
    var ampState by remember { mutableStateOf(listOf<Int>()) }

    Column(
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.ExtraLarge)
                .padding(top = Spacing.Medium, bottom = Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 封面区：弹性吸收剩余高度——矮屏上封面自动缩小而不挤压下方控件，
        // 高屏上封面在区域内垂直居中，信息与控制区始终贴底对齐
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = Spacing.Medium),
            contentAlignment = Alignment.Center,
        ) {
            // 封面（跳转全屏歌词时作为共享元素飞入歌词页 header）
            Box(
                modifier =
                    Modifier
                        .aspectRatio(1f)
                        .then(
                            if (artworkMorphState != null) {
                                Modifier.playerArtworkMorphTarget(artworkMorphState)
                            } else {
                                Modifier
                            },
                        ).playerLyricsMorphSource(
                            lyricsMorphState,
                            lyricsTransitionProgressProvider,
                        ).graphicsLayer {
                            val hasSharedArtwork = artworkMorphState?.hasUsableBounds == true
                            val heroReveal =
                                if (hasSharedArtwork) {
                                    1f
                                } else {
                                    calculateFadeAlpha(progressProvider(), HERO_REVEAL_THRESHOLD)
                                }
                            val sharedArtworkAlpha =
                                targetArtworkAlpha(
                                    progress = morphProgressProvider(),
                                    inFlight = artworkMorphInFlightProvider(),
                                    hasUsableBounds = hasSharedArtwork,
                                )
                            // 飞行期间本体隐藏，由浮层接管显示
                            val lyricsArtworkAlpha =
                                if (
                                    !lyricsMorphState.isReady ||
                                    lyricsTransitionProgressProvider() <=
                                    LYRICS_ARTWORK_SOURCE_HANDOFF
                                ) {
                                    1f
                                } else {
                                    0f
                                }
                            alpha = heroReveal * sharedArtworkAlpha * lyricsArtworkAlpha
                            translationY = (1f - heroReveal) * 48f
                            scaleX = floatLerp(COLLAPSED_HERO_SCALE, 1f, heroReveal)
                            scaleY = floatLerp(COLLAPSED_HERO_SCALE, 1f, heroReveal)
                        }.clip(Shapes.LargeCornerBasedShape),
            ) {
                AnimatedContent(
                    currentMediaItem.invoke(),
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        materialSharedAxisYIn(true) togetherWith materialSharedAxisYOut(true)
                    },
                    contentKey = { it?.mediaId ?: "-1" },
                ) { currentMediaItem ->
                    val resolvedArtworkUri =
                        currentMediaItem?.mediaMetadata?.artworkUri
                            ?: currentMediaItem?.mediaMetadata?.albumCoverFallbackUri()
                    val artworkContainerColor =
                        if (resolvedArtworkUri == null && artworkPainter == null) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    val placeholderContentColor =
                        if (resolvedArtworkUri == null && artworkPainter == null) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    StableAudioCover(
                        uri = resolvedArtworkUri,
                        retainedPainter = artworkPainter,
                        onPainterReady = onArtworkPainterReady,
                        onPainterFailed = onArtworkPainterFailed,
                        placeHolder = {
                            MusicCoverPlaceholder(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(Shapes.LargeCornerBasedShape)
                                        .background(artworkContainerColor),
                                containerColor = artworkContainerColor,
                                contentColor = placeholderContentColor,
                                contentDescription = stringResource(R.string.cover_placeholder),
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(Shapes.LargeCornerBasedShape)
                                .background(artworkContainerColor)
                                .clickHighlight {
                                    onOpenLyrics()
                                    /*
                                    // 防止重复点击！！
                                    if (path.scenes.none { it is LyricScene } &&
                                        coverTransition.phase.value == GeometryPhase.Source
                                    ) {
                                        path.push(
                                            LyricScene(
                                                heroArtworkUri =
                                                    currentMediaItem
                                                        ?.mediaMetadata
                                                        ?.artworkUri,
                                                coverTransition = coverTransition,
                                                progressBarStyleProvider = { progressBarStyle },
                                                amplitudesProvider = { ampState },
                                            ),
                                        )
                                    }
                                     */
                                },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Large))

        // 歌曲信息
        SongInfo(
            title = title,
            artist = artist,
            modifier =
                Modifier.graphicsLayer {
                    val metaReveal = calculateFadeAlpha(progressProvider(), META_REVEAL_THRESHOLD)
                    alpha = metaReveal
                    translationY = (1f - metaReveal) * 24f
                },
        )

        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        // mini 歌词：点击跳转全屏歌词页面
        MiniLyric(
            onClick = {
                onOpenLyrics()
                /*
                // 防止重复点击！！
                if (path.scenes.none { it is LyricScene } &&
                    coverTransition.phase.value == GeometryPhase.Source
                ) {
                    path.push(
                        LyricScene(
                            heroArtworkUri =
                                currentMediaItem
                                    .invoke()
                                    ?.mediaMetadata
                                    ?.artworkUri,
                            coverTransition = coverTransition,
                            progressBarStyleProvider = { progressBarStyle },
                            amplitudesProvider = { ampState },
                        ),
                    )
                }
                 */
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val lyricReveal =
                            calculateFadeAlpha(progressProvider(), MINI_LYRIC_REVEAL_THRESHOLD)
                        alpha = lyricReveal
                        translationY = (1f - lyricReveal) * 24f
                    },
        )

        val amplituda: Amplituda = koinInject<Amplituda>()

        // 优化：使用缓存机制避免重复加载波形数据
        val amplitudeCache = remember { mutableMapOf<String, List<Int>>() }
        // 音频波形数据
        LaunchedEffect(
            currentMediaItem.invoke()?.mediaId,
            progressBarStyle,
            enableHeavyEffects,
        ) {
            if (!enableHeavyEffects || progressBarStyle != ProgressBarStyle.TimeDomainWaveform) {
                ampState = emptyList()
                return@LaunchedEffect
            }
            val mediaId = currentMediaItem.invoke()?.mediaId ?: return@LaunchedEffect

            // 检查缓存
            if (amplitudeCache.containsKey(mediaId)) {
                ampState = amplitudeCache[mediaId] ?: emptyList()
                return@LaunchedEffect
            }
            launch(Dispatchers.IO) {
                val data = loadAmplitudeData(currentMediaItem.invoke(), amplituda, songUseCases)

                // 保存到缓存，最多保留3首歌曲的数据
                if (amplitudeCache.size >= 3) {
                    // 移除最旧的项
                    amplitudeCache.remove(amplitudeCache.keys.first())
                }
                amplitudeCache[mediaId] = data
                ampState = data
            }
        }
        LaunchedEffect(progressBarStyle, ampState) {
            onPlaybackVisualsChanged(progressBarStyle, ampState)
        }
        Spacer(modifier = Modifier.height(Spacing.Small))
        // 音质标签：固定高度槽位保持版面节奏，无标签时留白而不是显示空药丸
        val qualityTags =
            buildList {
                if (audioQualityInfo.sampleRate > 0) {
                    add("${audioQualityInfo.sampleRate / 1000}kHz")
                }
                if (audioQualityInfo.bitRate > 0) {
                    add("${audioQualityInfo.bitRate / 1000}kbps")
                }
                if (audioQualityInfo.isLossless) {
                    add(stringResource(R.string.lossless))
                }
                if (audioQualityInfo.bitRate >= 320000 && !audioQualityInfo.isLossless) {
                    add(stringResource(R.string.high_quality))
                }
                audioQualityInfo.cloudProvider?.let { provider ->
                    if (isEmpty()) add(stringResource(R.string.cloud_streaming))
                    add(provider.cloudProviderLabel())
                }
            }
        Box(
            modifier =
                Modifier
                    .height(28.dp)
                    .graphicsLayer {
                        val tagsReveal =
                            calculateFadeAlpha(progressProvider(), TAGS_REVEAL_THRESHOLD)
                        alpha = tagsReveal
                        translationY = (1f - tagsReveal) * 24f
                    },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .clickHighlight(onClick = onAudioQualityClick)
                        .padding(horizontal = Spacing.Medium, vertical = 5.dp)
                        .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.player_audio_quality_and_effects),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                qualityTags.forEach { tag ->
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.55f),
                    )
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Medium))
        Spacer(modifier = Modifier.height(playbackSectionHeight))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerAudioQualitySheet(
    audioQualityInfo: AudioQualityInfo,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val settingsViewModel: SettingsViewModel = koinActivityViewModel()
    val neteaseQuality by settingsViewModel.neteaseAudioQuality.collectAsStateWithLifecycle()
    val qqQuality by settingsViewModel.qqAudioQuality.collectAsStateWithLifecycle()
    val progressStyle by settingsViewModel.progressBarStyle.collectAsStateWithLifecycle()
    val provider = audioQualityInfo.cloudProvider?.uppercase(Locale.ROOT)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(620.dp),
        ) {
            item(key = "quality_header") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.player_current_audio_quality),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text =
                                when (provider) {
                                    "NETEASE" -> stringResource(R.string.player_quality_netease_desc)
                                    "QQ_MUSIC" -> stringResource(R.string.player_quality_qq_desc)
                                    else -> localQualityDescription(audioQualityInfo)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.player_quality_more_adjustments),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            when (provider) {
                "NETEASE" -> {
                    NeteaseAudioQuality.entries.forEach { quality ->
                        item(key = "netease_${quality.value}") {
                            QualityChoiceRow(
                                title = quality.neteaseTitle(),
                                subtitle = quality.neteaseDescription(),
                                icon = quality.playerIcon(),
                                selected = neteaseQuality == quality.value,
                                onClick = {
                                    playerViewModel.applyCloudQuality(RemoteMusicProvider.NETEASE, quality.value)
                                },
                            )
                        }
                    }
                }

                "QQ_MUSIC" -> {
                    QqAudioQuality.entries.forEach { quality ->
                        item(key = "qq_${quality.value}") {
                            QualityChoiceRow(
                                title = quality.qqTitle(),
                                subtitle = quality.qqDescription(),
                                icon = quality.playerIcon(),
                                selected = qqQuality == quality.value,
                                onClick = {
                                    playerViewModel.applyCloudQuality(RemoteMusicProvider.QQ_MUSIC, quality.value)
                                },
                            )
                        }
                    }
                }

                else -> {
                    item(key = "local_quality") {
                        QualityChoiceRow(
                            title = stringResource(R.string.player_local_original_quality),
                            subtitle = localQualityDescription(audioQualityInfo),
                            icon = Icons.Default.MusicNote,
                            selected = true,
                            onClick = {},
                        )
                    }
                }
            }

            item(key = "progress_divider") {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.Medium))
                Text(
                    text = stringResource(R.string.settings_progress_bar_style),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                )
            }
            ProgressBarStyle.presets.forEach { style ->
                item(key = "progress_${style.value}") {
                    QualityChoiceRow(
                        title = style.playerTitle(),
                        subtitle = stringResource(R.string.player_progress_style_desc),
                        icon =
                            when (style) {
                                ProgressBarStyle.ExpressiveWavy -> Icons.Default.Waves
                                ProgressBarStyle.DynamicWaveform -> Icons.Default.GraphicEq
                                ProgressBarStyle.TimeDomainWaveform -> Icons.Default.SurroundSound
                            },
                        selected = progressStyle == style.value,
                        onClick = { settingsViewModel.setProgressBarStyle(style.value) },
                    )
                }
            }
            item(key = "quality_bottom_space") { Spacer(Modifier.height(Spacing.ExtraLarge)) }
        }
    }
}

@Composable
private fun QualityChoiceRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color =
            if (selected) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        shape = Shapes.LargeCornerBasedShape,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun NeteaseAudioQuality.neteaseTitle(): String =
    stringResource(
        when (this) {
            NeteaseAudioQuality.AUTO -> R.string.netease_quality_auto
            NeteaseAudioQuality.STANDARD -> R.string.netease_quality_standard
            NeteaseAudioQuality.EXHIGH -> R.string.netease_quality_exhigh
            NeteaseAudioQuality.LOSSLESS -> R.string.netease_quality_lossless
            NeteaseAudioQuality.HIRES -> R.string.netease_quality_hires
            NeteaseAudioQuality.JY_EFFECT -> R.string.netease_quality_jy_effect
            NeteaseAudioQuality.SKY -> R.string.netease_quality_sky
            NeteaseAudioQuality.MASTER -> R.string.netease_quality_master
        },
    )

@Composable
private fun NeteaseAudioQuality.neteaseDescription(): String =
    stringResource(
        when (this) {
            NeteaseAudioQuality.AUTO -> R.string.netease_quality_auto_desc
            NeteaseAudioQuality.STANDARD -> R.string.netease_quality_standard_desc
            NeteaseAudioQuality.EXHIGH -> R.string.netease_quality_exhigh_desc
            NeteaseAudioQuality.LOSSLESS -> R.string.netease_quality_lossless_desc
            NeteaseAudioQuality.HIRES -> R.string.netease_quality_hires_desc
            NeteaseAudioQuality.JY_EFFECT -> R.string.netease_quality_jy_effect_desc
            NeteaseAudioQuality.SKY -> R.string.netease_quality_sky_desc
            NeteaseAudioQuality.MASTER -> R.string.netease_quality_master_desc
        },
    )

private fun NeteaseAudioQuality.playerIcon() =
    when (this) {
        NeteaseAudioQuality.AUTO -> Icons.Default.MusicNote
        NeteaseAudioQuality.STANDARD -> Icons.Default.Waves
        NeteaseAudioQuality.EXHIGH -> Icons.Default.GraphicEq
        NeteaseAudioQuality.LOSSLESS -> Icons.Default.HighQuality
        NeteaseAudioQuality.HIRES -> Icons.Default.SurroundSound
        NeteaseAudioQuality.JY_EFFECT -> Icons.Default.GraphicEq
        NeteaseAudioQuality.SKY -> Icons.Default.Waves
        NeteaseAudioQuality.MASTER -> Icons.Default.HighQuality
    }

@Composable
private fun QqAudioQuality.qqTitle(): String =
    stringResource(
        when (this) {
            QqAudioQuality.AUTO -> R.string.qq_quality_auto
            QqAudioQuality.STANDARD -> R.string.qq_quality_standard
            QqAudioQuality.HIGH -> R.string.qq_quality_high
            QqAudioQuality.LOSSLESS -> R.string.qq_quality_lossless
        },
    )

@Composable
private fun QqAudioQuality.qqDescription(): String =
    stringResource(
        when (this) {
            QqAudioQuality.AUTO -> R.string.qq_quality_auto_desc
            QqAudioQuality.STANDARD -> R.string.qq_quality_standard_desc
            QqAudioQuality.HIGH -> R.string.qq_quality_high_desc
            QqAudioQuality.LOSSLESS -> R.string.qq_quality_lossless_desc
        },
    )

private fun QqAudioQuality.playerIcon() =
    when (this) {
        QqAudioQuality.AUTO -> Icons.Default.MusicNote
        QqAudioQuality.STANDARD -> Icons.Default.Waves
        QqAudioQuality.HIGH -> Icons.Default.GraphicEq
        QqAudioQuality.LOSSLESS -> Icons.Default.HighQuality
    }

@Composable
private fun ProgressBarStyle.playerTitle(): String =
    stringResource(
        when (this) {
            ProgressBarStyle.ExpressiveWavy -> R.string.progress_bar_style_expressive_wavy
            ProgressBarStyle.DynamicWaveform -> R.string.progress_bar_style_dynamic_waveform
            ProgressBarStyle.TimeDomainWaveform -> R.string.progress_bar_style_time_domain_waveform
        },
    )

@Composable
private fun localQualityDescription(info: AudioQualityInfo): String {
    val values =
        buildList {
            if (info.sampleRate > 0) add("${info.sampleRate / 1000} kHz")
            if (info.bitRate > 0) add("${info.bitRate / 1000} kbps")
            if (info.isLossless) add(stringResource(R.string.lossless))
        }
    return values.joinToString(" · ").ifBlank { stringResource(R.string.player_local_quality_unknown) }
}

private fun String.cloudProviderLabel(): String =
    when (uppercase(Locale.ROOT)) {
        "NETEASE" -> "网易云音乐"
        "QQ_MUSIC" -> "QQ 音乐"
        "TELEGRAM" -> "Telegram"
        "JELLYFIN" -> "Jellyfin"
        "EMBY" -> "Emby"
        "SUBSONIC" -> "Subsonic"
        "ONLINE_SOURCE" -> "在线音源"
        else -> replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
    }

// ============================================
// UI 子组件
// ============================================

/**
 * 进度条区块。
 * 单独抽出该叶子组件，使每秒的播放位置更新只重组这里，
 * 而不是连带整个 [PlayerPage] 一起重组。
 */
@Composable
private fun SeekBarSection(
    seekPositionProvider: () -> Float,
    realPositionProvider: () -> Float,
    duration: Long,
    amplitudes: List<Int>,
    progressBarStyle: ProgressBarStyle,
    playerViewModel: PlayerViewModel,
    isPlaying: Boolean,
    isAppInForeground: Boolean,
    isSeekingState: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val seekPosition = seekPositionProvider()
    val realPosition = realPositionProvider()
    val sliderProgress = if (duration > 0) (seekPosition / duration).coerceIn(0f, 1f) else 0f
    if (progressBarStyle == ProgressBarStyle.ExpressiveWavy) {
        PlayerWavyProgressSection(
            progress = sliderProgress,
            currentPosition = realPosition.toLong(),
            duration = duration,
            isPlaying = isPlaying,
            isSeeking = isSeekingState,
            onProgressChange = onValueChange,
            onProgressChangeFinished = onValueChangeFinished,
            modifier =
                modifier.graphicsLayer {
                    val seekbarReveal =
                        calculateFadeAlpha(progressProvider(), SEEKBAR_REVEAL_THRESHOLD)
                    alpha = seekbarReveal
                    translationY = (1f - seekbarReveal) * 24f
                },
        )
        return
    }

    // FFT 插值计算随下方 collectAsStateWithLifecycle 收集自动启停，无需手动订阅/解绑
    val useDynamicWaveform = progressBarStyle == ProgressBarStyle.DynamicWaveform && isAppInForeground
    Column(
        modifier =
            modifier.graphicsLayer {
                val seekbarReveal =
                    calculateFadeAlpha(progressProvider(), SEEKBAR_REVEAL_THRESHOLD)
                alpha = seekbarReveal
                translationY = (1f - seekbarReveal) * 24f
            },
    ) {
        when (progressBarStyle) {
            ProgressBarStyle.ExpressiveWavy -> Unit

            ProgressBarStyle.DynamicWaveform -> {
                val fftDrawData =
                    if (useDynamicWaveform) {
                        val drawData by playerViewModel.fftDrawData.collectAsStateWithLifecycle()
                        drawData
                    } else {
                        EmptyFftDrawData
                    }
                AudioDynamicWaveSlider(
                    progress = sliderProgress,
                    fftAmplitudes = fftDrawData,
                    onProgressChange = {
                        onValueChange.invoke(it)
                    },
                    onProgressChangeFinished = {
                        onValueChangeFinished.invoke()
                    },
                    waveformBrush = SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                    progressBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                )
            }

            ProgressBarStyle.TimeDomainWaveform -> {
                AudioWaveSlider(
                    progress = sliderProgress,
                    amplitudes = amplitudes,
                    onProgressChange = {
                        onValueChange.invoke(it)
                    },
                    onProgressChangeFinished = {
                        onValueChangeFinished.invoke()
                    },
                    waveformBrush = SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest),
                    progressBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Small))
        // 当前位置 和 总时长（等宽数字避免走时跳动，两端与波形边缘对齐）
        val timeStyle =
            MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = "tnum",
            )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = formatTime(realPosition.toLong()),
                    style = timeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                // 滑动到的地方
                AnimatedVisibility(
                    visible = isSeekingState,
                ) {
                    Text(
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.inversePrimary,
                                    shape = Shapes.SmallCornerBasedShape,
                                ).padding(vertical = 4.dp, horizontal = 8.dp),
                        text = formatTime(seekPosition.toLong()),
                        style = timeStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                text = formatTime(duration),
                style = timeStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 4.dp),
            )
        }
    }
}

// ---------- 播放器控制组件 ----------

/**
 * 歌曲信息
 *
 * [titleTransition] / [artistTransition] 非 null 时，歌名与作者文本作为
 * 跳转全屏歌词的共享元素源节点：飞行期间本体隐藏，静止阶段记录源矩形。
 */
@Composable
private fun SongInfo(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    titleTransition: GeometryTransition? = null,
    artistTransition: GeometryTransition? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            title,
            transitionSpec = {
                materialSharedAxisYIn(true) togetherWith materialSharedAxisYOut(true)
            },
            contentKey = { it },
            modifier = Modifier.geometrySourceFor(titleTransition),
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
        AnimatedContent(
            artist,
            contentKey = { it },
            transitionSpec = {
                materialSharedAxisYIn(true) togetherWith materialSharedAxisYOut(true)
            },
            modifier = Modifier.geometrySourceFor(artistTransition),
        ) { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Modifier.geometrySourceFor(transition: GeometryTransition?): Modifier =
    if (transition == null) {
        this
    } else {
        this
            .graphicsLayer {
                alpha = if (transition.shouldShowSource()) 1f else 0f
            }.geometrySource(transition)
    }

// ============================================
// 工具函数
// ============================================

/**
 * 格式化时间 (毫秒 -> mm:ss)
 */
fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.CHINESE, "%d:%02d", minutes, seconds)
}

/**
 * 计算淡入透明度
 * @param progress 当前进度 (0-1)
 * @param threshold 开始淡入的阈值
 * @return 透明度值 (0-1)
 */
private fun calculateFadeAlpha(
    progress: Float,
    threshold: Float,
): Float =
    if (progress < threshold) {
        0f
    } else {
        ((progress - threshold) / (1f - threshold)).coerceIn(0f, 1f)
    }

private fun unifiedPlayerContentAlpha(progress: Float): Float {
    val t = ((progress.coerceIn(0f, 1f) - 0.42f) / 0.36f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun smoothStepProgress(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * 加载音频波形数据
 */
private suspend fun loadAmplitudeData(
    mediaItem: MediaItem?,
    amplituda: Amplituda,
    songUseCases: SongUseCases,
): List<Int> =
    withContext(Dispatchers.IO) {
        val config = mediaItem?.localConfiguration ?: return@withContext emptyList()

        // ALAC 和 MP4 格式不支持波形提取
        if (config.mimeType == MimeTypes.AUDIO_ALAC || config.mimeType == MimeTypes.AUDIO_MP4) {
            return@withContext emptyList()
        }

        val cache = mediaItem.mediaMetadata.extras?.getString("waveformData")

        if (cache == EMPTY_WAVEFORM_CACHE) {
            Timber.tag("ExpandedPlayerScreen").d("波形提取结果已缓存为空，跳过重复读取")
            return@withContext emptyList()
        }
        if (!TextUtils.isEmpty(cache)) {
            val cachedList = cache!!.split(",").mapNotNull { it.toIntOrNull() }
            if (cachedList.isNotEmpty()) {
                Timber.tag("ExpandedPlayerScreen").d("使用缓存的波形数据，长度: ${cachedList.size}")
                return@withContext cachedList
            }
        }
        Timber.tag("ExpandedPlayerScreen").d("没有缓存的波形数据，开始提取...")
        return@withContext try {
            val uri = config.uri
            openWaveformInputStream(uri)?.use { inputStream ->
                val tempFile =
                    File.createTempFile("amplitude_cache", null, App.getInstance().cacheDir)

                try {
                    tempFile.outputStream().use { outputStream ->
                        FileUtils.copy(inputStream, outputStream)
                    }

                    var result = emptyList<Int>()
                    Timber.tag("ExpandedPlayerScreen").d("处理缓存文件: ${tempFile.absolutePath}")
                    amplituda.processAudio(tempFile).get(
                        {
                            result = it.amplitudesAsList()
                        },
                        { result = emptyList() },
                    )
                    val serializedWaveform =
                        result
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(",")
                            ?: EMPTY_WAVEFORM_CACHE
                    mediaItem.mediaId
                        .toLongOrNull()
                        ?.takeIf { it > 0L }
                        ?.let { mediaStoreId ->
                            songUseCases.updateSongWaveform(
                                mediaId = mediaStoreId,
                                waveformData = serializedWaveform,
                            )
                        }
                    mediaItem.mediaMetadata.extras?.putString(
                        "waveformData",
                        serializedWaveform,
                    )
                    result
                } finally {
                    tempFile.delete()
                }
            } ?: emptyList()
        } catch (error: Throwable) {
            Timber
                .tag("ExpandedPlayerScreen")
                .w(error, "Unable to extract waveform for ${mediaItem.mediaId}")
            emptyList()
        }
    }

private fun openWaveformInputStream(uri: Uri): InputStream? =
    when (uri.scheme?.lowercase(Locale.ROOT)) {
        "http", "https" -> {
            val connection =
                (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept-Encoding", "identity")
                    requestMethod = "GET"
                }
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    null
                } else {
                    object : FilterInputStream(connection.inputStream) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                connection.disconnect()
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                connection.disconnect()
                throw error
            }
        }

        else -> App.getInstance().contentResolver.openInputStream(uri)
    }
