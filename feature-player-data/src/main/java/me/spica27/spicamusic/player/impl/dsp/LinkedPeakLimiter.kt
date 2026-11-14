package me.spica27.spicamusic.player.impl.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/** Instant attack, 80 ms release; one gain for all channels preserves the stereo image. */
internal class LinkedPeakLimiter {
    private var gain = 1f
    private var release = 0f

    fun configure(sampleRate: Float) {
        release = exp(-1.0 / (sampleRate.coerceAtLeast(1f) * 0.08)).toFloat()
        reset()
    }

    fun reset() {
        gain = 1f
    }

    fun process(frame: FloatArray) {
        var peak = 0f
        for (index in frame.indices) {
            if (!frame[index].isFinite()) frame[index] = 0f
            peak = maxOf(peak, abs(frame[index]))
        }
        val required = if (peak > 0.98f) 0.98f / peak else 1f
        gain = min(required, 1f - (1f - gain) * release)
        for (index in frame.indices) frame[index] *= gain
    }
}
