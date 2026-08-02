package me.spica27.spicamusic.topdisplay

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.spica27.spicamusic.common.entity.LyricItem
import me.spica27.spicamusic.common.entity.getSentenceContent
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.lyricon.LyriconProviderManager
import timber.log.Timber

/**
 * Owns the mutually exclusive top-display mode while reusing the one existing player and its
 * existing position stream.
 */
class TopDisplayModeController(
    private val lyriconManager: LyriconProviderManager,
    private val liveUpdateManager: MusicLiveUpdateManager,
    private val preferences: PreferencesManager,
) {
    private var player: Player? = null
    private var scope: CoroutineScope? = null
    private var preferenceJob: Job? = null
    private var mode: TopDisplayMode = TopDisplayMode.OFF
    private var lyricsMediaId: String? = null
    private var lyrics: List<LyricItem> = emptyList()
    private var lyricsOffsetMs: Long = 0L
    private var lastLiveLyricKey: String? = null
    private var lastLiveUpdatePositionMs: Long = Long.MIN_VALUE

    private val playerListener =
        object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                lyricsMediaId = null
                lyrics = emptyList()
                lyricsOffsetMs = 0L
                lastLiveLyricKey = null
                lastLiveUpdatePositionMs = Long.MIN_VALUE
                lyriconManager.clearLyrics()
                syncCurrentState(force = true)
                Timber.tag(TAG).d("Song changed: mediaId=%s", mediaItem?.mediaId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncCurrentState(force = true)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when {
                    playbackState == Player.STATE_ENDED -> onPlaybackStopped()
                    playbackState == Player.STATE_IDLE && player?.mediaItemCount == 0 -> onPlaybackStopped()
                    else -> syncCurrentState(force = true)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    lyriconManager.seekTo(newPosition.positionMs)
                    updatePosition(newPosition.positionMs, force = true)
                }
            }

            override fun onTimelineChanged(
                timeline: Timeline,
                reason: Int,
            ) {
                if (timeline.isEmpty) onPlaybackStopped()
            }
        }

    @Synchronized
    fun start(player: Player) {
        if (this.player === player && scope != null) return
        release()
        this.player = player
        player.addListener(playerListener)
        lyriconManager.start(player)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        preferenceJob =
            scope?.launch {
                combine(
                    preferences.getString(PreferencesManager.Keys.TOP_DISPLAY_MODE, ""),
                    preferences.getBoolean(PreferencesManager.Keys.LYRICON_ENABLED, true),
                ) { savedMode, legacyLyriconEnabled ->
                    if (savedMode.isBlank()) {
                        if (legacyLyriconEnabled) {
                            TopDisplayMode.STATUS_LYRIC
                        } else {
                            TopDisplayMode.OFF
                        }
                    } else {
                        TopDisplayMode.fromString(savedMode)
                    }
                }.distinctUntilChanged()
                    .collect(::applyModeInternal)
            }
    }

    suspend fun applyMode(mode: TopDisplayMode) {
        preferences.setString(PreferencesManager.Keys.TOP_DISPLAY_MODE, mode.value)
        preferences.setBoolean(
            PreferencesManager.Keys.LYRICON_ENABLED,
            mode == TopDisplayMode.STATUS_LYRIC,
        )
        applyModeInternal(mode)
    }

    @Synchronized
    fun updateLyrics(
        mediaId: String,
        lyrics: List<LyricItem>?,
        durationMs: Long,
        offsetMs: Long,
    ) {
        val activeItem = player?.currentMediaItem ?: return
        if (stableId(activeItem) != mediaId) return
        lyricsMediaId = mediaId
        this.lyrics = lyrics.orEmpty().sortedBy(LyricItem::time)
        lyricsOffsetMs = offsetMs
        lyriconManager.updateLyrics(mediaId, lyrics, durationMs, offsetMs)
        syncCurrentState(force = true)
    }

    @Synchronized
    fun updatePosition(
        positionMs: Long,
        force: Boolean = false,
    ) {
        lyriconManager.updatePosition(positionMs)
        if (mode != TopDisplayMode.LIVE_UPDATE) return
        if (!liveUpdateManager.isSupported() || !liveUpdateManager.canPostPromotedNotification()) return
        val lyricKey = currentLyric(positionMs)?.let { "${it.time}:${it.displayText()}" }
        val lyricChanged = lyricKey != lastLiveLyricKey
        val intervalReached =
            lastLiveUpdatePositionMs == Long.MIN_VALUE ||
                kotlin.math.abs(positionMs - lastLiveUpdatePositionMs) >= LIVE_UPDATE_INTERVAL_MS
        if (force || lyricChanged || intervalReached) {
            syncCurrentState(positionOverrideMs = positionMs, force = true)
        }
    }

    @Synchronized
    fun onPlaybackStopped() {
        liveUpdateManager.cancel()
        lyriconManager.clear()
        lastLiveLyricKey = null
        lastLiveUpdatePositionMs = Long.MIN_VALUE
    }

    @Synchronized
    fun release() {
        player?.removeListener(playerListener)
        player = null
        preferenceJob?.cancel()
        preferenceJob = null
        scope?.cancel()
        scope = null
        liveUpdateManager.cancel()
        lyriconManager.release()
        mode = TopDisplayMode.OFF
        lyricsMediaId = null
        lyrics = emptyList()
        lyricsOffsetMs = 0L
    }

    fun isLiveUpdateSupported(): Boolean = liveUpdateManager.isSupported()

    fun canPostPromotedNotification(): Boolean = liveUpdateManager.canPostPromotedNotification()

    @Synchronized
    private fun applyModeInternal(newMode: TopDisplayMode) {
        if (player == null) {
            liveUpdateManager.cancel()
            lyriconManager.setEnabled(false)
            mode = newMode
            Timber.tag(TAG).d("Mode saved for next service start: mode=%s", mode)
            return
        }
        if (mode == newMode) {
            syncCurrentState(force = true)
            return
        }
        when (newMode) {
            TopDisplayMode.OFF -> {
                liveUpdateManager.cancel()
                lyriconManager.setEnabled(false)
            }

            TopDisplayMode.STATUS_LYRIC -> {
                liveUpdateManager.cancel()
                lyriconManager.setEnabled(true)
            }

            TopDisplayMode.LIVE_UPDATE -> {
                lyriconManager.setEnabled(false)
            }
        }
        mode = newMode
        Timber.tag(TAG).d(
            "Mode changed: mode=%s, sdk=%d, supported=%s, promotedAllowed=%s",
            mode,
            android.os.Build.VERSION.SDK_INT,
            liveUpdateManager.isSupported(),
            liveUpdateManager.canPostPromotedNotification(),
        )
        syncCurrentState(force = true)
    }

    @Synchronized
    private fun syncCurrentState(
        positionOverrideMs: Long? = null,
        force: Boolean = false,
    ) {
        val activePlayer = player ?: return
        when (mode) {
            TopDisplayMode.OFF -> return
            TopDisplayMode.STATUS_LYRIC -> {
                lyriconManager.syncCurrentState()
                return
            }

            TopDisplayMode.LIVE_UPDATE -> Unit
        }
        val item = activePlayer.currentMediaItem
        if (item == null || activePlayer.mediaItemCount == 0) {
            liveUpdateManager.cancel()
            return
        }
        if (activePlayer.playbackState == Player.STATE_ENDED) {
            liveUpdateManager.cancel()
            return
        }
        val position = (positionOverrideMs ?: activePlayer.currentPosition).coerceAtLeast(0L)
        val state = currentState(activePlayer, item, position)
        val lyricKey = currentLyric(position)?.let { "${it.time}:${it.displayText()}" }
        if (force) {
            liveUpdateManager.showOrUpdate(state)
            lastLiveLyricKey = lyricKey
            lastLiveUpdatePositionMs = position
        }
    }

    private fun currentState(
        player: Player,
        item: MediaItem,
        positionMs: Long,
    ): MusicLiveUpdateState {
        val metadata = item.mediaMetadata
        return MusicLiveUpdateState(
            songId = stableId(item),
            title =
                metadata.title?.toString()
                    ?: metadata.displayTitle?.toString()
                    ?: "",
            artist = metadata.artist?.toString().orEmpty(),
            lyric = currentLyric(positionMs)?.displayText()?.takeIf(String::isNotBlank),
            positionMs = positionMs,
            durationMs =
                player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                    ?: metadata.durationMs?.takeIf { it > 0L }
                    ?: 0L,
            isPlaying = player.isPlaying,
            artworkUri = metadata.artworkUri,
        )
    }

    private fun currentLyric(positionMs: Long): LyricItem? {
        val activeMediaId = player?.currentMediaItem?.let(::stableId) ?: return null
        if (lyricsMediaId != activeMediaId || lyrics.isEmpty()) return null
        val adjustedPosition = positionMs - lyricsOffsetMs
        return lyrics.lastOrNull { it.time <= adjustedPosition }
    }

    private fun LyricItem.displayText(): String =
        when (this) {
            is LyricItem.NormalLyric -> content
            is LyricItem.WordsLyric -> getSentenceContent()
        }

    private fun stableId(item: MediaItem): String =
        item.mediaId.takeIf(String::isNotBlank)
            ?: item.localConfiguration?.uri?.toString()
            ?: listOf(
                item.mediaMetadata.title,
                item.mediaMetadata.artist,
                item.mediaMetadata.durationMs,
            ).joinToString("|")

    private companion object {
        const val TAG = "TopDisplayMode"
        const val LIVE_UPDATE_INTERVAL_MS = 10_000L
    }
}
