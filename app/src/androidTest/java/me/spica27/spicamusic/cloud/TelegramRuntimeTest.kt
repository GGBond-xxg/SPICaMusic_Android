package me.spica27.spicamusic.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** Uses the packaged JNI library; does not send a phone number or change account data. */
@RunWith(AndroidJUnit4::class)
class TelegramRuntimeTest {
    @Test fun initializingWithoutSavedChannelsLoadsNativeClientAndReceivesAuthState() =
        runBlocking {
            val client = GlobalContext.get().get<TelegramClientManager>()
            client.initializeAsync()
            // Concurrent callers must share one client and receive the same observable result.
            repeat(4) { client.initializeAsync() }
            val state =
                withTimeout(10000) {
                    client.runtimeState.first { it == TelegramRuntimeState.Available || it == TelegramRuntimeState.Unavailable }
                }
            assertEquals(TelegramRuntimeState.Available, state)
            assertNotNull(withTimeout(10000) { client.authorizationState.first { it != null } })
        }
}
