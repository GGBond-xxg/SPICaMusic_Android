package me.spica27.spicamusic.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KugouSmsTest {
    @Test fun rsaUsesProtocolTrailingPadding() {
        assertEquals(
            "01778864A125157E414221798414E0A9D5D01485360EA935894E901D32D3468FCEC78757AE3655A3E68546DE6E7D52FB0AACBAB88A8244639B59B7479B26222C6B1D40B34E03C82F83E6507FF8262D079D39A35E3AC35A3A722DA22FDBADEBCC4301329AE3C3C8E7F34A5BDE059564F5C8DE8FE17E3A4DACD3F9C239DDB88322",
            kugouSmsRsa("{\"clienttime_ms\":1700000000000,\"key\":\"abcdefghijklmnop\"}"),
        )
    }

    @Test fun encryptedCredentialsAndLargeAccountIdsArePreserved() {
        val key = "abcdefghijklmnop"
        val derived = kugouMd5(key)
        val encrypted = aesHex("{\"token\":\"test-token\",\"userid\":12345678901}", derived, derived.takeLast(16))
        val result =
            parseKugouSmsResult(
                JSONObject().put("status", 1).put("data", JSONObject().put("nickname", "测试账号").put("secu_params", encrypted)),
                key,
            )
        assertEquals("test-token", result.account?.secret)
        assertEquals("12345678901", result.account?.userId)
        assertEquals("测试账号", result.account?.displayName)
    }

    @Test fun multiAccountResponseRequiresSelection() {
        for (field in listOf("user_list", "info_list")) {
            val result =
                parseKugouSmsResult(
                    JSONObject(
                        """{"error_code":34175,"data":{"$field":[{"userid":12345678901,"nickname":"甲"},{"user_id":222,"user_name":"乙"}]}}""",
                    ),
                    "",
                )
            assertNull(result.account)
            assertEquals(listOf("12345678901", "222"), result.candidates.map { it.userId })
        }
    }

    @Test fun sendingAndLoginUseHttpsSignedBodiesAndSharedSession() =
        runBlocking {
            val session = KugouSmsSession()
            var calls = 0
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        val request = chain.request()
                        calls++
                        assertEquals("https", request.url.scheme)
                        assertEquals("POST", request.method)
                        assertEquals(session.mid, request.header("mid"))
                        assertEquals(session.dfid, request.header("dfid"))
                        val payload = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                        val params =
                            request.url.queryParameterNames
                                .filter { it != "signature" }
                                .associateWith { request.url.queryParameter(it)!! }
                        assertEquals(signKugouRequest(params, payload), request.url.queryParameter("signature"))
                        val body = JSONObject(payload)
                        if (calls == 1) {
                            assertEquals("/v7/send_mobile_code", request.url.encodedPath)
                            assertEquals("13800138000", body.getString("mobile"))
                        } else {
                            assertEquals("/v7/login_by_verifycode", request.url.encodedPath)
                            assertEquals("12345678901", body.getString("userid"))
                            assertFalse(payload.contains("123456\""))
                            assertFalse(payload.contains("13800138000"))
                            assertEquals(256, body.getString("pk").length)
                            assertEquals("1", request.header("support-calm"))
                        }
                        Response
                            .Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(
                                """{"status":1,"data":{"userid":12345678901,"token":"test-token"}}""".toResponseBody(
                                    "application/json".toMediaType(),
                                ),
                            ).build()
                    }.build()
            val client = KugouClient(http)
            client.sendSms("13800138000", session)
            assertEquals("test-token", client.loginSms("13800138000", "123456", session, "12345678901").account?.secret)
            assertEquals(2, calls)
            assertTrue(runCatching { client.sendSms("123", session) }.isFailure)
            assertEquals(2, calls)
        }

    @Test fun businessErrorsNeverCreateAnAccount() =
        runBlocking {
            val http =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"status":0,"error_code":20028}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            val error = runCatching { KugouClient(http).loginSms("13800138000", "123456", KugouSmsSession()) }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("验证"))
        }
}
