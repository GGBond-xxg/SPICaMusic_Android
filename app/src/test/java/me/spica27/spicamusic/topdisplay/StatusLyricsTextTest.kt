package me.spica27.spicamusic.topdisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusLyricsTextTest {
    @Test
    fun pausedEditorUsesSampleEvenWhenAnOldLyricExists() {
        assertEquals("sample", statusLyricsText(false, "old lyric", "song", "sample"))
        assertEquals("sample", statusLyricsText(false, null, null, "sample"))
    }

    @Test
    fun playingEditorUsesCurrentLyricInsteadOfSample() {
        assertEquals("current lyric", statusLyricsText(true, "current lyric", "song", "sample"))
        assertEquals("song", statusLyricsText(true, "", "song", "sample"))
        assertNull(statusLyricsText(true, null, null, "sample"))
    }

    @Test
    fun leavingEditorHidesPausedSampleButKeepsPlayingLyrics() {
        assertNull(statusLyricsText(false, "old lyric", "song", null))
        assertEquals("current lyric", statusLyricsText(true, "current lyric", "song", null))
    }
}
