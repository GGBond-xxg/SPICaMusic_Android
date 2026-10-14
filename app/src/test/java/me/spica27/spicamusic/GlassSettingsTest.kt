package me.spica27.spicamusic

import me.spica27.spicamusic.common.entity.GlassSettings
import me.spica27.spicamusic.ui.player.parseSleepTimerMinutes
import org.junit.Assert.*
import org.junit.Test

class GlassSettingsTest {
    @Test fun oldOrInvalidPreferencesUseSafeDefaults() {
        assertEquals(GlassSettings(), GlassSettings.decode(""))
        assertEquals(GlassSettings(), GlassSettings.decode("invalid|bad|bad|bad"))
        assertEquals(GlassSettings(false, 16, 0, 95), GlassSettings.decode("false|100|-8|999"))
    }

    @Test fun effectSettingsRoundTrip() {
        val settings = GlassSettings(false, 12, 43, 76)
        assertEquals(settings, GlassSettings.decode(settings.encode()))
    }

    @Test fun timerAcceptsWholeMinutesWithinRange() {
        assertEquals(1, parseSleepTimerMinutes("1"))
        assertEquals(37, parseSleepTimerMinutes("37"))
        assertEquals(240, parseSleepTimerMinutes("240"))
        listOf("", "0", "241", "-3", "1.5", "abc").forEach { assertNull(parseSleepTimerMinutes(it)) }
    }
}
