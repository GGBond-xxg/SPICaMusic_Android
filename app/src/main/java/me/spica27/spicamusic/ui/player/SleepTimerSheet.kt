package me.spica27.spicamusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.spica27.spicamusic.R
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.theme.glassSurface
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import me.spica27.spicamusic.ui.theme.GlassButton as Button
import me.spica27.spicamusic.ui.theme.GlassModalBottomSheet as ModalBottomSheet

private val SleepTimerPresets = listOf(15, 30, 45, 60, 90)
private const val CustomTimerMin = 1
private const val CustomTimerMax = 240
private const val CustomTimerStep = 5

/** 原项目样式的睡眠定时底部面板。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    durationMs: Long?,
    remainingMs: Long?,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var customExpanded by remember { mutableStateOf(false) }
    var editingMinutes by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var customMinutes by remember(durationMs) {
        mutableIntStateOf(
            durationMs
                ?.let { TimeUnit.MILLISECONDS.toMinutes(it).toInt() }
                ?.coerceIn(CustomTimerMin, CustomTimerMax)
                ?: 20,
        )
    }

    fun dismissAfter(action: () -> Unit) {
        action()
        scope.launch {
            delay(120)
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        if (editingMinutes) {
            TimerMinutesEditor(
                initialMinutes = customMinutes,
                onConfirm = {
                    customMinutes = it
                    editingMinutes = false
                },
                onDismiss = { editingMinutes = false },
                modifier =
                    Modifier
                        .heightIn(
                            max = 480.dp,
                        ).navigationBarsPadding()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.Large)
                        .padding(bottom = Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = Spacing.Small, bottom = Spacing.ExtraSmall)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(
                                CircleShape,
                            ).glassSurface(
                                CircleShape,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                opacity = 0.7f,
                            ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sleep_timer),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.sleep_timer_dialog_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))
                SleepTimerHero(
                    durationMs = durationMs,
                    remainingMs = remainingMs,
                    customExpanded = customExpanded,
                    customMinutes = customMinutes,
                )
                Spacer(modifier = Modifier.height(Spacing.Large))

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = ((maxWidth.value + 8f) / (56f * LocalDensity.current.fontScale + 8f)).toInt().coerceIn(1, 5)
                    // Round down so pixel rounding cannot push the last preset onto a new row.
                    val presetWidth = ((maxWidth - Spacing.Small * (columns - 1)) / columns).value.toInt().dp
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                        maxItemsInEachRow = columns,
                    ) {
                        SleepTimerPresets.forEach { minutes ->
                            val selected = durationMs == TimeUnit.MINUTES.toMillis(minutes.toLong())
                            Column(
                                modifier =
                                    Modifier
                                        .width(presetWidth)
                                        .clip(Shapes.ExtraLargeCornerBasedShape)
                                        .glassSurface(
                                            Shapes.ExtraLargeCornerBasedShape,
                                            if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                            tint =
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                },
                                            opacity = 0.7f,
                                        ).clickable {
                                            PlayerHaptics.click()
                                            dismissAfter { onSetTimer(minutes) }
                                        }.padding(vertical = Spacing.Medium),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = minutes.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Text(
                                    text = stringResource(R.string.sleep_timer_unit_minutes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.Medium))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(
                                Shapes.ExtraLargeCornerBasedShape,
                            ).glassSurface(
                                Shapes.ExtraLargeCornerBasedShape,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                                opacity = 0.7f,
                            ).padding(Spacing.Medium),
                ) {
                    FlowRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PlayerHaptics.click()
                                    customExpanded = !customExpanded
                                    editingMinutes = false
                                }.padding(vertical = Spacing.Small),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_timer_custom),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.sleep_timer_minutes, customMinutes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(visible = customExpanded) {
                        Column {
                            Text(
                                text = stringResource(R.string.sleep_timer_custom_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(Spacing.Medium))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                TimerStepButton(
                                    icon = Icons.Rounded.Remove,
                                    enabled = customMinutes > CustomTimerMin,
                                    onClick = {
                                        PlayerHaptics.click()
                                        customMinutes =
                                            (customMinutes - CustomTimerStep).coerceAtLeast(CustomTimerMin)
                                    },
                                )
                                Text(
                                    text = stringResource(R.string.sleep_timer_minutes, customMinutes),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                PlayerHaptics.click()
                                                editingMinutes = true
                                            }.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                                )
                                TimerStepButton(
                                    icon = Icons.Rounded.Add,
                                    enabled = customMinutes < CustomTimerMax,
                                    onClick = {
                                        PlayerHaptics.click()
                                        customMinutes =
                                            (customMinutes + CustomTimerStep).coerceAtMost(CustomTimerMax)
                                    },
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.Medium))
                            Button(
                                onClick = {
                                    PlayerHaptics.click()
                                    dismissAfter { onSetTimer(customMinutes) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !editingMinutes,
                            ) {
                                Text(stringResource(R.string.sleep_timer_start), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = remainingMs != null) {
                    Button(
                        onClick = {
                            PlayerHaptics.click()
                            dismissAfter(onCancelTimer)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.Medium),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.Small))
                        Text(stringResource(R.string.sleep_timer_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerHero(
    durationMs: Long?,
    remainingMs: Long?,
    customExpanded: Boolean,
    customMinutes: Int,
) {
    val targetSweep =
        when {
            customExpanded -> customMinutes.toFloat() / CustomTimerMax
            durationMs != null && remainingMs != null && durationMs > 0L ->
                (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> 0f
        }
    val sweep by animateFloatAsState(targetSweep, tween(600), label = "sleepTimerRing")
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(Shapes.ExtraLarge2CornerBasedShape)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f), Color.Transparent),
                    ),
                ).padding(vertical = Spacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(184.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(184.dp)) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2f
                val diameter = size.minDimension - stroke
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (sweep > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(primary, tertiary, primary)),
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter, diameter),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            when {
                customExpanded ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = customMinutes.toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.sleep_timer_unit_minutes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                remainingMs != null ->
                    Text(
                        text = formatSleepTimerRemaining(remainingMs),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                else ->
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(44.dp),
                    )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.Medium))
        Text(
            text =
                when {
                    customExpanded -> stringResource(R.string.sleep_timer_custom_hint)
                    remainingMs != null -> stringResource(R.string.sleep_timer_active_hint)
                    else -> stringResource(R.string.sleep_timer_dialog_subtitle)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimerStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = playerClick(onClick),
        enabled = enabled,
        modifier =
            Modifier
                .size(
                    48.dp,
                ).clip(
                    CircleShape,
                ).glassSurface(
                    CircleShape,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                    opacity = 0.7f,
                ),
    ) {
        Icon(icon, null)
    }
}

fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
