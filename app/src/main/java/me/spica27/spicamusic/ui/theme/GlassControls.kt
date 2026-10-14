@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.spica27.spicamusic.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
) {
    val glass = LocalGlassEnabled.current
    AlertDialog(
        onDismissRequest,
        { GlassDialogActions(content = confirmButton) },
        modifier =
            if (glass) {
                modifier
                    .background(
                        containerColor,
                        shape,
                    ).glassSurface(shape, containerColor, tint = containerColor, opacity = 0.82f, frosted = true)
            } else {
                modifier
            },
        dismissButton = dismissButton?.let { { GlassDialogActions(content = it) } },
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (glass) Color.Transparent else containerColor,
        tonalElevation = if (glass) 0.dp else tonalElevation,
    )
}

/** Wrap long/localized action labels rather than squeezing adjacent buttons together. */
@Composable
fun GlassDialogActions(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset.Zero,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 3.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    DropdownMenu(
        expanded,
        onDismissRequest,
        modifier =
            if (glass) {
                modifier
                    .background(
                        containerColor,
                        shape,
                    ).glassSurface(shape, containerColor, tint = containerColor, opacity = 0.82f)
            } else {
                modifier
            },
        shape = shape,
        containerColor = if (glass) Color.Transparent else containerColor,
        offset = offset,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        shadowElevation = shadowElevation,
        content = content,
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    Card(
        modifier =
            if (glass) {
                modifier.glassSurface(
                    shape,
                    colors.containerColor,
                    tint = colors.containerColor,
                    frosted = true,
                )
            } else {
                modifier
            },
        shape = shape,
        colors = if (glass) colors.copy(containerColor = Color.Transparent) else colors,
        content = content,
    )
}

@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glass = LocalGlassEnabled.current
    FilterChip(
        selected,
        onClick,
        {
            ProvideTextStyle(LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)) {
                Box(Modifier.padding(vertical = 4.dp)) { label() }
            }
        },
        modifier =
            if (glass) {
                modifier.padding(4.dp).glassSurface(
                    CircleShape,
                    MaterialTheme.colorScheme.surfaceContainer,
                    tint = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                )
            } else {
                modifier.padding(4.dp)
            },
        enabled = enabled,
        shape = if (glass) CircleShape else FilterChipDefaults.shape,
        border = if (glass) null else FilterChipDefaults.filterChipBorder(enabled, selected),
        colors =
            if (glass) {
                FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    selectedContainerColor = Color.Transparent,
                )
            } else {
                FilterChipDefaults.filterChipColors()
            },
    )
}

@Composable
fun GlassAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glass = LocalGlassEnabled.current
    AssistChip(
        onClick,
        {
            ProvideTextStyle(LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)) {
                Box(Modifier.padding(vertical = 4.dp)) { label() }
            }
        },
        modifier =
            if (glass) {
                modifier
                    .padding(
                        4.dp,
                    ).glassSurface(CircleShape, MaterialTheme.colorScheme.surfaceContainer)
            } else {
                modifier.padding(4.dp)
            },
        enabled = enabled,
        shape = if (glass) CircleShape else AssistChipDefaults.shape,
        border = if (glass) null else AssistChipDefaults.assistChipBorder(enabled),
        colors =
            if (glass) {
                AssistChipDefaults.assistChipColors(
                    containerColor = Color.Transparent,
                )
            } else {
                AssistChipDefaults.assistChipColors()
            },
    )
}

@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetGesturesEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = contentColorFor(containerColor),
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    ModalBottomSheet(
        onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        containerColor = if (glass) Color.Transparent else containerColor,
        contentColor = contentColor,
        dragHandle = if (glass) null else dragHandle,
    ) {
        if (glass) {
            GlassWindowBackdrop {
                // Draw inside the sheet's translated surface so its lens and clip move with it.
                Column(
                    // A separate dialog window may not replay the underlying page's graphics layer.
                    // Keep an opaque base so missing/transparent samples cannot expose page text.
                    Modifier.fillMaxWidth().background(containerColor, BottomSheetDefaults.ExpandedShape).glassSurface(
                        BottomSheetDefaults.ExpandedShape,
                        containerColor,
                        tint = containerColor,
                        opacity = 0.78f,
                        frosted = true,
                    ),
                ) {
                    if (dragHandle != null) {
                        Box(Modifier.align(Alignment.CenterHorizontally)) { dragHandle() }
                    }
                    content()
                }
            }
        } else {
            content()
        }
    }
}

