package me.spica27.spicamusic

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootBackNavigationTest {
    @Test
    fun rootBackFinishesMainActivity() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            val deadline = SystemClock.elapsedRealtime() + 1_000
            while (
                scenario.state != Lifecycle.State.DESTROYED &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(50)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        } finally {
            scenario.close()
        }
    }
}
