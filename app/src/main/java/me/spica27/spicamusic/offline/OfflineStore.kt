package me.spica27.spicamusic.offline

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class OfflineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val mimeType: String?,
    val durationMs: Long,
    val bytes: Long,
)

data class DownloadProgress(
    val title: String,
    val bytes: Long = 0,
    val total: Long = -1,
    val error: Boolean = false,
    val errorMessage: String? = null,
    val canceling: Boolean = false,
)

@UnstableApi
class OfflineStore(
    context: Context,
) {
    private val context = context.applicationContext
    private val root = File(context.noBackupFilesDir, "offline_audio").apply { mkdirs() }
    private val settings = context.getSharedPreferences("offline_settings", Context.MODE_PRIVATE)
    private val _tracks = MutableStateFlow<List<OfflineTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress = _progress.asStateFlow()
    private val _limitMiB = MutableStateFlow(settings.getInt("limit_mib", 1024))
    val limitMiB = _limitMiB.asStateFlow()
    private val canceled = ConcurrentHashMap.newKeySet<String>()

    init {
        root.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
        refresh()
        val retained = _tracks.value.map { "${key(it.id)}.audio" }.toSet()
        root.listFiles()?.filter { it.extension == "audio" && it.name !in retained }?.forEach { it.delete() }
    }

    fun enqueue(item: MediaItem) {
        require(item.mediaId.startsWith("cloud:"))
        if (fileFor(item.mediaId) != null || (_progress.value[item.mediaId]?.error == false)) return
        canceled.remove(item.mediaId)
        update(
            item.mediaId,
            DownloadProgress(
                item.mediaMetadata.title
                    ?.toString()
                    .orEmpty(),
            ),
        )
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OfflineDownloadService::class.java).putExtra("item", item.toBundleIncludeLocalConfiguration()),
            )
        } catch (error: RuntimeException) {
            fail(item.mediaId)
        }
    }

    fun update(
        id: String,
        progress: DownloadProgress,
    ) {
        if (!isCanceled(id)) _progress.update { it + (id to progress) }
    }

    fun fail(id: String) {
        _progress.update { current -> current[id]?.let { current + (id to it.copy(error = true)) } ?: current }
    }

    fun fail(
        id: String,
        message: String,
    ) {
        _progress.update { current -> current[id]?.let { current + (id to it.copy(error = true, errorMessage = message)) } ?: current }
    }

    fun cancel(id: String) {
        canceled.add(id)
        // Reserve active IDs until the worker closes the partial file.
        _progress.update { current ->
            val state = current[id]
            if (state == null || state.error) current - id else current + (id to state.copy(canceling = true))
        }
    }

    fun finishCanceled(id: String) {
        if (isCanceled(id)) _progress.update { it - id }
    }

    fun isCanceled(id: String) = id in canceled

    fun partial(id: String) = File(root, "${key(id)}.part")

    fun availableBytes() = _limitMiB.value.toLong() * 1024 * 1024 - _tracks.value.sumOf { it.bytes }

    @Synchronized
    fun setLimit(mib: Int) {
        require(mib in listOf(256, 512, 1024, 2048, 4096))
        require(_tracks.value.sumOf { it.bytes } <= mib.toLong() * 1024 * 1024)
        settings.edit().putInt("limit_mib", mib).apply()
        _limitMiB.value = mib
    }

    @Synchronized
    fun fileFor(id: String): File? =
        File(root, "${key(id)}.audio").takeIf { file ->
            val track = _tracks.value.firstOrNull { it.id == id }
            track != null && file.isFile && file.length() == track.bytes && track.bytes > 0
        }

    @Synchronized
    fun complete(
        item: MediaItem,
        partial: File,
    ) {
        check(!isCanceled(item.mediaId))
        check(partial.length() in 1..availableBytes())
        val target = File(root, "${key(item.mediaId)}.audio")
        check(partial.renameTo(target))
        val metadata = item.mediaMetadata
        val json =
            JSONObject()
                .put("id", item.mediaId)
                .put("title", metadata.title?.toString().orEmpty())
                .put("artist", metadata.artist?.toString().orEmpty())
                .put("album", metadata.albumTitle?.toString().orEmpty())
                .put(
                    "mime",
                    item.localConfiguration?.mimeType.orEmpty(),
                ).put("duration", metadata.durationMs ?: 0L)
                .put("bytes", target.length())
        val file = AtomicFile(File(root, "${key(item.mediaId)}.json"))
        val output = file.startWrite()
        try {
            output.write(json.toString().toByteArray())
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            target.delete()
            throw error
        }
        refresh()
        _progress.update { it - item.mediaId }
    }

    @Synchronized
    fun remove(id: String) {
        cancel(id)
        val audio = File(root, "${key(id)}.audio")
        check(!audio.exists() || audio.delete())
        AtomicFile(File(root, "${key(id)}.json")).delete()
        refresh()
    }

    @Synchronized
    fun clear() {
        _progress.value.keys.forEach(::cancel)
        _tracks.value.toList().forEach { remove(it.id) }
    }

    fun mediaItem(track: OfflineTrack): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(track.id)
            .setUri(Uri.fromFile(requireNotNull(fileFor(track.id))))
            .setMimeType(track.mimeType)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(
                        track.title,
                    ).setArtist(
                        track.artist,
                    ).setAlbumTitle(
                        track.album,
                    ).setDurationMs(track.durationMs)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            ).build()

    @Synchronized
    private fun refresh() {
        _tracks.value =
            root
                .listFiles()
                .orEmpty()
                .filter { it.extension == "json" }
                .mapNotNull { file ->
                    runCatching {
                        val json = JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
                        val track =
                            OfflineTrack(
                                json.getString(
                                    "id",
                                ),
                                json.getString("title"),
                                json.getString("artist"),
                                json.getString("album"),
                                json.optString("mime").takeIf {
                                    it.isNotBlank()
                                },
                                json.getLong("duration"),
                                json.getLong("bytes"),
                            )
                        track.takeIf { File(root, "${key(it.id)}.audio").length() == it.bytes && it.bytes > 0 }
                    }.getOrNull()
                }.sortedBy { it.title }
    }

    private fun key(id: String) = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
}
