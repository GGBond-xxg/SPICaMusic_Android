package me.spica27.spicamusic.topdisplay

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.spica27.spicamusic.R
import timber.log.Timber
import kotlin.math.roundToInt

/** Flyme's documented ticker extension, or a permission-gated application overlay on other ROMs. */
class SystemStatusLyricsManager(
    context: Context,
) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("status_lyrics_appearance", Context.MODE_PRIVATE)
    private val windows = app.getSystemService(WindowManager::class.java)
    private val notifications = NotificationManagerCompat.from(app)
    private val power = app.getSystemService(PowerManager::class.java)
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private val nativeFlags: Pair<Int, Int>? = readFlymeFlags()
    private val mutableAppearance = MutableStateFlow(readAppearance())
    val appearance = mutableAppearance.asStateFlow()
    private var textView: TextView? = null
    private var lastLayout: StatusLyricsBounds? = null
    private var lastAppearance: StatusLyricsAppearance? = null
    private var lastNativeKey: List<String>? = null
    private var nativePosted = false
    private var nativeNotification: Notification? = null
    private var channelCreated = false
    private var lastState: MusicLiveUpdateState? = null
    private var nextOverlayAttemptMs = 0L
    private var nextBackendAttemptMs = 0L
    private var adjustmentText: String? = null
    private val adjustmentHandler = Handler(Looper.getMainLooper())
    private val adjustmentTick =
        object : Runnable {
            override fun run() {
                if (adjustmentText == null) return
                refreshDisplay()
                adjustmentHandler.postDelayed(this, 250L)
            }
        }

    init {
        // A killed process may have left this independent notification behind.
        runCatching { notifications.cancel(NOTIFICATION_ID) }
    }

    fun isNativeSupported(): Boolean = nativeFlags != null

    fun canShowOverlay(): Boolean = Settings.canDrawOverlays(app)

    fun updateAppearance(
        value: StatusLyricsAppearance,
        persist: Boolean = true,
    ) {
        val safe = value.sanitized()
        mutableAppearance.value = safe
        if (persist) saveAppearance()
        refreshDisplay()
    }

    fun saveAppearance() {
        val safe = appearance.value
        preferences
            .edit()
            .putInt("x_dp", safe.xDp)
            .putInt("y_dp", safe.yDp)
            .putInt("width_dp", safe.widthDp)
            .putInt("text_sp", safe.textSizeSp)
            .putInt("opacity", safe.opacityPercent)
            .apply()
    }

    fun resetAppearance() = updateAppearance(StatusLyricsAppearance())

    /** The settings page owns this temporary output, independently of playback/service lifetime. */
    fun beginLiveAdjustment(sampleText: String) {
        if (isNativeSupported()) return
        adjustmentText = sampleText
        nextBackendAttemptMs = 0L
        nextOverlayAttemptMs = 0L
        adjustmentHandler.removeCallbacks(adjustmentTick)
        adjustmentTick.run()
    }

    fun endLiveAdjustment() {
        adjustmentText = null
        adjustmentHandler.removeCallbacks(adjustmentTick)
        saveAppearance()
        refreshDisplay()
    }

    /** Called on the playback service's main dispatcher, including after permission/rotation changes. */
    fun showOrUpdate(state: MusicLiveUpdateState) {
        lastState = state
        refreshDisplay()
    }

    private fun refreshDisplay() {
        if (SystemClock.uptimeMillis() < nextBackendAttemptMs) return
        try {
            renderState(lastState)
        } catch (error: Exception) {
            // Optional OEM/overlay services must not terminate the playback service's lyric clock.
            Timber.tag(TAG).w(error, "Status lyric backend unavailable")
            clearOutputs()
            nextBackendAttemptMs = SystemClock.uptimeMillis() + 5_000L
        }
    }

    private fun renderState(state: MusicLiveUpdateState?) {
        val editingText = adjustmentText
        if (editingText != null && !isNativeSupported()) {
            clearNative()
            if (canShowOverlay() && power.isInteractive && !keyguard.isKeyguardLocked) {
                showOverlay(state?.lyric?.takeIf(String::isNotBlank) ?: editingText)
            } else {
                removeOverlay()
            }
            return
        }
        if (state == null || !state.isPlaying) {
            clearOutputs()
            return
        }
        val line = state.lyric?.takeIf(String::isNotBlank) ?: state.title
        if (line.isBlank()) {
            clearOutputs()
            return
        }
        if (nativeFlags != null) {
            removeOverlay()
            showNative(state, line)
        } else {
            clearNative()
            // Do not leave lyrics over the lock screen or sensitive permission screens.
            if (!canShowOverlay() || !power.isInteractive || keyguard.isKeyguardLocked) {
                removeOverlay()
                return
            }
            showOverlay(line)
        }
    }

    fun cancel() {
        lastState = null
        nextOverlayAttemptMs = 0L
        nextBackendAttemptMs = 0L
        // Paused playback must not erase the live positioning sample owned by the visible editor.
        if (adjustmentText != null) refreshDisplay() else clearOutputs()
    }

    fun release() = cancel()

    private fun clearOutputs() {
        removeOverlay()
        clearNative()
    }

    private fun showNative(
        state: MusicLiveUpdateState,
        line: String,
    ) {
        if (!notifications.areNotificationsEnabled()) {
            clearNative()
            return
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        if (!channelCreated) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    app.getString(R.string.system_status_lyrics_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
        channelCreated = true
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) {
            clearNative()
            return
        }
        val key = listOf(state.songId, state.title, state.artist, line)
        if (nativePosted && lastNativeKey == key) return
        runCatching {
            val intent =
                app.packageManager
                    .getLaunchIntentForPackage(app.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val notification =
                NotificationCompat
                    .Builder(app, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_music_note)
                    .setContentTitle(state.title)
                    .setContentText(line)
                    .setSubText(state.artist)
                    .setTicker(line)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .apply {
                        if (intent != null) {
                            setContentIntent(
                                PendingIntent.getActivity(
                                    app,
                                    NOTIFICATION_ID,
                                    intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                ),
                            )
                        }
                    }.build()
                    .apply {
                        val flagsPair = requireNotNull(nativeFlags)
                        flags = flags or flagsPair.first or flagsPair.second or Notification.FLAG_NO_CLEAR
                        extras.putBoolean("ticker_icon_switch", false)
                    }
            notifications.notify(NOTIFICATION_ID, notification)
            nativePosted = true
            nativeNotification = notification
            lastNativeKey = key
        }.onFailure { Timber.tag(TAG).w(it, "Flyme lyric notification could not be posted") }
    }

    private fun clearNative() {
        if (!nativePosted) return
        // Flyme documents clearing the ticker flags before removing its dedicated notification.
        runCatching {
            nativeNotification?.let { notification ->
                notification.tickerText = null
                nativeFlags?.let { notification.flags = notification.flags and (it.first or it.second).inv() }
                notifications.notify(NOTIFICATION_ID, notification)
            }
        }
        runCatching { notifications.cancel(NOTIFICATION_ID) }
        nativePosted = false
        nativeNotification = null
        lastNativeKey = null
    }

    private fun showOverlay(line: String) {
        if (SystemClock.uptimeMillis() < nextOverlayAttemptMs) return
        val style = appearance.value
        val metrics = app.resources.displayMetrics

        fun dp(value: Int) = (value * metrics.density).roundToInt()
        val available =
            if (Build.VERSION.SDK_INT >= 30) {
                val windowMetrics = windows.currentWindowMetrics
                val insets =
                    windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                    )
                (windowMetrics.bounds.width() - insets.left - insets.right) to
                    (windowMetrics.bounds.height() - insets.top - insets.bottom)
            } else {
                metrics.widthPixels to metrics.heightPixels
            }
        val view =
            textView ?: TextView(app).apply {
                gravity = Gravity.CENTER
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }
        // Re-evaluate scaled pixels after a system font-size change, even with unchanged preferences.
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp.toFloat())
        if (lastAppearance != style || textView == null) {
            view.setTextColor(Color.argb(style.opacityPercent * 255 / 100, 255, 255, 255))
        }
        val font = view.paint.fontMetricsInt
        val bounds =
            statusLyricsBounds(
                available.first,
                available.second,
                dp(style.widthDp),
                font.bottom - font.top + dp(8),
                dp(style.xDp),
                dp(style.yDp),
            )
        val params =
            WindowManager
                .LayoutParams(
                    bounds.width,
                    bounds.height,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = bounds.x
                    y = bounds.y
                    // Android 12 blocks pass-through touches above the platform obscuring-opacity limit.
                    alpha =
                        if (Build.VERSION.SDK_INT >= 31) {
                            app.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch.coerceIn(0f, 0.8f)
                        } else {
                            0.8f
                        }
                    setTitle("SPICaMusic status lyrics")
                }
        runCatching {
            if (view.text.toString() != line) view.text = line
            if (textView == null) {
                windows.addView(view, params)
                textView = view
            } else if (lastLayout != bounds || lastAppearance != style) {
                windows.updateViewLayout(view, params)
            }
            lastLayout = bounds
            lastAppearance = style
        }.onFailure {
            Timber.tag(TAG).w(it, "Status lyric overlay could not be displayed")
            removeOverlay()
            nextOverlayAttemptMs = SystemClock.uptimeMillis() + 5_000L
        }
    }

    private fun removeOverlay() {
        textView?.let { runCatching { windows.removeViewImmediate(it) } }
        textView = null
        lastLayout = null
        lastAppearance = null
    }

    private fun readAppearance(): StatusLyricsAppearance =
        runCatching {
            StatusLyricsAppearance(
                preferences.getInt("x_dp", 0),
                preferences.getInt("y_dp", 0),
                preferences.getInt("width_dp", 240),
                preferences.getInt("text_sp", 14),
                preferences.getInt("opacity", 90),
            ).sanitized()
        }.getOrDefault(StatusLyricsAppearance())

    private fun readFlymeFlags(): Pair<Int, Int>? {
        if (!Build.MANUFACTURER.equals("meizu", ignoreCase = true) && !Build.BRAND.equals("meizu", ignoreCase = true)) return null
        return runCatching {
            val show = Notification::class.java.getField("FLAG_ALWAYS_SHOW_TICKER").getInt(null)
            val update = Notification::class.java.getField("FLAG_ONLY_UPDATE_TICKER").getInt(null)
            require(show != 0 && update != 0 && show != update)
            show to update
        }.getOrNull()
    }

    private companion object {
        const val TAG = "SystemStatusLyrics"
        const val CHANNEL_ID = "system_status_lyrics"
        const val NOTIFICATION_ID = 2029
    }
}
