package me.spica27.spicamusic.ui.player

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import me.spica27.spicamusic.R
import me.spica27.spicamusic.ui.theme.GlassAlertDialog
import me.spica27.spicamusic.ui.theme.GlassTextButton

internal fun parseSleepTimerMinutes(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..240 }

@Composable
internal fun TimerMinutesDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialMinutes.toString()
    var input by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val minutes = parseSleepTimerMinutes(input.text)
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_input_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.text.length <= 4 && it.text.all(Char::isDigit)) input = it },
                modifier = Modifier.focusRequester(focus),
                label = { Text(stringResource(R.string.timer_input_minutes)) },
                supportingText = { Text(stringResource(R.string.timer_input_range)) },
                isError = minutes == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { minutes?.let(onConfirm) }),
            )
        },
        confirmButton = {
            GlassTextButton(onClick = { minutes?.let(onConfirm) }, enabled = minutes != null) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { GlassTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
