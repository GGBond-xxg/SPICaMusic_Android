@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica27.spicamusic.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.topdisplay.StatusLyricsAlignment
import me.spica27.spicamusic.topdisplay.StatusLyricsAppearance
import me.spica27.spicamusic.topdisplay.SystemStatusLyricsManager
import me.spica27.spicamusic.ui.about.AboutScaffold
import me.spica27.spicamusic.ui.theme.GlassButton
import me.spica27.spicamusic.ui.theme.GlassCard
import me.spica27.spicamusic.ui.theme.GlassFilterChip
import me.spica27.spicamusic.ui.theme.GlassSlider
import me.spica27.spicamusic.ui.theme.GlassTextButton
import org.koin.compose.koinInject
import kotlin.math.roundToInt

class StatusLyricsSettingsScene : StackScene() {
    @Composable
    override fun Content() {
        val manager: SystemStatusLyricsManager = koinInject()
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val nativeSupported = remember(manager) { manager.isNativeSupported() }
        var settingsUnavailable by remember { mutableStateOf(false) }
        val appearance by manager.appearance.collectAsStateWithLifecycle()
        val sampleText = stringResource(R.string.status_lyrics_preview_text)

        DisposableEffect(lifecycleOwner, manager, sampleText) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> manager.beginLiveAdjustment(sampleText)
                        Lifecycle.Event.ON_PAUSE -> manager.endLiveAdjustment()
                        else -> Unit
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                manager.beginLiveAdjustment(sampleText)
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                manager.endLiveAdjustment()
            }
        }

        AboutScaffold(title = stringResource(R.string.status_lyrics_settings)) {
            item {
                GlassButton(
                    onClick = {
                        manager.endLiveAdjustment()
                        settingsUnavailable =
                            runCatching {
                                context.startActivity(
                                    if (nativeSupported) {
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    } else {
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    },
                                )
                            }.recoverCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                                )
                            }.isFailure
                        if (settingsUnavailable) manager.beginLiveAdjustment(sampleText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (nativeSupported) R.string.status_lyrics_notifications_action else R.string.status_lyrics_permission_action,
                        ),
                    )
                }
            }
            if (settingsUnavailable) {
                item {
                    Text(
                        stringResource(
                            if (nativeSupported) R.string.status_lyrics_notifications_unavailable else R.string.status_lyrics_settings_unavailable,
                        ),
                    )
                }
            }
            if (nativeSupported) {
                item { Text(stringResource(R.string.status_lyrics_native_hint), style = MaterialTheme.typography.bodyMedium) }
            } else {
                item {
                    GlassCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.status_lyrics_alignment), style = MaterialTheme.typography.titleMedium)
                            FlowRow {
                                StatusLyricsAlignment.entries.forEach { alignment ->
                                    GlassFilterChip(
                                        selected = appearance.alignment == alignment,
                                        onClick = { manager.updateAppearance(manager.appearance.value.copy(alignment = alignment)) },
                                        label = {
                                            Text(
                                                stringResource(
                                                    when (alignment) {
                                                        StatusLyricsAlignment.LEFT -> R.string.status_lyrics_align_left
                                                        StatusLyricsAlignment.CENTER -> R.string.status_lyrics_align_center
                                                        StatusLyricsAlignment.RIGHT -> R.string.status_lyrics_align_right
                                                    },
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item { StatusLyricsColorSettings(appearance, manager) }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_position_x),
                        appearance.xDp,
                        -180f..180f,
                        "dp",
                        { manager.updateAppearance(manager.appearance.value.copy(xDp = it), persist = false) },
                        manager::saveAppearance,
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_position_y),
                        appearance.yDp,
                        0f..240f,
                        "dp",
                        { manager.updateAppearance(manager.appearance.value.copy(yDp = it), persist = false) },
                        manager::saveAppearance,
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_width),
                        appearance.widthDp,
                        120f..360f,
                        "dp",
                        { manager.updateAppearance(manager.appearance.value.copy(widthDp = it), persist = false) },
                        manager::saveAppearance,
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_text_size),
                        appearance.textSizeSp,
                        10f..28f,
                        "sp",
                        { manager.updateAppearance(manager.appearance.value.copy(textSizeSp = it), persist = false) },
                        manager::saveAppearance,
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_opacity),
                        appearance.opacityPercent,
                        20f..100f,
                        "%",
                        { manager.updateAppearance(manager.appearance.value.copy(opacityPercent = it), persist = false) },
                        manager::saveAppearance,
                    )
                }
                item {
                    GlassButton(onClick = manager::resetAppearance, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.status_lyrics_reset))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLyricsColorSettings(
    appearance: StatusLyricsAppearance,
    manager: SystemStatusLyricsManager,
) {
    var customExpanded by rememberSaveable { mutableStateOf(false) }
    val presets =
        listOf(
            0xFFFFFF to R.string.status_lyrics_color_white,
            0x000000 to R.string.status_lyrics_color_black,
            0xFF5252 to R.string.status_lyrics_color_red,
            0xFFCA28 to R.string.status_lyrics_color_yellow,
            0x69F0AE to R.string.status_lyrics_color_green,
            0x40C4FF to R.string.status_lyrics_color_blue,
        )
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.status_lyrics_color), style = MaterialTheme.typography.titleMedium)
            FlowRow {
                presets.forEach { (rgb, label) ->
                    GlassFilterChip(
                        selected = appearance.colorRgb == rgb,
                        onClick = { manager.updateAppearance(manager.appearance.value.copy(colorRgb = rgb)) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .background(Color(0xFF000000.toInt() or rgb), CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                )
                                Text(stringResource(label))
                            }
                        },
                    )
                }
            }
            GlassTextButton(onClick = { customExpanded = !customExpanded }) {
                Text(
                    stringResource(R.string.status_lyrics_custom_color) + " · #" +
                        appearance.colorRgb
                            .toString(16)
                            .padStart(6, '0')
                            .uppercase(),
                )
            }
            if (customExpanded) {
                listOf(
                    16 to R.string.status_lyrics_color_red,
                    8 to R.string.status_lyrics_color_green,
                    0 to R.string.status_lyrics_color_blue,
                ).forEach { (shift, label) ->
                    val channel = (appearance.colorRgb shr shift) and 0xFF
                    val channelLabel = stringResource(label)
                    Text("$channelLabel · $channel", style = MaterialTheme.typography.bodyLarge)
                    GlassSlider(
                        value = channel.toFloat(),
                        onValueChange = { value ->
                            val rgb = (manager.appearance.value.colorRgb and (0xFF shl shift).inv()) or (value.roundToInt() shl shift)
                            manager.updateAppearance(manager.appearance.value.copy(colorRgb = rgb), persist = false)
                        },
                        onValueChangeFinished = manager::saveAppearance,
                        valueRange = 0f..255f,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = channelLabel },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLyricsSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChange: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$label · $value $unit", style = MaterialTheme.typography.bodyLarge)
            GlassSlider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                valueRange = range,
                onValueChangeFinished = onFinish,
            )
        }
    }
}
