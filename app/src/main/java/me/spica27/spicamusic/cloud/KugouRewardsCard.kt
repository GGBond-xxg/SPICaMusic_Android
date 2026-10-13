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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var showAdInfo by remember(account.userId) { mutableStateOf(false) }
    var openError by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("概念版会员与奖励", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("每日自动签到")
                    Text(
                        "按账号每天一次 · 北京时间",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.autoEnabled,
                    enabled = !state.busy,
                    onCheckedChange = { rewards.setAutomatic(account.userId, it) },
                    modifier = Modifier.semantics { contentDescription = "每日自动签到" },
                )
            }
            Text(
                if (state.signedToday) "今日签到已完成 · 概念版会员已领取" else state.message,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.signedToday && state.message != "签到成功，概念版会员已领取") {
                Text(state.message, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { rewards.retry(account.userId) },
                    enabled = !state.busy && !state.signedToday,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.signedToday) {
                            "今日已签到"
                        } else if (state.busy) {
                            "处理中…"
                        } else {
                            "手动签到"
                        },
                    )
                }
                OutlinedButton(
                    onClick = { rewards.claimListening(account.userId) },
                    enabled = !state.busy && !state.listenedToday && state.canClaimListening,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.listenedToday) "听歌已领取" else "听歌领取")
                }
            }
            if (!state.listenedToday && !state.canClaimListening) {
                Text(
                    "在本应用连续播放一首酷狗歌曲满 60 秒后，可手动领取听歌奖励。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { showAdInfo = true }, modifier = Modifier.fillMaxWidth()) {
                Text("广告奖励 · 打开概念版")
            }
            Text(
                "后台签到时间由系统安排；打开应用时也会检查。领取结果以酷狗返回为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (openError) Text("无法打开概念版，请安装官方应用后重试。", color = MaterialTheme.colorScheme.error)
        }
    }
    if (showAdInfo) {
        AlertDialog(
            onDismissRequest = { showAdInfo = false },
            title = { Text("在官方概念版领取广告奖励") },
            text = { Text("请在酷狗概念版登录同一个账号（${account.userId}），进入会员任务并完成广告观看。本应用不会自动观看广告或代报广告完成。") },
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
                }) { Text("打开概念版") }
            },
            dismissButton = { TextButton(onClick = { showAdInfo = false }) { Text("取消") } },
        )
    }
}
