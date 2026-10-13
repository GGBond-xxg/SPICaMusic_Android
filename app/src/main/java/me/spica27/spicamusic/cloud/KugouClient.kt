package me.spica27.spicamusic.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class KugouQrStatus(
    val status: Int,
    val account: RemoteMusicAccount? = null,
)

/** Native adapter: credentials remain in CloudAccountStore; streams are resolved at play time. */
class KugouClient(
    baseClient: OkHttpClient,
) {
    private val client = baseClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()

    private data class CachedPlaylists(
        val account: RemoteMusicAccount,
        val playlists: List<RemotePlaylist>,
        val expiresAt: Long,
    )

    private val playlistCache = ConcurrentHashMap<String, CachedPlaylists>()

    fun cachedPlaylists(account: RemoteMusicAccount): List<RemotePlaylist> =
        playlistCache[account.id]?.takeIf { it.account == account }?.playlists.orEmpty()

    fun clearCache(accountId: String) {
        playlistCache.remove(accountId)
    }

    internal suspend fun claimDailyVip(
        account: RemoteMusicAccount,
        day: String,
    ) = withContext(Dispatchers.IO) {
        requireKugouLogin(account)
        request(
            "https://gateway.kugou.com/youth/v1/recharge/receive_vip_listen_song",
            account,
            params = mapOf("source_id" to "90139", "receive_day" to day),
            body = JSONObject().put("receive_day", day),
            acceptedCodes = setOf(131001),
            strict = true,
        )
        Unit
    }

    internal suspend fun upgradeDailyVip(account: RemoteMusicAccount) =
        withContext(Dispatchers.IO) {
            requireKugouLogin(account)
            request(
                "https://gateway.kugou.com/youth/v1/listen_song/upgrade_vip_reward",
                account,
                params = mapOf("kugouid" to account.userId, "ad_type" to "1"),
                post = true,
                acceptedCodes = setOf(20030, 131001),
                strict = true,
            )
            Unit
        }

    internal suspend fun claimListeningReward(
        account: RemoteMusicAccount,
        audioId: String,
    ) = withContext(Dispatchers.IO) {
        requireKugouLogin(account)
        require(audioId.toLongOrNull()?.let { it > 0 } == true) { "请先播放一首酷狗歌曲" }
        request(
            "https://gateway.kugou.com/youth/v2/report/listen_song",
            account,
            params = mapOf("clientver" to "10566"),
            body = JSONObject().put("mixsongid", audioId.toLong()),
            acceptedCodes = setOf(130012),
            strict = true,
            userAgent = "Android13-1070-10566-201-0-ReportPlaySongToServerProtocol-wifi",
        )
        Unit
    }

    suspend fun createQr(): String =
        withContext(Dispatchers.IO) {
            val data =
                request(
                    "https://login-user.kugou.com/v2/qrcode",
                    params =
                        mapOf(
                            "appid" to "1001",
                            "type" to "1",
                            "plat" to "4",
                            "srcappid" to "2919",
                            "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=3116&",
                        ),
                    web = true,
                ).getJSONObject("data")
            data.optString("qrcode").also { check(it.isNotBlank()) { "无法生成登录二维码，请重试" } }
        }

    internal suspend fun checkQr(key: String): KugouQrStatus =
        withContext(Dispatchers.IO) {
            val data =
                request(
                    "https://login-user.kugou.com/v2/get_userinfo_qrcode",
                    params =
                        mapOf(
                            "appid" to "3116",
                            "plat" to "4",
                            "srcappid" to "2919",
                            "qrcode" to key,
                        ),
                    web = true,
                ).getJSONObject("data")
            val status = data.optInt("status", -1)
            KugouQrStatus(status, if (status == 4) kugouAccount(data) else null)
        }

    suspend fun listSongs(
        account: RemoteMusicAccount,
        query: String,
        offset: Int,
        limit: Int,
    ): RemoteSongPage =
        withContext(Dispatchers.IO) {
            require(limit > 0 && offset >= 0)
            if (query.isBlank()) {
                val liked =
                    listPlaylists(account).firstOrNull { it.isLikedSongs }
                        ?: return@withContext RemoteSongPage(emptyList(), null)
                val rows = playlistPage(account, liked.id, offset / limit + 1, limit)
                return@withContext RemoteSongPage(
                    parseKugouSongs(rows),
                    (offset + rows.length()).takeIf { rows.length() == limit },
                )
            }
            val data =
                request(
                    "https://gateway.kugou.com/song_search_v2",
                    account,
                    mapOf(
                        "keyword" to query,
                        "page" to (offset / limit + 1).toString(),
                        "pagesize" to limit.toString(),
                        "platform" to "WebFilter",
                        "iscorrection" to "1",
                        "albumhide" to "0",
                        "nocollect" to "0",
                    ),
                    router = "songsearch.kugou.com",
                ).getJSONObject("data")
            val rows = data.optJSONArray("lists") ?: JSONArray()
            RemoteSongPage(
                parseKugouSongs(rows),
                (offset + rows.length()).takeIf {
                    rows.length() == limit && it < data.optInt("total", Int.MAX_VALUE)
                },
            )
        }

    suspend fun listPlaylists(
        account: RemoteMusicAccount,
        forceRefresh: Boolean = false,
    ): List<RemotePlaylist> =
        withContext(Dispatchers.IO) {
            playlistCache[account.id]
                ?.takeIf { !forceRefresh && it.account == account && it.expiresAt > System.currentTimeMillis() }
                ?.let { return@withContext it.playlists }
            val result = mutableListOf<RemotePlaylist>()
            var page = 1
            var received = 0
            do {
                currentCoroutineContext().ensureActive()
                val data =
                    request(
                        "https://gateway.kugou.com/v7/get_all_list",
                        account,
                        mapOf("plat" to "1"),
                        body =
                            JSONObject()
                                .put("userid", account.userId)
                                .put("token", account.secret)
                                .put("total_ver", 979)
                                .put("type", 2)
                                .put("page", page)
                                .put("pagesize", 100),
                        router = "cloudlist.service.kugou.com",
                    ).getJSONObject("data")
                val rows = data.optJSONArray("info") ?: data.optJSONArray("list") ?: data.optJSONArray("lists") ?: JSONArray()
                for (i in 0 until rows.length()) {
                    val item = rows.optJSONObject(i) ?: continue
                    val id = item.optString("listid")
                    if (id.isBlank()) continue
                    result +=
                        RemotePlaylist(
                            id,
                            item.optString("name", "未命名歌单"),
                            kugouArtwork(item.optString("pic", item.optString("cover", item.optString("imgurl")))),
                            item.optInt("count"),
                            creatorName = item.optString("list_create_username"),
                            isOwned = item.optString("list_create_userid", account.userId) == account.userId,
                            isLikedSongs = item.optInt("is_def") == 2 || item.optString("name") == "我喜欢",
                        )
                }
                received = rows.length()
                page++
            } while (received == 100 && page <= 100)
            check(received < 100 || page <= 100) { "歌单数量过多，请稍后重试" }
            result.distinctBy { it.id }.also {
                playlistCache[account.id] =
                    CachedPlaylists(account, it, System.currentTimeMillis() + 300_000)
            }
        }

    private fun playlistPage(
        account: RemoteMusicAccount,
        playlistId: String,
        page: Int,
        limit: Int,
    ): JSONArray {
        val data =
            request(
                "https://gateway.kugou.com/v4/get_list_all_file",
                account,
                body =
                    JSONObject()
                        .put("listid", playlistId)
                        .put("userid", account.userId)
                        .put("token", account.secret)
                        .put("area_code", 1)
                        .put("show_relate_goods", 0)
                        .put("pagesize", limit)
                        .put("allplatform", 1)
                        .put("show_cover", 1)
                        .put("type", 0)
                        .put("page", page),
                router = "cloudlist.service.kugou.com",
            ).getJSONObject("data")
        return data.optJSONArray("info") ?: data.optJSONArray("songs") ?: data.optJSONArray("list") ?: JSONArray()
    }

    suspend fun listPlaylistSongs(
        account: RemoteMusicAccount,
        playlistId: String,
    ): List<RemoteSong> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<RemoteSong>()
            var page = 1
            var received = 0
            do {
                currentCoroutineContext().ensureActive()
                val rows = playlistPage(account, playlistId, page, 100)
                result += parseKugouSongs(rows)
                received = rows.length()
                page++
            } while (received == 100 && page <= 100)
            check(received < 100 || page <= 100) { "歌单歌曲过多，请稍后重试" }
            result.distinctBy { it.id }
        }

    suspend fun resolveStream(
        account: RemoteMusicAccount,
        songId: String,
    ): ResolvedRemoteStream =
        withContext(Dispatchers.IO) {
            val hash = songId.substringBefore('_').lowercase()
            require(hash.matches(Regex("[a-f0-9]{32}"))) { "酷狗歌曲标识无效" }
            val response =
                request(
                    "https://tracker.kugou.com/v5/url",
                    account,
                    mapOf(
                        "hash" to hash,
                        "album_audio_id" to songId.substringAfter('_', "0"),
                        "album_id" to "0",
                        "area_code" to "1",
                        "ssa_flag" to "is_fromtrack",
                        "version" to "11430",
                        "page_id" to "967177915",
                        "quality" to "128",
                        "behavior" to "play",
                        "pid" to "411",
                        "cmd" to "26",
                        "pidversion" to "3001",
                        "IsFreePart" to "0",
                        "cdnBackup" to "1",
                        "module" to "",
                        "clientver" to "11430",
                        "ppage_id" to "356753938,823673182,967485191",
                        "key" to kugouMd5(hash + "185672dd44712f60bb1736df5a377e82" + "3116-" + account.userId),
                    ),
                )
            parseKugouStream(response.optJSONObject("data") ?: response)
        }

    internal fun request(
        endpoint: String,
        account: RemoteMusicAccount? = null,
        params: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        web: Boolean = false,
        router: String? = null,
        post: Boolean = false,
        acceptedCodes: Set<Int> = emptySet(),
        strict: Boolean = false,
        userAgent: String = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi",
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val values =
            mutableMapOf(
                "dfid" to "-",
                "mid" to "-",
                "uuid" to "-",
                "appid" to "3116",
                "clientver" to "11440",
                "clienttime" to (System.currentTimeMillis() / 1000).toString(),
            )
        account?.let {
            values["userid"] = it.userId
            values["token"] = it.secret
        }
        values.putAll(params)
        val payload = body?.toString().orEmpty()
        values["signature"] = signKugouRequest(values, payload, web)
        val url =
            endpoint
                .toHttpUrl()
                .newBuilder()
                .apply {
                    values.forEach { (key, value) -> addQueryParameter(key, value) }
                }.build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("dfid", values.getValue("dfid"))
                .header("mid", values.getValue("mid"))
                .header("clienttime", values.getValue("clienttime"))
        router?.let { request.header("x-router", it) }
        headers.forEach { (name, value) -> request.header(name, value) }
        body?.let { request.post(payload.toRequestBody("application/json; charset=utf-8".toMediaType())) }
        if (post && body == null) request.post(ByteArray(0).toRequestBody(null))
        return client.newCall(request.build()).execute().use {
            check(it.isSuccessful) { "酷狗服务暂时不可用（HTTP ${it.code}）" }
            JSONObject(it.body.string()).also { json ->
                val errorCode = json.optInt("error_code", json.optInt("errcode", 0))
                check(errorCode in acceptedCodes || (json.optInt("status", if (strict) -1 else 1) == 1 && errorCode == 0)) {
                    if (errorCode == 20028) {
                        "酷狗要求验证，请在官方客户端完成验证后重试"
                    } else {
                        "酷狗请求失败（$errorCode），请重试或重新登录"
                    }
                }
            }
        }
    }
}

