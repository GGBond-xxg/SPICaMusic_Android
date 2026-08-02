package me.spica27.spicamusic.lyricon

import android.content.Context
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ConnectionListener
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.service.addConnectionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.spica27.spicamusic.common.entity.LyricItem
import me.spica27.spicamusic.common.entity.getSentenceContent
import timber.log.Timber

/**
 * The single boundary between SPICa's player/lyric models and the optional Lyricon provider.
 *
 * Lyricon may be absent, disconnected or disabled. Every SDK call is isolated so provider
 * failures can never interrupt Media3 playback.
 */
class LyriconProviderManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var provider: LyriconProvider? = null
    private var connectionListener: ConnectionListener? = null
    private var player: Player? = null
    private var scope: CoroutineScope? = null
    private var enabled = false
    private var started = false
    private var currentLyricsMediaId: String? = null
    private var currentLyrics: List<RichLyricLine> = emptyList()

    private val playerListener =
        object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                synchronized(this@LyriconProviderManager) {
                    currentLyricsMediaId = null
                    currentLyrics = emptyList()
                }
                syncCurrentState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE && player?.mediaItemCount == 0) {
                    clear()
                } else {
                    syncPlaybackState()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    seekTo(newPosition.positionMs)
                }
            }

            override fun onTimelineChanged(
                timeline: Timeline,
                reason: Int,
            ) {
                if (timeline.isEmpty) clear()
            }
        }

    @Synchronized
    fun start(player: Player) {
        if (started && this.player === player) return
        release()
        started = true
        this.player = player
        player.addListener(playerListener)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @Synchronized
    fun release() {
        player?.removeListener(playerListener)
        player = null
        scope?.cancel()
        scope = null
        provider?.let { activeProvider ->
            safe("clear on release") {
                activeProvider.player.setPlaybackState(false)
                activeProvider.player.setSong(null)
            }
            connectionListener?.let { listener ->
                safe("remove connection listener") {
                    activeProvider.service.removeConnectionListener(listener)
                }
            }
            safe("unregister") { activeProvider.unregister() }
            safe("destroy") { activeProvider.destroy() }
        }
        provider = null
        connectionListener = null
        currentLyricsMediaId = null
        currentLyrics = emptyList()
        enabled = false
        started = false
        Timber.tag(TAG).d("Provider released")
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
        currentLyricsMediaId = mediaId
        currentLyrics = lyrics.toLyriconLines(durationMs, offsetMs)
        Timber.tag(TAG).d("Lyrics ready: mediaId=%s, lines=%d", mediaId, currentLyrics.size)
        syncCurrentState()
    }

    @Synchronized
    fun updatePosition(positionMs: Long) {
        if (!enabled || player?.currentMediaItem == null) return
        safe("position sync") {
            provider?.player?.setPosition(positionMs.coerceAtLeast(0L))
        }
    }

    @Synchronized
    fun seekTo(positionMs: Long) {
        if (!enabled || player?.currentMediaItem == null) return
        safe("seek sync") {
            provider?.player?.seekTo(positionMs.coerceAtLeast(0L))
        }
        syncPlaybackState()
    }

    @Synchronized
    fun clear() {
        currentLyricsMediaId = null
        currentLyrics = emptyList()
        if (!enabled) return
        safe("clear") {
            provider?.player?.setPlaybackState(false)
            provider?.player?.setSong(null)
        }
    }

    @Synchronized
    fun clearLyrics() {
        currentLyricsMediaId = null
        currentLyrics = emptyList()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value && (value.not() || provider != null)) return
        enabled = value
        if (!value) {
            provider?.let { activeProvider ->
                safe("disable") {
                    activeProvider.player.setPlaybackState(false)
                    activeProvider.player.setSong(null)
                    activeProvider.unregister()
                }
            }
            Timber.tag(TAG).d("Provider disabled")
            return
        }
        val activeProvider =
            provider
                ?: runCatching(::createProvider)
                    .onFailure { Timber.tag(TAG).w(it, "Lyricon provider creation failed") }
                    .getOrNull()
                    ?.also { provider = it }
                ?: return
        safe("register") {
            activeProvider.register()
            Timber.tag(TAG).d("Provider registration requested")
        }
        syncCurrentState()
    }

    private fun createProvider(): LyriconProvider {
        val newProvider =
            LyriconFactory.createProvider(
                context = appContext,
                providerPackageName = appContext.packageName,
                playerPackageName = appContext.packageName,
                logo = null,
                metadata = null,
            )
        newProvider.autoSync = true
        connectionListener =
            newProvider.service.addConnectionListener {
                onConnected {
                    Timber.tag(TAG).d("Provider connected")
                    requestStateSync()
                }
                onReconnected {
                    Timber.tag(TAG).d("Provider reconnected")
                    requestStateSync()
                }
                onDisconnected {
                    Timber.tag(TAG).d("Provider disconnected")
                }
                onConnectTimeout {
                    Timber.tag(TAG).d("Provider connection timed out")
                }
            }
        return newProvider
    }

    private fun requestStateSync() {
        scope?.launch {
            syncCurrentState()
        }
    }

    @Synchronized
    fun syncCurrentState() {
        if (!enabled) return
        val activePlayer = player ?: return
        val item = activePlayer.currentMediaItem
        if (item == null || activePlayer.mediaItemCount == 0) {
            clear()
            return
        }
        val mediaId = stableId(item)
        val duration = activePlayer.duration.validDuration(item.mediaMetadata.durationMs)
        val lyricLines =
            if (currentLyricsMediaId == mediaId) {
                currentLyrics
            } else {
                emptyList()
            }
        val song =
            Song(
                id = mediaId,
                name =
                    item.mediaMetadata.title?.toString()
                        ?: item.mediaMetadata.displayTitle?.toString()
                        ?: mediaId,
                artist =
                    item.mediaMetadata.artist
                        ?.toString()
                        .orEmpty(),
                duration = duration,
                lyrics = lyricLines,
            )
        safe("song sync") {
            val remotePlayer = provider?.player ?: return@safe
            remotePlayer.setSong(song)
            remotePlayer.setPosition(activePlayer.currentPosition.coerceAtLeast(0L))
            remotePlayer.setPlaybackState(activePlayer.toPlatformPlaybackState())
            remotePlayer.setDisplayTranslation(
                lyricLines.any { !it.translation.isNullOrBlank() },
            )
            remotePlayer.setDisplayRoma(false)
            remotePlayer.setPositionUpdateInterval(POSITION_UPDATE_INTERVAL_MS)
        }
        Timber.tag(TAG).d("Song synced: mediaId=%s, lines=%d", mediaId, lyricLines.size)
    }

    @Synchronized
    private fun syncPlaybackState() {
        if (!enabled) return
        val activePlayer = player ?: return
        if (activePlayer.currentMediaItem == null) return
        safe("playback state sync") {
            provider?.player?.setPlaybackState(activePlayer.toPlatformPlaybackState())
        }
    }

    private fun Player.toPlatformPlaybackState(): PlaybackState {
        val state =
            when {
                playbackState == Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
                isPlaying -> PlaybackState.STATE_PLAYING
                else -> PlaybackState.STATE_PAUSED
            }
        return PlaybackState
            .Builder()
            .setState(
                state,
                currentPosition.coerceAtLeast(0L),
                if (isPlaying) playbackParameters.speed else 0f,
                SystemClock.elapsedRealtime(),
            ).build()
    }

    private fun stableId(item: MediaItem): String =
        item.mediaId.takeIf(String::isNotBlank)
            ?: item.localConfiguration?.uri?.toString()
            ?: listOf(
                item.mediaMetadata.title,
                item.mediaMetadata.artist,
                item.mediaMetadata.durationMs,
            ).joinToString("|")

    private fun Long.validDuration(fallback: Long?): Long =
        takeIf { it != C.TIME_UNSET && it > 0L }
            ?: fallback?.takeIf { it > 0L }
            ?: 0L

    private fun List<LyricItem>?.toLyriconLines(
        durationMs: Long,
        offsetMs: Long,
    ): List<RichLyricLine> {
        if (isNullOrEmpty()) return emptyList()
        val sorted = sortedBy(LyricItem::time)
        return sorted.mapIndexedNotNull { index, item ->
            val begin = (item.time + offsetMs).coerceAtLeast(0L)
            val nextBegin =
                sorted
                    .getOrNull(index + 1)
                    ?.let { (it.time + offsetMs).coerceAtLeast(0L) }
            when (item) {
                is LyricItem.NormalLyric -> {
                    if (item.content.isBlank()) return@mapIndexedNotNull null
                    val end = lineEnd(begin, nextBegin, durationMs)
                    RichLyricLine(
                        begin = begin,
                        end = end,
                        text = item.content,
                        translation = item.translation?.takeIf(String::isNotBlank),
                    )
                }

                is LyricItem.WordsLyric -> {
                    val text = item.getSentenceContent()
                    if (text.isBlank()) return@mapIndexedNotNull null
                    val rawEnd = (item.endTime + offsetMs).coerceAtLeast(begin + 1L)
                    val end = maxOf(rawEnd, lineEnd(begin, nextBegin, durationMs))
                    val words =
                        item.words.mapNotNull { word ->
                            if (word.content.isEmpty()) return@mapNotNull null
                            val wordBegin = (word.startTime + offsetMs).coerceIn(begin, end - 1L)
                            val wordEnd = (word.endTime + offsetMs).coerceIn(wordBegin + 1L, end)
                            LyricWord(
                                begin = wordBegin,
                                end = wordEnd,
                                text = word.content,
                            )
                        }
                    RichLyricLine(
                        begin = begin,
                        end = end,
                        text = text,
                        words = words.takeIf(List<LyricWord>::isNotEmpty),
                        translation =
                            item.translation
                                .firstOrNull { it.content.isNotBlank() }
                                ?.content,
                    )
                }
            }
        }
    }

    private fun lineEnd(
        begin: Long,
        nextBegin: Long?,
        durationMs: Long,
    ): Long =
        when {
            nextBegin != null && nextBegin > begin -> nextBegin
            durationMs > begin -> durationMs
            else -> begin + DEFAULT_LAST_LINE_DURATION_MS
        }

    private inline fun safe(
        operation: String,
        block: () -> Unit,
    ) {
        runCatching(block).onFailure {
            Timber.tag(TAG).w(it, "Lyricon %s failed", operation)
        }
    }

    private companion object {
        const val TAG = "LyriconProvider"
        const val POSITION_UPDATE_INTERVAL_MS = 500
        const val DEFAULT_LAST_LINE_DURATION_MS = 5_000L
    }
}
