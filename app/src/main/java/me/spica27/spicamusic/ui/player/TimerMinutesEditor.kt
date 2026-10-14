package me.spica27.spicamusic.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.R
import me.spica27.spicamusic.ui.theme.GlassDialogActions
import me.spica27.spicamusic.ui.theme.GlassTextButton

internal fun parseSleepTimerMinutes(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..240 }

@Composable
internal fun TimerMinutesEditor(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialMinutes.toString()
    var input by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val minutes = parseSleepTimerMinutes(input.text)
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.timer_input_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.text.length <= 4 && it.text.all(Char::isDigit)) input = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(stringResource(R.string.timer_input_minutes)) },
            supportingText = { Text(stringResource(R.string.timer_input_range)) },
            isError = minutes == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { minutes?.let(onConfirm) }),
        )
        GlassDialogActions {
            GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            GlassTextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null) { Text(stringResource(R.string.confirm)) }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(imeBottom) { if (imeBottom > 0) bringIntoView.bringIntoView() }
}
