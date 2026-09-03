package me.spica27.spicamusic.cloud

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.spica27.spicamusic.BuildConfig
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TelegramClientManager(
    private val context: Context,
    private val accountStore: CloudAccountStore,
) {
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState = _authorizationState.asStateFlow()

    private val _errors = MutableSharedFlow<TdApi.Error>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()

    private val _fileUpdates = MutableSharedFlow<TdApi.File>(extraBufferCapacity = 32)
    val fileUpdates = _fileUpdates.asSharedFlow()

    @Volatile
    private var client: Client? = null

    @Volatile
    private var nativeLoadComplete = false

    @Volatile
    private var nativeLoadSucceeded = false

    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializationMutex = Mutex()

    @Volatile
    private var config: TelegramConfig? =
        accountStore.getTelegramConfig()
            ?: BundledTelegramCredentials.load(context)

    /** Starts the optional Telegram runtime without blocking the caller. */
    fun initializeAsync() {
        initializationScope.launch { ensureInitialized() }
    }

    fun configure(newConfig: TelegramConfig) {
        config = newConfig
        accountStore.saveTelegramConfig(newConfig)
        if (_authorizationState.value is TdApi.AuthorizationStateWaitTdlibParameters) {
            sendTdlibParameters(newConfig)
        }
    }

    fun isAvailable(): Boolean = nativeLoadComplete && nativeLoadSucceeded

    fun hasConfig(): Boolean = config != null

    fun isReady(): Boolean = _authorizationState.value is TdApi.AuthorizationStateReady

    suspend fun awaitReady(timeoutMs: Long = 20_000L): Boolean {
        ensureInitialized()
        if (isReady()) return true
        return withTimeoutOrNull(timeoutMs) {
            authorizationState.first {
                it is TdApi.AuthorizationStateReady || it is TdApi.AuthorizationStateClosed
            }
        } is TdApi.AuthorizationStateReady
    }

    suspend fun <T : TdApi.Object> sendRequest(function: TdApi.Function<*>): T {
        ensureInitialized()
        return suspendCancellableCoroutine { continuation ->
            val current = client
            if (current == null) {
                continuation.resumeWithException(
                    IllegalStateException("当前设备无法加载 Telegram TDLib"),
                )
                return@suspendCancellableCoroutine
            }
            current.send(function) { result ->
                when (result) {
                    is TdApi.Error -> {
                        _errors.tryEmit(result)
                        continuation.resumeWithException(
                            TelegramRequestException(result.code, result.message),
                        )
                    }
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(result as T)
                    }
                }
            }
        }
    }

    fun logout() {
        client?.send(TdApi.LogOut()) { result ->
            if (result is TdApi.Error) _errors.tryEmit(result)
        }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                _authorizationState.value = update.authorizationState
                when (val state = update.authorizationState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> {
                        config?.let(::sendTdlibParameters)
                    }
                    is TdApi.AuthorizationStateClosed -> client = null
                    else -> Unit
                }
            }
            is TdApi.UpdateFile -> _fileUpdates.tryEmit(update.file)
            is TdApi.Error -> _errors.tryEmit(update)
        }
    }

    private suspend fun ensureInitialized() {
        withContext(Dispatchers.IO) {
            if (nativeLoadComplete) return@withContext
            initializationMutex.withLock {
                if (nativeLoadComplete) return@withLock
                nativeLoadSucceeded = nativeLibraryAvailable
                if (nativeLoadSucceeded) {
                    Client.execute(TdApi.SetLogVerbosityLevel(if (BuildConfig.DEBUG) 1 else 0))
                    client = Client.create(::handleUpdate, null, null)
                }
                nativeLoadComplete = true
            }
        }
    }

    private fun sendTdlibParameters(value: TelegramConfig) {
        val databaseDirectory = File(context.filesDir, "tdlib").apply { mkdirs() }.absolutePath
        val filesDirectory = File(context.cacheDir, "tdlib_files").apply { mkdirs() }.absolutePath
        client?.send(
            TdApi.SetTdlibParameters(
                false,
                databaseDirectory,
                filesDirectory,
                null,
                true,
                true,
                true,
                false,
                value.apiId,
                value.apiHash,
                "zh",
                android.os.Build.MODEL,
                android.os.Build.VERSION.RELEASE,
                BuildConfig.VERSION_NAME,
            ),
        ) { result ->
            if (result is TdApi.Error) {
                Timber.w("TDLib parameters rejected: ${result.code}")
                _errors.tryEmit(result)
            }
        }
    }

    companion object {
        val nativeLibraryAvailable: Boolean by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            runCatching {
                System.loadLibrary("tdjni")
                true
            }.getOrElse {
                Timber.e(it, "TDLib native library is unavailable")
                false
            }
        }
    }
}

class TelegramRequestException(
    val errorCode: Int,
    message: String,
) : Exception(message)
