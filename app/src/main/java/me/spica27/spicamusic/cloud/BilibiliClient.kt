package me.spica27.spicamusic.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Bilibili web API adapter. Playback URLs are resolved just before streaming, never persisted. */
class BilibiliClient(
    private val http: OkHttpClient,
) {
    private val playlistCache = ConcurrentHashMap<String, List<RemotePlaylist>>()
    private val keyLock = Mutex()
    private var signingKey = ""
    private var keyExpiresAt = 0L
    private var anonymousCookie = ""
    private val sessionLock = Mutex()

    suspend fun authenticate(cookie: String): Result<RemoteMusicAccount> =
        try {
            val data = request("/x/web-interface/nav", cookie = cookie)
            check(data.optBoolean("isLogin")) { "请先在网页中登录 Bilibili，再点击完成登录" }
            Result.success(
                RemoteMusicAccount(
                    "",
                    RemoteMusicProvider.BILIBILI,
                    data.optString("uname", "Bilibili"),
                    secret = cookie,
                    userId = data.getLong("mid").toString(),
                ),
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }

    fun cachedPlaylists(accountId: String): List<RemotePlaylist> = playlistCache[accountId].orEmpty()

    fun clearCache(accountId: String) {
        playlistCache.remove(accountId)
    }

    suspend fun listSongs(
        account: RemoteMusicAccount,
        query: String,
        offset: Int,
        limit: Int,
    ): RemoteSongPage {
        val video = extractBilibiliVideoId(query)
        if (video != null) {
            if (offset > 0) return RemoteSongPage(emptyList(), null)
            val detail = signedRequest("/x/web-interface/wbi/view", mapOf("bvid" to video), account.secret)
            val pages = detail.optJSONArray("pages") ?: JSONArray()
            val songs =
                (0 until pages.length()).mapNotNull { index ->
                    val page = pages.optJSONObject(index) ?: return@mapNotNull null
                    val cid = page.optLong("cid")
                    if (cid <= 0) return@mapNotNull null
                    RemoteSong(
                        "$video-$cid",
                        if (pages.length() >
                            1
                        ) {
                            "${detail.optString("title")} · ${page.optString("part")}"
                        } else {
                            detail.optString("title")
                        },
                        detail.optJSONObject("owner")?.optString("name").orEmpty(),
                        "Bilibili",
                        page.optLong("duration") * 1000,
                        "audio/mp4",
                        coverUrl(detail.optString("pic")),
                    )
                }
            return RemoteSongPage(songs, null)
        }
        if (query.isBlank()) {
            if (offset > 0) return RemoteSongPage(emptyList(), null)
            val data = request("/x/web-interface/ranking/v2", mapOf("rid" to "3", "type" to "all"), account.secret)
            return RemoteSongPage(parseVideos(data.optJSONArray("list")), null)
        }
        val pageSize = limit.coerceIn(1, 20)
        val page = offset / pageSize + 1
        val data =
            signedRequest(
                "/x/web-interface/wbi/search/type",
                mapOf(
                    "search_type" to "video",
                    "keyword" to query,
                    "page" to page.toString(),
                    "page_size" to pageSize.toString(),
                ),
                account.secret,
            )
        val songs = parseVideos(data.optJSONArray("result"))
        val next = if (page < data.optInt("numPages", 1)) offset + pageSize else null
        return RemoteSongPage(songs, next)
    }

    suspend fun listPlaylists(
        account: RemoteMusicAccount,
        forceRefresh: Boolean,
    ): List<RemotePlaylist> {
        if (account.userId.isBlank()) return emptyList()
        if (!forceRefresh) playlistCache[account.id]?.let { return it }
        val data = request("/x/v3/fav/folder/created/list-all", mapOf("up_mid" to account.userId), account.secret)
        val list = data.optJSONArray("list") ?: JSONArray()
        return (0 until list.length())
            .mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
                RemotePlaylist(
                    id.toString(),
                    item.optString("title"),
                    coverUrl(item.optString("cover")),
                    item.optInt("media_count"),
                    account.displayName,
                    isOwned = true,
                )
            }.also { playlistCache[account.id] = it }
    }

    suspend fun listPlaylistSongs(
        account: RemoteMusicAccount,
        playlistId: String,
    ): List<RemoteSong> {
        require(playlistId.toLongOrNull() != null) { "无效的收藏夹编号" }
        val songs = mutableListOf<RemoteSong>()
        var page = 1
        do {
            val data =
                request(
                    "/x/v3/fav/resource/list",
                    mapOf(
                        "media_id" to playlistId,
                        "pn" to page.toString(),
                        "ps" to "20",
                        "platform" to "web",
                    ),
                    account.secret,
                )
            val media = data.optJSONArray("medias") ?: JSONArray()
            songs += parseVideos(media)
            page++
            val hasMore = data.optBoolean("has_more") || data.optInt("has_more") == 1
        } while (hasMore && media.length() > 0 && page <= 500)
        return songs.distinctBy(RemoteSong::id)
    }

    suspend fun resolveStreamUrl(
        account: RemoteMusicAccount,
        songId: String,
    ): String {
        val parts = songId.split('-', limit = 2)
        val bvid = parts.first()
        require(extractBilibiliVideoId(bvid) == bvid) { "无效的 Bilibili 视频编号" }
        val cid =
            parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }
                ?: signedRequest("/x/web-interface/wbi/view", mapOf("bvid" to bvid), account.secret).getLong("cid")
        val data =
            signedRequest(
                "/x/player/wbi/playurl",
                mapOf(
                    "bvid" to bvid,
                    "cid" to cid.toString(),
                    "fnval" to "16",
                    "fnver" to "0",
                    "fourk" to "0",
                ),
                account.secret,
            )
        val audio = data.optJSONObject("dash")?.optJSONArray("audio") ?: JSONArray()
        // AAC works on all supported devices; avoid selecting Dolby/FLAC entitlement variants.
        val candidates = (0 until audio.length()).mapNotNull(audio::optJSONObject).sortedByDescending { it.optLong("bandwidth") }
        val url =
            candidates.firstNotNullOfOrNull { track ->
                track.optString("baseUrl").ifBlank { track.optString("base_url") }.takeIf { it.startsWith("https://") }
            }
        return checkNotNull(url) { "此视频暂无可播放音轨，请登录账号或更换视频" }
    }

    private suspend fun signedRequest(
        path: String,
        params: Map<String, String>,
        cookie: String,
    ): JSONObject {
        val key =
            keyLock.withLock {
                if (signingKey.isBlank() || System.currentTimeMillis() >= keyExpiresAt) {
                    // nav returns WBI keys even for visitors (code -101).
                    val nav = request("/x/web-interface/nav", cookie = cookie, allowAnonymousNav = true)
                    val images = nav.getJSONObject("wbi_img")
                    val raw =
                        images.getString("img_url").substringAfterLast('/').substringBefore('.') +
                            images.getString("sub_url").substringAfterLast('/').substringBefore('.')
                    signingKey = bilibiliMixinKey(raw)
                    keyExpiresAt = System.currentTimeMillis() + 30 * 60_000L
                }
                signingKey
            }
        return request(path, signBilibiliQuery(params, key, System.currentTimeMillis() / 1000), cookie)
    }

    private suspend fun request(
        path: String,
        params: Map<String, String> = emptyMap(),
        cookie: String = "",
        allowAnonymousNav: Boolean = false,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val session =
                if (cookie.isNotBlank()) {
                    cookie
                } else {
                    sessionLock.withLock {
                        if (anonymousCookie.isBlank()) {
                            val req =
                                Request
                                    .Builder()
                                    .url(
                                        "https://api.bilibili.com/x/frontend/finger/spi",
                                    ).header("User-Agent", BILIBILI_USER_AGENT)
                                    .header("Referer", BILIBILI_REFERER)
                                    .build()
                            http.newCall(req).execute().use { response ->
                                if (response.isSuccessful) {
                                    val data = JSONObject(response.body.string()).optJSONObject("data")
                                    val buvid = data?.optString("b_3").orEmpty()
                                    if (buvid.isNotBlank()) anonymousCookie = "buvid3=$buvid; buvid4=${data?.optString("b_4").orEmpty()}"
                                }
                            }
                        }
                        anonymousCookie
                    }
                }
            val url =
                ("https://api.bilibili.com$path")
                    .toHttpUrl()
                    .newBuilder()
                    .apply {
                        params.forEach { (key, value) ->
                            addQueryParameter(key, value)
                        }
                    }.build()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", BILIBILI_USER_AGENT)
                    .header("Referer", BILIBILI_REFERER)
                    .apply {
                        if (session.isNotBlank()) header("Cookie", session)
                    }.build()
            http.newCall(req).execute().use { response ->
                check(response.isSuccessful) { "Bilibili 请求失败（HTTP ${response.code}），请稍后重试" }
                val json = JSONObject(response.body.string())
                val code = json.optInt("code", -1)
                check(code == 0 || (allowAnonymousNav && code == -101)) {
                    when (code) {
                        -101 -> "Bilibili 登录已失效，请重新登录"
                        -352, -412 -> "Bilibili 暂时限制了请求，请登录账号或稍后重试"
                        else -> "Bilibili：${json.optString("message", "请求失败")}（$code）"
                    }
                }
                json.optJSONObject("data") ?: JSONObject()
            }
        }
}

