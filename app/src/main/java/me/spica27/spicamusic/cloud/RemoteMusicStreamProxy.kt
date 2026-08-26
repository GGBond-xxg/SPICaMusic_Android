package me.spica27.spicamusic.cloud

import android.os.SystemClock
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps provider credentials and short-lived upstream URLs out of Media3's queue.
 * Only loopback clients can reach this proxy; each request resolves a fresh provider URL and
 * forwards byte ranges without buffering the whole song.
 */
class RemoteMusicStreamProxy(
    baseClient: OkHttpClient,
    private val accountStore: CloudAccountStore,
    private val clients: RemoteMusicClientRegistry,
) {
    private data class CachedStreamUrl(
        val value: String,
        val expiresAtMs: Long,
    )

    private val upstreamClient = baseClient.newBuilder().build()
    private val startMutex = Mutex()
    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val streamUrlLocks = ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    private var port: Int = 0

    suspend fun streamUrl(
        account: RemoteMusicAccount,
        song: RemoteSong,
    ): String = streamUrl(account.id, song.id)

    suspend fun streamUrl(
        accountId: String,
        songId: String,
    ): String {
        ensureStarted()
        return "http://127.0.0.1:$port/remote/$accountId/$songId"
    }

    /** Resolve the short-lived provider URL before ExoPlayer advances to this item. */
    suspend fun prefetch(
        account: RemoteMusicAccount,
        songId: String,
    ) {
        resolveUpstreamUrl(account, songId)
    }

    private suspend fun ensureStarted() {
        if (server != null) return
        startMutex.withLock {
            if (server != null) return
            val selectedPort =
                withContext(Dispatchers.IO) {
                    ServerSocket(0).use { it.localPort }
                }
            val newServer =
                embeddedServer(CIO, port = selectedPort, host = "127.0.0.1") {
                    routing {
                        get("/remote/{accountId}/{songId}") {
                            val accountId = call.parameters["accountId"].orEmpty()
                            val songId = call.parameters["songId"].orEmpty()
                            if (!SAFE_ID.matches(accountId) || !SAFE_ID.matches(songId)) {
                                call.respond(HttpStatusCode.BadRequest, "Invalid cloud stream id")
                                return@get
                            }
                            val account =
                                accountStore
                                    .getRemoteAccounts()
                                    .firstOrNull { it.id == accountId }
                            if (account == null) {
                                call.respond(HttpStatusCode.NotFound, "Cloud account not found")
                                return@get
                            }
                            val upstreamUrl =
                                runCatching { resolveUpstreamUrl(account, songId) }
                                    .getOrElse {
                                        call.respond(
                                            HttpStatusCode.BadGateway,
                                            it.message ?: "Unable to resolve cloud stream",
                                        )
                                        return@get
                                    }
                            val requestBuilder =
                                Request
                                    .Builder()
                                    .url(upstreamUrl)
                                    .header("Accept-Encoding", "identity")
                            remoteStreamRequestHeaders(account, upstreamUrl).forEach { (name, value) ->
                                requestBuilder.header(name, value)
                            }
                            val requestedRange = call.request.headers["Range"]
                            requestedRange?.let {
                                if (!SAFE_RANGE.matches(it)) {
                                    call.respond(
                                        HttpStatusCode(416, "Range Not Satisfiable"),
                                        "Invalid byte range",
                                    )
                                    return@get
                                }
                                requestBuilder.header("Range", it)
                            }
                            withContext(Dispatchers.IO) {
                                upstreamClient.newCall(requestBuilder.build()).execute()
                            }.use { response ->
                                if (!response.isSuccessful && response.code != 206) {
                                    if (response.code == 401 || response.code == 403) {
                                        streamUrlCache.remove(streamCacheKey(account.id, songId))
                                    }
                                    call.respond(
                                        HttpStatusCode.fromValue(
                                            if (response.code in 400..599) response.code else 502,
                                        ),
                                        "Upstream stream failed",
                                    )
                                    return@get
                                }
                                call.response.header("Accept-Ranges", "bytes")
                                val type =
                                    response
                                        .header("Content-Type")
                                        ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                                        ?: ContentType.Audio.Any
                                val upstreamLength = response.body.contentLength().takeIf { it >= 0L }
                                val fallbackRange =
                                    if (requestedRange != null && response.code == 200 && upstreamLength != null) {
                                        parseByteRange(requestedRange, upstreamLength)
                                    } else {
                                        null
                                    }
                                if (fallbackRange != null) {
                                    val (start, end) = fallbackRange
                                    call.response.header("Content-Range", "bytes $start-$end/$upstreamLength")
                                    call.response.header("Content-Length", (end - start + 1L).toString())
                                    call.respondOutputStream(type, HttpStatusCode.PartialContent) {
                                        response.body.byteStream().use { input ->
                                            if (!input.skipFully(start)) return@use
                                            input.copyLimitedTo(this, end - start + 1L)
                                        }
                                    }
                                } else {
                                    response.header("Content-Length")?.let { call.response.header("Content-Length", it) }
                                    response.header("Content-Range")?.let { call.response.header("Content-Range", it) }
                                    call.respondOutputStream(
                                        contentType = type,
                                        status = HttpStatusCode.fromValue(response.code),
                                    ) {
                                        response.body.byteStream().use { input -> input.copyTo(this, BUFFER_SIZE) }
                                    }
                                }
                            }
                        }
                    }
                }
            withContext(Dispatchers.IO) { newServer.start(wait = false) }
            port = selectedPort
            server = newServer
        }
    }

    private suspend fun resolveUpstreamUrl(
        account: RemoteMusicAccount,
        songId: String,
    ): String {
        val key = streamCacheKey(account.id, songId)
        val nowMs = SystemClock.elapsedRealtime()
        streamUrlCache[key]?.takeIf { it.expiresAtMs > nowMs }?.let { return it.value }
        val lock = streamUrlLocks.getOrPut(key, ::Mutex)
        return lock.withLock {
            val lockedNowMs = SystemClock.elapsedRealtime()
            streamUrlCache[key]?.takeIf { it.expiresAtMs > lockedNowMs }?.value
                ?: clients.resolveStreamUrl(account, songId).also { resolved ->
                    streamUrlCache[key] =
                        CachedStreamUrl(
                            value = resolved,
                            expiresAtMs = lockedNowMs + STREAM_URL_CACHE_MS,
                        )
                }
        }
    }

    private fun streamCacheKey(
        accountId: String,
        songId: String,
    ): String = "$accountId:$songId"

    private companion object {
        val SAFE_ID = Regex("^[A-Za-z0-9_.:-]{1,160}$")
        val SAFE_RANGE = Regex("^bytes=\\d*-\\d*$")
        const val BUFFER_SIZE = 64 * 1024
        const val STREAM_URL_CACHE_MS = 2 * 60 * 1000L
    }
}

