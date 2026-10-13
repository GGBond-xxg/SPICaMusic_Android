package me.spica27.spicamusic.cloud

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KugouSmsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formGatesSendingAndSupportsAccountSelection() {
        val calls = AtomicInteger()
        val account = AtomicReference<RemoteMusicAccount?>()
        val client =
            KugouClient(
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val response =
                            when (calls.incrementAndGet()) {
                                1 -> """{"status":1}"""
                                2 -> """{"status":0,"error_code":34175,"data":{"user_list":[{"userid":12345678901,"nickname":"测试账号"}]}}"""
                                else -> """{"status":1,"data":{"userid":12345678901,"nickname":"测试账号","token":"ui-test-token"}}"""
                            }
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(response.toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build(),
            )
        compose.setContent { MaterialTheme { Surface { KugouSmsLogin(Modifier, account::set, null, client) } } }
        compose.onNodeWithText("获取验证码").assertIsNotEnabled()
        compose.onNodeWithText("登录").assertIsNotEnabled()
        compose.onNodeWithText("手机号（+86）").performTextInput("13800138000")
        compose.onNodeWithText("获取验证码").performClick()
        compose.waitUntil(5000) { calls.get() == 1 }
        compose.onNodeWithText("验证码已发送，请查看短信").assertExists()
        compose.onNode(hasText("秒后重新获取", substring = true)).assertIsNotEnabled()
        compose.onNodeWithText("短信验证码").performTextInput("123456")
        compose.onNodeWithText("登录").performScrollTo().performClick()
        compose.waitUntil(5000) { calls.get() == 2 }
        compose.onNodeWithText("测试账号（12345678901）").performScrollTo().performClick()
        compose.waitUntil(5000) { account.get() != null }
        assertEquals("12345678901", account.get()?.userId)
        assertEquals(3, calls.get())
    }
}
