package me.spica27.spicamusic.cloud

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun KugouSmsLogin(
    modifier: Modifier,
    onLogin: (RemoteMusicAccount) -> Unit,
    onCancel: (() -> Unit)?,
    client: KugouClient = koinInject(),
) {
    val session = remember { KugouSmsSession() }
    val scope = rememberCoroutineScope()
    var mobile by remember { mutableStateOf("") }
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
        message = "正在登录…"
        scope.launch {
            try {
                val result = client.loginSms(mobile, code, session, userId)
                candidates = result.candidates
                result.account?.let {
                    code = ""
                    currentOnLogin(it)
                }
                message = if (candidates.isNotEmpty()) "请选择要登录的账号" else "登录成功"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: "登录失败，请重试"
            } finally {
                busy = false
            }
        }
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("验证码登录酷狗", style = MaterialTheme.typography.headlineSmall)
        Text("使用酷狗音乐或概念版绑定的中国大陆手机号。", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            mobile,
            {
                mobile = it.filter(Char::isDigit).take(11)
                candidates = emptyList()
                code = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            singleLine = true,
            label = { Text("手机号（+86）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        OutlinedButton(enabled = !busy && seconds == 0 && mobile.matches(Regex("1[3-9][0-9]{9}")), onClick = {
            busy = true
            scope.launch {
                try {
                    client.sendSms(mobile, session)
                    deadline = SystemClock.elapsedRealtime() + 60_000
                    seconds = 60
                    candidates = emptyList()
                    message = "验证码已发送，请查看短信"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    message = error.message ?: "发送失败，请稍后重试"
                } finally {
                    busy = false
                }
            }
        }) { Text(if (seconds > 0) "$seconds 秒后重新获取" else "获取验证码") }
        OutlinedTextField(
            code,
            {
                code = it.filter(Char::isDigit).take(8)
                candidates = emptyList()
            },
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode },
            enabled = !busy,
            singleLine = true,
            label = { Text("短信验证码") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Button(onClick = { login() }, enabled = !busy && mobile.length == 11 && code.length in 4..8) { Text(if (busy) "请稍候…" else "登录") }
        candidates.forEach { candidate ->
            OutlinedButton(enabled = !busy, onClick = { login(candidate.userId) }, modifier = Modifier.fillMaxWidth()) {
                Text("${candidate.nickname}（${candidate.userId}）")
            }
        }
        if (message.isNotBlank()) Text(message)
        Text("登录凭据加密保存在本机。若酷狗要求安全验证，请在官方客户端完成验证，或切换扫码登录。", style = MaterialTheme.typography.bodySmall)
        onCancel?.let { TextButton(onClick = it, enabled = !busy) { Text("返回账号") } }
    }
}
