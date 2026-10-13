package me.spica27.spicamusic.cloud

import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KugouRewardsUiTest {
    @get:Rule val compose = createComposeRule()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "kugou_ui_test_${UUID.randomUUID()}_"
    private val names = mutableSetOf<String>()
    private val context =
        object : ContextWrapper(base) {
            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ): SharedPreferences {
                names += prefix + name
                return base.getSharedPreferences(prefix + name, mode)
            }
        }
    private val account = RemoteMusicAccount("test", RemoteMusicProvider.KUGOU, "Test", secret = "test-token", userId = "12345678901")
    private val rewards =
        KugouRewards(
            context,
            CloudAccountStore(context),
            KugouClient(
                OkHttpClient
                    .Builder()
                    .addInterceptor {
                        error("UI tests must never request remote rewards")
                    }.build(),
            ),
        )

    @After fun cleanup() {
        names.forEach { base.deleteSharedPreferences(it) }
    }

    @Test fun loggedInCardRequiresListeningAndOffersManualOfficialAdFlow() {
        compose.setContent { MaterialTheme { Surface { KugouRewardsCard(account, rewards) } } }
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_rewards_title)).assertExists()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_listening_claim)).assertIsNotEnabled()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_ads_open)).performClick()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_ads_title)).assertExists()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.cloud_action_cancel)).performClick()
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_ads_title)).assertDoesNotExist()
    }

    @Test fun loggedOutAccountHasNoRewardControls() {
        compose.setContent { MaterialTheme { KugouRewardsCard(account.copy(secret = ""), rewards) } }
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_rewards_title)).assertDoesNotExist()
    }

    @Test fun otherProvidersHaveNoKugouRewardControls() {
        compose.setContent { MaterialTheme { KugouRewardsCard(account.copy(provider = RemoteMusicProvider.NETEASE), rewards) } }
        compose.onNodeWithText(cloudText(me.spica27.spicamusic.R.string.kugou_rewards_title)).assertDoesNotExist()
    }
}
