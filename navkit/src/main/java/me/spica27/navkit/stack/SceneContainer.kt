package me.spica27.navkit.stack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import me.spica27.navkit.scene.Scene

/** Optional application styling around a scene; navigation owns its lifecycle and state. */
val LocalSceneContainer = staticCompositionLocalOf<@Composable (Scene, @Composable () -> Unit) -> Unit> {
    { _, content -> content() }
}
