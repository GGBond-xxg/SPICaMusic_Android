package me.spica27.spicamusic.ui.audioeffects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.NeteaseAudioQuality
import me.spica27.spicamusic.ui.about.AboutItemDivider
import me.spica27.spicamusic.ui.about.AboutScaffold
import me.spica27.spicamusic.ui.about.AboutSectionCard
import me.spica27.spicamusic.ui.settings.SettingsViewModel
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.clickHighlight
import org.koin.compose.viewmodel.koinViewModel

/**
 * 音效设置页。
 *
 * 均衡器区域沿用原项目的开关、预设和 10 段增益控制；其余播放输出开关保留
 * 当前项目已有的淡入淡出、Hi-Fi 与 USB DAC 能力。
 */
class AudioEffectsScene : StackScene() {
    override val predictiveBackEnabled: Boolean = false

    @Composable
    override fun Content() {
        val audioViewModel: AudioEffectsViewModel = koinViewModel()
        val settingsViewModel: SettingsViewModel = koinViewModel()

        val eqEnabled by audioViewModel.eqEnabled.collectAsStateWithLifecycle()
        val eqBands by audioViewModel.eqBands.collectAsStateWithLifecycle()
        val reverbEnabled by audioViewModel.reverbEnabled.collectAsStateWithLifecycle()
        val reverbLevel by audioViewModel.reverbLevel.collectAsStateWithLifecycle()
        val reverbRoomSize by audioViewModel.reverbRoomSize.collectAsStateWithLifecycle()
        val loudnessEnabled by
            audioViewModel.loudnessNormalizationEnabled.collectAsStateWithLifecycle()
        val fadeEnabled by settingsViewModel.fadeEnabled.collectAsStateWithLifecycle()
        val backgroundPlayback by settingsViewModel.backgroundPlayback.collectAsStateWithLifecycle()
        val resumeOnHeadset by settingsViewModel.resumeOnHeadset.collectAsStateWithLifecycle()
        val neteaseQualityValue by settingsViewModel.neteaseAudioQuality.collectAsStateWithLifecycle()
        val hiFiMode by settingsViewModel.hiFiMode.collectAsStateWithLifecycle()
        val usbDacOutput by settingsViewModel.usbDacOutput.collectAsStateWithLifecycle()
        val usbDeviceName by settingsViewModel.usbDeviceName.collectAsStateWithLifecycle()
        var qualityExpanded by rememberSaveable { mutableStateOf(false) }
        val qualityLabels = neteaseQualityLabels()
        val qualityName =
            qualityLabels[neteaseQualityValue]
                ?: qualityLabels.getValue(NeteaseAudioQuality.AUTO.value)

        AboutScaffold(title = stringResource(R.string.settings_sound_effects)) {
            item(key = "equalizer") {
                AboutSectionCard(
                    title = stringResource(R.string.audio_effects_section_equalizer),
                    subtitle = stringResource(R.string.audio_effects_subtitle),
                ) {
                    AudioSwitchRow(
                        title = stringResource(R.string.audio_effects_eq_enable),
                        subtitle =
                            if (hiFiMode) {
                                stringResource(R.string.equalizer_disabled_by_hifi)
                            } else {
                                stringResource(R.string.audio_effects_eq_enable_desc)
                            },
                        icon = Icons.Default.GraphicEq,
                        checked = eqEnabled && !hiFiMode,
                        enabled = !hiFiMode,
                        onCheckedChange = audioViewModel::setEqEnabled,
                    )
                    AnimatedVisibility(
                        visible = eqEnabled && !hiFiMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            EqualizerPresets(onPreset = audioViewModel::applyPreset)
                            EqualizerBands(
                                bands = eqBands,
                                onBandChange = audioViewModel::setEqBandGain,
                            )
                        }
                    }
                }
            }

            item(key = "reverb") {
                AboutSectionCard(title = stringResource(R.string.audio_effects_section_reverb)) {
                    AudioSwitchRow(
                        title = stringResource(R.string.audio_effects_reverb_enable),
                        subtitle = stringResource(R.string.reverb_spatial_desc),
                        icon = Icons.Default.SurroundSound,
                        checked = reverbEnabled,
                        onCheckedChange = audioViewModel::setReverbEnabled,
                    )
                    AnimatedVisibility(
                        visible = reverbEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            EffectSliderRow(
                                label = stringResource(R.string.reverb_intensity),
                                value = reverbLevel,
                                valueText = percentText(reverbLevel),
                                onValueChange = audioViewModel::setReverbLevel,
                            )
                            EffectSliderRow(
                                label = stringResource(R.string.room_size),
                                value = reverbRoomSize,
                                valueText = percentText(reverbRoomSize),
                                onValueChange = audioViewModel::setReverbRoomSize,
                            )
                        }
                    }
                }
            }

