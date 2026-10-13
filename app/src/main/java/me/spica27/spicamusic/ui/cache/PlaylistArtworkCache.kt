package me.spica27.spicamusic.ui.cache

import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.spica27.spicamusic.App
import java.io.IOException

/** Decoded local thumbnails survive lazy-item disposal; a cache hit draws on the first frame. */
internal class PlaylistArtworkCache(
    private val loader: suspend (Long, Int) -> ImageBitmap? = ::loadPlaylistArtwork,
) {
    private data class Key(
        val albumId: Long,
        val size: Int,
    )

    private val lock = Any()
    private val images =
        object : LruCache<Key, ImageBitmap>(8 * 1024 * 1024) {
            override fun sizeOf(
                key: Key,
                value: ImageBitmap,
            ): Int = value.width * value.height * 4
        }
    private val pending = mutableMapOf<Key, Deferred<ImageBitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decodes = Semaphore(2)

    fun peek(
        albumId: Long,
        size: Int,
    ): ImageBitmap? = images.get(Key(albumId, size))

    suspend fun load(
        albumId: Long,
        size: Int,
    ): ImageBitmap? {
        val key = Key(albumId, size)
        val work =
            synchronized(lock) {
                images.get(key)?.let { return it }
                pending.getOrPut(key) {
                    // Owned by the cache, so a fling disposing the requester cannot cancel decoding.
                    scope.async(start = CoroutineStart.LAZY) {
                        try {
                            decodes.withPermit { loader(albumId, size) }?.also { images.put(key, it) }
                        } finally {
                            synchronized(lock) { pending.remove(key) }
                        }
                    }
                }
            }
        return work.await()
    }
}

internal val LocalPlaylistArtworkCache = staticCompositionLocalOf { sharedPlaylistArtworkCache }
private val sharedPlaylistArtworkCache = PlaylistArtworkCache()

private suspend fun loadPlaylistArtwork(
    albumId: Long,
    size: Int,
): ImageBitmap? {
    // Decode only the requested thumbnail size on the cache's IO scope. These pixels are owned
    // by this cache, independently of any UI image loader or bitmap pool.
    return try {
        val source =
            ImageDecoder.createSource(
                App.getInstance().contentResolver,
                "content://media/external/audio/albumart/$albumId".toUri(),
            )
        ImageDecoder
            .decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = (size.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }.asImageBitmap()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
