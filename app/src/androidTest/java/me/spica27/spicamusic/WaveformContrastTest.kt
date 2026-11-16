package me.spica27.spicamusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.ui.widget.audio_seekbar.AudioDynamicWaveSlider
import me.spica27.spicamusic.ui.widget.audio_seekbar.AudioWaveSlider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WaveformContrastTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playedWaveformRetainsFullOpacityInsteadOfInheritingTrackAlpha() {
        var dynamic by mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme(onSurface = Color.White)) {
                val modifier = Modifier.width(280.dp).height(64.dp).background(Color.Black)
                if (dynamic) {
                    AudioDynamicWaveSlider(
                        modifier = modifier,
                        progress = .5f,
                        fftAmplitudes = FloatArray(128) { .8f },
                        waveformBrush = SolidColor(Color.White.copy(alpha = .22f)),
                        progressBrush = SolidColor(Color.White),
                        onProgressChange = {},
                    )
                } else {
                    AudioWaveSlider(
                        modifier = modifier,
                        progress = .5f,
                        amplitudes =
                            List(128) {
                                if (it % 2 ==
                                    0
                                ) {
                                    100
                                } else {
                                    200
                                }
                            },
                        waveformBrush = SolidColor(Color.White.copy(alpha = .22f)),
                        progressBrush = SolidColor(Color.White),
                        onProgressChange = {},
                    )
                }
            }
        }
        for (useDynamic in listOf(false, true)) {
            compose.runOnIdle { dynamic = useDynamic }
            val pixels = compose.onRoot().captureToImage().toPixelMap()

            fun peakLuminance(
                from: Float,
                to: Float,
            ): Float {
                var peak = 0f
                for (x in (pixels.width * from).toInt() until (pixels.width * to).toInt()) {
                    for (y in 0 until pixels.height) peak = maxOf(peak, pixels[x, y].luminance())
                }
                return peak
            }
            assertTrue("Played waveform must stay opaque ($useDynamic)", peakLuminance(.1f, .4f) > .8f)
            assertTrue("Unplayed waveform must remain subdued ($useDynamic)", peakLuminance(.6f, .9f) < .1f)
        }
    }
}
