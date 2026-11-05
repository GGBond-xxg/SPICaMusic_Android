@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica27.spicamusic.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.topdisplay.StatusLyricsAppearance
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
        var overlayAllowed by remember(manager) { mutableStateOf(manager.canShowOverlay()) }
        var notificationsAllowed by remember(context) {
            mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
        }
        var settingsUnavailable by remember { mutableStateOf(false) }
        val appearance by manager.appearance.collectAsStateWithLifecycle()
        var draft by remember(appearance) { mutableStateOf(appearance) }

        DisposableEffect(lifecycleOwner, manager) {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        overlayAllowed = manager.canShowOverlay()
                        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        AboutScaffold(title = stringResource(R.string.status_lyrics_settings)) {
            item {
                GlassCard {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(
                                if (nativeSupported) {
                                    R.string.status_lyrics_native_summary
                                } else {
                                    R.string.status_lyrics_overlay_summary
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(
                                if (nativeSupported) {
                                    R.string.status_lyrics_native_hint
                                } else {
                                    R.string.status_lyrics_overlay_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (nativeSupported) {
                            Text(
                                stringResource(
                                    if (notificationsAllowed) {
                                        R.string.status_lyrics_notifications_granted
                                    } else {
                                        R.string.status_lyrics_notifications_needed
                                    },
                                ),
                                color =
                                    if (notificationsAllowed) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                            )
                            GlassButton(
                                onClick = {
                                    settingsUnavailable =
                                        runCatching {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                            )
                                        }.recoverCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:${context.packageName}"),
                                                ),
                                            )
                                        }.isFailure
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.status_lyrics_notifications_action))
                            }
                        } else {
                            Text(
                                stringResource(
                                    if (overlayAllowed) {
                                        R.string.status_lyrics_permission_granted
                                    } else {
                                        R.string.status_lyrics_permission_needed
                                    },
                                ),
                                color =
                                    if (overlayAllowed) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                            )
                            GlassButton(
                                onClick = {
                                    settingsUnavailable =
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}"),
                                                ),
                                            )
                                        }.recoverCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.parse("package:${context.packageName}"),
                                                ),
                                            )
                                        }.isFailure
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.status_lyrics_permission_action))
                            }
                        }
                        if (settingsUnavailable) {
                            Text(
                                stringResource(
                                    if (nativeSupported) {
                                        R.string.status_lyrics_notifications_unavailable
                                    } else {
                                        R.string.status_lyrics_settings_unavailable
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            if (!nativeSupported) {
                item {
                    GlassCard {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.status_lyrics_preview), style = MaterialTheme.typography.titleMedium)
                            StatusLyricsPreview(draft)
                            Text(stringResource(R.string.status_lyrics_preview_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_position_x),
                        draft.xDp,
                        -180f..180f,
                        "dp",
                        { draft = draft.copy(xDp = it) },
                        { manager.updateAppearance(draft) },
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_position_y),
                        draft.yDp,
                        0f..240f,
                        "dp",
                        { draft = draft.copy(yDp = it) },
                        { manager.updateAppearance(draft) },
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_width),
                        draft.widthDp,
                        120f..360f,
                        "dp",
                        { draft = draft.copy(widthDp = it) },
                        { manager.updateAppearance(draft) },
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_text_size),
                        draft.textSizeSp,
                        10f..28f,
                        "sp",
                        { draft = draft.copy(textSizeSp = it) },
                        { manager.updateAppearance(draft) },
                    )
                }
                item {
                    StatusLyricsSlider(
                        stringResource(R.string.status_lyrics_opacity),
                        draft.opacityPercent,
                        20f..100f,
                        "%",
                        { draft = draft.copy(opacityPercent = it) },
                        { manager.updateAppearance(draft) },
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
private fun StatusLyricsPreview(appearance: StatusLyricsAppearance) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clipToBounds()
            .background(Color(0xFF24262B)),
    ) {
        val scale = (maxWidth / 400.dp).coerceAtMost(1f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(x = (appearance.xDp * scale).dp, y = (16 + appearance.yDp * 0.5f).dp)
                .width((appearance.widthDp * scale).dp)
                .padding(4.dp),
        ) {
            Text(
                stringResource(R.string.status_lyrics_preview_text),
                color = Color.White.copy(alpha = appearance.opacityPercent / 100f),
                fontSize = appearance.textSizeSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
