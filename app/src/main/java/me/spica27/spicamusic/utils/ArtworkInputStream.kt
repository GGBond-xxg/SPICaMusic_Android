package me.spica27.spicamusic.utils

import android.content.Context
import android.net.Uri
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

private const val ARTWORK_CONNECT_TIMEOUT_MS = 8_000
private const val ARTWORK_READ_TIMEOUT_MS = 12_000
private const val MAX_REMOTE_ARTWORK_BYTES = 16L * 1024L * 1024L

/**
 * Opens artwork without first copying the complete response into the app heap.
 *
 * A fresh stream is opened for every call so BitmapFactory can perform its bounds and sampled
 * decode passes independently. Remote responses are capped because artwork endpoints can
 * occasionally return an audio file or an unbounded error response instead of an image.
 */
internal fun <T> withArtworkInputStream(
    context: Context,
    uri: Uri,
    block: (InputStream) -> T,
): T? =
    if (uri.scheme == "http" || uri.scheme == "https") {
        val connection =
            URL(uri.toString()).openConnection().apply {
                connectTimeout = ARTWORK_CONNECT_TIMEOUT_MS
                readTimeout = ARTWORK_READ_TIMEOUT_MS
                useCaches = true
                setRequestProperty("Accept", "image/*")
            }
        try {
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_REMOTE_ARTWORK_BYTES) {
                throw IOException("Remote artwork is too large: $contentLength bytes")
            }

            val contentType =
                connection.contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
            if (
                !contentType.isNullOrEmpty() &&
                !contentType.startsWith("image/") &&
                contentType != "application/octet-stream" &&
                contentType != "binary/octet-stream"
            ) {
                throw IOException("Remote artwork has unsupported content type: $contentType")
            }

            connection.getInputStream().use { input ->
                SizeLimitedInputStream(input, MAX_REMOTE_ARTWORK_BYTES).use(block)
            }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use(block)
    }

internal class SizeLimitedInputStream(
    input: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(input) {
    private var consumedBytes = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) recordBytes(1)
        return value
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) recordBytes(count.toLong())
        return count
    }

    override fun skip(byteCount: Long): Long {
        val count = super.skip(byteCount)
        if (count > 0) recordBytes(count)
        return count
    }

    private fun recordBytes(count: Long) {
        consumedBytes += count
        if (consumedBytes > maximumBytes) {
            throw IOException("Remote artwork exceeded $maximumBytes bytes")
        }
    }
}
