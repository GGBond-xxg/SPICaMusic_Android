package me.spica27.spicamusic

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Dialog windows use Android resources, not a test's overridden LocalContext. */
class EnglishAppLocaleRule : ExternalResource() {
    private var original: LocaleList? = null

    override fun before() {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(LocaleManager::class.java)
            original = manager.applicationLocales
            manager.applicationLocales = LocaleList.forLanguageTags("en")
        }
    }

    override fun after() {
        if (Build.VERSION.SDK_INT >= 33) {
            original?.let {
                InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext
                    .getSystemService(LocaleManager::class.java)
                    .applicationLocales =
                    it
            }
        }
    }
}
