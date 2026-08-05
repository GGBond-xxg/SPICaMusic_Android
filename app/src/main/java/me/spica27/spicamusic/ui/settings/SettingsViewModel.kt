package me.spica27.spicamusic.ui.settings

import android.app.Application
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.spica27.spicamusic.common.entity.DynamicCoverType
import me.spica27.spicamusic.common.entity.DynamicSpectrumBackground
import me.spica27.spicamusic.common.entity.ProgressBarStyle
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.common.entity.ThemeMode
import me.spica27.spicamusic.feature.settings.domain.SettingsUseCases
import me.spica27.spicamusic.service.PlaybackAudioCapabilities
import me.spica27.spicamusic.topdisplay.TopDisplayMode
import me.spica27.spicamusic.topdisplay.TopDisplayModeController

/**
 * 设置页面 ViewModel
 */
@Stable
class SettingsViewModel(
    private val app: Application,
    private val settingsUseCases: SettingsUseCases,
    private val topDisplayModeController: TopDisplayModeController,
) : ViewModel() {
    // 主题模式；旧版本只有 DARK_MODE 布尔值，首次读取时自动兼容。
    val themeMode =
        combine(
            settingsUseCases.getString(SettingsUseCases.Keys.THEME_MODE, ""),
            settingsUseCases.getBoolean(SettingsUseCases.Keys.DARK_MODE, false),
        ) { savedMode, legacyDarkMode ->
            if (savedMode.isBlank()) {
                if (legacyDarkMode) ThemeMode.DARK.value else ThemeMode.SYSTEM.value
            } else {
                ThemeMode.fromString(savedMode).value
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM.value)

    fun setThemeMode(value: String) {
        viewModelScope.launch {
            val mode = ThemeMode.fromString(value)
            settingsUseCases.setString(SettingsUseCases.Keys.THEME_MODE, mode.value)
            // 保留旧键，便于旧组件或降级安装继续读取。
            settingsUseCases.setBoolean(SettingsUseCases.Keys.DARK_MODE, mode == ThemeMode.DARK)
        }
    }

    // 主题色风格
    val themeColorStyle =
        settingsUseCases
            .getString(
                SettingsUseCases.Keys.THEME_COLOR_STYLE,
                ThemeColorStyle.Textured.value,
            ).stateIn(viewModelScope, SharingStarted.Eagerly, ThemeColorStyle.Textured.value)

    fun setThemeColorStyle(value: String) {
        viewModelScope.launch {
            settingsUseCases.setString(SettingsUseCases.Keys.THEME_COLOR_STYLE, value)
        }
    }

    // 屏幕常亮
    val keepScreenOn =
        settingsUseCases
            .getBoolean(SettingsUseCases.Keys.KEEP_SCREEN_ON, false)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setBoolean(SettingsUseCases.Keys.KEEP_SCREEN_ON, enabled)
        }
    }

    val backgroundPlayback =
        settingsUseCases
            .getBoolean(SettingsUseCases.Keys.BACKGROUND_PLAYBACK, true)
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setBoolean(SettingsUseCases.Keys.BACKGROUND_PLAYBACK, enabled)
        }
    }

    val resumeOnHeadset =
        settingsUseCases
            .getBoolean(SettingsUseCases.Keys.RESUME_ON_HEADSET, false)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setResumeOnHeadset(enabled: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setBoolean(SettingsUseCases.Keys.RESUME_ON_HEADSET, enabled)
        }
    }

    val fadeEnabled =
        settingsUseCases
            .getBoolean(SettingsUseCases.Keys.FADE_ENABLED, false)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setFadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsUseCases.setBoolean(SettingsUseCases.Keys.FADE_ENABLED, enabled)
        }
    }

    val fadeDurationMs =
        settingsUseCases
            .getFloat(SettingsUseCases.Keys.FADE_DURATION_MS, 4_000f)
            .stateIn(viewModelScope, SharingStarted.Eagerly, 4_000f)

    fun setFadeDuration(value: String) {
        val duration = value.toFloatOrNull()?.coerceIn(1_000f, 8_000f) ?: return
        viewModelScope.launch {
            settingsUseCases.setFloat(SettingsUseCases.Keys.FADE_DURATION_MS, duration)
        }
    }

    val hiFiMode =
        settingsUseCases
            .getBoolean(SettingsUseCases.Keys.HIFI_MODE, false)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hiFiSupported: Boolean = PlaybackAudioCapabilities.supportsFloatOutput()

    fun setHiFiMode(enabled: Boolean) {
        if (enabled && !hiFiSupported) return
        viewModelScope.launch {
            settingsUseCases.setBoolean(SettingsUseCases.Keys.HIFI_MODE, enabled)
        }
    }

    val usbDacOutput =
        settingsUseCases
            .getBoolean(SettingsUseCases.Keys.USB_DAC_OUTPUT, false)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _usbDeviceName =
        MutableStateFlow(
            PlaybackAudioCapabilities.displayName(
                PlaybackAudioCapabilities.usbOutput(app),
            ),
        )
    val usbDeviceName = _usbDeviceName.asStateFlow()
    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                updateUsbDevice()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                updateUsbDevice()
            }
        }

    init {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    fun setUsbDacOutput(enabled: Boolean) {
        if (enabled && _usbDeviceName.value == null) return
        viewModelScope.launch {
            settingsUseCases.setBoolean(SettingsUseCases.Keys.USB_DAC_OUTPUT, enabled)
        }
    }

    private fun updateUsbDevice() {
        _usbDeviceName.value =
            PlaybackAudioCapabilities.displayName(
                PlaybackAudioCapabilities.usbOutput(app),
            )
    }

    override fun onCleared() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        super.onCleared()
    }

    val topDisplayMode =
        combine(
            settingsUseCases.getString(SettingsUseCases.Keys.TOP_DISPLAY_MODE, ""),
            settingsUseCases.getBoolean(SettingsUseCases.Keys.LYRICON_ENABLED, true),
        ) { savedMode, legacyLyriconEnabled ->
            if (savedMode.isBlank()) {
                if (legacyLyriconEnabled) {
                    TopDisplayMode.STATUS_LYRIC.value
                } else {
                    TopDisplayMode.OFF.value
                }
            } else {
                TopDisplayMode.fromString(savedMode).value
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            TopDisplayMode.STATUS_LYRIC.value,
        )

    val liveUpdateSupported: Boolean = topDisplayModeController.isLiveUpdateSupported()
    val promotedNotificationAllowed: Boolean = topDisplayModeController.canPostPromotedNotification()

    fun setTopDisplayMode(value: String) {
        viewModelScope.launch {
            topDisplayModeController.applyMode(TopDisplayMode.fromString(value))
        }
    }

    // 动态频谱
    val dynamicSpectrumBackground =
        settingsUseCases
            .getString(
                SettingsUseCases.Keys.DYNAMIC_SPECTRUM_BACKGROUND,
                DynamicSpectrumBackground.FluidWarp.value,
            ).stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                DynamicSpectrumBackground.FluidWarp.value,
            )

    fun setDynamicSpectrumBackground(value: String) {
        viewModelScope.launch {
            settingsUseCases.setString(SettingsUseCases.Keys.DYNAMIC_SPECTRUM_BACKGROUND, value)
        }
    }

    // 动态封面
    val dynamicCoverType =
        settingsUseCases
            .getString(
                SettingsUseCases.Keys.DYNAMIC_COVER_TYPE,
                DynamicCoverType.ShiningStars.value,
            ).stateIn(viewModelScope, SharingStarted.Eagerly, DynamicCoverType.ShiningStars.value)

    fun setDynamicCoverType(value: String) {
        viewModelScope.launch {
            settingsUseCases.setString(SettingsUseCases.Keys.DYNAMIC_COVER_TYPE, value)
        }
    }

    // 进度条样式
    val progressBarStyle =
        settingsUseCases
            .getString(
                SettingsUseCases.Keys.PROGRESS_BAR_STYLE,
                ProgressBarStyle.ExpressiveWavy.value,
            ).stateIn(viewModelScope, SharingStarted.Eagerly, ProgressBarStyle.ExpressiveWavy.value)

    fun setProgressBarStyle(value: String) {
        viewModelScope.launch {
            settingsUseCases.setString(SettingsUseCases.Keys.PROGRESS_BAR_STYLE, value)
        }
    }
}
