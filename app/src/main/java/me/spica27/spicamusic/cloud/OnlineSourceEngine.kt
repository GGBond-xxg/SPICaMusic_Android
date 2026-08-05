package me.spica27.spicamusic.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class OnlineSourceInfo(
    val key: String,
    val name: String,
    val actions: Set<String>,
    val qualities: List<String>,
)

data class OnlineSourceScriptInfo(
    val name: String,
    val version: String,
    val description: String,
)

data class OnlineSourceStatus(
    val installed: Boolean = false,
    val ready: Boolean = false,
    val script: OnlineSourceScriptInfo? = null,
    val sources: List<OnlineSourceInfo> = emptyList(),
    val error: String? = null,
)

data class OnlineSourceSong(
    val id: String,
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val rawInfoJson: String,
)

/**
 * Stores the user-selected LX Music compatible source script in private app storage.
 *
 * Scripts are deliberately not shipped by SPICa Music. The user remains in control of the
 * source, and replacing/removing a source is an atomic private-file operation.
 */
class OnlineSourceFileStore(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val directory: File
        get() = File(context.filesDir, "online_source")

    val file: File
        get() = File(directory, "source.js")

    fun exists(): Boolean = file.isFile && file.length() > 0L

    suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            file.takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }

    suspend fun import(uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readLimited(MAX_SCRIPT_BYTES + 1)
                    } ?: error("无法读取所选文件")
                saveValidated(bytes)
            }
        }

    suspend fun import(url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = URI(url.trim())
                require(parsed.scheme.equals("https", ignoreCase = true)) { "只支持 HTTPS 音源地址" }
                val request =
                    Request
                        .Builder()
                        .url(parsed.toURL())
                        .header("Accept", "application/javascript, text/javascript, text/plain")
                        .build()
                client
                    .newBuilder()
                    .callTimeout(30, TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response ->
                        check(response.isSuccessful) { "下载失败：HTTP ${response.code}" }
                        val bytes = response.body.byteStream().readLimited(MAX_SCRIPT_BYTES + 1)
                        saveValidated(bytes)
                    }
            }
        }

    suspend fun delete() {
        withContext(Dispatchers.IO) {
            if (file.exists() && !file.delete()) error("删除音源失败")
        }
    }

    private fun saveValidated(bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "音源文件为空" }
        require(bytes.size <= MAX_SCRIPT_BYTES) { "音源文件不能超过 2 MB" }
        val source = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        require("EVENT_NAMES" in source && ".inited" in source) {
            "不是受支持的 LX Music 音源脚本"
        }
        directory.mkdirs()
        val temporary = File(directory, "source.js.tmp")
        temporary.writeText(source, Charsets.UTF_8)
        check(
            temporary.renameTo(file) ||
                runCatching {
                    temporary.copyTo(file, overwrite = true)
                    temporary.delete()
                }.isSuccess,
        ) { "保存音源失败" }
    }

    private companion object {
        const val MAX_SCRIPT_BYTES = 2 * 1024 * 1024
    }
}

private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 32 * 1024))
    val buffer = ByteArray(8 * 1024)
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        output.write(buffer, 0, count)
        remaining -= count
    }
    return output.toByteArray()
}

/**
 * Minimal, sandboxed host for the public LX custom-source contract.
 *
 * The source runs in a headless WebView JavaScript sandbox with file/content access disabled.
 * Its only Android bridge is a constrained HTTP(S) request function matching `lx.request`.
 */
