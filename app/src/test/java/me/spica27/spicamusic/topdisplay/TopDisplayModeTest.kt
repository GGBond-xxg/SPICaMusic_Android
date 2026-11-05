package me.spica27.spicamusic.topdisplay

import org.junit.Assert.assertEquals
import org.junit.Test

class TopDisplayModeTest {
    @Test
    fun legacyEnabledStillSelectsLyricon() {
        assertEquals(TopDisplayMode.STATUS_LYRIC, TopDisplayMode.resolve("", true))
        assertEquals(TopDisplayMode.OFF, TopDisplayMode.resolve("", false))
        assertEquals(TopDisplayMode.STATUS_LYRIC, TopDisplayMode.resolve("status_lyric", false))
    }

    @Test
    fun savedModeOverridesLegacyFlag() {
        TopDisplayMode.entries.forEach { mode ->
            assertEquals(mode, TopDisplayMode.resolve(mode.value, true))
            assertEquals(mode, TopDisplayMode.resolve(mode.value, false))
        }
    }

    @Test
    fun unknownValueCannotEnableAnyOutput() {
        assertEquals(TopDisplayMode.OFF, TopDisplayMode.resolve("future_mode", true))
        assertEquals(TopDisplayMode.OFF, TopDisplayMode.fromString("corrupt"))
    }
}
