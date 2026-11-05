@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica27.spicamusic.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import me.spica27.spicamusic.topdisplay.SystemStatusLyricsManager
import me.spica27.spicamusic.ui.about.AboutScaffold
import me.spica27.spicamusic.ui.theme.GlassButton
import me.spica27.spicamusic.ui.theme.GlassCard
import me.spica27.spicamusic.ui.theme.GlassSlider
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