class OnlineSourceEngine(
    context: Context,
    private val store: OnlineSourceFileStore,
    baseClient: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val client =
        baseClient
            .newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    private val mutex = Mutex()

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var currentStatus = OnlineSourceStatus()
    private var initialization: CompletableDeferred<String>? = null
    private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<BridgeResult>>()
    private val bridge = SourceBridge()

    private data class BridgeResult(
        val value: String?,
        val error: String?,
    )

    private inner class SourceBridge {
        @JavascriptInterface
        fun http(
            url: String,
            optionsJson: String,
        ): String = executeHttp(url, optionsJson)

        @JavascriptInterface
        fun initialized(payload: String) {
            initialization?.complete(payload)
        }

        @JavascriptInterface
        fun initializationFailed(message: String) {
            initialization?.completeExceptionally(
                IllegalArgumentException(message.ifBlank { "脚本执行失败" }),
            )
        }

        @JavascriptInterface
        fun completed(
            requestId: String,
            value: String?,
            error: String?,
        ) {
            pendingCalls.remove(requestId)?.complete(BridgeResult(value, error))
        }
    }

    suspend fun status(refresh: Boolean = false): OnlineSourceStatus {
        if (refresh || (store.exists() && !currentStatus.ready)) initialize()
        if (!store.exists()) currentStatus = OnlineSourceStatus()
        return currentStatus
    }

    suspend fun initialize(): OnlineSourceStatus =
        mutex.withLock {
            closeLocked()
            if (!store.exists()) {
                currentStatus = OnlineSourceStatus()
                return@withLock currentStatus
            }
            val script =
                store.read().orEmpty().takeIf(String::isNotBlank)
                    ?: return@withLock failed("音源文件为空")
            val result =
                runCatching {
                    createRuntime(script)
                }
            if (result.isFailure) {
                return@withLock failed(
                    "音源初始化失败：${result.exceptionOrNull()?.message.orEmpty()}",
                    parseScriptInfo(script),
                )
            }

            val sources = parseSources(result.getOrThrow())
            if (sources.isEmpty()) {
                closeLocked()
                return@withLock failed("脚本没有注册可用音源", parseScriptInfo(script))
            }
            currentStatus =
                OnlineSourceStatus(
                    installed = true,
                    ready = true,
                    script = parseScriptInfo(script),
                    sources = sources,
                )
            currentStatus
        }

    suspend fun reload(): OnlineSourceStatus = initialize()

    suspend fun resolveUrl(
        source: String,
        songInfoJson: String,
        preferredQualities: List<String> = DEFAULT_QUALITY_ORDER,
    ): String {
        val status = status()
        check(status.ready) { status.error ?: "在线音源未就绪" }
        val sourceInfo =
            status.sources.firstOrNull { it.key == source }
                ?: error("脚本不支持音源 $source")
        val qualities =
            (preferredQualities.filter { it in sourceInfo.qualities } + sourceInfo.qualities)
                .distinct()
                .ifEmpty { listOf("128k") }
        var lastError: Throwable? = null
        for (quality in qualities) {
            val info =
                JSONObject()
                    .put("type", quality)
                    .put("musicInfo", JSONObject(songInfoJson))
            val result =
                runCatching { call(source, "musicUrl", info) }
                    .onFailure { lastError = it }
                    .getOrNull()
            extractUrl(result)?.let { return it }
        }
        throw IllegalStateException(lastError?.message ?: "音源没有返回可播放地址")
    }

    suspend fun search(
        source: String,
        keyword: String,
        page: Int,
        pageSize: Int = 30,
    ): List<OnlineSourceSong> {
        val status = status()
        val sourceInfo =
            status.sources.firstOrNull { it.key == source }
                ?: error("脚本不支持音源 $source")
        require("musicSearch" in sourceInfo.actions || "search" in sourceInfo.actions) {
            "该音源不支持搜索"
        }
        val info =
            JSONObject()
                .put("keyword", keyword)
                .put("page", page)
                .put("pagesize", pageSize)
                .put("type", "music")
        val root = jsonObject(call(source, "musicSearch", info)) ?: return emptyList()
        val array =
            root.optJSONArray("list")
                ?: root.optJSONObject("data")?.optJSONArray("list")
                ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseSong(source, item)?.let(::add)
            }
        }
    }

    suspend fun close() =
        mutex.withLock {
            closeLocked()
            currentStatus = OnlineSourceStatus(installed = store.exists())
        }

    private suspend fun call(
        source: String,
        action: String,
        info: JSONObject,
    ): String {
        val runtime = webView ?: error("在线音源未初始化")
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<BridgeResult>()
        pendingCalls[requestId] = deferred
        val command =
            "__spica_call(" +
                "${JSONObject.quote(requestId)}," +
                "${JSONObject.quote(source)}," +
                "${JSONObject.quote(action)}," +
                "JSON.parse(${JSONObject.quote(info.toString())}))"
        withContext(Dispatchers.Main) {
            runtime.evaluateJavascript(command, null)
        }
        val result =
            try {
                withTimeout(60_000L) { deferred.await() }
            } finally {
                pendingCalls.remove(requestId)
            }
        if (!result.error.isNullOrBlank()) error(result.error)
        return result.value.orEmpty()
    }

    private fun executeHttp(
        url: String,
        optionsJson: String,
    ): String {
        val responseJson = JSONObject()
        return runCatching {
            val uri = URI(url)
            require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
                "只允许 HTTP(S) 请求"
            }
            require(!isLocalHost(uri.host.orEmpty())) { "音源不能访问本机地址" }
            val options = runCatching { JSONObject(optionsJson) }.getOrElse { JSONObject() }
            val method = options.optString("method", "GET").uppercase()
            val headers = options.optJSONObject("headers") ?: JSONObject()
            val requestBuilder =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept-Encoding", "identity")
            headers.keys().forEach { name ->
                requestBuilder.header(name, headers.optString(name))
            }
            val body = options.optString("body").takeIf { options.has("body") }
            if (method == "GET" || method == "HEAD") {
                requestBuilder.method(method, null)
            } else {
                val mediaType = headers.optString("Content-Type").toMediaTypeOrNull()
                requestBuilder.method(method, body.orEmpty().toRequestBody(mediaType))
            }
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseHeaders = JSONObject()
                response.headers.names().forEach { name ->
                    responseHeaders.put(name.lowercase(), response.headers.values(name).joinToString(", "))
                }
                responseJson
                    .put("statusCode", response.code)
                    .put("headers", responseHeaders)
                    .put("body", response.body.string())
                    .toString()
            }
        }.getOrElse {
            responseJson
                .put("statusCode", 0)
                .put("headers", JSONObject())
                .put("body", "")
                .put("error", it.message ?: it.javaClass.simpleName)
                .toString()
        }
    }

    private suspend fun closeLocked() {
        val old = webView
        webView = null
        initialization?.cancel()
        initialization = null
        pendingCalls.values.forEach { it.cancel() }
        pendingCalls.clear()
        if (old != null) {
            withContext(Dispatchers.Main) {
                old.removeJavascriptInterface(BRIDGE_NAME)
                old.stopLoading()
                old.destroy()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun createRuntime(script: String): String {
        val pageReady = CompletableDeferred<Unit>()
        val init = CompletableDeferred<String>()
        initialization = init
        val runtime =
            withContext(Dispatchers.Main) {
                WebView(appContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    addJavascriptInterface(bridge, BRIDGE_NAME)
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                pageReady.complete(Unit)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = true
                        }
                    loadDataWithBaseURL(
                        "https://spica.invalid/",
                        "<!doctype html><meta charset=\"utf-8\"><script>$HOST_SCRIPT</script>",
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        webView = runtime
        withTimeout(10_000L) { pageReady.await() }
        withContext(Dispatchers.Main) {
            runtime.evaluateJavascript(
                """
                try {
                  (0,eval)(${JSONObject.quote(script)});
                } catch (error) {
                  SpicaNative.initializationFailed(
                    String(error && error.message ? error.message : error)
                  );
                }
                //# sourceURL=online-source.js
                """.trimIndent(),
                null,
            )
        }
        return try {
            withTimeout(12_000L) { init.await() }
        } finally {
            initialization = null
        }
    }

    private fun failed(
        message: String,
        scriptInfo: OnlineSourceScriptInfo? = null,
    ): OnlineSourceStatus =
        OnlineSourceStatus(
            installed = store.exists(),
            script = scriptInfo,
            error = message,
        ).also { currentStatus = it }

    private fun parseSources(raw: String): List<OnlineSourceInfo> {
        val root = JSONObject(raw)
        val sources = root.optJSONObject("sources") ?: return emptyList()
        return buildList {
            sources.keys().forEach { key ->
                val item = sources.optJSONObject(key) ?: return@forEach
                val actions = item.optJSONArray("actions").asStringList().toSet()
                if ("musicUrl" !in actions && "musicSearch" !in actions && "search" !in actions) {
                    return@forEach
                }
                add(
                    OnlineSourceInfo(
                        key = key,
                        name = item.optString("name").ifBlank { key.uppercase() },
                        actions = actions,
                        qualities = item.optJSONArray("qualitys").asStringList(),
                    ),
                )
            }
        }
    }

    private fun parseScriptInfo(script: String): OnlineSourceScriptInfo {
        fun metadata(name: String): String =
            Regex("""(?m)^\s*\*\s*@$name\s+(.+?)\s*$""")
                .find(script)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
        return OnlineSourceScriptInfo(
            name = metadata("name").ifBlank { "自定义音源" },
            version = metadata("version").ifBlank { "未知" },
            description = metadata("description"),
        )
    }

    private fun parseSong(
        source: String,
        item: JSONObject,
    ): OnlineSourceSong? {
        val id =
            item
                .optString("songmid")
                .ifBlank { item.optString("id") }
                .ifBlank { item.optString("hash") }
                .takeIf(String::isNotBlank)
                ?: return null
        val title = item.optString("name").ifBlank { item.optString("title") }
        if (title.isBlank()) return null
        val artist =
            when (val singer = item.opt("singer")) {
                is String -> singer
                is JSONArray -> singer.asStringList().joinToString(" / ")
                else -> item.optString("artist")
            }.ifBlank { "未知歌手" }
        val album =
            when (val albumValue = item.opt("album")) {
                is String -> albumValue
                is JSONObject -> albumValue.optString("name")
                else -> item.optString("albumName")
            }
        val duration =
            item.optLong("duration").takeIf { it > 0L }
                ?: item.optLong("interval").takeIf { it > 0L }?.times(1_000L)
                ?: 0L
        val artwork =
            item
                .optString("pic")
                .ifBlank { item.optString("img") }
                .ifBlank { item.optJSONObject("album")?.optString("picUrl").orEmpty() }
                .takeIf(String::isNotBlank)
        return OnlineSourceSong(
            id = id,
            source = source,
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            artworkUrl = artwork,
            rawInfoJson = item.toString(),
        )
    }

    private fun extractUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val candidate =
            when {
                trimmed.startsWith("\"") -> runCatching { JSONArray("[$trimmed]").getString(0) }.getOrNull()
                trimmed.startsWith("{") -> JSONObject(trimmed).optString("url")
                trimmed.startsWith("[") -> JSONArray(trimmed).optString(0)
                else -> trimmed
            }
        return candidate?.takeIf {
            it.startsWith("https://", true) || it.startsWith("http://", true)
        }
    }

    private fun jsonObject(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val normalized =
            if (raw.trim().startsWith("\"")) {
                runCatching { JSONArray("[$raw]").getString(0) }.getOrNull() ?: raw
            } else {
                raw
            }
        return runCatching { JSONObject(normalized) }.getOrNull()
    }

    private fun JSONArray?.asStringList(): List<String> =
        if (this == null) {
            emptyList()
        } else {
            (0 until length()).mapNotNull { index ->
                optString(index).takeIf(String::isNotBlank)
            }
        }

    private fun isLocalHost(host: String): Boolean {
        val value = host.lowercase()
        return value == "localhost" ||
            value == "::1" ||
            value.startsWith("127.") ||
            value.startsWith("169.254.")
    }

    private companion object {
        val DEFAULT_QUALITY_ORDER = listOf("flac24bit", "24bit", "flac", "320k", "128k")

        val HOST_SCRIPT =
            """
            (function () {
              const handlers = Object.create(null);
              let initPayload = null;
              let callValue = null;
              let callError = null;

              function normalizeOptions(options) {
                const input = options || {};
                const output = {
                  method: String(input.method || 'GET').toUpperCase(),
                  headers: input.headers || {},
                };
                if (input.body != null) {
                  output.body = typeof input.body === 'string' ? input.body : JSON.stringify(input.body);
                } else if (input.form && typeof input.form === 'object') {
                  output.body = Object.keys(input.form)
                    .map(k => encodeURIComponent(k) + '=' + encodeURIComponent(String(input.form[k])))
                    .join('&');
                  if (!output.headers['Content-Type'] && !output.headers['content-type']) {
                    output.headers['Content-Type'] = 'application/x-www-form-urlencoded';
                  }
                } else if (input.json && typeof input.json === 'object') {
                  output.body = JSON.stringify(input.json);
                  if (!output.headers['Content-Type'] && !output.headers['content-type']) {
                    output.headers['Content-Type'] = 'application/json';
                  }
                }
                return output;
              }

              const api = {
                EVENT_NAMES: {
                  request: 'request',
                  inited: 'inited',
                  updateAlert: 'updateAlert',
                },
                request(url, options, callback) {
                  try {
                    const response = JSON.parse(SpicaNative.http(String(url), JSON.stringify(normalizeOptions(options))));
                    if (response.error) callback(new Error(String(response.error)), null);
                    else callback(null, response);
                  } catch (error) {
                    callback(error instanceof Error ? error : new Error(String(error)), null);
                  }
                },
                on(name, handler) {
                  handlers[name] = handler;
                },
                send(name, payload) {
                  if (name === 'inited') {
                    initPayload = JSON.stringify(payload || { sources: {} });
                    SpicaNative.initialized(initPayload);
                  }
                },
                env: 'android',
                version: '2.0.0',
              };

              globalThis.lx = api;
              globalThis.__spica_call = (requestId, source, action, info) => {
                callValue = null;
                callError = null;
                const handler = handlers.request;
                if (typeof handler !== 'function') {
                  callError = '脚本未注册请求处理器';
                  SpicaNative.completed(requestId, null, callError);
                  return;
                }
                try {
                  Promise.resolve(handler({ source, action, info })).then(
                    value => {
                      callValue = JSON.stringify(value);
                      SpicaNative.completed(requestId, callValue, null);
                    },
                    error => {
                      callError = String(error && error.message ? error.message : error);
                      SpicaNative.completed(requestId, null, callError);
                    },
                  );
                } catch (error) {
                  callError = String(error && error.message ? error.message : error);
                  SpicaNative.completed(requestId, null, callError);
                }
              };
            })();
            """.trimIndent()
        const val BRIDGE_NAME = "SpicaNative"
    }
}