internal fun requireKugouLogin(account: RemoteMusicAccount) {
    require(
        account.provider == RemoteMusicProvider.KUGOU &&
            account.secret.isNotBlank() &&
            account.userId.toLongOrNull()?.let { it > 0 } == true,
    ) { "请先登录酷狗账号" }
}

internal fun kugouAccount(data: JSONObject): RemoteMusicAccount {
    val userId = data.optString("userid")
    val token = data.optString("token")
    check(userId.toLongOrNull()?.let { it > 0 } == true && token.isNotBlank() && token != "null") {
        "登录结果缺少账号凭据，请重新登录"
    }
    return RemoteMusicAccount(
        "",
        RemoteMusicProvider.KUGOU,
        data.optString("nickname").ifBlank { "酷狗 $userId" },
        secret = token,
        userId = userId,
    )
}

internal fun signKugouRequest(
    params: Map<String, String>,
    body: String = "",
    web: Boolean = false,
): String {
    val salt = if (web) "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt" else "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
    return kugouMd5(
        salt + params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" } +
            (if (web) "" else body) + salt,
    )
}

internal fun kugouMd5(value: String): String =
    MessageDigest
        .getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun kugouArtwork(value: String): String? =
    value
        .replace("{size}", "400")
        .let { if (it.startsWith("//")) "https:$it" else it }
        .takeIf { it.toHttpUrlOrNull() != null }