            item(key = "loudness") {
                AboutSectionCard(title = stringResource(R.string.audio_effects_section_loudness)) {
                    AudioSwitchRow(
                        title = stringResource(R.string.audio_effects_loudness_title),
                        subtitle = stringResource(R.string.audio_effects_loudness_desc),
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        checked = loudnessEnabled,
                        onCheckedChange = audioViewModel::setLoudnessNormalizationEnabled,
                    )
                }
            }

            item(key = "playback_effects") {
                AboutSectionCard(
                    title = stringResource(R.string.settings_playback),
                    subtitle = stringResource(R.string.settings_sound_effects_subtitle),
                ) {
                    AudioSwitchRow(
                        title = stringResource(R.string.settings_fade),
                        subtitle = stringResource(R.string.settings_fade_subtitle),
                        icon = Icons.Default.SwapCalls,
                        checked = fadeEnabled,
                        onCheckedChange = settingsViewModel::setFadeEnabled,
                    )
                    AboutItemDivider()
                    AudioSwitchRow(
                        title = stringResource(R.string.settings_background_playback),
                        subtitle = stringResource(R.string.settings_background_playback_subtitle),
                        icon = Icons.Default.PlayCircle,
                        checked = backgroundPlayback,
                        onCheckedChange = settingsViewModel::setBackgroundPlayback,
                    )
                    AboutItemDivider()
                    AudioSwitchRow(
                        title = stringResource(R.string.settings_headset_resume),
                        subtitle = stringResource(R.string.settings_headset_resume_subtitle),
                        icon = Icons.Default.Headphones,
                        checked = resumeOnHeadset,
                        onCheckedChange = settingsViewModel::setResumeOnHeadset,
                    )
                    AboutItemDivider()
                    AudioSelectRow(
                        title = stringResource(R.string.settings_audio_quality),
                        subtitle = stringResource(R.string.settings_netease_quality_short, qualityName),
                        icon = Icons.Default.Waves,
                        expanded = qualityExpanded,
                        onClick = { qualityExpanded = !qualityExpanded },
                    )
                    AnimatedVisibility(
                        visible = qualityExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            NeteaseAudioQuality.entries.forEach { quality ->
                                AudioQualityOptionRow(
                                    quality = quality,
                                    label = qualityLabels.getValue(quality.value),
                                    selected = quality.value == neteaseQualityValue,
                                    onClick = {
                                        settingsViewModel.setNeteaseAudioQuality(quality.value)
                                        qualityExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    AboutItemDivider()
                    AudioSwitchRow(
                        title = stringResource(R.string.settings_hifi),
                        subtitle =
                            stringResource(
                                if (settingsViewModel.hiFiSupported) {
                                    R.string.settings_hifi_subtitle
                                } else {
                                    R.string.settings_hifi_unsupported
                                },
                            ),
                        icon = Icons.Default.HighQuality,
                        checked = hiFiMode,
                        enabled = settingsViewModel.hiFiSupported,
                        onCheckedChange = settingsViewModel::setHiFiMode,
                    )
                    AboutItemDivider()
                    AudioSwitchRow(
                        title = stringResource(R.string.settings_usb_dac),
                        subtitle =
                            usbDeviceName?.let {
                                stringResource(R.string.settings_usb_dac_connected, it)
                            } ?: stringResource(R.string.settings_usb_dac_disconnected),
                        icon = Icons.Default.Usb,
                        checked = usbDacOutput,
                        enabled = usbDeviceName != null,
                        onCheckedChange = settingsViewModel::setUsbDacOutput,
                    )
                }
            }
        }
    }
}

@Composable
private fun neteaseQualityLabels(): Map<String, String> =
    mapOf(
        NeteaseAudioQuality.AUTO.value to stringResource(R.string.netease_quality_auto),
        NeteaseAudioQuality.STANDARD.value to stringResource(R.string.netease_quality_standard),
        NeteaseAudioQuality.EXHIGH.value to stringResource(R.string.netease_quality_exhigh),
        NeteaseAudioQuality.LOSSLESS.value to stringResource(R.string.netease_quality_lossless),
        NeteaseAudioQuality.HIRES.value to stringResource(R.string.netease_quality_hires),
        NeteaseAudioQuality.JY_EFFECT.value to stringResource(R.string.netease_quality_jy_effect),
        NeteaseAudioQuality.SKY.value to stringResource(R.string.netease_quality_sky),
        NeteaseAudioQuality.MASTER.value to stringResource(R.string.netease_quality_master),
    )

private fun NeteaseAudioQuality.icon(): ImageVector =
    when (this) {
        NeteaseAudioQuality.AUTO -> Icons.AutoMirrored.Filled.VolumeUp
        NeteaseAudioQuality.STANDARD -> Icons.Default.VolumeDown
        NeteaseAudioQuality.EXHIGH -> Icons.Default.Waves
        NeteaseAudioQuality.LOSSLESS -> Icons.Default.GraphicEq
        NeteaseAudioQuality.HIRES -> Icons.Default.HighQuality
        NeteaseAudioQuality.JY_EFFECT -> Icons.Default.SurroundSound
        NeteaseAudioQuality.SKY -> Icons.Default.SwapCalls
        NeteaseAudioQuality.MASTER -> Icons.Default.Usb
    }

@Composable
private fun AudioSelectRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(Shapes.LargeCornerBasedShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
        )
    }
}

