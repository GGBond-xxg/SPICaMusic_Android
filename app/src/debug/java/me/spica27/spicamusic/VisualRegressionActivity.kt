package me.spica27.spicamusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.common.entity.GlassSettings
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.ui.player.SleepTimerSheet
import me.spica27.spicamusic.ui.theme.GlassBackdropHost
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import me.spica27.spicamusic.ui.widget.audio_seekbar.AudioDynamicWaveSlider
import me.spica27.spicamusic.ui.widget.audio_seekbar.AudioWaveSlider

/** Deterministic screenshot fixture for devices whose Compose test idling never completes. */
class VisualRegressionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("landscape", false)) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        val timer = intent.getBooleanExtra("timer", false)
        val glass = intent.getBooleanExtra("glass", true)
        val dark = intent.getBooleanExtra("dark", true)
        val scale = intent.getFloatExtra("fontScale", 1f).coerceIn(1f, 2f)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                SPICaMusicTheme(
                    darkTheme = dark,
                    themeColor = Color(0xFF103D5A),
                    themeColorStyle = if (glass) ThemeColorStyle.Glass else ThemeColorStyle.Flat,
                    animateColors = false,
                    glassSettings = GlassSettings(smoothMode = intent.getBooleanExtra("smooth", false)),
                ) {
                    GlassBackdropHost {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = .2f))
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Text("Waveform contrast · 50%", style = MaterialTheme.typography.titleLarge)
                            Text("Time-domain waveform")
                            AudioWaveSlider(
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                progress = .5f,
                                amplitudes = List(128) { 100 + it % 17 * 10 },
                                waveformBrush = SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = .22f)),
                                progressBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                onProgressChange = {},
                            )
                            Text("Dynamic waveform")
                            AudioDynamicWaveSlider(
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                progress = .5f,
                                fftAmplitudes = FloatArray(128) { .15f + (it % 23) / 30f },
                                waveformBrush = SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = .22f)),
                                progressBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                onProgressChange = {},
                            )
                        }
                        if (timer) {
                            var enabled by remember { mutableStateOf(false) }
                            SleepTimerSheet(null, null, {}, {}, {}, enabled, { enabled = it })
                        }
                    }
                }
            }
        }
    }
}
