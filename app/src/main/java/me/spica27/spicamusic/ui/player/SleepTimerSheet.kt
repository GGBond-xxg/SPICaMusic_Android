package me.spica27.spicamusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.spica27.spicamusic.R
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

private val SleepTimerPresets = listOf(15, 30, 45, 60, 90)
private const val CustomTimerMin = 5
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
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
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close))
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                SleepTimerPresets.forEach { minutes ->
                    val selected = durationMs == TimeUnit.MINUTES.toMillis(minutes.toLong())
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(Shapes.ExtraLargeCornerBasedShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                ).clickable {
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

            Spacer(modifier = Modifier.height(Spacing.Medium))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .animateContentSize(tween(220))
                        .clip(Shapes.ExtraLargeCornerBasedShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { customExpanded = !customExpanded }
                        .padding(Spacing.Medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TimerStepButton(
                                icon = Icons.Rounded.Remove,
                                enabled = customMinutes > CustomTimerMin,
                                onClick = { customMinutes = (customMinutes - CustomTimerStep).coerceAtLeast(CustomTimerMin) },
                            )
                            Text(
                                text = stringResource(R.string.sleep_timer_minutes, customMinutes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            TimerStepButton(
                                icon = Icons.Rounded.Add,
                                enabled = customMinutes < CustomTimerMax,
                                onClick = { customMinutes = (customMinutes + CustomTimerStep).coerceAtMost(CustomTimerMax) },
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.Medium))
                        Button(
                            onClick = { dismissAfter { onSetTimer(customMinutes) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.sleep_timer_start))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = remainingMs != null) {
                Button(
                    onClick = { dismissAfter(onCancelTimer) },
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
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
