package me.spica27.spicamusic.ui.player

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import me.spica27.spicamusic.common.entity.LyricItem
import me.spica27.spicamusic.feature.player.domain.PlayerUseCases
import me.spica27.spicamusic.player.api.PlayerAction
import me.spica27.spicamusic.service.SharedLyricsStore

/** UI facade for the playback service's single shared lyric loader. */
@Stable
class LyricsViewModel(
    private val player: PlayerUseCases,
    private val lyricsStore: SharedLyricsStore,
) : ViewModel() {
    val uiState = lyricsStore.uiState

    fun updateOffset(offsetMs: Long) = lyricsStore.updateOffset(offsetMs)

    fun selectAndSaveLyricSource(index: Int) = lyricsStore.selectAndSaveLyricSource(index)

    fun seekToAndPlay(posMs: Long) = player.doAction(PlayerAction.SeekToAndPlay(posMs))

    fun getCurrentPositionMs(): Long = player.currentPosition

    companion object {
        fun parseLyrics(lyricsText: String): List<LyricItem>? = SharedLyricsStore.parseLyrics(lyricsText)

        internal fun lyricsStorageId(mediaId: String): Long = SharedLyricsStore.lyricsStorageId(mediaId)

        internal fun shouldPreferEmbeddedLyrics(
            cachedSourceName: String?,
            hasCachedLyrics: Boolean,
            hasEmbeddedLyrics: Boolean,
        ): Boolean = SharedLyricsStore.shouldPreferEmbeddedLyrics(cachedSourceName, hasCachedLyrics, hasEmbeddedLyrics)
    }
}
