package me.spica27.spicamusic.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import me.spica27.spicamusic.App
import me.spica27.spicamusic.core.preferences.PreferencesManager
import org.koin.core.context.GlobalContext

/** Only invoked by UI actions, never by playback state updates or automatic track changes. */
internal object PlayerHaptics {
    private var lastClick = Long.MIN_VALUE

    fun click() {
        val preferences = GlobalContext.get().get<PreferencesManager>()
        if (!preferences.getCachedBoolean(PreferencesManager.Keys.PLAYER_HAPTICS_ENABLED, true)) return
        val strength = preferences.getInitialString(PreferencesManager.Keys.PLAYER_HAPTICS_STRENGTH, "medium")
        val now = SystemClock.uptimeMillis()
        // A button can delegate to another clickable component; emit one pulse per action.
        if (lastClick != Long.MIN_VALUE && now - lastClick < 40) return
        lastClick = now
        vibrate(strength)
    }

    fun vibrate(strength: String) {
        val context = App.getInstance()
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } ?: return
        if (!vibrator.hasVibrator()) return
        val (duration, amplitude) = playerHapticPulse(strength)
        vibrator.vibrate(
            VibrationEffect.createOneShot(duration, if (vibrator.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE),
            AudioAttributes
                .Builder()
                .setUsage(
                    AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                ).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
    }
}

internal fun playerHapticPulse(strength: String): Pair<Long, Int> =
    when (strength) {
        "light" -> 12L to 65
        "strong" -> 35L to 255
        else -> 22L to 150
    }

internal fun playerClick(action: () -> Unit): () -> Unit =
    {
        PlayerHaptics.click()
        action()
    }
