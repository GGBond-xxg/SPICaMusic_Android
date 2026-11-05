package me.spica27.spicamusic.topdisplay

import me.spica27.spicamusic.common.entity.LyricItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundLyricSyncTest {
    private val lines =
        listOf(
            LyricItem.NormalLyric(content = "first", time = 1000L, key = "a"),
            LyricItem.NormalLyric(content = "second", time = 3000L, key = "b"),
        )

    @Test
    fun lateLyricsForPreviousTrackAreIgnored() {
        assertNull(currentLyricFor("new", "old", lines, 5000L, 0L))
        assertNull(currentLyricFor("new", null, lines, 5000L, 0L))
    }

    @Test
    fun delayedLyricsAndBackwardsSeekUsePlaybackTimeline() {
        assertNull(currentLyricFor("song", "song", lines, 1499L, 500L))
        assertEquals(lines[0], currentLyricFor("song", "song", lines, 1500L, 500L))
        assertEquals(lines[1], currentLyricFor("song", "song", lines, 3500L, 500L))
        assertEquals(lines[0], currentLyricFor("song", "song", lines, 2500L, 500L))
    }

    @Test
    fun negativeOffsetAdvancesLyricsAndEmptyTrackClearsThem() {
        assertEquals(lines[0], currentLyricFor("song", "song", lines, 500L, -500L))
        assertNull(currentLyricFor("song", "song", emptyList(), 5000L, 0L))
    }
}
