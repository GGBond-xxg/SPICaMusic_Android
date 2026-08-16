package me.spica27.spicamusic.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** A persistent audio cache used only by media items carrying a cloud cache key. */
@UnstableApi
class CloudAudioCache(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val cache =
        SimpleCache(
            File(appContext.cacheDir, CACHE_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(Long.MAX_VALUE),
            StandaloneDatabaseProvider(appContext),
        )
    private val upstreamFactory = DefaultDataSource.Factory(appContext)
    private val cachedFactory =
        CacheDataSource
            .Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val dataSourceFactory: DataSource.Factory =
        DataSource.Factory {
            CloudOnlyDataSource(
                upstream = upstreamFactory.createDataSource(),
                cached = cachedFactory.createDataSource(),
            )
        }

    @Synchronized
    fun trimTo(maxBytes: Long) {
        val limit = maxBytes.coerceAtLeast(0L)
        if (cache.cacheSpace <= limit) return
        val spans =
            cache.keys
                .flatMap { cache.getCachedSpans(it) }
                .sortedBy { it.lastTouchTimestamp }
        for (span in spans) {
            if (cache.cacheSpace <= limit) break
            runCatching { cache.removeSpan(span) }
        }
    }

    @Synchronized
    fun release() = cache.release()

    private class CloudOnlyDataSource(
        private val upstream: DataSource,
        private val cached: DataSource,
    ) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
            cached.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val selected =
                if (dataSpec.key?.startsWith(CACHE_KEY_PREFIX) == true) cached else upstream
            active = selected
            return selected.open(dataSpec)
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = requireNotNull(active).read(buffer, offset, length)

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            active?.close()
            active = null
        }
    }

    companion object {
        const val CACHE_KEY_PREFIX = "spica-cloud:"
        const val DEFAULT_MAX_MIB = 1024
        private const val CACHE_DIRECTORY = "cloud_audio"

        fun cacheKey(stableId: String): String = CACHE_KEY_PREFIX + stableId
    }
}
