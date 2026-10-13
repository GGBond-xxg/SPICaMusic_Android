package me.spica27.spicamusic.cloud

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.spicamusic.R
import org.koin.compose.koinInject

@Composable
internal fun KugouRewardsCard(
    account: RemoteMusicAccount,
    rewards: KugouRewards = koinInject(),
) {
    if (account.provider != RemoteMusicProvider.KUGOU || account.secret.isBlank()) return
    val states by rewards.states.collectAsStateWithLifecycle()
    val state = states[account.userId] ?: KugouRewardState()
    val context = LocalContext.current
    val message = localizedRewardMessage(state.message)
    val checkinDescription = stringResource(R.string.kugou_auto_checkin)
    var showAdInfo by remember(account.userId) { mutableStateOf(false) }
    var openError by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.kugou_rewards_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.kugou_auto_checkin))
                    Text(
                        stringResource(R.string.kugou_checkin_timezone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.autoEnabled,
                    enabled = !state.busy,
                    onCheckedChange = { rewards.setAutomatic(account.userId, it) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = checkinDescription
                        },
                )
            }
            Text(
                if (state.signedToday) stringResource(R.string.kugou_checkin_done) else message,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.signedToday && state.message != stringResource(R.string.kugou_checkin_success)) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { rewards.retry(account.userId) },
                    enabled = !state.busy && !state.signedToday,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.signedToday) {
                            stringResource(R.string.kugou_checked_today)
                        } else if (state.busy) {
                            stringResource(R.string.kugou_rewards_busy)
                        } else {
                            stringResource(R.string.kugou_checkin_manual)
                        },
                    )
                }
                OutlinedButton(
                    onClick = { rewards.claimListening(account.userId) },
                    enabled = !state.busy && !state.listenedToday && state.canClaimListening,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.listenedToday) {
                            stringResource(
                                R.string.kugou_listening_done,
                            )
                        } else {
                            stringResource(R.string.kugou_listening_claim)
                        },
                    )
                }
            }
            if (!state.listenedToday && !state.canClaimListening) {
                Text(
                    stringResource(R.string.kugou_listening_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { showAdInfo = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.kugou_ads_open))
            }
            Text(
                stringResource(R.string.kugou_schedule_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (openError) Text(stringResource(R.string.kugou_open_error), color = MaterialTheme.colorScheme.error)
        }
    }
    if (showAdInfo) {
        AlertDialog(
            onDismissRequest = { showAdInfo = false },
            title = { Text(stringResource(R.string.kugou_ads_title)) },
            text = { Text(stringResource(R.string.kugou_ads_help, account.userId)) },
            confirmButton = {
                TextButton(onClick = {
                    showAdInfo = false
                    openError =
                        runCatching {
                            val launch =
                                context.packageManager.getLaunchIntentForPackage("com.kugou.android.lite")
                                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kugouyouth.com/"))
                            context.startActivity(launch)
                        }.isFailure
                }) { Text(stringResource(R.string.kugou_open_concept)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdInfo = false }) { Text(stringResource(R.string.cloud_action_cancel)) }
            },
        )
    }
}

/** Reward status is cached across activity/locale changes and old versions stored Chinese text. */
@Composable
private fun localizedRewardMessage(message: String): String {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val resources =
        listOf(
            R.string.kugou_auto_default,
            R.string.kugou_checkin_success,
            R.string.kugou_checkin_busy,
            R.string.kugou_listening_required,
            R.string.kugou_listening_busy,
            R.string.kugou_listening_claimed_today,
            R.string.kugou_checkin_attempted,
            R.string.kugou_checkin_pending,
            R.string.cloud_retry_later,
            R.string.cloud_network_error,
        )
    val translations =
        remember {
            listOf("en", "zh-CN", "zh-TW")
                .flatMap { language ->
                    val localized =
                        context.createConfigurationContext(
                            android.content.res.Configuration(configuration).apply {
                                setLocale(java.util.Locale.forLanguageTag(language))
                            },
                        )
                    resources.map { localized.getString(it) to it }
                }.toMap()
        }
    return translations[message]?.let { stringResource(it) } ?: message
}
