package me.spica27.navkit.stack

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.spica27.navkit.path.NavigationPath
import me.spica27.navkit.scene.SceneStage
import me.spica27.navkit.scene.StackScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var path: NavigationPath

    @Test
    fun backWithoutPredictiveAnimationReturnsToSettings() {
        setUpStack()
        val settings = TestScene("Settings")
        push(settings)
        push(TestScene("Audio effects", predictiveBackEnabled = false))

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { path.scenes.size == 2 }
        compose.runOnIdle {
            assertEquals(settings, path.scenes.last())
            assertFalse(compose.activity.isFinishing)
        }

        // The predictive handler must become available again after leaving the opt-out page.
        predictiveBack()
        compose.waitUntil(5_000) { path.scenes.size == 1 }
        assertRootBackUnhandled()
    }

    @Test
    fun canceledBackKeepsOptOutSceneThenCompletedBackReturns() {
        setUpStack()
        val effects = TestScene("Audio effects", predictiveBackEnabled = false)
        push(effects)
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.5f))
            dispatcher.dispatchOnBackCancelled()
        }
        compose.runOnIdle {
            assertEquals(effects, path.scenes.last())
            assertEquals(2, path.scenes.size)
            assertFalse(compose.activity.isFinishing)
        }

        predictiveBack()
        compose.waitUntil(5_000) { path.scenes.size == 1 }
        assertRootBackUnhandled()
    }

    private fun setUpStack() {
        compose.setContent {
            MaterialTheme {
                NavigationStack(initialScene = { TestScene("Home") }) { path = it }
            }
        }
        compose.waitForIdle()
        assertRootBackUnhandled()
    }

    private fun push(scene: StackScene) {
        compose.runOnIdle { path.push(scene) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(scene, path.scenes.last())
            assertEquals(SceneStage.Appeared, scene.stage.value)
            assertTrue(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }

    private fun predictiveBack() {
        compose.runOnIdle {
            val dispatcher = compose.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(0.6f))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }

    private fun assertRootBackUnhandled() {
        compose.runOnIdle {
            assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 100f, progress, BackEventCompat.EDGE_LEFT)

    private class TestScene(
        private val title: String,
        override val predictiveBackEnabled: Boolean = true,
    ) : StackScene() {
        @Composable
        override fun Content() {
            Text(title)
        }
    }
}
