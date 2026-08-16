package me.spica27.spicamusic.ui.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** 从本地音频文件标签读取内嵌歌词。 */
internal class EmbeddedLyricsReader(
    private val context: Context,
) {
    private data class CacheEntry(
        val lyrics: String?,
    )

    private val cacheLock = Any()
    private val lyricsCache =
        object : LinkedHashMap<Long, CacheEntry>(LYRICS_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CacheEntry>?): Boolean = size > LYRICS_CACHE_SIZE
        }

    suspend fun read(mediaStoreId: Long): String? =
        withContext(Dispatchers.IO) {
            if (mediaStoreId <= 0L) return@withContext null

            synchronized(cacheLock) {
                lyricsCache[mediaStoreId]
            }?.let { cached ->
                return@withContext cached.lyrics
            }

            val uri =
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaStoreId,
                )

            val lookup =
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        val metadata =
                            TagLib.getMetadata(
                                fd = pfd.detachFd(),
                                readPictures = false,
                            ) ?: return@use null

                        val candidates =
                            EMBEDDED_LYRICS_KEYS
                                .asSequence()
                                .flatMap { key -> metadata.propertyMap[key].orEmpty().asSequence() }
                                .map(String::trim)
                                .filter(String::isNotBlank)
                                .distinct()
                                .toList()

                        candidates.firstOrNull(::looksSynchronized)
                            ?: candidates.firstOrNull()
                    }
                }
            lookup.exceptionOrNull()?.let { error ->
                Timber.w(error, "读取本地内嵌歌词失败: mediaStoreId=$mediaStoreId")
                return@withContext null
            }

            val lyrics = lookup.getOrNull()
            synchronized(cacheLock) {
                lyricsCache[mediaStoreId] = CacheEntry(lyrics)
            }
            lyrics
        }

    private fun looksSynchronized(value: String): Boolean =
        value.lineSequence().any { line ->
            val text = line.trimStart()
            text.startsWith("[") &&
                (
                    text.contains(":") ||
                        text.contains("](") ||
                        text.substringBefore(']').contains(",")
                )
        }

    private companion object {
        const val LYRICS_CACHE_SIZE = 128

        val EMBEDDED_LYRICS_KEYS =
            listOf(
                "SYNCEDLYRICS",
                "LYRICS",
                "UNSYNCEDLYRICS",
                "TTML",
                "SYNCED LYRICS",
                "UNSYNCED LYRICS",
            )
    }
}
