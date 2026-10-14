package me.spica27.spicamusic.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    modifier: Modifier = Modifier,
) {
    val initial = initialMinutes.toString()
    var input by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val minutes = parseSleepTimerMinutes(input.text)
    val confirm = {
        minutes?.let {
            keyboard?.hide()
            onConfirm(it)
        }
        Unit
    }
    val inputField: @Composable (Modifier, Boolean) -> Unit = { fieldModifier, compact ->
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.text.length <= 4 && it.text.all(Char::isDigit)) input = it },
            modifier = fieldModifier.focusRequester(focus),
            label = { Text(stringResource(R.string.timer_input_minutes)) },
            supportingText = if (compact) null else ({ Text(stringResource(R.string.timer_input_range)) }),
            isError = minutes == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
        )
    }
    val cancelButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        GlassTextButton(onClick = {
            keyboard?.hide()
            onDismiss()
        }, modifier = buttonModifier) {
            Text(stringResource(R.string.cancel))
        }
    }
    val confirmButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        GlassTextButton(onClick = confirm, modifier = buttonModifier, enabled = minutes != null) {
            Text(stringResource(R.string.confirm))
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && maxWidth >= 500.dp) {
            // Keep this branch stable while IME insets animate, preserving the focused field.
            // A landscape IME can leave less than 100dp. Put the field beside the actions
            // instead of reserving all of that height for buttons and hiding the input.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                inputField(Modifier.weight(1f), true)
                me.spica27.spicamusic.ui.theme.EqualHeightActionRow(Modifier.weight(1f)) {
                    cancelButton(Modifier.weight(1f).fillMaxHeight())
                    confirmButton(Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The form scrolls independently while actions remain above the IME.
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.timer_input_title), style = MaterialTheme.typography.titleMedium)
                    inputField(Modifier.fillMaxWidth(), false)
                }
                GlassDialogActions {
                    cancelButton(Modifier)
                    confirmButton(Modifier)
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
