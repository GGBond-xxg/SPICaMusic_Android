package me.spica27.spicamusic.player.api

import androidx.media3.common.MediaItem

/**
 * 播放器操作基类
 */
sealed class PlayerAction {
    /**
     * 播放
     */
    data object Play : PlayerAction()

    /**
     * 暂停
     */
    data object Pause : PlayerAction()

    /**
     * 播放或暂停切换
     */
    data object PlayOrPause : PlayerAction()

    /**
     * 下一曲
     */
    data object SkipToNext : PlayerAction()

    /**
     * 上一曲
     */
    data object SkipToPrevious : PlayerAction()

    /**
     * 根据媒体ID播放
     */
    data class PlayById(val mediaId: String) : PlayerAction()

    /**
     * 跳转到指定位置
     */
    data class SeekTo(val positionMs: Long) : PlayerAction()

    /**
     * 跳转到指定位置并立即开始播放。
     *
     * 歌词行点击需要把跳转和播放作为同一次播放器操作提交，避免播放器暂停时
     * 只更新位置、以及服务重连时两个异步操作发生竞争。
     */
    data class SeekToAndPlay(val positionMs: Long) : PlayerAction()

    /**
     * 播放完成后暂停
     */
    data class PauseWhenCompletion(val cancel: Boolean = false) : PlayerAction()

    /**
     * 设置播放模式
     */
    data class SetPlayMode(val playMode: PlayMode) : PlayerAction()

    /**
     * 添加到下一曲播放
     */
    data class AddToNext(val mediaId: String) : PlayerAction()

    /** 添加已经解析好的云端媒体项到下一首。 */
    data class AddMediaItemToNext(
        val item: MediaItem,
    ) : PlayerAction()

    /**
     * 从播放列表移除
     */
    data class RemoveWithMediaId(val mediaId: String) : PlayerAction()

    /**
     * 添加到队列末尾
     */
    data class AddToQueue(
        val mediaIds: List<String>,
    ): PlayerAction()

    /** 添加已经解析好的云端媒体项到队列末尾。 */
    data class AddMediaItemsToQueue(
        val items: List<MediaItem>,
    ) : PlayerAction()


    /**
     * 更新播放列表
     */
    data class UpdateList(
        val mediaIds: List<String>,
        val mediaId: String? = null,
        val start: Boolean = false,
    ) : PlayerAction()

    /**
     * 播放已经解析好的媒体项。
     *
     * 云端媒体的地址和鉴权信息不是 MediaStore ID，不能再经由本地资料库反查。
     */
    data class PlayMediaItems(
        val items: List<MediaItem>,
        val startIndex: Int = 0,
    ) : PlayerAction()

    /**
     * 从头开始播放
     */
    data object ReloadAndPlay : PlayerAction()

    /** Rebuild the current media source while preserving its queue position and playhead. */
    data class ReloadCurrentMedia(
        val customCacheKey: String,
        val positionMs: Long,
    ) : PlayerAction()
}
