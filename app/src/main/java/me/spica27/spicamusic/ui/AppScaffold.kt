package me.spica27.spicamusic.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.navkit.stack.NavigationStack
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.common.entity.ThemeMode
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.ui.home.HomeScene
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.player.PlayerViewModel
import me.spica27.spicamusic.ui.theme.CircularRevealThemeHost
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

/**
 * 应用主框架
 * PlayerViewModel 在此处创建，作为 Activity 级别的单例共享
 */
@Composable
fun AppScaffold() {
    val preferencesManager = koinInject<PreferencesManager>()
    val initialThemeMode =
        remember(preferencesManager) {
            preferencesManager.getInitialThemeMode()
        }

    val savedThemeMode by
        preferencesManager
            .getString(PreferencesManager.Keys.THEME_MODE, "")
            .collectAsStateWithLifecycle(initialThemeMode)
    val legacyDarkMode by
        preferencesManager
            .getBoolean(PreferencesManager.Keys.DARK_MODE)
            .collectAsStateWithLifecycle(false)
    val systemDarkMode = isSystemInDarkTheme()
    val themeMode =
        if (savedThemeMode.isBlank()) {
            if (legacyDarkMode) ThemeMode.DARK else ThemeMode.SYSTEM
        } else {
            ThemeMode.fromString(savedThemeMode)
        }
    val isDarkMode = themeMode.resolve(systemDarkMode)

    val themeColorStyleValue by
        preferencesManager
            .getString(PreferencesManager.Keys.THEME_COLOR_STYLE, ThemeColorStyle.Textured.value)
            .collectAsStateWithLifecycle(ThemeColorStyle.Textured.value)
    val initialCircularRevealEnabled =
        remember(preferencesManager) {
            preferencesManager.getCachedBoolean(
                PreferencesManager.Keys.CIRCULAR_REVEAL_ENABLED,
                true,
            )
        }
    val circularRevealEnabled by
        preferencesManager
            .getBoolean(
                PreferencesManager.Keys.CIRCULAR_REVEAL_ENABLED,
                true,
            ).collectAsStateWithLifecycle(initialCircularRevealEnabled)

    val playerViewModel: PlayerViewModel = koinActivityViewModel()
    val color by playerViewModel.playerThemeColor.collectAsStateWithLifecycle()
    val keepScreenOn by
        preferencesManager
            .getBoolean(PreferencesManager.Keys.KEEP_SCREEN_ON)
            .collectAsStateWithLifecycle(false)
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    KeepScreenOnEffect(enabled = keepScreenOn && isPlaying)

    CircularRevealThemeHost(
        enabled = circularRevealEnabled,
        targetDarkTheme = isDarkMode,
        // Artwork palette updates must not pass through the reveal host's asynchronous
        // displayed-state hand-off. Doing so briefly leaves the navigation content without a
        // rendered frame while Media3 advances. The host only owns light/dark changes; album
        // colors flow directly into Material Kolor just as they do in the upstream project.
        targetThemeColor = Color.Unspecified,
    ) { revealedDarkTheme, _ ->
        AppThemeContent(
            darkTheme = revealedDarkTheme,
            themeColor = color,
            themeColorStyle = ThemeColorStyle.fromString(themeColorStyleValue),
            playerViewModel = playerViewModel,
        )
    }
}

@Composable
private fun AppThemeContent(
    darkTheme: Boolean,
    themeColor: Color,
    themeColorStyle: ThemeColorStyle,
    playerViewModel: PlayerViewModel,
) {
    val themedView = LocalView.current
    LaunchedEffect(darkTheme) {
        val window = (themedView.context as Activity).window
        WindowCompat.getInsetsController(window, themedView).isAppearanceLightStatusBars = !darkTheme
    }
    SPICaMusicTheme(
        darkTheme = darkTheme,
        themeColor = themeColor,
        themeColorStyle = themeColorStyle,
        // Album artwork changes are animated by the player's retained backdrop. Animating the
        // entire Material color scheme at the same time invalidates the full navigation subtree
        // and can expose the sheet's plain surface for one frame during Media3 hand-off.
        animateColors = false,
    ) {
        CompositionLocalProvider(LocalPlayerViewModel provides playerViewModel) {
            NavigationStack(
                initialScene = { HomeScene() },
                content = {},
            )
        }
    }
}

@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current

    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled

        onDispose {
            view.keepScreenOn = previous
        }
    }
}
