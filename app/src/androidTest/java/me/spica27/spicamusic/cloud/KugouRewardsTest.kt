package me.spica27.spicamusic.cloud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class KugouRewardsTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "kugou_test_${UUID.randomUUID()}_"
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
    private val store = CloudAccountStore(context)
    private val account = RemoteMusicAccount("test", RemoteMusicProvider.KUGOU, "Test", secret = "test-token", userId = "12345678901")
    private val calls = mutableListOf<Request>()
    private val ledger = KugouRewardLedger(context.getSharedPreferences("kugou_rewards", Context.MODE_PRIVATE))

    @After fun cleanup() {
        names.forEach { base.deleteSharedPreferences(it) }
    }

    private fun client(reply: (Request) -> String = { "{\"status\":1,\"error_code\":0}" }): KugouClient =
        KugouClient(
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    synchronized(calls) { calls += request }
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(reply(request).toResponseBody("application/json".toMediaType()))
                        .build()
                }.build(),
        )

    @Test fun concurrentTriggersAndRestartOnlyClaimOncePerUserAndDay() =
        runBlocking {
            store.saveRemoteAccount(account)
            val client = client()
            val rewards = KugouRewards(context, store, client)
            coroutineScope { repeat(5) { launch { rewards.signIn(account.userId, automatic = true) } } }
            assertEquals(2, calls.size)
            assertTrue(
                rewards.states.value
                    .getValue(account.userId)
                    .signedToday,
            )
            KugouRewards(context, CloudAccountStore(context), client).runAutomaticSignIns()
            assertEquals(2, calls.size)
            store.saveRemoteAccount(account.copy(id = "another", userId = "42"))
            rewards.runAutomaticSignIns()
            assertEquals(4, calls.size)
            assertTrue(ledger.shouldRun(account.userId, "2099-01-01"))
        }

    @Test fun partialFailureIsRetainedAndManualRetryOnlyUpgrades() =
        runBlocking {
            store.saveRemoteAccount(account)
            var failUpgrade = true
            val rewards =
                KugouRewards(
                    context,
                    store,
                    client {
                        if (it.url.encodedPath.endsWith("upgrade_vip_reward") &&
                            failUpgrade
                        ) {
                            "{\"status\":0,\"error_code\":123}"
                        } else {
                            "{\"status\":1}"
                        }
                    },
                )
            rewards.signIn(account.userId, automatic = true)
            assertEquals(2, calls.size)
            assertTrue(
                rewards.states.value
                    .getValue(account.userId)
                    .claimedBaseToday,
            )
            assertFalse(
                rewards.states.value
                    .getValue(account.userId)
                    .signedToday,
            )
            rewards.signIn(account.userId, automatic = true)
            assertEquals(2, calls.size)
            failUpgrade = false
            rewards.signIn(account.userId, automatic = false)
            assertEquals(3, calls.size)
            assertTrue(
                calls
                    .last()
                    .url.encodedPath
                    .endsWith("upgrade_vip_reward"),
            )
            assertTrue(
                rewards.states.value
                    .getValue(account.userId)
                    .signedToday,
            )
        }

    @Test fun loggedOutAndDisabledAccountsNeverAutoClaim() =
        runBlocking {
            val rewards = KugouRewards(context, store, client())
            rewards.signIn(account.userId, automatic = true)
            store.saveRemoteAccount(account)
            ledger.put(account.userId, "disabled", "true")
            rewards.runAutomaticSignIns()
            assertTrue(calls.isEmpty())
            ledger.put(account.userId, "disabled", "false")
            store.removeRemoteAccount(account.id)
            rewards.signIn(account.userId, automatic = false)
            assertTrue(calls.isEmpty())
        }

    @Test fun missingSuccessStatusNeverMarksSignInComplete() =
        runBlocking {
            store.saveRemoteAccount(account)
            val rewards = KugouRewards(context, store, client { "{}" })
            rewards.runAutomaticSignIns()
            rewards.runAutomaticSignIns()
            assertEquals(1, calls.size)
            assertFalse(
                rewards.states.value
                    .getValue(account.userId)
                    .signedToday,
            )
            assertFalse(
                rewards.states.value
                    .getValue(account.userId)
                    .claimedBaseToday,
            )
        }

    @Test fun alreadyClaimedResponsesAreIdempotentAndPostSignatureMatchesBody() =
        runBlocking {
            store.saveRemoteAccount(account)
            val rewards =
                KugouRewards(
                    context,
                    store,
                    client {
                        val buffer = okio.Buffer()
                        it.body!!.writeTo(buffer)
                        val body = buffer.readUtf8()
                        val params =
                            it.url.queryParameterNames
                                .filterNot { key ->
                                    key == "signature"
                                }.associateWith { key -> it.url.queryParameter(key)!! }
                        assertEquals(signKugouRequest(params, body), it.url.queryParameter("signature"))
                        val code =
                            if (it.url.encodedPath.endsWith("upgrade_vip_reward")) {
                                assertEquals("", body)
                                20030
                            } else {
                                assertEquals(kugouRewardDay(), JSONObject(body).getString("receive_day"))
                                131001
                            }
                        "{\"status\":0,\"error_code\":$code}"
                    },
                )
            rewards.runAutomaticSignIns()
            assertTrue(
                rewards.states.value
                    .getValue(account.userId)
                    .signedToday,
            )
        }

    @Test fun listeningUsesActualAudioIdAndRequiresLogin() =
        runBlocking {
            val client = client()
            client.claimListeningReward(account, "9876543210")
            val buffer = okio.Buffer()
            calls.single().body!!.writeTo(buffer)
            assertEquals(9876543210L, JSONObject(buffer.readUtf8()).getLong("mixsongid"))
            var rejected = false
            try {
                client.claimListeningReward(account.copy(secret = ""), "9876543210")
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            assertEquals(1, calls.size)
        }
}