@Composable
private fun AudioQualityOptionRow(
    quality: NeteaseAudioQuality,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickHighlight(onClick = onClick)
                .padding(start = 32.dp, end = Spacing.Large, top = Spacing.Small, bottom = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Icon(
            quality.icon(),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EffectSliderRow(
    label: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun AudioSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "audio_setting_icon_scale",
    )
    val iconBackground by animateColorAsState(
        targetValue =
            if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        label = "audio_setting_icon_background",
    )
    val iconTint by animateColorAsState(
        targetValue =
            if (checked) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "audio_setting_icon_tint",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
                .clickHighlight(enabled = enabled, onClick = { onCheckedChange(!checked) })
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(Shapes.LargeCornerBasedShape)
                    .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** 原项目的四组均衡器预设。 */
@Composable
private fun EqualizerPresets(onPreset: (AudioEffectsViewModel.Preset) -> Unit) {
    val presets =
        listOf(
            AudioEffectsViewModel.Preset.POP to stringResource(R.string.preset_pop),
            AudioEffectsViewModel.Preset.ROCK to stringResource(R.string.preset_rock),
            AudioEffectsViewModel.Preset.CLASSICAL to stringResource(R.string.preset_classical),
            AudioEffectsViewModel.Preset.JAZZ to stringResource(R.string.preset_jazz),
        )
    Column(
        modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = stringResource(R.string.presets_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            presets.forEach { (preset, label) ->
                FilterChip(
                    selected = false,
                    onClick = { onPreset(preset) },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 原项目的 10 段均衡器，每段范围为 -12 dB 到 +12 dB。 */
@Composable
private fun EqualizerBands(
    bands: List<Float>,
    onBandChange: (Int, Float) -> Unit,
) {
    val frequencyLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        repeat(10) { index ->
            val gain = bands.getOrElse(index) { 0f }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = gain.toInt().toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                VerticalBandSlider(
                    value = gain,
                    onValueChange = { onBandChange(index, it) },
                    modifier =
                        Modifier
                            .height(140.dp)
                            .fillMaxWidth(),
                )
                Text(
                    text = frequencyLabels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun VerticalBandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -12f..12f,
            modifier =
                Modifier
                    .graphicsLayer { rotationZ = 270f }
                    .layout { measurable, constraints ->
                        val placeable =
                            measurable.measure(
                                Constraints(
                                    minWidth = constraints.minHeight,
                                    maxWidth = constraints.maxHeight,
                                    minHeight = constraints.minWidth,
                                    maxHeight = constraints.maxWidth,
                                ),
                            )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                x = (placeable.height - placeable.width) / 2,
                                y = (placeable.width - placeable.height) / 2,
                            )
                        }
                    },
        )
    }
}

private fun percentText(value: Float): String = "${(value * 100).toInt()}%"
