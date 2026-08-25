package me.spica27.spicamusic.ui.player

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spcia.lyric_core.entity.SongLyrics
import me.spcia.lyric_core.parser.YrcParser
import me.spica27.spicamusic.common.entity.LyricItem
import me.spica27.spicamusic.common.utils.LrcParser
import me.spica27.spicamusic.feature.lyrics.domain.LyricsUseCases
import me.spica27.spicamusic.feature.player.domain.PlayerUseCases
import me.spica27.spicamusic.player.api.PlayerAction
import me.spica27.spicamusic.topdisplay.TopDisplayModeController
import timber.log.Timber

/**
 * 歌词页面 ViewModel
 * 负责歌词加载（缓存优先）、偏移量持久化、多歌词源管理
 */
@Stable
class LyricsViewModel(
    context: Context,
    private val player: PlayerUseCases,
    private val lyricsUseCases: LyricsUseCases,
    private val topDisplayModeController: TopDisplayModeController,
) : ViewModel() {
    private val embeddedLyricsReader = EmbeddedLyricsReader(context.applicationContext)

    data class UiState(
        val isLoading: Boolean = false,
        val lyrics: List<LyricItem>? = null,
        val errorMessage: String? = null,
        val lyricsOffsetMs: Long = 0L,
        val allLyricSources: List<SongLyrics> = emptyList(),
        val allParsedLyrics: List<List<LyricItem>> = emptyList(),
        val currentSourceIndex: Int = 0,
        val currentLyricsStorageId: Long = 0L,
        val currentMediaId: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private data class LyricsRequest(
        val mediaId: String?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
    )

    init {
        viewModelScope.launch {
            player.currentMediaItem
                .map { mediaItem ->
                    LyricsRequest(
                        mediaId = mediaItem?.mediaId,
                        title = mediaItem?.mediaMetadata?.title?.toString(),
                        artist = mediaItem?.mediaMetadata?.artist?.toString(),
                        album = mediaItem?.mediaMetadata?.albumTitle?.toString(),
                        durationMs = mediaItem?.mediaMetadata?.durationMs ?: 0L,
                    )
                }.distinctUntilChanged()
                .collectLatest { request ->
                    loadLyrics(
                        mediaId = request.mediaId,
                        title = request.title,
                        artist = request.artist,
                        album = request.album,
                        durationMs = request.durationMs,
                    )
                }
        }
        viewModelScope.launch {
            combine(player.currentMediaItem, _uiState) { mediaItem, state ->
                mediaItem to state
            }.collect { (mediaItem, state) ->
                val mediaId = mediaItem?.mediaId ?: return@collect
                if (state.currentMediaId != mediaId) return@collect
                topDisplayModeController.updateLyrics(
                    mediaId = mediaId,
                    lyrics = state.lyrics,
                    durationMs = mediaItem.mediaMetadata.durationMs ?: 0L,
                    offsetMs = state.lyricsOffsetMs,
                )
            }
        }
    }

    private suspend fun loadLyrics(
        mediaId: String?,
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long,
    ) {
        if (mediaId == null) {
            _uiState.value = UiState()
            return
        }

        val mediaStoreId = mediaId.toLongOrNull()?.takeIf { it > 0L } ?: 0L
        val lyricsStorageId = lyricsStorageId(mediaId)

        _uiState.update {
            UiState(
                isLoading = true,
                currentLyricsStorageId = lyricsStorageId,
                currentMediaId = mediaId,
            )
        }

        if (title.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "歌曲信息缺失") }
            return
        }

        try {
            coroutineScope {
                // 本地音频标签优先。网络源仍会在后台加载，供用户手动切换。
                val rawEmbeddedLyrics = embeddedLyricsReader.read(mediaStoreId)
                val parsedEmbeddedLyrics =
                    rawEmbeddedLyrics?.let { lyricsText ->
                        parseEmbeddedLyricsInBackground(lyricsText, durationMs)
                    }
                val embeddedLyricsText =
                    rawEmbeddedLyrics?.takeIf { !parsedEmbeddedLyrics.isNullOrEmpty() }
                val embeddedSource =
                    embeddedLyricsText?.let { lyricsText ->
                        SongLyrics(
                            id = -mediaStoreId.coerceAtLeast(1L),
                            name = LOCAL_LYRICS_NAME,
                            artist = LOCAL_LYRICS_ARTIST,
                            album = listOfNotNull(artist, album).joinToString(" · "),
                            albumArt = "",
                            duration = (durationMs / 1000L).toInt(),
                            lyrics = lyricsText,
                        )
                    }

                val cached =
                    withContext(Dispatchers.IO) {
                        lyricsUseCases.getCachedLyrics(lyricsStorageId)
                    }

                var currentLyrics: List<LyricItem>? = null
                var currentOffset = cached?.delay ?: 0L
                var errorMsg: String? = null

                val preferEmbeddedLyrics =
                    shouldPreferEmbeddedLyrics(
                        cachedSourceName = cached?.lyricSourceName,
                        hasCachedLyrics = !cached?.lyrics.isNullOrBlank(),
                        hasEmbeddedLyrics = embeddedLyricsText != null,
                    )

                if (preferEmbeddedLyrics) {
                    currentLyrics = parsedEmbeddedLyrics
                    Timber.d("使用本地内嵌歌词: mediaId=$mediaStoreId")
                } else if (cached != null && cached.lyrics.isNotBlank()) {
                    Timber.d("使用缓存歌词: storageId=$lyricsStorageId, source=${cached.lyricSourceName}")
                    currentLyrics = parseLyricsInBackground(cached.lyrics)
                    if (currentLyrics.isNullOrEmpty()) {
                        errorMsg = "歌词解析失败"
                        currentLyrics = null
                    }
                }

                // 本地或缓存歌词一旦解析完成就立即交给 UI；在线源只负责稍后补全切换列表。
                if (!currentLyrics.isNullOrEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lyrics = currentLyrics,
                            errorMessage = null,
                            lyricsOffsetMs = currentOffset,
                            allLyricSources = listOfNotNull(embeddedSource),
                            allParsedLyrics = listOfNotNull(parsedEmbeddedLyrics),
                            currentSourceIndex = 0,
                        )
                    }
                }

                val remoteResults =
                    try {
                        withContext(Dispatchers.IO) {
                            lyricsUseCases.searchAllLyrics(title)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.w(error, "在线歌词搜索失败，本地歌词仍可使用")
                        emptyList()
                    }
                val results = listOfNotNull(embeddedSource) + remoteResults
                val parsedAll = parseLyricsSourcesInBackground(results)

                val sourceIndex: Int
                if (cached != null && cached.lyrics.isNotBlank()) {
                    sourceIndex =
                        results
                            .indexOfFirst { source ->
                                "${source.artist} - ${source.name}" == cached.lyricSourceName ||
                                    source.lyrics == cached.lyrics
                            }.coerceAtLeast(0)
                    if (preferEmbeddedLyrics) {
                        currentLyrics = parsedEmbeddedLyrics
                        errorMsg = null
                    }
                } else if (embeddedSource != null) {
                    sourceIndex = 0
                    errorMsg = null
                    withContext(Dispatchers.IO) {
                        lyricsUseCases.saveLyricsSource(
                            mediaStoreId = lyricsStorageId,
                            lyrics = embeddedSource.lyrics,
                            sourceName = "$LOCAL_LYRICS_ARTIST - $LOCAL_LYRICS_NAME",
                            delayMs = currentOffset,
                        )
                    }
                } else {
                    sourceIndex = 0
                    if (results.isEmpty()) {
                        errorMsg = "暂无歌词"
                    } else {
                        currentLyrics = parsedAll.firstOrNull()?.ifEmpty { null }
                        if (currentLyrics == null) errorMsg = "歌词解析失败"
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lyrics = currentLyrics,
                        errorMessage = errorMsg,
                        lyricsOffsetMs = currentOffset,
                        allLyricSources = results,
                        allParsedLyrics = parsedAll,
                        currentSourceIndex = sourceIndex,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch lyrics")
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    // 网络失败但有缓存时保留已加载的歌词
                    errorMessage = if (state.lyrics == null) "加载歌词失败: ${e.message ?: "未知错误"}" else null,
                    allLyricSources = emptyList(),
                    allParsedLyrics = emptyList(),
                )
            }
        }
    }

    /** 更新歌词偏移量并持久化到数据库 */
    fun updateOffset(offsetMs: Long) {
        val lyricsStorageId = _uiState.value.currentLyricsStorageId
        _uiState.update { it.copy(lyricsOffsetMs = offsetMs) }
        if (lyricsStorageId == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = lyricsUseCases.getCachedLyrics(lyricsStorageId)
            if (existing != null) {
                lyricsUseCases.updateDelay(lyricsStorageId, offsetMs)
            }
        }
    }

    /** 选择并应用指定索引的歌词源，同时持久化到数据库 */
    fun selectAndSaveLyricSource(index: Int) {
        val state = _uiState.value
        val source = state.allLyricSources.getOrNull(index) ?: return
        val parsed = state.allParsedLyrics.getOrNull(index)

        _uiState.update {
            it.copy(
                currentSourceIndex = index,
                lyrics = if (!parsed.isNullOrEmpty()) parsed else it.lyrics,
                errorMessage = if (!parsed.isNullOrEmpty()) null else it.errorMessage,
            )
        }

        val lyricsStorageId = state.currentLyricsStorageId
        if (lyricsStorageId == 0L) return

        viewModelScope.launch(Dispatchers.IO) {
            val sourceName = "${source.artist} - ${source.name}"
            lyricsUseCases.saveLyricsSource(lyricsStorageId, source.lyrics, sourceName, state.lyricsOffsetMs)
            Timber.d("已缓存歌词: storageId=$lyricsStorageId, source=$sourceName")
        }
    }

    /** 从指定歌词时间开始播放。 */
    fun seekToAndPlay(posMs: Long) {
        player.doAction(PlayerAction.SeekToAndPlay(posMs))
    }

    /** 获取当前播放位置（毫秒） */
    fun getCurrentPositionMs(): Long = player.currentPosition

    private suspend fun parseLyricsInBackground(lyricsText: String): List<LyricItem>? =
        withContext(Dispatchers.Default) {
            parseLyrics(lyricsText)
        }

    /**
     * 普通 LRC/YRC 按时间轴解析；只有纯文本歌词时，为每行生成均匀时间点，
     * 保证音频标签里的 USLT/UNSYNCEDLYRICS 也能在歌词页阅读。
     */
    private suspend fun parseEmbeddedLyricsInBackground(
        lyricsText: String,
        durationMs: Long,
    ): List<LyricItem>? =
        withContext(Dispatchers.Default) {
            parseLyrics(lyricsText)?.takeIf { it.isNotEmpty() }
                ?: lyricsText
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .filterNot { line -> line.startsWith("[") && line.endsWith("]") }
                    .toList()
                    .takeIf { it.isNotEmpty() }
                    ?.let { lines ->
                        val interval =
                            if (durationMs > 0L) {
                                (durationMs / (lines.size + 1)).coerceAtLeast(1L)
                            } else {
                                4_000L
                            }
                        lines.mapIndexed { index, line ->
                            val time = index * interval
                            LyricItem.NormalLyric(
                                content = line,
                                time = time,
                                key = "embedded-$index-$time",
                            )
                        }
                    }
        }

    private suspend fun parseLyricsSourcesInBackground(results: List<SongLyrics>): List<List<LyricItem>> =
        withContext(Dispatchers.Default) {
            results.map { parseLyrics(it.lyrics).orEmpty() }
        }

    companion object {
        private const val LOCAL_LYRICS_NAME = "本地歌词"
        private const val LOCAL_LYRICS_ARTIST = "内嵌标签"
        private const val LOCAL_LYRICS_SOURCE_NAME = "$LOCAL_LYRICS_ARTIST - $LOCAL_LYRICS_NAME"
        private const val FNV_64_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_64_PRIME = 1099511628211L

        /** 本地歌曲沿用 MediaStore ID；云端/外部歌曲使用稳定的负数键写入同一歌词缓存。 */
        internal fun lyricsStorageId(mediaId: String): Long {
            mediaId.toLongOrNull()?.takeIf { it > 0L }?.let { return it }

            var hash = FNV_64_OFFSET_BASIS
            mediaId.encodeToByteArray().forEach { byte ->
                hash = (hash xor (byte.toLong() and 0xffL)) * FNV_64_PRIME
            }
            return (hash or Long.MIN_VALUE).takeUnless { it == 0L } ?: Long.MIN_VALUE
        }

        internal fun shouldPreferEmbeddedLyrics(
            cachedSourceName: String?,
            hasCachedLyrics: Boolean,
            hasEmbeddedLyrics: Boolean,
        ): Boolean =
            hasEmbeddedLyrics &&
                (!hasCachedLyrics || cachedSourceName == LOCAL_LYRICS_SOURCE_NAME)

        private fun String.isYrcFormat(): Boolean =
            lineSequence().any { line ->
                line.startsWith("[") && line.contains("](")
            }

        /**
         * 解析歌词文本为 LyricItem 列表
         */
        fun parseLyrics(lyricsText: String): List<LyricItem>? {
            if (lyricsText.isBlank()) return null

            return if (lyricsText.isYrcFormat()) {
                try {
                    YrcParser.parseToLyricItems(lyricsText).ifEmpty {
                        LrcParser.parse(lyricsText)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "YRC parse failed, fallback to LRC")
                    LrcParser.parse(lyricsText)
                }
            } else {
                LrcParser.parse(lyricsText)
            }
        }
    }
}
