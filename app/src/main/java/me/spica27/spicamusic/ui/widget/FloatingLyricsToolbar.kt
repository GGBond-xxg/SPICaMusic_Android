package me.spica27.spicamusic.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.spcia.lyric_core.entity.SongLyrics
import me.spica27.spicamusic.R
import java.util.Locale
import kotlin.math.roundToInt

/** 顶部编辑按钮打开的歌词工具面板。所有二级选项都在当前面板内下拉展开。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorSheet(
    offsetMs: Long,
    textAlignment: LyricsTextAlignment,
    textScale: Float,
    selectedFontId: String,
    customFonts: List<LyricsCustomFont>,
    lyricSources: List<SongLyrics>,
    currentLyricSourceIndex: Int,
    onOffsetChange: (Long) -> Unit,
    onTextAlignmentChange: (LyricsTextAlignment) -> Unit,
    onTextScaleChange: (Float) -> Unit,
    onFontSelected: (String) -> Unit,
    onFontDeleted: (LyricsCustomFont) -> Unit,
    onAddFont: () -> Unit,
    onLyricSourceSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedSection by remember { mutableStateOf<String?>(null) }
    val selectedFontName =
        when (selectedFontId) {
            LyricsFontIds.DEFAULT -> stringResource(R.string.default_font)
            LyricsFontIds.MI_SANS -> "MiSans"
            else -> customFonts.firstOrNull { it.id == selectedFontId }?.name ?: stringResource(R.string.default_font)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lyrics_editor),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.lyrics_editor_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LyricsCircleButton(
                    onClick = onDismiss,
                    size = 40.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close))
                }
            }

            EditorSection(title = stringResource(R.string.lyrics_alignment)) {
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AlignmentOption(
                        label = stringResource(R.string.align_left),
                        icon = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                        selected = textAlignment == LyricsTextAlignment.Start,
                        onClick = { onTextAlignmentChange(LyricsTextAlignment.Start) },
                        modifier = Modifier.weight(1f),
                    )
                    AlignmentOption(
                        label = stringResource(R.string.align_center),
                        icon = Icons.Rounded.FormatAlignCenter,
                        selected = textAlignment == LyricsTextAlignment.Center,
                        onClick = { onTextAlignmentChange(LyricsTextAlignment.Center) },
                        modifier = Modifier.weight(1f),
                    )
                    AlignmentOption(
                        label = stringResource(R.string.align_right),
                        icon = Icons.AutoMirrored.Rounded.FormatAlignRight,
                        selected = textAlignment == LyricsTextAlignment.End,
                        onClick = { onTextAlignmentChange(LyricsTextAlignment.End) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ExpandableEditorSection(
                title = stringResource(R.string.lyrics_text_size),
                value = stringResource(R.string.percentage_format, (textScale * 100f).roundToInt()),
                icon = Icons.Rounded.FormatSize,
                expanded = expandedSection == SECTION_TEXT_SIZE,
                onToggle = {
                    expandedSection = if (expandedSection == SECTION_TEXT_SIZE) null else SECTION_TEXT_SIZE
                },
            ) {
                StepperRow(
                    value = stringResource(R.string.percentage_format, (textScale * 100f).roundToInt()),
                    onDecrease = { onTextScaleChange((textScale - TEXT_SCALE_STEP).coerceAtLeast(MIN_TEXT_SCALE)) },
                    onReset = { onTextScaleChange(1f) },
                    onIncrease = { onTextScaleChange((textScale + TEXT_SCALE_STEP).coerceAtMost(MAX_TEXT_SCALE)) },
                )
            }

            ExpandableEditorSection(
                title = stringResource(R.string.lyrics_font_style),
                value = selectedFontName,
                icon = Icons.Rounded.FontDownload,
                expanded = expandedSection == SECTION_FONT,
                onToggle = { expandedSection = if (expandedSection == SECTION_FONT) null else SECTION_FONT },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    DropdownChoice(
                        title = stringResource(R.string.default_font),
                        selected = selectedFontId == LyricsFontIds.DEFAULT,
                        onClick = { onFontSelected(LyricsFontIds.DEFAULT) },
                    )
                    DropdownDivider()
                    DropdownChoice(
                        title = "MiSans",
                        selected = selectedFontId == LyricsFontIds.MI_SANS,
                        onClick = { onFontSelected(LyricsFontIds.MI_SANS) },
                    )
                    customFonts.forEach { font ->
                        DropdownDivider()
                        DropdownChoice(
                            title = font.name,
                            selected = selectedFontId == font.id,
                            onClick = { onFontSelected(font.id) },
                            onDelete = { onFontDeleted(font) },
                        )
                    }
                    DropdownDivider()
                    DropdownChoice(
                        title = stringResource(R.string.add_more_fonts),
                        leadingIcon = Icons.Rounded.Add,
                        selected = false,
                        onClick = onAddFont,
                    )
                }
            }

            if (lyricSources.size > 1) {
                val currentSource = lyricSources.getOrNull(currentLyricSourceIndex)
                ExpandableEditorSection(
                    title = stringResource(R.string.toggle_lyrics),
                    value = currentSource?.name.orEmpty(),
                    icon = Icons.Rounded.LibraryMusic,
                    expanded = expandedSection == SECTION_LYRICS_SOURCE,
                    onToggle = {
                        expandedSection = if (expandedSection == SECTION_LYRICS_SOURCE) null else SECTION_LYRICS_SOURCE
                    },
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        lyricSources.forEachIndexed { index, source ->
                            if (index > 0) DropdownDivider()
                            DropdownChoice(
                                title = source.name,
                                subtitle = source.artist,
                                selected = index == currentLyricSourceIndex,
                                onClick = { onLyricSourceSelected(index) },
                            )
                        }
                    }
                }
            }

            EditorSection(title = stringResource(R.string.lyrics_timing)) {
                StepperRow(
                    value = formatOffset(offsetMs),
                    onDecrease = { onOffsetChange(offsetMs - 500L) },
                    onReset = { onOffsetChange(0L) },
                    onIncrease = { onOffsetChange(offsetMs + 500L) },
                    decreaseDescription = stringResource(R.string.advance_half_second),
                    increaseDescription = stringResource(R.string.delay_half_second),
                )
            }
        }
    }
}

@Composable
private fun ExpandableEditorSection(
    title: String,
    value: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(180), label = "sectionArrow")
    Column(
        modifier = Modifier.animateContentSize(tween(220)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.75f, fill = false),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            content()
        }
    }
}

@Composable
private fun StepperRow(
    value: String,
    onDecrease: () -> Unit,
    onReset: () -> Unit,
    onIncrease: () -> Unit,
    decreaseDescription: String? = null,
    increaseDescription: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LyricsCircleButton(onClick = onDecrease, size = 40.dp, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(Icons.Rounded.Remove, decreaseDescription)
        }
        LyricsPill(onClick = onReset, containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        LyricsCircleButton(onClick = onIncrease, size = 40.dp, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(Icons.Rounded.Add, increaseDescription)
        }
    }
}

@Composable
private fun DropdownChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_font),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DropdownDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun AlignmentOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(containerColor)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

private fun formatOffset(offsetMs: Long): String {
    val seconds = offsetMs / 1000f
    return when {
        offsetMs == 0L -> "0.0s"
        offsetMs > 0 -> String.format(Locale.CHINESE, "+%.1fs", seconds)
        else -> String.format(Locale.CHINESE, "%.1fs", seconds)
    }
}

private const val SECTION_TEXT_SIZE = "text_size"
private const val SECTION_FONT = "font"
private const val SECTION_LYRICS_SOURCE = "lyrics_source"
private const val MIN_TEXT_SCALE = 0.8f
private const val MAX_TEXT_SCALE = 1.3f
private const val TEXT_SCALE_STEP = 0.05f
