package me.spica27.spicamusic.player.impl.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcel
import android.util.Base64
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.player.api.PlayMode
import me.spica27.spicamusic.storage.api.ISongRepository
import org.koin.java.KoinJavaComponent.getKoin

class PlayerKVUtils(
    context: Context,
) {
    private val sharedPreferences = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    private val songRepository = getKoin().get<ISongRepository>()

    companion object {
      private const val KEY_HISTORY_IDS = "history_ids"
      private const val KEY_HISTORY_ITEMS = "history_items_v2"
      private const val KEY_HISTORY_POSITION = "history_position"
      private const val KEY_CURRENT_MEDIA_ID = "current_media_id"
      private const val KEY_PLAY_MODE = "play_mode"
      private const val MAX_RESTORED_ITEMS = 10_000
    }

    /**
     * 历史播放的id
     */
    fun setHistoryIds(ids: List<Long>) {
        sharedPreferences.edit { putString("history_ids", ids.joinToString(",")) }
    }

    /**
     * 获取历史播放歌曲列表
     * 使用批量查询优化，避免 N+1 问题
     */
    @WorkerThread
    suspend fun getHistoryItems(): List<Song> {
        val ids = getHistoryIds()
        if (ids.isEmpty()) return emptyList()
        
        // 使用批量查询而非循环单独查询
        val songs = songRepository.getSongsByMediaStoreIds(ids)
        
        // 按历史顺序返回结果
        val songMap = songs.associateBy { it.mediaStoreId }
        return ids.mapNotNull { songMap[it] }
    }

    /**
     * Restores the complete Media3 queue. Cloud entries cannot be recreated from
     * [ISongRepository], because their stable ids are intentionally non-numeric.
     * Numeric entries are still refreshed from the local database so deleted or
     * changed local files are handled exactly as before.
     */
    @WorkerThread
    @OptIn(UnstableApi::class)
    suspend fun getHistoryMediaItems(): List<MediaItem> {
        val savedItems = decodeHistoryItems()
        if (savedItems.isEmpty()) {
            return getHistoryItems().map { it.toMediaItem() }
        }

        val localIds = savedItems.mapNotNull { it.mediaId.toLongOrNull() }
        val localSongs =
            if (localIds.isEmpty()) {
                emptyMap()
            } else {
                songRepository
                    .getSongsByMediaStoreIds(localIds)
                    .associateBy { it.mediaStoreId }
            }

        return savedItems.mapNotNull { item ->
            val localId = item.mediaId.toLongOrNull()
            if (localId == null) {
                item
            } else {
                localSongs[localId]?.toMediaItem()
            }
        }
    }

    /**
     * Returns the last serialized current item without opening Room or connecting MediaBrowser.
     *
     * This is a first-frame snapshot only. [getHistoryMediaItems] still refreshes local entries
     * from Room before the player restores its authoritative queue.
     */
    @OptIn(UnstableApi::class)
    fun getCachedCurrentMediaItem(): MediaItem? {
        val items = decodeHistoryItems()
        if (items.isEmpty()) return null
        val currentMediaId = getCurrentMediaId()
        return items.firstOrNull { it.mediaId == currentMediaId }
            ?: items.getOrNull(getHistoryPosition().coerceIn(items.indices))
    }

    /**
     * Stores local and cloud queue entries, including their display metadata.
     * MediaItem's bundle format keeps this independent from individual cloud
     * providers while the service refreshes process-local stream URLs on restore.
     */
    @OptIn(UnstableApi::class)
    fun setHistoryMediaItems(items: List<MediaItem>) {
        val encoded =
            runCatching {
                val parcel = Parcel.obtain()
                try {
                    parcel.writeInt(items.size)
                    items.forEach { parcel.writeBundle(it.toBundleIncludeLocalConfiguration()) }
                    Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
                } finally {
                    parcel.recycle()
                }
            }.getOrNull() ?: return

        if (sharedPreferences.getString(KEY_HISTORY_ITEMS, null) == encoded) return
        sharedPreferences.edit { putString(KEY_HISTORY_ITEMS, encoded) }
    }

    @OptIn(UnstableApi::class)
    private fun decodeHistoryItems(): List<MediaItem> {
        val encoded = sharedPreferences.getString(KEY_HISTORY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            try {
                parcel.unmarshall(bytes, 0, bytes.size)
                parcel.setDataPosition(0)
                val size = parcel.readInt()
                if (size !in 0..MAX_RESTORED_ITEMS) return@runCatching emptyList()
                buildList(size) {
                    repeat(size) {
                        val bundle = parcel.readBundle(Bundle::class.java.classLoader)
                        if (bundle != null) {
                            add(MediaItem.fromBundle(bundle))
                        }
                    }
                }
            } finally {
                parcel.recycle()
            }
        }.getOrElse {
            sharedPreferences.edit { remove(KEY_HISTORY_ITEMS) }
            emptyList()
        }
    }

    /**
     * 获取历史播放的id
     */
    fun getHistoryIds(): List<Long> {
        val ids = sharedPreferences.getString(KEY_HISTORY_IDS, "")
        return ids?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }

    /**
     * 播放到第一个
     */
    fun setHistoryPosition(position: Int) {
        sharedPreferences.edit { putInt(KEY_HISTORY_POSITION, position) }
    }

    /**
     * 设置播放的到第几个的index到缓存
     */
    fun getHistoryPosition(): Int = sharedPreferences.getInt(KEY_HISTORY_POSITION, 0)

    /**
     * Persist the actual media id as well as its queue position. The id is the
     * authoritative restore key because the queue can be filtered or reordered
     * while the app process is not running.
     */
    fun setCurrentMediaId(mediaId: String?) {
        sharedPreferences
            .edit()
            .apply {
                if (mediaId == null) {
                    remove(KEY_CURRENT_MEDIA_ID)
                } else {
                    putString(KEY_CURRENT_MEDIA_ID, mediaId)
                }
            }.commit()
    }

    fun getCurrentMediaId(): String? = sharedPreferences.getString(KEY_CURRENT_MEDIA_ID, null)

    /**
     * 播放模式
     */
    fun setPlayMode(mode: String) {
        sharedPreferences.edit { putString(KEY_PLAY_MODE, mode) }
    }

    /**
     * 获取播放模式
     */
    fun getPlayMode(): String = sharedPreferences.getString(KEY_PLAY_MODE, null) ?: PlayMode.LOOP.name

    fun getPlayModeFlow(): Flow<PlayMode> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == KEY_PLAY_MODE) {
                        trySend(parsePlayMode(getPlayMode()))
                    }
                }
            if (sharedPreferences.contains(KEY_PLAY_MODE)) {
                trySend(parsePlayMode(getPlayMode()))
            }
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.buffer(Channel.CONFLATED)  // 使用 CONFLATED 替代 UNLIMITED，只保留最新值

    private fun parsePlayMode(mode: String): PlayMode =
        when (mode) {
            "LOOP" -> PlayMode.LOOP
            "SHUFFLE" -> PlayMode.SHUFFLE
            else -> PlayMode.LIST
        }
}
