package me.spica27.spicamusic.topdisplay

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusLyricsAppearanceTest {
    @Test
    fun largeFontAndExtremeOffsetsStayWithinLandscapeWindow() {
        assertEquals(StatusLyricsBounds(360, 80, 20, 40), statusLyricsBounds(400, 120, 360, 80, 180, 240))
    }

    @Test
    fun windowSmallerThanRequestedContentClampsBothAxes() {
        assertEquals(StatusLyricsBounds(100, 20, 0, 0), statusLyricsBounds(100, 20, 240, 40, -180, 100))
    }

    @Test
    fun restoredInvalidAppearanceCannotCreateUnboundedWindow() {
        assertEquals(StatusLyricsAppearance(-180, 240, 120, 28, 20), StatusLyricsAppearance(-9999, 9999, -1, 100, -1).sanitized())
    }
}
