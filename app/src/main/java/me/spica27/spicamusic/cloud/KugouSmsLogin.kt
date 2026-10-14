package me.spica27.spicamusic.cloud

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.spica27.spicamusic.R
import org.koin.compose.koinInject
import java.util.Locale

@Composable
internal fun KugouSmsLogin(
    modifier: Modifier,
    onLogin: (RemoteMusicAccount) -> Unit,
    onCancel: (() -> Unit)?,
    client: KugouClient = koinInject(),
) {
    val session = remember { KugouSmsSession() }
    val scope = rememberCoroutineScope()
    var countryCode by rememberSaveable { mutableStateOf("86") }
    var showCountryCode by remember { mutableStateOf(false) }
    var mobile by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var deadline by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableIntStateOf(0) }
    var candidates by remember { mutableStateOf(emptyList<KugouSmsCandidate>()) }
    val currentOnLogin by rememberUpdatedState(onLogin)
    LaunchedEffect(deadline) {
        do {
            seconds = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0).toInt()
            if (seconds > 0) delay(250)
        } while (seconds > 0)
    }

    fun login(userId: String? = null) {
        busy = true
        message = cloudText(R.string.kugou_login_busy)
        scope.launch {
            try {
                val result = client.loginSms(mobile, code, session, userId, countryCode)
                candidates = result.candidates
                result.account?.let {
                    code = ""
                    currentOnLogin(it)
                }
                message =
                    if (candidates.isNotEmpty()) {
                        cloudText(
                            R.string.kugou_login_choose,
                        )
                    } else {
                        cloudText(R.string.kugou_login_success)
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: cloudText(R.string.kugou_login_error)
            } finally {
                busy = false
            }
        }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.kugou_sms_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.kugou_sms_phone_help), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { showCountryCode = true }, enabled = !busy) {
            Text(stringResource(R.string.kugou_country_selected, countryCode))
        }
        if (countryCode !=
            "86"
        ) {
            Text(stringResource(R.string.kugou_sms_overseas_hint), style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                mobile,
                {
                    mobile = it.filter { char -> char in '0'..'9' }.take(15 - countryCode.length)
                    candidates = emptyList()
                    code = ""
                },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                singleLine = true,
                label = { Text(stringResource(R.string.kugou_sms_phone)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            OutlinedButton(
                modifier = Modifier.width(116.dp).heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                enabled =
                    !busy && seconds == 0 && isKugouSmsPhoneValid(mobile, countryCode),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            client.sendSms(mobile, session, countryCode)
                            deadline = SystemClock.elapsedRealtime() + 60_000
                            seconds = 60
                            candidates = emptyList()
                            message = cloudText(R.string.kugou_sms_sent)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            message = error.message ?: cloudText(R.string.kugou_sms_send_error)
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                Text(
                    if (seconds >
                        0
                    ) {
                        stringResource(R.string.kugou_sms_resend, seconds)
                    } else {
                        stringResource(R.string.kugou_sms_send)
                    },
                )
            }
        }
        OutlinedTextField(
            code,
            {
                code = it.filter(Char::isDigit).take(8)
                candidates = emptyList()
            },
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
            enabled = !busy,
            singleLine = true,
            label = { Text(stringResource(R.string.kugou_sms_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
            login()
        }, enabled = !busy && isKugouSmsPhoneValid(mobile, countryCode) && code.length in 4..8) {
            Text(
                if (busy) {
                    stringResource(
                        R.string.cloud_wait,
                    )
                } else {
                    stringResource(R.string.cloud_login)
                },
            )
        }
        candidates.forEach { candidate ->
            OutlinedButton(enabled = !busy, onClick = { login(candidate.userId) }, modifier = Modifier.fillMaxWidth()) {
                Text("${candidate.nickname}（${candidate.userId}）")
            }
        }
        if (message.isNotBlank()) Text(message)
        Text(stringResource(R.string.kugou_sms_privacy), style = MaterialTheme.typography.bodySmall)
        onCancel?.let {
            TextButton(
                onClick = it,
                enabled = !busy,
            ) { Text(stringResource(R.string.cloud_back_account)) }
        }
    }
    if (showCountryCode) {
        KugouCountryCodeDialog(countryCode, onDismiss = { showCountryCode = false }) { selected ->
            if (selected != countryCode) {
                countryCode = selected
                mobile = ""
                code = ""
                candidates = emptyList()
                message = ""
                // Keep the existing cooldown to avoid bypassing rate limits by changing regions.
            }
            showCountryCode = false
        }
    }
}

@Composable
private fun KugouCountryCodeDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var custom by remember { mutableStateOf(current) }
    val locale = LocalConfiguration.current.locales[0]
    val regions =
        listOf(
            "CN" to "86",
            "HK" to "852",
            "MO" to "853",
            "TW" to "886",
            "SG" to "65",
            "MY" to "60",
            "US" to "1",
            "CA" to "1",
            "GB" to "44",
            "JP" to "81",
            "KR" to "82",
            "AU" to "61",
            "ID" to "62",
            "TH" to "66",
            "VN" to "84",
            "IN" to "91",
            "DE" to "49",
            "FR" to "33",
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kugou_country_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter { char -> char in '0'..'9' }.take(3) },
                    label = { Text(stringResource(R.string.kugou_country_custom)) },
                    prefix = { Text("+") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(regions.size) { index ->
                        val (region, dial) = regions[index]
                        TextButton(onClick = { onSelect(dial) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${Locale.Builder().setRegion(region).build().getDisplayCountry(locale)} +$dial")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(custom) }, enabled = custom.matches(Regex("[1-9][0-9]{0,2}"))) {
                Text(stringResource(R.string.kugou_country_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cloud_action_cancel)) } },
    )
}
