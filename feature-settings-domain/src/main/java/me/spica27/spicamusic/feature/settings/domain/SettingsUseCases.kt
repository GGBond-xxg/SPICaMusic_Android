package me.spica27.spicamusic.feature.settings.domain

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import me.spica27.spicamusic.core.preferences.PreferencesManager

class SettingsUseCases(
    private val preferencesManager: PreferencesManager,
) {
    object Keys {
        val DARK_MODE = PreferencesManager.Keys.DARK_MODE
        val THEME_MODE = PreferencesManager.Keys.THEME_MODE
        val THEME_COLOR_STYLE = PreferencesManager.Keys.THEME_COLOR_STYLE
        val KEEP_SCREEN_ON = PreferencesManager.Keys.KEEP_SCREEN_ON
        val BACKGROUND_PLAYBACK = PreferencesManager.Keys.BACKGROUND_PLAYBACK
        val RESUME_ON_HEADSET = PreferencesManager.Keys.RESUME_ON_HEADSET
        val FADE_ENABLED = PreferencesManager.Keys.FADE_ENABLED
        val FADE_DURATION_MS = PreferencesManager.Keys.FADE_DURATION_MS
        val HIFI_MODE = PreferencesManager.Keys.HIFI_MODE
        val USB_DAC_OUTPUT = PreferencesManager.Keys.USB_DAC_OUTPUT
        val LYRICON_ENABLED = PreferencesManager.Keys.LYRICON_ENABLED
        val TOP_DISPLAY_MODE = PreferencesManager.Keys.TOP_DISPLAY_MODE
        val DYNAMIC_SPECTRUM_BACKGROUND = PreferencesManager.Keys.DYNAMIC_SPECTRUM_BACKGROUND
        val DYNAMIC_COVER_TYPE = PreferencesManager.Keys.DYNAMIC_COVER_TYPE
        val PROGRESS_BAR_STYLE = PreferencesManager.Keys.PROGRESS_BAR_STYLE
        val EQ_ENABLED = PreferencesManager.Keys.EQ_ENABLED
        val EQ_BANDS = PreferencesManager.Keys.EQ_BANDS
        val REVERB_ENABLED = PreferencesManager.Keys.REVERB_ENABLED
        val REVERB_LEVEL = PreferencesManager.Keys.REVERB_LEVEL
        val REVERB_ROOM_SIZE = PreferencesManager.Keys.REVERB_ROOM_SIZE
        val SCAN_LAST_COMPLETED_AT = PreferencesManager.Keys.SCAN_LAST_COMPLETED_AT
    }

    fun getBoolean(
        key: Preferences.Key<Boolean>,
        defaultValue: Boolean = false,
    ): Flow<Boolean> = preferencesManager.getBoolean(key, defaultValue)

    suspend fun setBoolean(
        key: Preferences.Key<Boolean>,
        value: Boolean,
    ) {
        preferencesManager.setBoolean(key, value)
    }

    fun getString(
        key: Preferences.Key<String>,
        defaultValue: String = "",
    ): Flow<String> = preferencesManager.getString(key, defaultValue)

    suspend fun setString(
        key: Preferences.Key<String>,
        value: String,
    ) {
        preferencesManager.setString(key, value)
    }

    fun getFloat(
        key: Preferences.Key<String>,
        defaultValue: Float = 0f,
    ): Flow<Float> = preferencesManager.getFloat(key, defaultValue)

    suspend fun setFloat(
        key: Preferences.Key<String>,
        value: Float,
    ) {
        preferencesManager.setFloat(key, value)
    }

    fun getFloatList(
        key: Preferences.Key<String>,
        defaultValue: List<Float> = emptyList(),
    ): Flow<List<Float>> = preferencesManager.getFloatList(key, defaultValue)

    suspend fun setFloatList(
        key: Preferences.Key<String>,
        value: List<Float>,
    ) {
        preferencesManager.setFloatList(key, value)
    }
}
