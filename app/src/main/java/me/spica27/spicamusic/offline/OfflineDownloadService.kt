package me.spica27.spicamusic.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.MainActivity
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.CloudPlaybackItemResolver
import org.koin.android.ext.android.inject

@UnstableApi
class OfflineDownloadService : Service() {
    private val store: OfflineStore by inject()
    private val resolver: CloudPlaybackItemResolver by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Channel<MediaItem>(Channel.UNLIMITED)
    private var pending = 0
    private var lastId = 0
    private var stage = "queued"

    override fun onCreate() {
        super.onCreate()
        getSystemService(
            NotificationManager::class.java,
        ).createNotificationChannel(
            NotificationChannel("offline_download", getString(R.string.offline_title), NotificationManager.IMPORTANCE_LOW),
        )
        val intent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        startForeground(
            7021,
            NotificationCompat
                .Builder(
                    this,
                    "offline_download",
                ).setSmallIcon(
                    R.drawable.ic_music_note,
                ).setContentTitle(
                    getString(R.string.offline_title),
                ).setContentText(getString(R.string.offline_downloading))
                .setContentIntent(intent)
                .setOngoing(true)
                .build(),
        )
        scope.launch {
            for (item in queue) {
                try {
                    if (!store.isCanceled(item.mediaId)) download(item)
                } catch (error: CancellationException) {
                    store.fail(item.mediaId)
                    throw error
                } catch (error: Exception) {
                    val status = (error as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                    android.util.Log.w("SpicaOffline", "Download failed: stage=$stage type=${error.javaClass.simpleName} http=$status")
                    if (!store.isCanceled(item.mediaId)) {
                        val message =
                            when {
                                status != null -> getString(R.string.offline_http_failed, status)
                                error is DownloadProblem -> getString(error.messageResource)
                                else -> getString(R.string.offline_failed)
                            }
                        store.fail(item.mediaId, message)
                    }
                } finally {
                    store.finishCanceled(item.mediaId)
                    pending--
                    if (pending == 0) stopSelfResult(lastId)
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        lastId = startId
        val item = intent?.getBundleExtra("item")?.let { MediaItem.fromBundle(it) }
        if (item != null) {
            pending++
            queue.trySend(item)
        } else if (pending == 0) {
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private suspend fun download(original: MediaItem) =
        withContext(Dispatchers.IO) {
            stage = "resolve"
            val item = resolver.resolve(original)
            val uri = requireNotNull(item.localConfiguration?.uri)
            if (uri.scheme !in listOf("http", "https")) throw DownloadProblem(R.string.offline_unsupported)
            val source =
                DefaultDataSource
                    .Factory(
                        this@OfflineDownloadService,
                        DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(15000),
                    ).createDataSource()
            val temporary = store.partial(item.mediaId)
            try {
                stage = "open"
                val length = source.open(DataSpec.Builder().setUri(uri).build())
                if (length > store.availableBytes()) throw DownloadProblem(R.string.offline_full)
                val type =
                    source.responseHeaders.entries
                        .firstOrNull { it.key.equals("Content-Type", true) }
                        ?.value
                        ?.firstOrNull()
                        .orEmpty()
                if (type.contains("text/", true) ||
                    type.contains("json", true) ||
                    type.contains("mpegurl", true)
                ) {
                    throw DownloadProblem(R.string.offline_unsupported)
                }
                stage = "read"
                var bytes = 0L
                var lastReport = 0L
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        check(!store.isCanceled(item.mediaId))
                        val read = source.read(buffer, 0, buffer.size)
                        if (read == C.RESULT_END_OF_INPUT) break
                        bytes += read
                        if (bytes > store.availableBytes()) throw DownloadProblem(R.string.offline_full)
                        output.write(buffer, 0, read)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastReport > 300) {
                            store.update(
                                item.mediaId,
                                DownloadProgress(
                                    item.mediaMetadata.title
                                        ?.toString()
                                        .orEmpty(),
                                    bytes,
                                    length,
                                ),
                            )
                            lastReport = now
                        }
                    }
                }
                require(bytes > 0 && (length < 0 || length == bytes))
                stage = "commit"
                store.complete(item, temporary)
            } finally {
                runCatching { source.close() }
                temporary.delete()
            }
        }

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        scope.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        queue.close()
        store.progress.value
            .filterValues { !it.error }
            .keys
            .forEach(store::fail)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class DownloadProblem(
        val messageResource: Int,
    ) : Exception()
}
