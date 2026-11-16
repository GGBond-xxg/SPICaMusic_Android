package me.spica27.spicamusic.service

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Shared with the UI, but attached to the service's player, not an activity lifecycle. */
@UnstableApi
class SleepTimerController(
    context: Context,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
) {
    private val preferences = context.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val timer = SleepTimerState()
    private var player: ExoPlayer? = null
    private var onStop: () -> Unit = {}
    private var job: Job? = null
    private val _durationMs = MutableStateFlow<Long?>(null)
    val durationMs = _durationMs.asStateFlow()
    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs = _remainingMs.asStateFlow()
    private val _finishCurrentSong = MutableStateFlow(preferences.getBoolean("finish_current_song", false))
    val finishCurrentSong = _finishCurrentSong.asStateFlow()

    private val listener =
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                // Also consume a pending timer if the user pauses: it must not affect a later resume.
                if (!playWhenReady && timer.waitingForSongEnd) finishWaiting()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                // An explicit skip/removal after expiry ends the target song too.
                if (timer.waitingForSongEnd) finishWaiting()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (timer.waitingForSongEnd &&
                    (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE)
                ) {
                    finishWaiting()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (timer.waitingForSongEnd) finishWaiting()
            }
        }

    fun attach(
        newPlayer: ExoPlayer,
        onStop: () -> Unit = {},
    ) {
        player?.removeListener(listener)
        player?.pauseAtEndOfMediaItems = false
        player = newPlayer
        this.onStop = onStop
        newPlayer.addListener(listener)
        newPlayer.pauseAtEndOfMediaItems = timer.waitingForSongEnd
    }

    fun detach() {
        cancel()
        player?.removeListener(listener)
        player = null
        onStop = {}
    }

    fun setFinishCurrentSong(enabled: Boolean) {
        _finishCurrentSong.value = enabled
        preferences.edit { putBoolean("finish_current_song", enabled) }
        if (!enabled && timer.waitingForSongEnd) finishWaiting()
    }

    fun start(minutes: Int) {
        cancel()
        timer.start(minutes.coerceIn(0, 240) * 60_000L, nowMs())
        publish()
        if (timer.durationMs == null) return
        job =
            scope.launch {
                while (isActive && timer.durationMs != null && !timer.waitingForSongEnd) {
                    delay(minOf(1_000L, timer.remainingMs ?: 1_000L))
                    val current = player
                    val canFinish =
                        current != null &&
                            current.currentMediaItem != null &&
                            current.playWhenReady &&
                            current.playbackState != Player.STATE_ENDED &&
                            current.playbackState != Player.STATE_IDLE &&
                            current.playerError == null
                    val stop = timer.tick(nowMs(), finishCurrentSong.value, canFinish)
                    // ExoPlayer stops at the audio boundary, including repeat-one and shuffled queues.
                    current?.pauseAtEndOfMediaItems = timer.waitingForSongEnd
                    publish()
                    if (stop) {
                        onStop()
                        current?.pause()
                    }
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        timer.cancel()
        player?.pauseAtEndOfMediaItems = false
        publish()
    }

    private fun finishWaiting() {
        if (!timer.songEnded()) return
        player?.pauseAtEndOfMediaItems = false
        publish()
        onStop()
        player?.pause()
    }

    private fun publish() {
        _durationMs.value = timer.durationMs
        _remainingMs.value = timer.remainingMs
    }
}
