package me.spica27.spicamusic

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.test.platform.app.InstrumentationRegistry
import me.spica27.spicamusic.service.SleepTimerController
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/** Real audio-engine boundary tests; silent sources never touch the user's queue. */
@UnstableApi
class SleepTimerPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun finishSongPausesAtBoundaryInAllRepeatModes() {
        for (mode in listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ALL)) {
            val clock = AtomicLong(0)
            lateinit var player: ExoPlayer
            lateinit var timer: SleepTimerController
            var originalOption = false
            try {
                instrumentation.runOnMainSync {
                    player = ExoPlayer.Builder(instrumentation.targetContext).build()
                    player.volume = 0f
                    player.repeatMode = mode
                    player.shuffleModeEnabled = mode == Player.REPEAT_MODE_ALL
                    player.setMediaSources(List(2) { SilenceMediaSource.Factory().setDurationUs(3_000_000L).createMediaSource() })
                    timer = SleepTimerController(instrumentation.targetContext, clock::get)
                    originalOption = timer.finishCurrentSong.value
                    timer.attach(player)
                    timer.setFinishCurrentSong(true)
                    player.prepare()
                    player.play()
                }
                await { player.isPlaying }
                instrumentation.runOnMainSync { timer.start(1) }
                clock.set(60_000)
                await { timer.remainingMs.value == 0L }
                instrumentation.runOnMainSync {
                    assertTrue(player.playWhenReady)
                    assertTrue(player.pauseAtEndOfMediaItems)
                }
                await { timer.remainingMs.value == null }
                instrumentation.runOnMainSync {
                    assertFalse(player.playWhenReady)
                    assertFalse(player.pauseAtEndOfMediaItems)
                    assertTrue("Must stop at the song boundary", player.currentPosition >= 2_900L)
                }
            } finally {
                instrumentation.runOnMainSync {
                    timer.setFinishCurrentSong(originalOption)
                    timer.detach()
                    player.release()
                }
            }
        }
    }

    @Test fun cancellingPendingTimerAllowsFollowingSong() {
        val clock = AtomicLong(0)
        lateinit var player: ExoPlayer
        lateinit var timer: SleepTimerController
        var originalOption = false
        try {
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(instrumentation.targetContext).build()
                player.volume = 0f
                player.setMediaSources(List(2) { SilenceMediaSource.Factory().setDurationUs(4_000_000L).createMediaSource() })
                timer = SleepTimerController(instrumentation.targetContext, clock::get)
                originalOption = timer.finishCurrentSong.value
                timer.attach(player)
                timer.setFinishCurrentSong(true)
                player.prepare()
                player.play()
            }
            await { player.isPlaying }
            instrumentation.runOnMainSync { timer.start(1) }
            clock.set(60_000)
            await { timer.remainingMs.value == 0L }
            instrumentation.runOnMainSync { timer.cancel() }
            await { player.currentMediaItemIndex == 1 }
            instrumentation.runOnMainSync {
                assertTrue(player.playWhenReady)
                assertFalse(player.pauseAtEndOfMediaItems)
            }
        } finally {
            instrumentation.runOnMainSync {
                timer.setFinishCurrentSong(originalOption)
                timer.detach()
                player.release()
            }
        }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = predicate() }
            if (ready) return
            Thread.sleep(25)
        }
        fail("Player condition did not become true within 10 seconds")
    }
}
