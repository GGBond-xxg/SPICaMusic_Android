package me.spica27.spicamusic

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootBackNavigationTest {
    @Test
    fun mainActivityHandlesAllLocaleConfigurationChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activityInfo =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                0,
            )
        val expected =
            ActivityInfo.CONFIG_LOCALE or
                ActivityInfo.CONFIG_LAYOUT_DIRECTION or
                ActivityInfo.CONFIG_SCREEN_LAYOUT

        assertEquals(expected, activityInfo.configChanges and expected)
    }

    @Test
    fun mainWindowIsOpaqueForSystemBackAnimation() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val attributes =
                    activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
                try {
                    assertFalse(attributes.getBoolean(0, false))
                } finally {
                    attributes.recycle()
                }
            }
        } finally {
            scenario.close()
        }
    }

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
