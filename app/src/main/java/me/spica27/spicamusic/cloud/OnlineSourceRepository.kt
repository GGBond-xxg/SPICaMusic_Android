package me.spica27.spicamusic.cloud

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OnlineSourceRepository(
    private val engine: OnlineSourceEngine,
) {
    suspend fun search(
        source: OnlineSourceInfo,
        keyword: String,
        page: Int,
        pageSize: Int = 30,
    ): List<OnlineSourceSong> {
        require(keyword.isNotBlank()) { "请输入搜索关键词" }
        require("musicSearch" in source.actions || "search" in source.actions) {
            "${source.name} 仅用于解析云端音乐库歌曲，未提供搜索"
        }
        return engine.search(source.key, keyword, page, pageSize)
    }
}

/**
 * Defers script URL resolution until Media3 actually opens a queue item.
 *
 * This keeps queue creation fast and lets a restored queue re-register its metadata after process
 * death. Only an opaque hash is exposed on the loopback URL.
 */
class OnlineSourceStreamProxy(
    baseClient: OkHttpClient,
    private val engine: OnlineSourceEngine,
) {
    private data class Entry(
        val source: String,
        val songInfoJson: String,
        val fallbackUrl: String?,
        val preferFallback: Boolean,
    )

    private val upstreamClient =
        baseClient
            .newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    private val entries = ConcurrentHashMap<String, Entry>()
    private val startMutex = Mutex()

    @Volatile
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    private var port: Int = 0

    suspend fun streamUrl(
        source: String,
        songInfoJson: String,
        fallbackUrl: String? = null,
        preferFallback: Boolean = false,
    ): String {
        ensureStarted()
        val token = tokenFor(source, songInfoJson, preferFallback)
        entries[token] = Entry(source, songInfoJson, fallbackUrl, preferFallback)
        return "http://127.0.0.1:$port/online/$token"
    }

    private suspend fun ensureStarted() {
        if (server != null) return
        startMutex.withLock {
            if (server != null) return
            val selectedPort = withContext(Dispatchers.IO) { ServerSocket(0).use { it.localPort } }
            val newServer =
                embeddedServer(CIO, port = selectedPort, host = "127.0.0.1") {
                    routing {
                        get("/online/{token}") {
                            val token = call.parameters["token"].orEmpty()
                            val entry = entries[token]
                            if (!SAFE_TOKEN.matches(token) || entry == null) {
                                call.respond(HttpStatusCode.NotFound, "Online source item not found")
                                return@get
                            }
                            val range = call.request.headers["Range"]
                            if (range != null && !SAFE_RANGE.matches(range)) {
                                call.respond(
                                    HttpStatusCode(416, "Range Not Satisfiable"),
                                    "Invalid byte range",
                                )
                                return@get
                            }

                            var resolutionError: Throwable? = null
                            val onlineUrl =
                                runCatching {
                                    this@OnlineSourceStreamProxy.engine.resolveUrl(
                                        entry.source,
                                        entry.songInfoJson,
                                    )
                                }.onFailure { resolutionError = it }
                                    .getOrNull()
                            val candidates =
                                orderedStreamCandidates(
                                    onlineUrl = onlineUrl,
                                    fallbackUrl = entry.fallbackUrl,
                                    preferFallback = entry.preferFallback,
                                )
                            if (candidates.isEmpty()) {
                                call.respond(
                                    HttpStatusCode.BadGateway,
                                    resolutionError?.message ?: "Online source resolution failed",
                                )
                                return@get
                            }

                            var lastStatus = HttpStatusCode.BadGateway
                            var lastFailure = resolutionError?.message ?: "Unable to open cloud stream"
                            for (upstreamUrl in candidates) {
                                val requestBuilder =
                                    Request
                                        .Builder()
                                        .url(upstreamUrl)
                                        .header("Accept-Encoding", "identity")
                                        .header("User-Agent", STREAM_USER_AGENT)
                                refererFor(entry.source)?.let { requestBuilder.header("Referer", it) }
                                range?.let { requestBuilder.header("Range", it) }

                                val responseResult =
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            upstreamClient.newCall(requestBuilder.build()).execute()
                                        }
                                    }
                                if (responseResult.isFailure) {
                                    lastStatus = HttpStatusCode.BadGateway
                                    lastFailure =
                                        responseResult.exceptionOrNull()?.message
                                            ?: "Cloud stream connection failed"
                                    continue
                                }
                                val response = responseResult.getOrThrow()
                                val statusAccepted = response.isSuccessful || response.code == 206
                                val contentTypeHeader = response.header("Content-Type")
                                val contentRejected = isClearlyNonAudioContentType(contentTypeHeader)
                                if (!statusAccepted || contentRejected) {
                                    lastStatus =
                                        HttpStatusCode.fromValue(
                                            if (response.code in 400..599) response.code else 502,
                                        )
                                    lastFailure =
                                        if (contentRejected) {
                                            "Cloud source returned non-audio content"
                                        } else {
                                            "Upstream stream failed"
                                        }
                                    response.close()
                                    continue
                                }

                                response.use {
                                    response.header("Accept-Ranges")?.let {
                                        call.response.header("Accept-Ranges", it)
                                    }
                                    response.header("Content-Length")?.let {
                                        call.response.header("Content-Length", it)
                                    }
                                    response.header("Content-Range")?.let {
                                        call.response.header("Content-Range", it)
                                    }
                                    val contentType =
                                        contentTypeHeader
                                            ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                                            ?: ContentType.Audio.Any
                                    call.respondOutputStream(
                                        contentType = contentType,
                                        status = HttpStatusCode.fromValue(response.code),
                                    ) {
                                        response.body.byteStream().use { input ->
                                            input.copyTo(this, BUFFER_SIZE)
                                        }
                                    }
                                }
                                return@get
                            }
                            call.respond(lastStatus, lastFailure)
                        }
                    }
                }
            withContext(Dispatchers.IO) { newServer.start(wait = false) }
            port = selectedPort
            server = newServer
        }
    }

    private fun tokenFor(
        source: String,
        songInfoJson: String,
        preferFallback: Boolean,
    ): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("$source\u0000$preferFallback\u0000$songInfoJson".toByteArray())
            .take(18)
            .joinToString("") { "%02x".format(it) }

    private fun refererFor(source: String): String? =
        when (source.lowercase()) {
            "wy" -> "https://music.163.com/"
            "tx" -> "https://y.qq.com/"
            "kw" -> "https://www.kuwo.cn/"
            "kg" -> "https://www.kugou.com/"
            "mg" -> "https://music.migu.cn/"
            else -> null
        }

    private companion object {
        val SAFE_TOKEN = Regex("^[a-f0-9]{36}$")
        val SAFE_RANGE = Regex("^bytes=\\d*-\\d*$")
        const val BUFFER_SIZE = 64 * 1024
        const val STREAM_USER_AGENT = "SPICaMusic/OnlineSource"
    }
}

internal fun orderedStreamCandidates(
    onlineUrl: String?,
    fallbackUrl: String?,
    preferFallback: Boolean,
): List<String> =
    if (preferFallback) {
        listOfNotNull(fallbackUrl, onlineUrl).distinct()
    } else {
        listOfNotNull(onlineUrl, fallbackUrl).distinct()
    }

internal fun isClearlyNonAudioContentType(value: String?): Boolean {
    val normalized =
        value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
    return normalized.startsWith("text/") ||
        normalized == "application/json" ||
        normalized == "application/xml" ||
        normalized == "application/xhtml+xml"
}
