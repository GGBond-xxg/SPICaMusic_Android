package me.spica27.spicamusic.ui.audioeffects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

class EqualizerScene : StackScene() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val viewModel: AudioEffectsViewModel = koinViewModel()
        val enabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
        val bands by viewModel.eqBands.collectAsStateWithLifecycle()
        val hiFiMode by viewModel.hiFiMode.collectAsStateWithLifecycle()
        val controlsEnabled = !hiFiMode

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.equalizer_10_band)) },
                    navigationIcon = {
                        IconButton(onClick = { path.popTop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { viewModel.setAllEqBands(List(10) { 0f }) },
                            enabled = controlsEnabled,
                        ) {
                            Text(stringResource(R.string.equalizer_flat))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 32.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "switch") {
                    Card(
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Default.Equalizer,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.equalizer_enable),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text =
                                        stringResource(
                                            if (hiFiMode) {
                                                R.string.equalizer_disabled_by_hifi
                                            } else {
                                                R.string.equalizer_enable_subtitle
                                            },
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = enabled && controlsEnabled,
                                onCheckedChange = viewModel::setEqEnabled,
                                enabled = controlsEnabled,
                            )
                        }
                    }
                }

                item(key = "presets") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.dsp_equalizer_hint_preset),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PresetChip(
                                text = stringResource(R.string.equalizer_pop),
                                onClick = { viewModel.applyPreset(AudioEffectsViewModel.Preset.POP) },
                                enabled = controlsEnabled,
                            )
                            PresetChip(
                                text = stringResource(R.string.equalizer_rock),
                                onClick = { viewModel.applyPreset(AudioEffectsViewModel.Preset.ROCK) },
                                enabled = controlsEnabled,
                            )
                            PresetChip(
                                text = stringResource(R.string.equalizer_classical),
                                onClick = { viewModel.applyPreset(AudioEffectsViewModel.Preset.CLASSICAL) },
                                enabled = controlsEnabled,
                            )
                            PresetChip(
                                text = stringResource(R.string.equalizer_jazz),
                                onClick = { viewModel.applyPreset(AudioEffectsViewModel.Preset.JAZZ) },
                                enabled = controlsEnabled,
                            )
                        }
                    }
                }

                items(
                    count = FREQUENCIES.size,
                    key = { index -> FREQUENCIES[index] },
                    contentType = { "eq_band" },
                ) { index ->
                    val value = bands.getOrElse(index) { 0f }
                    EqualizerBand(
                        frequency = FREQUENCIES[index],
                        value = value,
                        enabled = enabled && controlsEnabled,
                        onValueChange = { viewModel.setEqBandGain(index, it) },
                    )
                }
            }
        }
    }

    private companion object {
        val FREQUENCIES =
            listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")
    }
}

@Composable
private fun PresetChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text) },
    )
}

@Composable
private fun EqualizerBand(
    frequency: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = frequency,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = String.format(Locale.US, "%+.1f dB", value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                valueRange = -12f..12f,
                steps = 47,
            )
        }
    }
}
