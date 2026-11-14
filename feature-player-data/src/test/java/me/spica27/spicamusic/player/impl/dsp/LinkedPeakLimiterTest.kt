package me.spica27.spicamusic.player.impl.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class LinkedPeakLimiterTest {
    @Test fun quietAudioIsUnchanged() {
        val limiter = LinkedPeakLimiter().apply { configure(48000f) }
        val frame = floatArrayOf(.2f, -.4f)
        limiter.process(frame)
        assertEquals(.2f, frame[0], .00001f)
        assertEquals(-.4f, frame[1], .00001f)
    }

    @Test fun boostedAudioStaysBelowFullScaleAndKeepsChannelRatio() {
        val limiter = LinkedPeakLimiter().apply { configure(48000f) }
        repeat(48000) { sample ->
            val signal = (sin(sample * 2.0 * Math.PI * 1000 / 48000) * 4).toFloat()
            val frame = floatArrayOf(signal, -signal / 2)
            limiter.process(frame)
            assertTrue(abs(frame[0]) <= .980001f)
            assertEquals(-frame[0] / 2, frame[1], .00001f)
        }
    }

    @Test fun gainRecoversSmoothlyAndFlushStartsFresh() {
        val limiter = LinkedPeakLimiter().apply { configure(48000f) }
        limiter.process(floatArrayOf(4f, 2f))
        var previous = 0f
        repeat(48000) {
            val frame = floatArrayOf(.5f, .5f)
            limiter.process(frame)
            assertTrue(frame[0] >= previous)
            if (it > 0) assertTrue(frame[0] - previous < .001f)
            previous = frame[0]
        }
        assertEquals(.5f, previous, .001f)
        limiter.process(floatArrayOf(4f))
        limiter.reset()
        val frame = floatArrayOf(.5f)
        limiter.process(frame)
        assertEquals(.5f, frame[0], .00001f)
    }

    @Test fun invalidSamplesDoNotPoisonFollowingAudio() {
        val limiter = LinkedPeakLimiter().apply { configure(44100f) }
        val frame = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY)
        limiter.process(frame)
        assertTrue(frame.all { it == 0f })
        val next = floatArrayOf(.25f)
        limiter.process(next)
        assertEquals(.25f, next[0], .00001f)
    }
}