internal const val BILIBILI_REFERER = "https://www.bilibili.com/"
internal const val BILIBILI_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

internal fun extractBilibiliVideoId(text: String): String? =
    Regex("(?<![A-Za-z0-9])BV[1-9A-HJ-NP-Za-km-z]{10}(?![A-Za-z0-9])").find(text)?.value

internal fun bilibiliMixinKey(raw: String): String {
    val order =
        intArrayOf(
            46,
            47,
            18,
            2,
            53,
            8,
            23,
            32,
            15,
            50,
            10,
            31,
            58,
            3,
            45,
            35,
            27,
            43,
            5,
            49,
            33,
            9,
            42,
            19,
            29,
            28,
            14,
            39,
            12,
            38,
            41,
            13,
        )
    require(raw.length >= 64) { "Bilibili 签名信息不完整" }
    return order.map { raw[it] }.joinToString("")
}

internal fun signBilibiliQuery(
    params: Map<String, String>,
    key: String,
    timestamp: Long,
): Map<String, String> {
    val cleaned = (params + ("wts" to timestamp.toString())).mapValues { (_, value) -> value.filterNot { it in "!'()*" } }.toSortedMap()
    val query =
        cleaned.entries.joinToString(
            "&",
        ) { (name, value) ->
            "${URLEncoder.encode(
                name,
                "UTF-8",
            ).replace("+", "%20")}=${URLEncoder.encode(value, "UTF-8").replace("+", "%20")}"
        }
    val digest = MessageDigest.getInstance("MD5").digest((query + key).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    return cleaned + ("w_rid" to digest)
}

