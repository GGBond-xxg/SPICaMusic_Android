package me.spica27.spicamusic

import me.spica27.spicamusic.service.SleepTimerState
import org.junit.Assert.*
import org.junit.Test

class SleepTimerStateTest {
    @Test fun defaultTimerStopsAtDeadline() {
        val timer = SleepTimerState()
        timer.start(60_000, 10_000)
        assertFalse(timer.tick(69_999, false, true))
        assertEquals(1L, timer.remainingMs)
        assertTrue(timer.tick(70_000, false, true))
        assertNull(timer.remainingMs)
        assertFalse(timer.tick(71_000, false, true))
    }

    @Test fun finishSongWaitsOnlyAfterDeadlineAndConsumesExactlyOnce() {
        val timer = SleepTimerState()
        timer.start(60_000, 0)
        assertFalse(timer.songEnded()) // earlier songs do not cancel the countdown
        assertFalse(timer.tick(59_000, true, true))
        assertFalse(timer.waitingForSongEnd)
        assertFalse(timer.tick(60_000, true, true))
        assertEquals(0L, timer.remainingMs)
        assertTrue(timer.waitingForSongEnd)
        assertTrue(timer.songEnded())
        assertFalse(timer.songEnded())
        assertNull(timer.durationMs)
    }

    @Test fun pausedEndedEmptyOrFailedPlaybackDoesNotLeavePendingStop() {
        val timer = SleepTimerState()
        timer.start(60_000, 0)
        assertTrue(timer.tick(65_000, true, false))
        assertFalse(timer.waitingForSongEnd)
        assertFalse(timer.songEnded())
    }

    @Test fun cancelAndRestartClearPendingSongBoundary() {
        val timer = SleepTimerState()
        timer.start(1_000, 0)
        timer.tick(1_000, true, true)
        timer.cancel()
        assertFalse(timer.songEnded())
        timer.start(1_000, 2_000)
        timer.tick(3_000, true, true)
        timer.start(60_000, 4_000)
        assertFalse(timer.waitingForSongEnd)
        assertFalse(timer.songEnded())
        assertFalse(timer.tick(5_000, false, true))
        assertEquals(59_000L, timer.remainingMs)
    }

    @Test fun changingOptionDuringCountdownUsesLatestSetting() {
        val timer = SleepTimerState()
        timer.start(1_000, 0)
        timer.tick(500, false, true)
        assertFalse(timer.tick(1_000, true, true))
        assertTrue(timer.waitingForSongEnd)
        assertTrue(timer.tick(1_001, false, true))
        assertFalse(timer.waitingForSongEnd)
    }

    @Test fun delayedWakeupUsesElapsedTimeAndZeroCancels() {
        val timer = SleepTimerState()
        timer.start(60_000, 1_000)
        assertTrue(timer.tick(200_000, false, true))
        timer.start(60_000, 200_000)
        timer.start(0, 200_000)
        assertNull(timer.remainingMs)
        assertFalse(timer.tick(300_000, true, true))
    }
}
