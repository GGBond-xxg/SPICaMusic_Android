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
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_sms_send)).assertIsNotEnabled()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.cloud_login)).assertIsNotEnabled()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_sms_phone)).performTextInput("13800138000")
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_sms_send)).performClick()
        compose.waitUntil(5000) { calls.get() == 1 }
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_sms_sent)).assertExists()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_sms_send)).assertDoesNotExist()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_sms_code)).performTextInput("123456")
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.cloud_login)).performScrollTo().performClick()
        compose.waitUntil(5000) { calls.get() == 2 }
        compose.onNodeWithText("测试账号（12345678901）").performScrollTo().performClick()
        compose.waitUntil(5000) { account.get() != null }
        assertEquals("12345678901", account.get()?.userId)
        assertEquals(3, calls.get())
    }

    @Test fun countrySelectionChangesRequestAndClearsStaleCredentials() {
        val payload = AtomicReference<String?>()
        val client =
            KugouClient(
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        payload.set(okio.Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8())
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"status":1}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build(),
            )

        fun text(
            id: Int,
            vararg args: Any,
        ) = cloudText(id, *args)
        compose.setContent { MaterialTheme { Surface { KugouSmsLogin(Modifier, {}, null, client) } } }
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_sms_phone)).performTextInput("13800138000")
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_country_selected, "86")).performScrollTo().performClick()
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_country_custom)).performTextReplacement("65")
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_country_confirm)).performClick()
        compose.onNodeWithText("13800138000").assertDoesNotExist()
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_sms_send)).assertIsNotEnabled()
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_sms_phone)).performTextInput("81234567")
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_sms_send)).performScrollTo().performClick()
        compose.waitUntil(5000) { payload.get() != null }
        assertEquals("+6581234567", org.json.JSONObject(payload.get()!!).getString("mobile"))
        compose.waitUntil(5000) {
            compose.onAllNodesWithText(text(me.spica27.spicamusic.R.string.kugou_sms_sent)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_sms_code)).performTextInput("123456")
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_country_selected, "65")).performScrollTo().performClick()
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_country_custom)).performTextReplacement("852")
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_country_confirm)).performClick()
        compose.onNodeWithText("123456").assertDoesNotExist()
        compose.onNodeWithText("81234567").assertDoesNotExist()
        // Switching region must not reset the successful-send cooldown.
        compose.onNodeWithText(text(me.spica27.spicamusic.R.string.kugou_sms_send)).assertDoesNotExist()
    }
}
