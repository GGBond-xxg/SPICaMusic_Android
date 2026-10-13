package me.spica27.spicamusic.cloud

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.R
import org.json.JSONObject
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class KugouSmsCandidate(
    val userId: String,
    val nickname: String,
)

internal data class KugouSmsResult(
    val account: RemoteMusicAccount? = null,
    val candidates: List<KugouSmsCandidate> = emptyList(),
)

/** Ephemeral login identity, shared by send-code and login; no device identifiers are collected. */
internal class KugouSmsSession {
    val guid = randomKey(32)
    val mid = kugouMd5(guid)
    val dfid = randomKey(24)
    val params get() = mapOf("mid" to mid, "uuid" to guid, "dfid" to dfid)
}

internal suspend fun KugouClient.sendSms(
    mobile: String,
    session: KugouSmsSession,
    countryCode: String = "86",
) = withContext(Dispatchers.IO) {
    val phone = kugouSmsPhone(mobile, countryCode)
    request(
        "https://login-user.kugou.com/v7/send_mobile_code",
        params = session.params,
        body = JSONObject().put("businessid", 5).put("mobile", phone).put("plat", 3),
        strict = true,
    )
    Unit
}

internal suspend fun KugouClient.loginSms(
    mobile: String,
    code: String,
    session: KugouSmsSession,
    userId: String? = null,
    countryCode: String = "86",
): KugouSmsResult =
    withContext(Dispatchers.IO) {
        val phone = kugouSmsPhone(mobile, countryCode)
        require(code.matches(Regex("[0-9]{4,8}"))) { cloudText(R.string.kugou_sms_invalid) }
        val time = System.currentTimeMillis()
        val key = randomKey(16)
        val derived = kugouMd5(key)
        val body =
            JSONObject()
                .put("plat", 1)
                .put("support_multi", 1)
                .put("t1", aesHex("|$time", "5e4ef500e9597fe004bd09a46d8add98", "04bd09a46d8add98"))
                .put(
                    "t2",
                    aesHex(
                        "${session.guid}|0f607264fc6318a92b9e13c65db7cd3c||${session.guid}|$time",
                        "fd14b35e3f81af3817a20ae7adae7020",
                        "17a20ae7adae7020",
                    ),
                ).put("clienttime_ms", time)
                .put("mobile", phone.take(2) + "*****" + phone.last())
                .put("key", kugouMd5("3116LnT6xpN3khm36zse0QzvmgTZ3waWdRSA11440$time"))
                .put("pk", kugouSmsRsa(JSONObject().put("clienttime_ms", time).put("key", key).toString()))
                .put("params", aesHex(JSONObject().put("mobile", phone).put("code", code).toString(), derived, derived.takeLast(16)))
                .put("dfid", session.dfid)
                .put("dev", session.guid)
                .put("gitversion", "5f0b7c4")
        userId?.let { body.put("userid", it) }
        val response =
            request(
                "https://loginserviceretry.kugou.com/v7/login_by_verifycode",
                params = session.params,
                body = body,
                strict = true,
                acceptedCodes = setOf(34175),
                headers = mapOf("support-calm" to "1"),
                userAgent = "Android16-1070-11440-130-0-LOGIN-wifi",
            )
        parseKugouSmsResult(response, key)
    }

internal fun parseKugouSmsResult(
    response: JSONObject,
    key: String,
): KugouSmsResult {
    val data = response.optJSONObject("data") ?: error("登录结果为空，请重新获取验证码")
    if (response.optInt("error_code") == 34175) {
        val rows = data.optJSONArray("user_list") ?: data.optJSONArray("info_list")
        val candidates =
            (0 until (rows?.length() ?: 0))
                .mapNotNull {
                    val row = rows?.optJSONObject(it) ?: return@mapNotNull null
                    val id = row.optString("userid", row.optString("userId", row.optString("user_id", row.optString("id"))))
                    if (id.toLongOrNull()?.let { value -> value > 0 } != true) return@mapNotNull null
                    KugouSmsCandidate(id, row.optString("nickname", row.optString("user_name", row.optString("name"))).ifBlank { "酷狗 $id" })
                }.distinctBy { it.userId }
        check(candidates.isNotEmpty()) { "无法读取关联账号，请使用扫码登录" }
        return KugouSmsResult(candidates = candidates)
    }
    check(response.optInt("status") == 1) { "验证码登录失败，请重新获取验证码" }
    data.optString("secu_params").takeIf { it.isNotBlank() }?.let { encrypted ->
        val derived = kugouMd5(key)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived.toByteArray(), "AES"), IvParameterSpec(derived.takeLast(16).toByteArray()))
        val decrypted = cipher.doFinal(decodeHex(encrypted)).toString(Charsets.UTF_8)
        if (decrypted.trimStart().startsWith("{")) {
            val values = JSONObject(decrypted)
            values.keys().forEach { data.put(it, values.get(it)) }
        } else {
            data.put("token", decrypted)
        }
    }
    return KugouSmsResult(account = kugouAccount(data))
}

private fun randomKey(length: Int): String {
    val random = SecureRandom()
    return buildString { repeat(length) { append(('a'.code + random.nextInt(26)).toChar()) } }
}

internal fun aesHex(
    value: String,
    key: String,
    iv: String,
): String {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray()))
    return cipher.doFinal(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

private fun decodeHex(value: String): ByteArray {
    require(value.length % 2 == 0 && value.all { it.digitToIntOrNull(16) != null }) { "登录凭据格式异常" }
    return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

internal fun kugouSmsRsa(value: String): String {
    val encoded = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB"
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(encoded, Base64.DEFAULT)))
    val bytes = value.toByteArray()
    require(bytes.size <= 128)
    // The protocol pads the JSON with trailing zero bytes, unlike ordinary RSA padding.
    val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    return cipher.doFinal(bytes.copyOf(128)).joinToString("") { "%02X".format(it) }
}

/** Domestic requests retain the existing wire format. Other regions carry an explicit E.164 prefix
 * in BOTH requests; never silently interpret an overseas number as a mainland number.
 * The undocumented service may reject overseas SMS; QR login remains available.
 */
internal fun kugouSmsPhone(
    mobile: String,
    countryCode: String,
): String {
    require(isKugouSmsPhoneValid(mobile, countryCode)) { cloudText(R.string.kugou_sms_invalid_phone) }
    return if (countryCode == "86") mobile else "+$countryCode$mobile"
}

internal fun isKugouSmsPhoneValid(
    mobile: String,
    countryCode: String,
): Boolean =
    countryCode.matches(Regex("[1-9][0-9]{0,2}")) &&
        mobile.matches(Regex("[0-9]{4,14}")) &&
        countryCode.length + mobile.length <= 15 &&
        (countryCode != "86" || mobile.matches(Regex("1[3-9][0-9]{9}")))
