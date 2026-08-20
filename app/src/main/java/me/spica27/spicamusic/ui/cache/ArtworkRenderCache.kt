package me.spica27.spicamusic.ui.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import me.spica27.spicamusic.App
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Small decoded thumbnails used only for the first frame after process death.
 *
 * The regular image pipeline still loads the authoritative artwork and replaces these thumbnails.
 */
object ArtworkRenderCache {
    private val writer = Executors.newSingleThreadExecutor()
    private val remoteClient = OkHttpClient()

    fun read(key: String?): Painter? {
        if (key.isNullOrBlank()) return null
        return runCatching {
            val file = fileFor(key)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                if (file.exists()) file.delete()
                null
            } else {
                file.setLastModified(System.currentTimeMillis())
                BitmapPainter(bitmap.asImageBitmap())
            }
        }.getOrNull()
    }

    fun write(
        key: String?,
        fallbackKey: String? = null,
    ) {
        if (key.isNullOrBlank()) return
        val cachedFile = fileFor(key)
        if (cachedFile.isFile && cachedFile.length() > 0L) return

        writer.execute {
            val destination = fileFor(key)
            if (destination.isFile && destination.length() > 0L) return@execute
            runCatching {
                val source =
                    sequenceOf(key, fallbackKey)
                        .filterNotNull()
                        .mapNotNull(::decode)
                        .firstOrNull()
                        ?: return@runCatching
                val directory = cacheDirectory()
                directory.mkdirs()
                val temporary = File(directory, destination.name + ".tmp")
                val thumbnail =
                    if (source.width > THUMBNAIL_SIZE || source.height > THUMBNAIL_SIZE) {
                        Bitmap.createScaledBitmap(
                            source,
                            THUMBNAIL_SIZE,
                            THUMBNAIL_SIZE,
                            true,
                        )
                    } else {
                        source
                    }
                FileOutputStream(temporary).use { output ->
                    thumbnail.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                if (thumbnail !== source) thumbnail.recycle()
                source.recycle()
                if (destination.exists()) destination.delete()
                temporary.renameTo(destination)
                prune(directory)
            }
        }
    }

    private fun fileFor(key: String): File =
        File(
            cacheDirectory(),
            MessageDigest
                .getInstance("SHA-256")
                .digest(key.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) } + ".png",
        )

    private fun cacheDirectory(): File = File(App.getInstance().cacheDir, CACHE_DIRECTORY)

    private fun decode(uriString: String): Bitmap? =
        runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)
            ) {
                remoteClient
                    .newCall(Request.Builder().url(uriString).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) return@use null
                        response.body.byteStream().use(BitmapFactory::decodeStream)
                    }
            } else {
                App
                    .getInstance()
                    .contentResolver
                    .openInputStream(uri)
                    ?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull()

    private fun prune(directory: File) {
        directory
            .listFiles()
            .orEmpty()
            .filter { it.extension == "png" }
            .sortedByDescending(File::lastModified)
            .drop(MAX_FILES)
            .forEach(File::delete)
    }

    private const val CACHE_DIRECTORY = "first_frame_artwork"
    private const val THUMBNAIL_SIZE = 128
    private const val MAX_FILES = 32
}
