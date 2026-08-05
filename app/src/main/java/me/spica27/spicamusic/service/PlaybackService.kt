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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.spica27.spicamusic.cloud.CloudPlaybackItemResolver
import me.spica27.spicamusic.feature.settings.domain.SettingsUseCases
import me.spica27.spicamusic.player.api.IMusicPlayer
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
    private val cloudPlaybackItemResolver: CloudPlaybackItemResolver by inject()
    private val settingsUseCases: SettingsUseCases by inject()

    private var mediaSession: MediaLibrarySession? = null
    private lateinit var exoPlayer: ExoPlayer
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

    private var resumeAfterDisconnect = false
    private var disconnectPauseAtMs = 0L
    private var fadeInStartedAtMs = 0L
    private var fadeMonitorJob: Job? = null
    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                applyPreferredUsbDevice()
                if (addedDevices.any(::isReconnectableOutput)) maybeResumeAfterReconnect()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                applyPreferredUsbDevice()
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
                        resumeAfterDisconnect = true
                        disconnectPauseAtMs = SystemClock.elapsedRealtime()
                    }
                    else -> clearReconnectResume()
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                if (fadeEnabled && mediaItem != null) {
                    fadeInStartedAtMs = SystemClock.elapsedRealtime()
                    exoPlayer.volume = 0f
                } else {
                    fadeInStartedAtMs = 0L
                    exoPlayer.volume = 1f
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
                runBlocking {
                    settingsUseCases.getBoolean(SettingsUseCases.Keys.HIFI_MODE, false).first()
                }
        usbDacOutputEnabled =
            runBlocking {
                settingsUseCases.getBoolean(SettingsUseCases.Keys.USB_DAC_OUTPUT, false).first()
            }
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
                ).setWakeMode(C.WAKE_MODE_LOCAL)
                .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
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
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        startSettingsObservers()
        startFadeMonitor()
        topDisplayModeController.start(exoPlayer)

        mediaSession =
            MediaLibrarySession
                .Builder(
                    this,
                    exoPlayer,
                    object : MediaLibrarySession.Callback {
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
                ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

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
        exoPlayer.removeListener(playbackListener)
        topDisplayModeController.release()
        serviceScope.cancel()
        playerScope.cancel()
        mediaSession?.run {
            exoPlayer.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun startSettingsObservers() {
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
            settingsUseCases
                .getBoolean(SettingsUseCases.Keys.EQ_ENABLED, false)
                .collect(player::setEQEnabled)
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

    private fun startFadeMonitor() {
        fadeMonitorJob?.cancel()
        fadeMonitorJob =
            playerScope.launch {
                while (isActive) {
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
                exoPlayer.play()
            }
            clearReconnectResume()
        }
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
            -> true
            else -> false
        }

    private companion object {
        const val DEFAULT_FADE_DURATION_MS = 4_000L
        const val FADE_TICK_MS = 50L
        const val HEADSET_RESUME_WINDOW_MS = 10 * 60 * 1_000L
    }
}
