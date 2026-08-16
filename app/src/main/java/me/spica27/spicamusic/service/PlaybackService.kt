package me.spica27.spicamusic.service

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
import androidx.media3.common.util.UnstableApi
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
import me.spica27.spicamusic.DesktopLyricsPermissionActivity
import me.spica27.spicamusic.MainActivity
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.CloudPlaybackItemResolver
import me.spica27.spicamusic.feature.settings.domain.SettingsUseCases
import me.spica27.spicamusic.player.api.IMusicPlayer
import me.spica27.spicamusic.player.impl.SpicaPlayer
import me.spica27.spicamusic.player.impl.utils.MediaLibrary
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
    private val settingsUseCases: SettingsUseCases by inject()
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
    private var cloudErrorSkipJob: Job? = null
    private var manualFadeActive = false
    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                applyPreferredUsbDevice()
                if (addedDevices.any(::isReconnectableOutput)) maybeResumeAfterReconnect()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
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
                cloudErrorSkipJob?.cancel()
                if (fadeEnabled && mediaItem != null) {
                    beginFadeIn()
                } else {
                    fadeInStartedAtMs = 0L
                    exoPlayer.volume = 1f
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && fadeEnabled && exoPlayer.volume <= 0.01f) {
                    fadeInStartedAtMs = SystemClock.elapsedRealtime()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handleCloudPlaybackError(error)
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
        topDisplayModeController.start(exoPlayer)
        desktopLyricsController.start(exoPlayer)

        val desktopLyricsCommand = SessionCommand(ACTION_TOGGLE_DESKTOP_LYRICS, android.os.Bundle.EMPTY)
        val closePlayerCommand = SessionCommand(ACTION_CLOSE_PLAYER, android.os.Bundle.EMPTY)
        val notificationButtons = notificationCommandButtons(desktopLyricsCommand, closePlayerCommand)

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
                                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                                        .buildUpon()
                                        .add(desktopLyricsCommand)
                                        .add(closePlayerCommand)
                                        .build(),
                                ).setCustomLayout(notificationButtons)
                                .setMediaButtonPreferences(notificationButtons)
                                .build()

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
                .setMediaButtonPreferences(notificationButtons)
                .build()
    }

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
    ): Int =
        when (intent?.action) {
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!backgroundPlaybackEnabled) {
            exoPlayer.pause()
            exoPlayer.stop()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        fadeMonitorJob?.cancel()
        manualFadeJob?.cancel()
        cloudErrorSkipJob?.cancel()
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

    private fun reconfigureAudioPipeline(enableHiFi: Boolean) {
        if (enableHiFi == hiFiOutputEnabled) return
        val oldPlayer = exoPlayer
        val mediaItems = List(oldPlayer.mediaItemCount) { oldPlayer.getMediaItemAt(it) }
        val currentIndex = oldPlayer.currentMediaItemIndex
        val currentPosition = oldPlayer.currentPosition
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
        replacement.addListener(playbackListener)
        exoPlayer = replacement
        sessionPlayer = FadeAwarePlayer(replacement)
        mediaSession?.setPlayer(sessionPlayer)
        oldPlayer.removeListener(playbackListener)
        oldPlayer.release()

        if (mediaItems.isNotEmpty()) {
            replacement.setMediaItems(
                mediaItems,
                currentIndex.coerceIn(0, mediaItems.lastIndex),
                currentPosition.coerceAtLeast(0L),
            )
            replacement.prepare()
        }
        topDisplayModeController.start(replacement)
        desktopLyricsController.start(replacement)
        hiFiOutputEnabled = enableHiFi
        replacement.playWhenReady = playWhenReady
        if (playWhenReady && fadeEnabled && mediaItems.isNotEmpty()) beginFadeIn()
        Timber
            .tag("PlaybackService")
            .i("Audio pipeline rebuilt: Hi-Fi=$enableHiFi, EQ=${!enableHiFi}")
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
        Toast
            .makeText(
                this,
                getString(
                    if (canSkip) {
                        R.string.cloud_playback_restricted_skipping
                    } else {
                        R.string.cloud_playback_restricted_stopped
                    },
                    failedTitle,
                ),
                Toast.LENGTH_LONG,
            ).show()
        Timber
            .tag("PlaybackService")
            .w(error, "Cloud playback failed: mediaId=%s, skip=%s", failedMediaId, canSkip)
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

    private fun closePlayerAndApp() {
        cloudErrorSkipJob?.cancel()
        desktopLyricsController.hide()
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
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
                .collect { backgroundPlaybackEnabled = it }
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
    }

    private fun startCacheTrimMonitor() {
        serviceScope.launch {
            while (isActive) {
                delay(CACHE_TRIM_INTERVAL_MS)
                cloudAudioCache.trimTo(maxCloudAudioCacheBytes)
            }
        }
    }

    private fun startFadeMonitor() {
        fadeMonitorJob?.cancel()
        fadeMonitorJob =
            playerScope.launch {
                while (isActive) {
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

    private fun beginFadeIn() {
        manualFadeJob?.cancel()
        manualFadeActive = false
        fadeInStartedAtMs = SystemClock.elapsedRealtime()
        exoPlayer.volume = 0f
    }

    private fun requestPlayWithFade(action: () -> Unit) {
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

    private fun requestFadeOut(action: () -> Unit) {
        if (!fadeEnabled || !exoPlayer.isPlaying) {
            action()
            return
        }
        manualFadeJob?.cancel()
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
                    manualFadeActive = false
                }
            }
    }

    private fun requestTrackChangeWithFade(action: () -> Unit) {
        requestFadeOut {
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
        const val CONTROL_FADE_MAX_MS = 600L
        const val CACHE_TRIM_INTERVAL_MS = 30_000L
        const val HEADSET_RESUME_WINDOW_MS = 10 * 60 * 1_000L
        const val CLOUD_MEDIA_ID_PREFIX = "cloud:"
        const val CLOUD_ERROR_SKIP_DELAY_MS = 600L
        const val PROCESS_EXIT_DELAY_MS = 500L
    }
}