internal fun parseKugouSongs(rows: JSONArray): List<RemoteSong> =
    buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val hash = row.optString("FileHash", row.optString("hash")).lowercase()
            if (!hash.matches(Regex("[a-f0-9]{32}"))) continue
            val filename = row.optString("FileName", row.optString("filename"))
            val artist = row.optString("SingerName", row.optString("singername")).ifBlank { filename.substringBefore(" - ", "未知歌手") }
            val title = row.optString("SongName", row.optString("songname")).ifBlank { filename.substringAfter(" - ", filename) }
            val audioId =
                row.optString(
                    "AlbumAudioID",
                    row.optString("album_audio_id", row.optString("MixSongID", row.optString("mixsongid", "0"))),
                )
            add(
                RemoteSong(
                    "${hash}_$audioId",
                    title.replace(Regex("<[^>]*>"), ""),
                    artist.replace(Regex("<[^>]*>"), ""),
                    row.optString("AlbumName", row.optString("album_name")),
                    when {
                        row.has("timelen") -> row.optLong("timelen")
                        row.has("timelength") -> row.optLong("timelength")
                        else -> row.optLong("Duration", row.optLong("duration")) * 1000
                    }.coerceAtLeast(0),
                    "audio/mpeg",
                    kugouArtwork(row.optString("Image", row.optString("cover", row.optString("album_sizable_cover")))),
                ),
            )
        }
    }.distinctBy { it.id }

internal fun parseKugouStream(data: JSONObject): ResolvedRemoteStream {
    val urls = data.optJSONArray("url")
    val url =
        (0 until (urls?.length() ?: 0))
            .mapNotNull { urls?.optString(it) }
            .firstOrNull { it.toHttpUrlOrNull() != null }
            ?: data.optString("url").takeIf { it.toHttpUrlOrNull() != null }
    check(url != null) { "此歌曲暂无可播放音源，请检查账号权限或稍后重试" }
    val restrictions = data.optJSONArray("fail_process")
    val preview =
        data.optInt("is_free_part") == 1 ||
            data.optInt("IsFreePart") == 1 ||
            (0 until (restrictions?.length() ?: 0)).any { restrictions?.optString(it) == "buy" }
    return ResolvedRemoteStream(url, preview)
}