internal fun parseBilibiliDuration(value: String): Long =
    value.split(':').fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0L) } * 1000

private fun coverUrl(value: String): String? =
    when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        value.startsWith("https://") -> value
        else -> null
    }

private fun parseVideos(array: JSONArray?): List<RemoteSong> =
    (0 until (array?.length() ?: 0)).mapNotNull { index ->
        val item = array?.optJSONObject(index) ?: return@mapNotNull null
        val bvid = item.optString("bvid").ifBlank { item.optString("bv_id") }
        if (extractBilibiliVideoId(bvid) != bvid) return@mapNotNull null
        val title =
            item
                .optString("title")
                .replace(Regex("<[^>]*>"), "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        val duration = item.optString("duration")
        val artist =
            item
                .optString("author")
                .ifBlank {
                    item.optJSONObject("owner")?.optString("name").orEmpty()
                }.ifBlank { item.optJSONObject("upper")?.optString("name").orEmpty() }
        RemoteSong(
            bvid,
            title,
            artist,
            "Bilibili",
            if (':' in
                duration
            ) {
                parseBilibiliDuration(duration)
            } else {
                (duration.toLongOrNull() ?: 0) * 1000
            },
            "audio/mp4",
            coverUrl(item.optString("pic").ifBlank { item.optString("cover") }),
        )
    }
