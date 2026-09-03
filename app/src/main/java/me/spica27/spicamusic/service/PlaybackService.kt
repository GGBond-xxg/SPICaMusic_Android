package me.spica27.spicamusic.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.spica27.spicamusic.BuildConfig
import me.spica27.spicamusic.DesktopLyricsPermissionActivity
import me.spica27.spicamusic.MainActivity
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.CloudPlaybackItemResolver
import me.spica27.spicamusic.cloud.CloudRecentStore
import me.spica27.spicamusic.diagnostics.DiagnosticLog
import me.spica27.spicamusic.feature.settings.domain.SettingsUseCases
import me.spica27.spicamusic.player.api.IMusicPlayer
import me.spica27.spicamusic.player.impl.SpicaPlayer
import me.spica27.spicamusic.player.impl.utils.MediaLibrary
import me.spica27.spicamusic.player.impl.utils.PlayerKVUtils
import me.spica27.spicamusic.topdisplay.TopDisplayModeController
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * 媒体播放后台服务
 * 使用 Media3 MediaLibraryService 实现后台播放
 * 集成 FFT 音频处理器进行频谱分析
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private val player: IMusicPlayer by inject()
    private val topDisplayModeController: TopDisplayModeController by inject()
    private val desktopLyricsController: DesktopLyricsController by inject()
    private val cloudPlaybackItemResolver: CloudPlaybackItemResolver by inject()
    private val cloudRecentStore: CloudRecentStore by inject()
    private val settingsUseCases: SettingsUseCases by inject()
    private val playerKVUtils: PlayerKVUtils by inject()
    private val cloudAudioCache by lazy { CloudAudioCache(this) }

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var sessionPlayer: Player
    private var audioSink: DefaultAudioSink? = null

    // 服务级别的协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    @Volatile
    private var backgroundPlaybackEnabled = true

    @Volatile
    private var resumeOnHeadsetEnabled = false

    @Volatile
    private var fadeEnabled = false

    @Volatile
    private var fadeDurationMs = DEFAULT_FADE_DURATION_MS

    @Volatile
    private var usbDacOutputEnabled = false

    @Volatile
    private var hiFiOutputEnabled = false

    @Volatile
    private var maxCloudAudioCacheBytes =
        CloudAudioCache.DEFAULT_MAX_MIB.toLong() * 1024L * 1024L

    private var resumeAfterDisconnect = false
    private var disconnectPauseAtMs = 0L
    private var fadeInStartedAtMs = 0L
    private var fadeMonitorJob: Job? = null
    private var manualFadeJob: Job? = null
    private var trackChangeFadePending = false
    private var cloudErrorSkipJob: Job? = null
    private var cloudPrefetchJob: Job? = null
    private var cloudSinkRecoveryJob: Job? = null
    private var diagnosticMonitorJob: Job? = null
    private var cloudPreviewHandledMediaId: String? = null
    private var cloudSinkEmptySinceMs = 0L
    private var cloudRecoveryMediaId: String? = null
    private var cloudRecoveryAttempts = 0
    private var cloudRecentCandidateMediaId: String? = null
    private var cloudRecentListeningSinceMs = 0L
    private var cloudRecentRecorded = false
    private var manualFadeActive = false
    private var legacyNotificationButtons: List<CommandButton> = emptyList()
    private var legacyNotificationCommands: SessionCommands? = null
    private val legacyActionRefreshHandler = Handler(Looper.getMainLooper())
    private val legacyActionRefreshRunnable = Runnable { refreshLegacySystemCustomActions() }
    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                Timber.tag("PlaybackService").i(
                    "audio-devices-added types=${addedDevices.joinToString { it.type.toString() }}",
                )
                applyPreferredUsbDevice()
                if (addedDevices.any(::isReconnectableOutput)) maybeResumeAfterReconnect()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                Timber.tag("PlaybackService").i(
                    "audio-devices-removed types=${removedDevices.joinToString { it.type.toString() }}",
                )
                applyPreferredUsbDevice()
                if (removedDevices.any(::isReconnectableOutput) && exoPlayer.playWhenReady) {
                    armReconnectResume()
                }
            }
        }
    private val playbackListener =
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                Timber.tag("PlaybackService").i(
                    "play-when-ready=$playWhenReady reason=$reason state=${exoPlayer.playbackState} " +
                        "isPlaying=${exoPlayer.isPlaying} suppression=${exoPlayer.playbackSuppressionReason} " +
                        "index=${exoPlayer.currentMediaItemIndex}",
                )
                when {
                    playWhenReady -> clearReconnectResume()
                    !resumeOnHeadsetEnabled -> clearReconnectResume()
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                        armReconnectResume()
                    }
                    else -> clearReconnectResume()
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                val uri = mediaItem?.localConfiguration?.uri
                Timber.tag("PlaybackService").i(
                    "media-transition reason=$reason index=${exoPlayer.currentMediaItemIndex}/" +
                        "${exoPlayer.mediaItemCount} id=${mediaItem?.mediaId} " +
                        "source=${uri?.scheme}://${uri?.host.orEmpty()}",
                )
                cloudErrorSkipJob?.cancel()
                cloudPreviewHandledMediaId = null
                cloudSinkEmptySinceMs = 0L
                if (cloudSinkRecoveryJob?.isActive != true || cloudRecoveryMediaId != mediaItem?.mediaId) {
                    cloudSinkRecoveryJob?.cancel()
                    cloudRecoveryMediaId = null
                    cloudRecoveryAttempts = 0
                }
                cloudRecentCandidateMediaId = null
                cloudRecentListeningSinceMs = 0L
                cloudRecentRecorded = false
                if (fadeEnabled && mediaItem != null) {
                    beginFadeIn()
                } else {
                    fadeInStartedAtMs = 0L
                    exoPlayer.volume = 1f
                }
                scheduleNextCloudPrefetch()
            }

            override fun onTimelineChanged(
                timeline: Timeline,
                reason: Int,
            ) {
                scheduleNextCloudPrefetch()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Timber.tag("PlaybackService").i(
                    "is-playing=$isPlaying playWhenReady=${exoPlayer.playWhenReady} " +
                        "state=${exoPlayer.playbackState} suppression=${exoPlayer.playbackSuppressionReason}",
                )
                if (isPlaying && fadeEnabled && exoPlayer.volume <= 0.01f) {
                    fadeInStartedAtMs = SystemClock.elapsedRealtime()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.tag("PlaybackService").e(
                    error,
                    "player-error code=${error.errorCode} index=${exoPlayer.currentMediaItemIndex}",
                )
                handleCloudPlaybackError(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Timber.tag("PlaybackService").i(
                    "playback-state=$playbackState playWhenReady=${exoPlayer.playWhenReady} " +
                        "isPlaying=${exoPlayer.isPlaying} buffered=${exoPlayer.bufferedPosition}",
                )
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                Timber.tag("PlaybackService").w(
                    "playback-suppression=$playbackSuppressionReason " +
                        "playWhenReady=${exoPlayer.playWhenReady} state=${exoPlayer.playbackState}",
                )
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Timber.tag("PlaybackService").i("audio-session-id=$audioSessionId")
            }

            override fun onVolumeChanged(volume: Float) {
                Timber.tag("PlaybackService").i("player-volume=$volume")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                Timber.tag("PlaybackService").i(
                    "position-discontinuity reason=$reason " +
                        "oldIndex=${oldPosition.mediaItemIndex} oldMs=${oldPosition.positionMs} " +
                        "newIndex=${newPosition.mediaItemIndex} newMs=${newPosition.positionMs}",
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                val selected =
                    tracks.groups
                        .filter { it.isSelected }
                        .flatMap { group ->
                            (0 until group.length)
                                .filter(group::isTrackSelected)
                                .map { index ->
                                    val format = group.getTrackFormat(index)
                                    "type=${group.type},mime=${format.sampleMimeType},codecs=${format.codecs}," +
                                        "rate=${format.sampleRate},channels=${format.channelCount},bitrate=${format.bitrate}"
                                }
                        }
                Timber.tag("PlaybackService").i("tracks-selected=${selected.joinToString(" | ")}")
            }

            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (BuildConfig.DIAGNOSTIC_LOGGING) {
                    val eventCodes = (0 until events.size()).joinToString { events.get(it).toString() }
                    Timber.tag("PlaybackService").d(
                        "events=[$eventCodes] index=${player.currentMediaItemIndex}/${player.mediaItemCount} " +
                            "position=${player.currentPosition} buffered=${player.bufferedPosition} " +
                            "duration=${player.duration} state=${player.playbackState} " +
                            "playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying}",
                    )
                }
                // Media3 rebuilds the platform PlaybackState after player changes. HyperOS reads
                // only that legacy state, so restore our custom actions after Media3 has updated it.
                scheduleLegacySystemCustomActionsRefresh()
            }
        }

    private inner class FadeAwarePlayer(
        private val delegatePlayer: ExoPlayer,
    ) : ForwardingPlayer(delegatePlayer) {
        override fun play() = requestPlayWithFade { delegatePlayer.play() }

        override fun pause() = requestFadeOut { delegatePlayer.pause() }

        override fun stop() = requestFadeOut { delegatePlayer.stop() }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) {
                requestPlayWithFade { delegatePlayer.playWhenReady = true }
            } else {
                requestFadeOut { delegatePlayer.playWhenReady = false }
            }
        }

        override fun seekToNext() = requestTrackChangeWithFade { delegatePlayer.seekToNext() }

        override fun seekToNextMediaItem() = requestTrackChangeWithFade { delegatePlayer.seekToNextMediaItem() }

        override fun seekToPrevious() = requestTrackChangeWithFade { delegatePlayer.seekToPrevious() }

        override fun seekToPreviousMediaItem() = requestTrackChangeWithFade { delegatePlayer.seekToPreviousMediaItem() }

        override fun seekTo(
            mediaItemIndex: Int,
            positionMs: Long,
        ) {
            if (mediaItemIndex == delegatePlayer.currentMediaItemIndex) {
                delegatePlayer.seekTo(mediaItemIndex, positionMs)
            } else {
                requestTrackChangeWithFade { delegatePlayer.seekTo(mediaItemIndex, positionMs) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag("PlaybackService").i(
            "service-created sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER}/${Build.MODEL}",
        )
        setMediaNotificationProvider(
            SpicaNotificationProvider(this),
        )
        val initialHiFi =
            PlaybackAudioCapabilities.supportsFloatOutput() &&
                settingsUseCases.getCachedBoolean(SettingsUseCases.Keys.HIFI_MODE, false)
        hiFiOutputEnabled = initialHiFi
        usbDacOutputEnabled =
            settingsUseCases.getCachedBoolean(SettingsUseCases.Keys.USB_DAC_OUTPUT, false)
        // 创建自定义渲染器工厂，添加音频处理器（FFT、EQ、混响）
        val renderersFactory =
            object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: android.content.Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink =
                    DefaultAudioSink
                        .Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(
                            // 音频处理链: FFT -> EQ -> Reverb
                            (player as? me.spica27.spicamusic.player.impl.SpicaPlayer)
                                ?.getAudioProcessors()
                                ?: arrayOf(player.fftAudioProcessor),
                        ).build()
                        .apply {
                            setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
                            audioSink = this
                            if (usbDacOutputEnabled) {
                                setPreferredDevice(PlaybackAudioCapabilities.usbOutput(this@PlaybackService))
                            }
                        }
            }.apply {
                setEnableAudioFloatOutput(initialHiFi)
            }
        exoPlayer =
            ExoPlayer
                .Builder(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        createAttributionContext("audioPlayback")
                    } else {
                        this
                    },
                    renderersFactory,
                ).setMediaSourceFactory(DefaultMediaSourceFactory(cloudAudioCache.dataSourceFactory))
                .setLoadControl(createLoadControl())
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
                .setHandleAudioBecomingNoisy(true)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
                        .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setUsePlatformDiagnostics(false)
                .build()
        exoPlayer.addListener(playbackListener)
        sessionPlayer = FadeAwarePlayer(exoPlayer)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        startSettingsObservers()
        startCacheTrimMonitor()
        startFadeMonitor()
        startDiagnosticMonitor()
        topDisplayModeController.start(exoPlayer)
        desktopLyricsController.start(exoPlayer)

        val desktopLyricsCommand = SessionCommand(ACTION_TOGGLE_DESKTOP_LYRICS, android.os.Bundle.EMPTY)
        val closePlayerCommand = SessionCommand(ACTION_CLOSE_PLAYER, android.os.Bundle.EMPTY)
        val notificationButtons = notificationCommandButtons(desktopLyricsCommand, closePlayerCommand)
        val notificationSessionCommands =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(desktopLyricsCommand)
                .add(closePlayerCommand)
                .build()
        legacyNotificationButtons = notificationButtons
        legacyNotificationCommands = notificationSessionCommands

        mediaSession =
            MediaLibrarySession
                .Builder(
                    this,
                    sessionPlayer,
                    object : MediaLibrarySession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult =
                            MediaSession.ConnectionResult
                                .AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(
                                    notificationSessionCommands,
                                ).setAvailablePlayerCommands(notificationPlayerCommands(session))
                                .setCustomLayout(notificationButtons)
                                .build()

                        override fun onPostConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ) {
                            // Platform/legacy SystemUI reads PlaybackState.customActions. Re-applying
                            // commands after connection makes Media3 publish these buttons there too.
                            session.setAvailableCommands(
                                controller,
                                notificationSessionCommands,
                                notificationPlayerCommands(session),
                            )
                            session.setCustomLayout(controller, notificationButtons)
                        }

                        override fun onCustomCommand(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            customCommand: SessionCommand,
                            args: android.os.Bundle,
                        ): ListenableFuture<SessionResult> {
                            when (customCommand.customAction) {
                                ACTION_TOGGLE_DESKTOP_LYRICS -> {
                                    startActivity(
                                        Intent(
                                            this@PlaybackService,
                                            DesktopLyricsPermissionActivity::class.java,
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }

                                ACTION_CLOSE_PLAYER -> closePlayerAndApp()
                                else ->
                                    return Futures.immediateFuture(
                                        SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                                    )
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }

                        override fun onAddMediaItems(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: List<MediaItem>,
                        ): ListenableFuture<List<MediaItem>> =
                            serviceScope.future {
                                mediaItems.map { cloudPlaybackItemResolver.resolve(it) }
                            }

                        override fun onGetLibraryRoot(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            params: LibraryParams?,
                        ): ListenableFuture<LibraryResult<MediaItem>> =
                            Futures.immediateFuture(
                                LibraryResult.ofItem(
                                    MediaItem
                                        .Builder()
                                        .setMediaId(MediaLibrary.ROOT)
                                        .build(),
                                    params,
                                ),
                            )

                        override fun onGetItem(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            mediaId: String,
                        ): ListenableFuture<LibraryResult<MediaItem>> {
                            Timber.tag("PlaybackService").d("onGetItem: mediaId=$mediaId")
                            return serviceScope.future {
                                val item = MediaLibrary.getItem(mediaId)
                                if (item != null) {
                                    Timber
                                        .tag("PlaybackService")
                                        .d("onGetItem: Found item ${item.mediaMetadata.title}")
                                    LibraryResult.ofItem(item, null)
                                } else {
                                    Timber
                                        .tag("PlaybackService")
                                        .e("onGetItem: Item not found for mediaId=$mediaId")
                                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                                }
                            }
                        }

                        override fun onGetChildren(
                            session: MediaLibrarySession,
                            browser: MediaSession.ControllerInfo,
                            parentId: String,
                            page: Int,
                            pageSize: Int,
                            params: LibraryParams?,
                        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
                            serviceScope.future {
                                val children = MediaLibrary.getChildren(parentId)
                                LibraryResult.ofItemList(children, params)
                            }

//                        override fun onPlaybackResumption(
//                            session: MediaSession,
//                            controller: MediaSession.ControllerInfo,
//                        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
//                            Timber.tag("PlaybackService").d("onPlaybackResumption called")
//                            // 从播放历史恢复最后播放的歌曲列表
//                            val items = MediaLibrary.getChildren(MediaLibrary.ALL_SONGS)
//                            return Futures.immediateFuture(
//                                MediaSession.MediaItemsWithStartPosition(
//                                    items,
//                                    0,
//                                    0,
//                                ),
//                            )
//                        }
                    },
                ).setSessionActivity(
                    android.app.PendingIntent.getActivity(
                        this,
                        0,
                        packageManager.getLaunchIntentForPackage(packageName),
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).setCustomLayout(notificationButtons)
                .build()
        publishLegacySystemCustomActions(
            requireNotNull(mediaSession),
            notificationButtons,
            notificationSessionCommands,
        )
        scheduleLegacySystemCustomActionsRefresh(1_000L)
    }

    /**
     * Media3 1.10 exposes the buttons to modern controllers but its platform compatibility
     * session can keep the default command set, causing PlaybackState.customActions to stay
     * empty on HyperOS. Synchronize that internal compatibility state so SystemUI receives the
     * same buttons. The reflection is deliberately isolated and safely falls back to Media3's
     * regular behavior if its internals change in a future upgrade.
     */
    private fun publishLegacySystemCustomActions(
        session: MediaSession,
        buttons: List<CommandButton>,
        commands: SessionCommands,
    ) {
        runCatching {
            val implField = MediaSession::class.java.getDeclaredField("impl").apply { isAccessible = true }
            val impl = requireNotNull(implField.get(session))
            val legacyStubField =
                generateSequence(impl.javaClass) { it.superclass }
                    .mapNotNull { type ->
                        runCatching { type.getDeclaredField("sessionLegacyStub") }.getOrNull()
                    }.first()
                    .apply { isAccessible = true }
            val legacyStub = requireNotNull(legacyStubField.get(impl))
            val playerWrapper =
                generateSequence(impl.javaClass) { it.superclass }
                    .flatMap { it.declaredMethods.asSequence() }
                    .first { it.name == "getPlayerWrapper" && it.parameterCount == 0 }
                    .apply { isAccessible = true }
                    .invoke(impl)
            val availablePlayerCommands = notificationPlayerCommands(session)
            legacyStub.javaClass
                .getDeclaredMethod(
                    "setAvailableCommands",
                    SessionCommands::class.java,
                    Player.Commands::class.java,
                ).apply { isAccessible = true }
                .invoke(legacyStub, commands, availablePlayerCommands)

            val immutableButtons = ImmutableList.copyOf(buttons)
            legacyStub.javaClass
                .getDeclaredMethod("setPlatformCustomLayout", ImmutableList::class.java)
                .apply { isAccessible = true }
                .invoke(legacyStub, immutableButtons)
            legacyStub.javaClass.declaredMethods
                .first {
                    it.name == "updateLegacySessionPlaybackStateAndQueue" &&
                        it.parameterCount == 1
                }.apply { isAccessible = true }
                .invoke(legacyStub, playerWrapper)
        }.onFailure { error ->
            Timber.tag("PlaybackService").w(error, "Unable to publish legacy system custom actions")
        }
    }

    private fun scheduleLegacySystemCustomActionsRefresh(delayMs: Long = 100L) {
        if (legacyNotificationButtons.isEmpty() || legacyNotificationCommands == null) return
        legacyActionRefreshHandler.removeCallbacks(legacyActionRefreshRunnable)
        legacyActionRefreshHandler.postDelayed(legacyActionRefreshRunnable, delayMs)
    }

    private fun refreshLegacySystemCustomActions() {
        val session = mediaSession ?: return
        val commands = legacyNotificationCommands ?: return
        publishLegacySystemCustomActions(session, legacyNotificationButtons, commands)
    }

    private fun notificationPlayerCommands(session: MediaSession): Player.Commands =
        session.player.availableCommands
            .buildUpon()
            // Cloud streams are served through our range-capable loopback proxies. Media3 can
            // temporarily classify a freshly opened progressive stream as unseekable before its
            // range response has been observed, which removes this command from controllers and
            // makes an otherwise valid seek a silent no-op. Keep current-item seeking available;
            // the proxy and ExoPlayer will resolve the requested byte range when it is submitted.
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    private fun notificationCommandButtons(
        desktopLyricsCommand: SessionCommand,
        closePlayerCommand: SessionCommand,
    ): List<CommandButton> =
        listOf(
            CommandButton
                .Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_notification_desktop_lyrics)
                .setDisplayName(getString(R.string.notification_desktop_lyrics))
                .setSessionCommand(desktopLyricsCommand)
                .setSlots(CommandButton.SLOT_BACK_SECONDARY)
                .build(),
            CommandButton
                .Builder(CommandButton.ICON_STOP)
                .setCustomIconResId(R.drawable.ic_notification_close)
                .setDisplayName(getString(R.string.notification_close))
                .setSessionCommand(closePlayerCommand)
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
                .build(),
        )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Timber.tag("PlaybackService").i(
            "start-command action=${intent?.action} flags=$flags startId=$startId",
        )
        return when (intent?.action) {
            ACTION_TOGGLE_DESKTOP_LYRICS -> {
                desktopLyricsController.toggle()
                START_STICKY
            }

            ACTION_SHOW_DESKTOP_LYRICS -> {
                desktopLyricsController.show()
                START_STICKY
            }

            ACTION_CLOSE_PLAYER -> {
                closePlayerAndApp()
                START_NOT_STICKY
            }

            else -> super.onStartCommand(intent, flags, startId)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.tag("PlaybackService").w(
            "task-removed backgroundPlayback=$backgroundPlaybackEnabled " +
                "playing=${exoPlayer.isPlaying} playWhenReady=${exoPlayer.playWhenReady} " +
                "state=${exoPlayer.playbackState}",
        )
        if (!backgroundPlaybackEnabled) {
            exoPlayer.pause()
            exoPlayer.stop()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Timber.tag("PlaybackService").w(
            "service-destroyed playing=${exoPlayer.isPlaying} " +
                "playWhenReady=${exoPlayer.playWhenReady} state=${exoPlayer.playbackState}",
        )
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        legacyActionRefreshHandler.removeCallbacks(legacyActionRefreshRunnable)
        fadeMonitorJob?.cancel()
        manualFadeJob?.cancel()
        cloudErrorSkipJob?.cancel()
        cloudPrefetchJob?.cancel()
        diagnosticMonitorJob?.cancel()
        exoPlayer.removeListener(playbackListener)
        topDisplayModeController.release()
        desktopLyricsController.release()
        serviceScope.cancel()
        playerScope.cancel()
        mediaSession?.run {
            exoPlayer.release()
            release()
            mediaSession = null
        }
        cloudAudioCache.release()
        super.onDestroy()
    }

    private fun scheduleNextCloudPrefetch() {
        cloudPrefetchJob?.cancel()
        val nextItems =
            buildList {
                var nextIndex = exoPlayer.nextMediaItemIndex
                while (
                    size < CLOUD_PREFETCH_AHEAD_COUNT &&
                    nextIndex in 0 until exoPlayer.mediaItemCount
                ) {
                    add(exoPlayer.getMediaItemAt(nextIndex))
                    val followingIndex =
                        exoPlayer.currentTimeline.getNextWindowIndex(
                            nextIndex,
                            exoPlayer.repeatMode,
                            exoPlayer.shuffleModeEnabled,
                        )
                    if (
                        followingIndex == C.INDEX_UNSET ||
                        followingIndex == nextIndex ||
                        followingIndex == exoPlayer.currentMediaItemIndex
                    ) {
                        break
                    }
                    nextIndex = followingIndex
                }
            }
        if (nextItems.isEmpty()) return
        cloudPrefetchJob =
            serviceScope.launch {
                delay(CLOUD_PREFETCH_DELAY_MS)
                nextItems.forEach { item ->
                    launch {
                        runCatching { cloudPlaybackItemResolver.prefetch(item) }
                            .onFailure {
                                Timber.tag("PlaybackService").d(it, "Unable to prefetch cloud stream")
                            }
                    }
                }
            }
    }

    /**
     * Media3's default initial buffer is deliberately conservative (roughly 2.5 seconds). That is
     * unnecessary for the app's cached audio proxy and makes cloud rows feel unresponsive even
     * after the command itself was handled immediately. Keep the long steady-state buffer while
     * allowing playback to begin after a smaller, still safe amount has arrived.
     */
    private fun createLoadControl(): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(
                STREAM_MIN_BUFFER_MS,
                STREAM_MAX_BUFFER_MS,
                STREAM_PLAYBACK_START_BUFFER_MS,
                STREAM_REBUFFER_START_BUFFER_MS,
            ).build()

    private fun buildExoPlayer(enableHiFi: Boolean): ExoPlayer {
        val renderersFactory =
            object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink =
                    DefaultAudioSink
                        .Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(
                            (player as? SpicaPlayer)?.getAudioProcessors()
                                ?: arrayOf(player.fftAudioProcessor),
                        ).build()
                        .apply {
                            setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
                            audioSink = this
                            if (usbDacOutputEnabled) {
                                setPreferredDevice(PlaybackAudioCapabilities.usbOutput(this@PlaybackService))
                            }
                        }
            }.apply {
                setEnableAudioFloatOutput(enableHiFi)
            }
        return ExoPlayer
            .Builder(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    createAttributionContext("audioPlayback")
                } else {
                    this
                },
                renderersFactory,
            ).setMediaSourceFactory(DefaultMediaSourceFactory(cloudAudioCache.dataSourceFactory))
            .setLoadControl(createLoadControl())
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
                    .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            ).setUsePlatformDiagnostics(false)
            .build()
    }

    private val audioPipelineMutex = Mutex()

    private suspend fun reconfigureAudioPipeline(enableHiFi: Boolean) =
        audioPipelineMutex.withLock {
            if (enableHiFi == hiFiOutputEnabled) return@withLock
            val oldPlayer = exoPlayer
            val mediaItems = List(oldPlayer.mediaItemCount) { oldPlayer.getMediaItemAt(it) }
            val currentIndex = oldPlayer.currentMediaItemIndex
            val initialPosition = oldPlayer.currentPosition
            val playWhenReady = oldPlayer.playWhenReady
            val repeatMode = oldPlayer.repeatMode
            val shuffleModeEnabled = oldPlayer.shuffleModeEnabled
            val playbackParameters = oldPlayer.playbackParameters

            manualFadeJob?.cancel()
            manualFadeActive = false
            fadeInStartedAtMs = 0L

            val replacement = buildExoPlayer(enableHiFi)
            replacement.repeatMode = repeatMode
            replacement.shuffleModeEnabled = shuffleModeEnabled
            replacement.playbackParameters = playbackParameters
            if (mediaItems.isNotEmpty()) {
                replacement.setMediaItems(
                    mediaItems,
                    currentIndex.coerceIn(0, mediaItems.lastIndex),
                    initialPosition.coerceAtLeast(0L),
                )
                replacement.prepare()
                val prepared =
                    withTimeoutOrNull(8_000L) {
                        while (
                            replacement.playbackState != Player.STATE_READY &&
                            replacement.playerError == null
                        ) {
                            delay(20L)
                        }
                        replacement.playbackState == Player.STATE_READY
                    } == true
                if (!prepared) {
                    replacement.release()
                    Timber.tag("PlaybackService").w("Hi-Fi pipeline warm-up failed; keeping current player")
                    return@withLock
                }
            }

            // Seek to the position reached while the replacement was warming, then start it at
            // zero volume. The brief overlap masks the AudioTrack hand-off without pausing music.
            replacement.seekTo(oldPlayer.currentPosition.coerceAtLeast(0L))
            replacement.volume = if (playWhenReady) 0f else oldPlayer.volume
            replacement.addListener(playbackListener)
            replacement.playWhenReady = playWhenReady
            if (playWhenReady && mediaItems.isNotEmpty()) {
                withTimeoutOrNull(800L) {
                    while (!replacement.isPlaying && replacement.playerError == null) delay(12L)
                }
            }

            exoPlayer = replacement
            sessionPlayer = FadeAwarePlayer(replacement)
            mediaSession?.setPlayer(sessionPlayer)
            oldPlayer.removeListener(playbackListener)
            if (playWhenReady) {
                repeat(8) { step ->
                    val fraction = (step + 1) / 8f
                    replacement.volume = fraction
                    oldPlayer.volume = 1f - fraction
                    delay(18L)
                }
            }
            oldPlayer.release()

            topDisplayModeController.start(replacement)
            desktopLyricsController.start(replacement)
            hiFiOutputEnabled = enableHiFi
            Timber
                .tag("PlaybackService")
                .i("Audio pipeline warm-swapped: Hi-Fi=$enableHiFi, EQ=${!enableHiFi}")
        }

    private fun handleCloudPlaybackError(error: PlaybackException) {
        val failedItem = exoPlayer.currentMediaItem ?: return
        if (!failedItem.mediaId.startsWith(CLOUD_MEDIA_ID_PREFIX)) return
        val failedMediaId = failedItem.mediaId
        val failedTitle =
            failedItem.mediaMetadata.title
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?: getString(R.string.unknown_song)
        val nextIndex = exoPlayer.currentMediaItemIndex + 1
        val canSkip = nextIndex in 0 until exoPlayer.mediaItemCount
        val responseCode =
            generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()
                ?.responseCode
        val restricted = isRestrictedCloudHttpStatus(responseCode)
        Toast
            .makeText(
                this,
                getString(
                    when {
                        restricted && canSkip -> R.string.cloud_playback_restricted_skipping
                        restricted -> R.string.cloud_playback_restricted_stopped
                        canSkip -> R.string.cloud_playback_failed_skipping
                        else -> R.string.cloud_playback_failed_stopped
                    },
                    failedTitle,
                ),
                Toast.LENGTH_LONG,
            ).show()
        Timber
            .tag("PlaybackService")
            .w(
                error,
                "Cloud playback failed: mediaId=%s, http=%s, restricted=%s, skip=%s",
                failedMediaId,
                responseCode,
                restricted,
                canSkip,
            )
        cloudErrorSkipJob?.cancel()
        cloudErrorSkipJob =
            playerScope.launch {
                delay(CLOUD_ERROR_SKIP_DELAY_MS)
                if (exoPlayer.currentMediaItem?.mediaId != failedMediaId) return@launch
                if (canSkip) {
                    exoPlayer.seekTo(nextIndex, 0L)
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else {
                    exoPlayer.pause()
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                }
            }
    }

    private fun handleCloudPreviewEnded(item: MediaItem) {
        val mediaId = item.mediaId
        if (cloudPreviewHandledMediaId == mediaId) return
        cloudPreviewHandledMediaId = mediaId
        val title =
            item.mediaMetadata.title
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?: getString(R.string.unknown_song)
        val nextIndex = exoPlayer.currentMediaItemIndex + 1
        val canSkip = nextIndex in 0 until exoPlayer.mediaItemCount
        Toast
            .makeText(
                this,
                getString(
                    if (canSkip) {
                        R.string.cloud_playback_preview_ended_skipping
                    } else {
                        R.string.cloud_playback_preview_ended_stopped
                    },
                    title,
                ),
                Toast.LENGTH_LONG,
            ).show()
        Timber
            .tag("PlaybackService")
            .w(
                "Cloud stream stopped feeding decoded audio before its timeline ended: mediaId=%s, position=%d, buffered=%d, skip=%s",
                mediaId,
                exoPlayer.currentPosition,
                exoPlayer.bufferedPosition,
                canSkip,
            )
        if (canSkip) {
            exoPlayer.seekToNextMediaItem()
        } else {
            exoPlayer.pause()
            exoPlayer.stop()
        }
    }

    private fun closePlayerAndApp() {
        cloudErrorSkipJob?.cancel()
        desktopLyricsController.hide()
        exoPlayer.pause()
        val currentIndex = exoPlayer.currentMediaItemIndex
        if (currentIndex >= 0 && exoPlayer.mediaItemCount > 0) {
            playerKVUtils.setHistoryMediaItems(
                List(exoPlayer.mediaItemCount) { exoPlayer.getMediaItemAt(it) },
            )
            playerKVUtils.setHistoryPosition(currentIndex)
            playerKVUtils.setHistoryProgressMs(exoPlayer.currentPosition)
            // This commit also makes the preceding SharedPreferences writes durable before the
            // process exits, so reopening can restore the same queue and current song.
            playerKVUtils.setCurrentMediaId(exoPlayer.currentMediaItem?.mediaId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent(MainActivity.ACTION_EXIT_APP).setPackage(packageName))
        stopSelf()
        Handler(Looper.getMainLooper()).postDelayed(
            { android.os.Process.killProcess(android.os.Process.myPid()) },
            PROCESS_EXIT_DELAY_MS,
        )
    }

    private fun startSettingsObservers() {
        serviceScope.launch {
            settingsUseCases
                .getString(
                    SettingsUseCases.Keys.CLOUD_AUDIO_CACHE_MIB,
                    CloudAudioCache.DEFAULT_MAX_MIB.toString(),
                ).collect { saved ->
                    val maxMib =
                        saved.toLongOrNull()?.coerceIn(128L, 8192L)
                            ?: CloudAudioCache.DEFAULT_MAX_MIB.toLong()
                    maxCloudAudioCacheBytes = maxMib * 1024L * 1024L
                    cloudAudioCache.trimTo(maxCloudAudioCacheBytes)
                }
        }
        serviceScope.launch {
            settingsUseCases
                .getBoolean(SettingsUseCases.Keys.BACKGROUND_PLAYBACK, true)
                .collect {
                    backgroundPlaybackEnabled = it
                    Timber.tag("PlaybackService").i("background-playback-setting=$it")
                }
        }
        serviceScope.launch {
            settingsUseCases
                .getBoolean(SettingsUseCases.Keys.RESUME_ON_HEADSET, false)
                .collect {
                    resumeOnHeadsetEnabled = it
                    if (!it) clearReconnectResume()
                }
        }
        serviceScope.launch {
            settingsUseCases
                .getBoolean(SettingsUseCases.Keys.FADE_ENABLED, false)
                .collect {
                    fadeEnabled = it
                    if (!it) {
                        manualFadeJob?.cancel()
                        trackChangeFadePending = false
                        manualFadeActive = false
                        fadeInStartedAtMs = 0L
                        playerScope.launch { exoPlayer.volume = 1f }
                    }
                }
        }
        serviceScope.launch {
            settingsUseCases
                .getFloat(SettingsUseCases.Keys.FADE_DURATION_MS, DEFAULT_FADE_DURATION_MS.toFloat())
                .collect { fadeDurationMs = it.toLong().coerceIn(1_000L, 8_000L) }
        }
        serviceScope.launch {
            settingsUseCases
                .getBoolean(SettingsUseCases.Keys.USB_DAC_OUTPUT, false)
                .collect {
                    usbDacOutputEnabled = it
                    playerScope.launch { applyPreferredUsbDevice() }
                }
        }
        serviceScope.launch {
            combine(
                settingsUseCases.getBoolean(SettingsUseCases.Keys.HIFI_MODE, false),
                settingsUseCases.getBoolean(SettingsUseCases.Keys.EQ_ENABLED, false),
            ) { requestedHiFi, eqEnabled -> requestedHiFi to eqEnabled }
                .collect { (requestedHiFi, eqEnabled) ->
                    val effectiveHiFi =
                        requestedHiFi && PlaybackAudioCapabilities.supportsFloatOutput()
                    if (effectiveHiFi && eqEnabled) {
                        settingsUseCases.setBoolean(SettingsUseCases.Keys.EQ_ENABLED, false)
                    }
                    player.setEQEnabled(eqEnabled && !effectiveHiFi)
                    playerScope.launch { reconfigureAudioPipeline(effectiveHiFi) }
                }
        }
        serviceScope.launch {
            settingsUseCases
                .getFloatList(SettingsUseCases.Keys.EQ_BANDS, List(10) { 0f })
                .collect { player.setAllEQBands(it.toFloatArray()) }
        }
        serviceScope.launch {
            settingsUseCases
                .getBoolean(SettingsUseCases.Keys.REVERB_ENABLED, false)
                .collect(player::setReverbEnabled)
        }
        serviceScope.launch {
            settingsUseCases
                .getFloat(SettingsUseCases.Keys.REVERB_LEVEL, 0.3f)
                .collect { level ->
                    val room =
                        settingsUseCases
                            .getFloat(SettingsUseCases.Keys.REVERB_ROOM_SIZE, 0.5f)
                            .first()
                    player.setReverb(level, room)
                }
        }
        serviceScope.launch {
            settingsUseCases
                .getFloat(SettingsUseCases.Keys.REVERB_ROOM_SIZE, 0.5f)
                .collect { room ->
                    val level =
                        settingsUseCases
                            .getFloat(SettingsUseCases.Keys.REVERB_LEVEL, 0.3f)
                            .first()
                    player.setReverb(level, room)
                }
        }
        serviceScope.launch {
            settingsUseCases
                .getBoolean(SettingsUseCases.Keys.LOUDNESS_NORMALIZATION_ENABLED, false)
                .collect(player::setLoudnessNormalizationEnabled)
        }
    }

    private fun startCacheTrimMonitor() {
        serviceScope.launch {
            while (isActive) {
                delay(CACHE_TRIM_INTERVAL_MS)
                cloudAudioCache.trimTo(maxCloudAudioCacheBytes)
            }
        }
    }

    private fun startDiagnosticMonitor() {
        if (!BuildConfig.DIAGNOSTIC_LOGGING) return
        diagnosticMonitorJob?.cancel()
        diagnosticMonitorJob =
            playerScope.launch {
                while (isActive) {
                    delay(DIAGNOSTIC_PLAYBACK_INTERVAL_MS)
                    val item = exoPlayer.currentMediaItem
                    Timber.tag("PlaybackHeartbeat").i(
                        "id=${item?.mediaId.orEmpty()} index=${exoPlayer.currentMediaItemIndex}/" +
                            "${exoPlayer.mediaItemCount} position=${exoPlayer.currentPosition} " +
                            "buffered=${exoPlayer.bufferedPosition} duration=${exoPlayer.duration} " +
                            "state=${exoPlayer.playbackState} playWhenReady=${exoPlayer.playWhenReady} " +
                            "isPlaying=${exoPlayer.isPlaying} suppression=${exoPlayer.playbackSuppressionReason} " +
                            "volume=${exoPlayer.volume} repeat=${exoPlayer.repeatMode} " +
                            "shuffle=${exoPlayer.shuffleModeEnabled} hiFi=$hiFiOutputEnabled " +
                            "usbDac=$usbDacOutputEnabled fade=$fadeEnabled background=$backgroundPlaybackEnabled " +
                            "sinkPending=${runCatching { audioSink?.hasPendingData() }.getOrNull()}",
                    )
                    DiagnosticLog.writeRuntimeSnapshot(this@PlaybackService, "playback-heartbeat")
                }
            }
    }

    private fun startFadeMonitor() {
        fadeMonitorJob?.cancel()
        fadeMonitorJob =
            playerScope.launch {
                while (isActive) {
                    monitorCloudStreamHealth(SystemClock.elapsedRealtime())
                    if (manualFadeActive) {
                        delay(FADE_TICK_MS)
                        continue
                    }
                    if (!fadeEnabled || !exoPlayer.isPlaying) {
                        if (!fadeEnabled) exoPlayer.volume = 1f
                        delay(FADE_TICK_MS)
                        continue
                    }
                    val now = SystemClock.elapsedRealtime()
                    val fadeInGain =
                        if (fadeInStartedAtMs > 0L) {
                            ((now - fadeInStartedAtMs).toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    if (fadeInGain >= 1f) fadeInStartedAtMs = 0L
                    val duration = exoPlayer.duration
                    val remaining = duration - exoPlayer.currentPosition
                    val fadeOutGain =
                        if (duration > 0L && remaining in 0..fadeDurationMs) {
                            (remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    exoPlayer.volume = minOf(fadeInGain, fadeOutGain)
                    delay(FADE_TICK_MS)
                }
            }
    }

    private fun monitorCloudStreamHealth(nowMs: Long) {
        val item = exoPlayer.currentMediaItem
        recordCloudRecentWhenEligible(item, nowMs)
        val duration = exoPlayer.duration
        val remaining = duration - exoPlayer.currentPosition
        val sinkHasAudio = runCatching { audioSink?.hasPendingData() == true }.getOrDefault(true)
        val action =
            cloudAudioUnderrunAction(
                isCloudItem = item?.mediaId?.startsWith(CLOUD_MEDIA_ID_PREFIX) == true,
                explicitPreview = item?.let(cloudPlaybackItemResolver::isExplicitPreview) == true,
                stillSameItem =
                    item != null &&
                        item.mediaId.startsWith(CLOUD_MEDIA_ID_PREFIX) &&
                        cloudPreviewHandledMediaId != item.mediaId,
                isPlaying = exoPlayer.isPlaying,
                playbackState = exoPlayer.playbackState,
                sinkHasPendingData = sinkHasAudio,
            )
        val stalled =
            action != CloudAudioUnderrunAction.NONE &&
                (duration <= 0L || remaining > CLOUD_NATURAL_END_GUARD_MS) &&
                item != null
        if (!stalled) {
            cloudSinkEmptySinceMs = 0L
            return
        }
        if (cloudSinkEmptySinceMs == 0L) {
            cloudSinkEmptySinceMs = nowMs
            return
        }
        if (nowMs - cloudSinkEmptySinceMs >= CLOUD_UNDERRUN_CONFIRM_MS) {
            cloudSinkEmptySinceMs = 0L
            when (action) {
                CloudAudioUnderrunAction.SKIP_PREVIEW -> handleCloudPreviewEnded(item)
                CloudAudioUnderrunAction.RESTART_STREAM -> recoverSilentCloudStream(item)
                CloudAudioUnderrunAction.NONE -> Unit
            }
        }
    }

    private fun recoverSilentCloudStream(item: MediaItem) {
        if (cloudSinkRecoveryJob?.isActive == true) return
        val mediaId = item.mediaId
        if (cloudRecoveryMediaId != mediaId) {
            cloudRecoveryMediaId = mediaId
            cloudRecoveryAttempts = 0
        }
        cloudRecoveryAttempts += 1
        val attempt = cloudRecoveryAttempts
        val index = exoPlayer.currentMediaItemIndex
        val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        Timber.tag("PlaybackService").w(
            "Cloud audio sink stayed empty while playback advanced; restarting stream: " +
                "mediaId=$mediaId position=$positionMs attempt=$attempt",
        )
        cloudSinkRecoveryJob =
            playerScope.launch {
                if (exoPlayer.currentMediaItem?.mediaId != mediaId) return@launch
                exoPlayer.pause()
                exoPlayer.stop()
                cloudPlaybackItemResolver.invalidateStream(item)
                cloudAudioCache.removeResource(item.localConfiguration?.customCacheKey)
                if (exoPlayer.currentMediaItem?.mediaId != mediaId || index !in 0 until exoPlayer.mediaItemCount) {
                    return@launch
                }
                // Reopen from byte zero so FLAC and other container extractors see a clean header.
                // Seeking directly into a damaged cached span can otherwise surface a malformed
                // source error before the provider gets a chance to serve fresh bytes.
                exoPlayer.seekTo(index, 0L)
                exoPlayer.prepare()
                val ready =
                    withTimeoutOrNull(CLOUD_RECOVERY_PREPARE_TIMEOUT_MS) {
                        while (
                            exoPlayer.currentMediaItem?.mediaId == mediaId &&
                            exoPlayer.playbackState != Player.STATE_READY &&
                            exoPlayer.playerError == null
                        ) {
                            delay(20L)
                        }
                        exoPlayer.currentMediaItem?.mediaId == mediaId &&
                            exoPlayer.playbackState == Player.STATE_READY
                    } == true
                if (!ready) return@launch
                exoPlayer.seekTo(index, (positionMs - CLOUD_RECOVERY_REWIND_MS).coerceAtLeast(0L))
                exoPlayer.play()
            }
    }

    private fun recordCloudRecentWhenEligible(
        item: MediaItem?,
        nowMs: Long,
    ) {
        val mediaId = item?.mediaId
        if (item == null || mediaId == null || !mediaId.startsWith(CLOUD_MEDIA_ID_PREFIX) || !exoPlayer.isPlaying) {
            cloudRecentListeningSinceMs = 0L
            return
        }
        if (cloudRecentCandidateMediaId != mediaId) {
            cloudRecentCandidateMediaId = mediaId
            cloudRecentListeningSinceMs = nowMs
            cloudRecentRecorded = false
            return
        }
        if (cloudRecentListeningSinceMs == 0L) cloudRecentListeningSinceMs = nowMs
        if (!cloudRecentRecorded && nowMs - cloudRecentListeningSinceMs >= CLOUD_RECENT_MIN_LISTEN_MS) {
            cloudRecentRecorded = true
            serviceScope.launch { cloudRecentStore.record(item) }
        }
    }

    private fun beginFadeIn() {
        manualFadeJob?.cancel()
        manualFadeActive = false
        fadeInStartedAtMs = SystemClock.elapsedRealtime()
        exoPlayer.volume = 0f
    }

    private fun requestPlayWithFade(action: () -> Unit) {
        // seekToNext()/seekTo(index) is intentionally delayed for a very short fade-out.
        // MediaBrowser immediately follows those commands with play()/playWhenReady=true.
        // Cancelling manualFadeJob here used to cancel the still-pending seek itself, which made
        // every next/previous/list-row action appear dead until playback was paused.
        if (trackChangeFadePending) {
            action()
            return
        }
        manualFadeJob?.cancel()
        manualFadeActive = false
        if (!fadeEnabled || exoPlayer.currentMediaItem == null || exoPlayer.isPlaying) {
            if (!fadeEnabled) exoPlayer.volume = 1f
            action()
            return
        }
        beginFadeIn()
        action()
    }

    private fun requestFadeOut(
        trackChange: Boolean = false,
        action: () -> Unit,
    ) {
        if (!fadeEnabled || !exoPlayer.isPlaying) {
            trackChangeFadePending = false
            action()
            return
        }
        manualFadeJob?.cancel()
        trackChangeFadePending = trackChange
        manualFadeJob =
            playerScope.launch {
                manualFadeActive = true
                try {
                    val startVolume = exoPlayer.volume.coerceIn(0f, 1f)
                    val duration = minOf(fadeDurationMs, CONTROL_FADE_MAX_MS)
                    val startedAt = SystemClock.elapsedRealtime()
                    while (isActive) {
                        val progress =
                            ((SystemClock.elapsedRealtime() - startedAt).toFloat() / duration)
                                .coerceIn(0f, 1f)
                        exoPlayer.volume = startVolume * (1f - progress)
                        if (progress >= 1f) break
                        delay(FADE_TICK_MS)
                    }
                    exoPlayer.volume = 0f
                    action()
                } finally {
                    trackChangeFadePending = false
                    manualFadeActive = false
                }
            }
    }

    private fun requestTrackChangeWithFade(action: () -> Unit) {
        requestFadeOut(trackChange = true) {
            action()
            if (!fadeEnabled) exoPlayer.volume = 1f
        }
    }

    private fun applyPreferredUsbDevice() {
        val preferred =
            if (usbDacOutputEnabled) {
                PlaybackAudioCapabilities.usbOutput(this)
            } else {
                null
            }
        audioSink?.setPreferredDevice(preferred)
    }

    private fun maybeResumeAfterReconnect() {
        if (!resumeOnHeadsetEnabled || !resumeAfterDisconnect) return
        if (SystemClock.elapsedRealtime() - disconnectPauseAtMs > HEADSET_RESUME_WINDOW_MS) {
            clearReconnectResume()
            return
        }
        playerScope.launch {
            delay(350)
            if (resumeAfterDisconnect &&
                !exoPlayer.playWhenReady &&
                audioManager
                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .any(::isReconnectableOutput)
            ) {
                requestPlayWithFade { exoPlayer.play() }
            }
            clearReconnectResume()
        }
    }

    private fun armReconnectResume() {
        if (!resumeOnHeadsetEnabled) return
        resumeAfterDisconnect = true
        disconnectPauseAtMs = SystemClock.elapsedRealtime()
    }

    private fun clearReconnectResume() {
        resumeAfterDisconnect = false
        disconnectPauseAtMs = 0L
    }

    private fun isReconnectableOutput(device: AudioDeviceInfo): Boolean =
        when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            -> true
            else -> false
        }

    companion object {
        const val ACTION_TOGGLE_DESKTOP_LYRICS =
            "me.spica27.spicamusic.action.TOGGLE_DESKTOP_LYRICS"
        const val ACTION_SHOW_DESKTOP_LYRICS =
            "me.spica27.spicamusic.action.SHOW_DESKTOP_LYRICS"
        const val ACTION_CLOSE_PLAYER = "me.spica27.spicamusic.action.CLOSE_PLAYER"

        const val DEFAULT_FADE_DURATION_MS = 4_000L
        const val FADE_TICK_MS = 50L

        // Transport controls must still feel immediate. Natural end-of-track fading continues to
        // use the user-selected duration; only explicit next/previous/list selection is capped.
        const val CONTROL_FADE_MAX_MS = 180L
        const val CACHE_TRIM_INTERVAL_MS = 30_000L
        const val DIAGNOSTIC_PLAYBACK_INTERVAL_MS = 10_000L
        const val HEADSET_RESUME_WINDOW_MS = 10 * 60 * 1_000L
        const val CLOUD_MEDIA_ID_PREFIX = "cloud:"
        const val CLOUD_ERROR_SKIP_DELAY_MS = 600L
        const val CLOUD_PREFETCH_DELAY_MS = 120L
        const val CLOUD_PREFETCH_AHEAD_COUNT = 2
        const val STREAM_MIN_BUFFER_MS = 15_000
        const val STREAM_MAX_BUFFER_MS = 50_000
        const val STREAM_PLAYBACK_START_BUFFER_MS = 750
        const val STREAM_REBUFFER_START_BUFFER_MS = 1_500
        const val CLOUD_UNDERRUN_CONFIRM_MS = 3_000L
        const val CLOUD_NATURAL_END_GUARD_MS = 5_000L
        const val CLOUD_RECOVERY_REWIND_MS = 300L
        const val CLOUD_RECOVERY_PREPARE_TIMEOUT_MS = 8_000L
        const val CLOUD_RECENT_MIN_LISTEN_MS = 10_000L
        const val PROCESS_EXIT_DELAY_MS = 500L
    }
}

internal fun isRestrictedCloudHttpStatus(responseCode: Int?): Boolean =
    responseCode == 401 ||
        responseCode == 403 ||
        responseCode == 404 ||
        responseCode == 410 ||
        responseCode == 451

@SuppressLint("UnsafeOptInUsageError")
internal fun shouldHandleCloudAudioUnderrun(
    explicitPreview: Boolean,
    stillSameItem: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    sinkHasPendingData: Boolean,
): Boolean =
    explicitPreview &&
        stillSameItem &&
        isPlaying &&
        playbackState == Player.STATE_READY &&
        // At the exact point a truncated preview runs dry Media3 commonly reports zero remaining
        // buffered duration while staying READY and advancing the full-song timeline. The
        // sustained empty audio sink is the reliable signal here; the caller confirms it for 3s.
        !sinkHasPendingData

internal enum class CloudAudioUnderrunAction {
    NONE,
    SKIP_PREVIEW,
    RESTART_STREAM,
}

internal fun cloudAudioUnderrunAction(
    isCloudItem: Boolean,
    explicitPreview: Boolean,
    stillSameItem: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    sinkHasPendingData: Boolean,
): CloudAudioUnderrunAction {
    if (
        !isCloudItem ||
        !stillSameItem ||
        !isPlaying ||
        playbackState != Player.STATE_READY ||
        sinkHasPendingData
    ) {
        return CloudAudioUnderrunAction.NONE
    }
    return if (explicitPreview) {
        CloudAudioUnderrunAction.SKIP_PREVIEW
    } else {
        CloudAudioUnderrunAction.RESTART_STREAM
    }
}