private fun parseByteRange(
    value: String,
    totalLength: Long,
): Pair<Long, Long>? {
    val bounds = value.removePrefix("bytes=").split('-', limit = 2)
    val start = bounds.getOrNull(0)?.toLongOrNull() ?: return null
    if (start !in 0 until totalLength) return null
    val requestedEnd = bounds.getOrNull(1)?.toLongOrNull() ?: (totalLength - 1L)
    return start to requestedEnd.coerceIn(start, totalLength - 1L)
}

private fun InputStream.skipFully(byteCount: Long): Boolean {
    var remaining = byteCount
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
        } else if (read() >= 0) {
            remaining -= 1L
        } else {
            return false
        }
    }
    return true
}

private fun InputStream.copyLimitedTo(
    output: OutputStream,
    byteCount: Long,
) {
    val buffer = ByteArray(64 * 1024)
    var remaining = byteCount
    while (remaining > 0L) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (count < 0) break
        output.write(buffer, 0, count)
        remaining -= count
    }
}

/**
 * Adds account headers only for the matching provider's official domains. This lets paid streams
 * use the authenticated session without leaking cookies to URLs returned by another source.
 */
internal fun remoteStreamRequestHeaders(
    account: RemoteMusicAccount,
    upstreamUrl: String,
): Map<String, String> {
    val host = upstreamUrl.toHttpUrlOrNull()?.host?.lowercase() ?: return emptyMap()
    return when (account.provider) {
        RemoteMusicProvider.NETEASE -> {
            if (host == "music.163.com" || host.endsWith(".music.163.com")) {
                mapOf(
                    "Cookie" to account.secret,
                    "Referer" to "https://music.163.com/",
                    "User-Agent" to REMOTE_STREAM_BROWSER_USER_AGENT,
                )
            } else if (host == "music.126.net" || host.endsWith(".music.126.net")) {
                mapOf(
                    "Referer" to "https://music.163.com/",
                    "User-Agent" to REMOTE_STREAM_BROWSER_USER_AGENT,
                )
            } else {
                emptyMap()
            }
        }

        RemoteMusicProvider.QQ_MUSIC -> {
            if (host == "qqmusic.qq.com" || host.endsWith(".qqmusic.qq.com")) {
                mapOf(
                    "Cookie" to account.secret,
                    "Referer" to "https://y.qq.com/",
                    "User-Agent" to REMOTE_STREAM_BROWSER_USER_AGENT,
                )
            } else {
                emptyMap()
            }
        }

        RemoteMusicProvider.SUBSONIC -> emptyMap()
    }
}

private const val REMOTE_STREAM_BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
