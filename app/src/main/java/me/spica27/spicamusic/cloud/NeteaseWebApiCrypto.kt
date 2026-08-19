package me.spica27.spicamusic.cloud

import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Minimal implementation of the public NetEase Web API request envelope. */
internal object NeteaseWebApiCrypto {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val PUBLIC_KEY_BASE64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val EAPI_DELIMITER = "-36cd479b6b5-"

    private val random = SecureRandom()
    private val publicKey by lazy {
        KeyFactory
            .getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_BASE64)))
    }

    fun encrypt(payload: JSONObject): Map<String, String> {
        val sessionKey =
            buildString(16) {
                repeat(16) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
            }
        val firstPass = aes(payload.toString(), PRESET_KEY)
        val params = aes(firstPass, sessionKey)
        val rsa =
            Cipher
                .getInstance("RSA/ECB/NoPadding")
                .apply { init(Cipher.ENCRYPT_MODE, publicKey) }
                .doFinal(sessionKey.reversed().toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return mapOf("params" to params, "encSecKey" to rsa)
    }

    /** NetEase desktop-client envelope used by the authenticated player-url/v1 endpoint. */
    fun encryptEapi(
        endpointPath: String,
        payload: JSONObject,
    ): String {
        val apiPath = endpointPath.replaceFirst("/eapi", "/api")
        val data = payload.toString()
        val digest =
            MessageDigest
                .getInstance("MD5")
                .digest("nobody${apiPath}use${data}md5forencrypt".toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val message = "$apiPath$EAPI_DELIMITER$data$EAPI_DELIMITER$digest"
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(EAPI_KEY.toByteArray(Charsets.UTF_8), "AES"),
        )
        return cipher
            .doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            .uppercase(Locale.ROOT)
    }

    private fun aes(
        value: String,
        key: String,
    ): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }
}