/** Retain Material input/keyboard/accessibility behavior and replace only the visual surface. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlassEnabled.current
    if (!glass) {
        IconButton(onClick, modifier, enabled, colors, interactionSource, shape = shape, content = content)
        return
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .padding(4.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                .glassSurface(shape, MaterialTheme.colorScheme.surfaceContainer),
        )
        IconButton(
            onClick,
            Modifier.size(48.dp),
            enabled,
            colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            interactionSource,
            shape = shape,
            content = content,
        )
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    val tint = if (colors.containerColor.alpha > 0f) colors.containerColor else MaterialTheme.colorScheme.surface
    Button(
        onClick = onClick,
        modifier =
            if (glass) {
                modifier.padding(4.dp).glassSurface(
                    shape,
                    tint,
                    tint = tint,
                    opacity =
                        if (colors.containerColor.alpha >
                            0f
                        ) {
                            0.82f
                        } else {
                            0.5f
                        },
                )
            } else {
                modifier.padding(4.dp)
            },
        enabled = enabled,
        shape = shape,
        colors = if (glass) colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = {
            ProvideTextStyle(
                LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            ) { content() }
        },
    )
}

@Composable
fun GlassElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.elevatedButtonColors(),
        elevation = ButtonDefaults.elevatedButtonElevation(),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun GlassFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    val tint = if (colors.containerColor.alpha > 0f) colors.containerColor else MaterialTheme.colorScheme.surface
    FilledTonalButton(
        onClick = onClick,
        modifier =
            if (glass) {
                modifier.padding(4.dp).glassSurface(
                    shape,
                    tint,
                    tint = tint,
                    opacity =
                        if (colors.containerColor.alpha >
                            0f
                        ) {
                            0.82f
                        } else {
                            0.5f
                        },
                )
            } else {
                modifier.padding(4.dp)
            },
        enabled = enabled,
        shape = shape,
        colors = if (glass) colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = {
            ProvideTextStyle(
                LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            ) { content() }
        },
    )
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    val tint = if (colors.containerColor.alpha > 0f) colors.containerColor else MaterialTheme.colorScheme.surface
    OutlinedButton(
        onClick = onClick,
        modifier =
            if (glass) {
                modifier.padding(4.dp).glassSurface(
                    shape,
                    tint,
                    tint = tint,
                    opacity =
                        if (colors.containerColor.alpha >
                            0f
                        ) {
                            0.82f
                        } else {
                            0.5f
                        },
                )
            } else {
                modifier.padding(4.dp)
            },
        enabled = enabled,
        shape = shape,
        colors = if (glass) colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else colors,
        elevation = if (glass) null else elevation,
        border = if (glass) null else border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = {
            ProvideTextStyle(
                LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            ) { content() }
        },
    )
}

@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = LocalGlassEnabled.current
    val tint = if (colors.containerColor.alpha > 0f) colors.containerColor else MaterialTheme.colorScheme.surface
    TextButton(
        onClick = onClick,
        modifier =
            if (glass) {
                modifier.padding(4.dp).glassSurface(
                    shape,
                    tint,
                    tint = tint,
                    opacity =
                        if (colors.containerColor.alpha >
                            0f
                        ) {
                            0.82f
                        } else {
                            0.5f
                        },
                )
            } else {
                modifier.padding(4.dp)
            },
        enabled = enabled,
        shape = shape,
        colors = if (glass) colors.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else colors,
        border = if (glass) null else border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = {
            ProvideTextStyle(
                LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            ) { content() }
        },
    )
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!LocalGlassEnabled.current) {
        Switch(checked, onCheckedChange, modifier, enabled = enabled, colors = colors, interactionSource = interactionSource)
        return
    }
    Box(modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }, contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(52.dp, 32.dp).glassSurface(
                CircleShape,
                MaterialTheme.colorScheme.surfaceContainer,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                opacity = if (checked) 0.65f else 0.5f,
            ),
        )
        Switch(
            checked,
            onCheckedChange,
            enabled = enabled,
            interactionSource = interactionSource,
            colors =
                colors.copy(
                    checkedTrackColor = Color.Transparent,
                    uncheckedTrackColor = Color.Transparent,
                    disabledCheckedTrackColor = Color.Transparent,
                    disabledUncheckedTrackColor = Color.Transparent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedBorderColor = Color.Transparent,
                    checkedThumbColor = Color.Transparent,
                    uncheckedThumbColor = Color.Transparent,
                    disabledCheckedThumbColor = Color.Transparent,
                    disabledUncheckedThumbColor = Color.Transparent,
                ),
            thumbContent = {
                Box(
                    Modifier.size(24.dp).glassSurface(
                        CircleShape,
                        MaterialTheme.colorScheme.surface,
                        tint = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        opacity = 0.85f,
                    ),
                )
            },
        )
    }
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: (@Composable (SliderState) -> Unit)? = null,
    track: (@Composable (SliderState) -> Unit)? = null,
) {
    val glass = LocalGlassEnabled.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors = colors,
        thumb = { state ->
            if (thumb != null) {
                thumb(state)
            } else if (glass) {
                Box(
                    Modifier
                        .size(28.dp)
                        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                        .glassSurface(
                            CircleShape,
                            MaterialTheme.colorScheme.primaryContainer,
                            tint = MaterialTheme.colorScheme.primaryContainer,
                            opacity = 0.78f,
                        ),
                )
            } else {
                SliderDefaults.Thumb(interactionSource, colors = colors, enabled = enabled)
            }
        },
        track = { state ->
            if (track != null) {
                track(state)
            } else {
                SliderDefaults.Track(
                    state,
                    thumbTrackGapSize = if (glass) 0.dp else 6.dp,
                    drawStopIndicator = null,
                    modifier = if (glass) Modifier.glassSurface(CircleShape, MaterialTheme.colorScheme.surfaceContainer) else Modifier,
                    enabled = enabled,
                    colors =
                        if (glass) {
                            colors.copy(
                                inactiveTrackColor = Color.Transparent,
                                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            )
                        } else {
                            colors
                        },
                )
            }
        },
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlassEnabled.current
    Surface(
        modifier = if (glass) modifier.glassSurface(shape, color, tint = color, opacity = 0.7f, frosted = true) else modifier,
        shape = shape,
        color = if (glass) Color.Transparent else color,
        contentColor = contentColor,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        shadowElevation = if (glass) 0.dp else shadowElevation,
        border = border,
        content = content,
    )
}

@Composable
fun GlassSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val glass = LocalGlassEnabled.current
    Surface(
        onClick,
        modifier = if (glass) modifier.glassSurface(shape, color, tint = color, opacity = 0.7f, frosted = true) else modifier,
        enabled = enabled,
        shape = shape,
        color = if (glass) Color.Transparent else color,
        contentColor = contentColor,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        shadowElevation = if (glass) 0.dp else shadowElevation,
        border = border,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = if (LocalGlassEnabled.current) modifier.appPageBackground() else modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = if (LocalGlassEnabled.current) Color.Transparent else containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val glass = LocalGlassEnabled.current
    TopAppBar(
        title,
        modifier = if (glass) modifier.glassSurface(RectangleShape, MaterialTheme.colorScheme.surface) else modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = if (glass) colors.copy(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent) else colors,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun GlassOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val glass = LocalGlassEnabled.current
    OutlinedTextField(
        value,
        onValueChange,
        modifier = if (glass) modifier.glassSurface(shape, MaterialTheme.colorScheme.surface) else modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = shape,
        colors =
            if (glass) {
                colors.copy(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                )
            } else {
                colors
            },
    )
}
