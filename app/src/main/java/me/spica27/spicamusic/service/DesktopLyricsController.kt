package me.spica27.spicamusic.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import me.spica27.spicamusic.R
import me.spica27.spicamusic.common.entity.LyricItem
import me.spica27.spicamusic.common.entity.getSentenceContent

/** Displays the current and next lyric in a draggable system overlay. */
class DesktopLyricsController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private var player: Player? = null
    private var rootView: LinearLayout? = null
    private var currentLyricView: TextView? = null
    private var nextLyricView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lyricsMediaId: String? = null
    private var lyrics: List<LyricItem> = emptyList()
    private var lyricsOffsetMs = 0L

    private val playerListener =
        object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                clearLyrics()
                refreshText()
            }

            override fun onPlaybackStateChanged(playbackState: Int) = refreshText()

            override fun onIsPlayingChanged(isPlaying: Boolean) = refreshText()
        }

    private val refreshRunnable =
        object : Runnable {
            override fun run() {
                if (rootView == null) return
                if (!Settings.canDrawOverlays(appContext)) {
                    hideInternal()
                    return
                }
                refreshTextInternal()
                mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }

    fun start(player: Player) =
        runOnMain {
            if (this.player === player) return@runOnMain
            this.player?.removeListener(playerListener)
            this.player = player
            player.addListener(playerListener)
            refreshTextInternal()
        }

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        runOnMain {
            if (rootView == null) showInternal()
        }
        return true
    }

    fun hide() = runOnMain(::hideInternal)

    fun toggle(): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        runOnMain {
            if (rootView == null) showInternal() else hideInternal()
        }
        return true
    }

    fun updateLyrics(
        mediaId: String,
        lyrics: List<LyricItem>?,
        offsetMs: Long,
    ) = runOnMain {
        lyricsMediaId = mediaId
        this.lyrics = lyrics.orEmpty().sortedBy(LyricItem::time)
        lyricsOffsetMs = offsetMs
        refreshTextInternal()
    }

    fun clearLyrics() =
        runOnMain {
            lyricsMediaId = null
            lyrics = emptyList()
            lyricsOffsetMs = 0L
            refreshTextInternal()
        }

    fun release() =
        runOnMain {
            player?.removeListener(playerListener)
            player = null
            hideInternal()
            lyricsMediaId = null
            lyrics = emptyList()
        }

    private fun showInternal() {
        if (rootView != null || !Settings.canDrawOverlays(appContext)) return
        val views = createOverlayView()
        val params =
            WindowManager
                .LayoutParams(
                    dp(320),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = preferences.getInt(KEY_X, dp(20))
                    y = preferences.getInt(KEY_Y, dp(140))
                }
        rootView = views.root
        currentLyricView = views.current
        nextLyricView = views.next
        layoutParams = params
        runCatching { windowManager.addView(views.root, params) }
            .onFailure {
                rootView = null
                currentLyricView = null
                nextLyricView = null
                layoutParams = null
                return
            }
        refreshTextInternal()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.post(refreshRunnable)
    }

    private fun hideInternal() {
        mainHandler.removeCallbacks(refreshRunnable)
        rootView?.let { view -> runCatching { windowManager.removeView(view) } }
        rootView = null
        currentLyricView = null
        nextLyricView = null
        layoutParams = null
    }

    private fun createOverlayView(): OverlayViews {
        val current =
            TextView(appContext).apply {
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                maxLines = 2
            }
        val next =
            TextView(appContext).apply {
                setTextColor(0xB3FFFFFF.toInt())
                textSize = 13f
                gravity = Gravity.CENTER
                maxLines = 1
            }
        val root =
            LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(12), dp(18), dp(12))
                elevation = dp(8).toFloat()
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(18).toFloat()
                        setColor(0xDB1B1B1F.toInt())
                        setStroke(dp(1), 0x40FFFFFF)
                    }
                addView(
                    current,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    next,
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { topMargin = dp(4) },
                )
                installDragListener(this)
            }
        return OverlayViews(root, current, next)
    }

    @Suppress("ClickableViewAccessibility")
    private fun installDragListener(view: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val metrics = appContext.resources.displayMetrics
                    params.x =
                        (startX + (event.rawX - downRawX).toInt())
                            .coerceIn(0, (metrics.widthPixels - view.width).coerceAtLeast(0))
                    params.y =
                        (startY + (event.rawY - downRawY).toInt())
                            .coerceIn(0, (metrics.heightPixels - view.height).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    preferences.edit {
                        putInt(KEY_X, params.x)
                        putInt(KEY_Y, params.y)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun refreshText() = runOnMain(::refreshTextInternal)

    private fun refreshTextInternal() {
        val activePlayer = player
        val item = activePlayer?.currentMediaItem
        val currentIndex =
            if (item != null && lyricsMediaId == stableId(item) && lyrics.isNotEmpty()) {
                val position = activePlayer.currentPosition.coerceAtLeast(0L) - lyricsOffsetMs
                lyrics.indexOfLast { lyric -> lyric.time <= position }
            } else {
                -1
            }
        if (currentIndex >= 0) {
            currentLyricView?.text = lyrics[currentIndex].displayText()
            nextLyricView?.text = lyrics.getOrNull(currentIndex + 1)?.displayText().orEmpty()
            return
        }

        currentLyricView?.text = appContext.getString(R.string.desktop_lyrics_no_lyrics)
        nextLyricView?.text =
            listOfNotNull(
                item
                    ?.mediaMetadata
                    ?.title
                    ?.toString()
                    ?.takeIf(String::isNotBlank),
                item
                    ?.mediaMetadata
                    ?.artist
                    ?.toString()
                    ?.takeIf(String::isNotBlank),
            ).joinToString(" · ")
    }

    private fun LyricItem.displayText(): String =
        when (this) {
            is LyricItem.NormalLyric -> content
            is LyricItem.WordsLyric -> getSentenceContent()
        }.ifBlank { "…" }

    private fun stableId(item: MediaItem): String =
        item.mediaId.takeIf(String::isNotBlank)
            ?: item.localConfiguration?.uri?.toString()
            ?: listOf(
                item.mediaMetadata.title,
                item.mediaMetadata.artist,
                item.mediaMetadata.durationMs,
            ).joinToString("|")

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()

    private data class OverlayViews(
        val root: LinearLayout,
        val current: TextView,
        val next: TextView,
    )

    private companion object {
        const val PREFERENCES_NAME = "desktop_lyrics_overlay"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val REFRESH_INTERVAL_MS = 200L
    }
}
