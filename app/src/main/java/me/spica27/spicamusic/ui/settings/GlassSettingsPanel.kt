@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica27.spicamusic.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.R
import me.spica27.spicamusic.common.entity.GlassSettings
import me.spica27.spicamusic.ui.theme.GlassSlider
import me.spica27.spicamusic.ui.theme.GlassSwitch
import kotlin.math.roundToInt

@Composable
fun GlassSettingsPanel(
    settings: GlassSettings,
    onChange: (GlassSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.glass_smooth_mode))
                Text(stringResource(R.string.glass_smooth_hint), style = MaterialTheme.typography.bodySmall)
            }
            GlassSwitch(draft.smoothMode, { onChange(draft.copy(smoothMode = it)) })
        }
        GlassSettingSlider(
            stringResource(R.string.glass_opacity),
            draft.opacity,
            20f..95f,
            { draft = draft.copy(opacity = it) },
            { onChange(draft) },
        )
        GlassSettingSlider(
            stringResource(R.string.glass_blur),
            if (draft.smoothMode) draft.blur.coerceAtMost(4) else draft.blur,
            0f..(if (draft.smoothMode) 4f else 16f),
            { draft = draft.copy(blur = it) },
            { onChange(draft) },
        )
        GlassSettingSlider(
            stringResource(R.string.glass_refraction),
            draft.refraction,
            0f..100f,
            { draft = draft.copy(refraction = it) },
            { onChange(draft) },
            enabled = !draft.smoothMode,
        )
        Text(stringResource(R.string.glass_apply_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GlassSettingSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
    onFinish: () -> Unit,
    enabled: Boolean = true,
) {
    Text("$label · $value", style = MaterialTheme.typography.bodyMedium)
    GlassSlider(
        value.toFloat(),
        { onChange(it.roundToInt()) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        valueRange = range,
        onValueChangeFinished = onFinish,
        enabled = enabled,
    )
}
