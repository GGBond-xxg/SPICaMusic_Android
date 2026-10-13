package me.spica27.spicamusic.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import me.spica27.spicamusic.App
import me.spica27.spicamusic.core.preferences.PreferencesManager
import org.koin.core.context.GlobalContext
import java.lang.ref.WeakReference

/** Only invoked by UI actions, never by playback state updates or automatic track changes. */
internal object PlayerHaptics {
    private var feedbackView = WeakReference<View>(null)

    fun bind(view: View) {
        feedbackView = WeakReference(view)
    }

    fun unbind(view: View) {
        if (feedbackView.get() === view) feedbackView.clear()
    }

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
        val amount = playerHapticStrength(strength)
        if (amount == 0) return
        val effect =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    vibrator.arePrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK).all { it } ->
                    VibrationEffect
                        .startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, amount / 100f)
                        .compose()
                else -> {
                    val effectId =
                        when {
                            amount <= 33 -> VibrationEffect.EFFECT_TICK
                            amount >= 80 -> VibrationEffect.EFFECT_HEAVY_CLICK
                            else -> VibrationEffect.EFFECT_CLICK
                        }
                    // Let the OEM select its calibrated touch effect when the vibrator cannot play
                    // predefined effects. Never stretch a raw pulse to simulate stronger feedback.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        vibrator.areEffectsSupported(effectId).firstOrNull() == Vibrator.VIBRATION_EFFECT_SUPPORT_NO
                    ) {
                        feedbackView.get()?.performHapticFeedback(
                            if (amount <= 33) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP,
                        )
                        return
                    }
                    VibrationEffect.createPredefined(effectId)
                }
            }
        vibrator.vibrate(
            effect,
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
    }
}

/** Keep the three legacy presets readable after upgrading; numeric values enable fine adjustment. */
internal fun playerHapticStrength(strength: String): Int =
    when (strength) {
        "light" -> 25
        "strong" -> 100
        "medium" -> 60
        else -> strength.toIntOrNull()?.coerceIn(0, 100) ?: 60
    }

internal fun playerClick(action: () -> Unit): () -> Unit =
    {
        PlayerHaptics.click()
        action()
    }
