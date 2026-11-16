package me.spica27.spicamusic

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import me.spica27.spicamusic.player.api.PlayerAction
import me.spica27.spicamusic.player.impl.SpicaPlayer
import me.spica27.spicamusic.player.impl.utils.PlayerKVUtils
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class PlaybackRestoreTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun coldRestoreAndLiveReconnectKeepPositionWithoutAutoplayOrQueueReplacement() {
        val isolatedContext =
            object : ContextWrapper(context) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ) = super.getSharedPreferences("restore_test_" + name, mode)
            }
        val preferences = isolatedContext.getSharedPreferences("player", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val storage = PlayerKVUtils(isolatedContext)
        val audio = File(context.cacheDir, "restore-test.wav")
        // 30 seconds of mono PCM silence, decoded by the real Media3 engine.
        val bytes = ByteBuffer.allocate(44 + 8000 * 2 * 30).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(bytes.capacity() - 8).put("WAVEfmt ".toByteArray())
        bytes
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(8000)
            .putInt(16000)
            .putShort(2)
            .putShort(16)
        bytes.put("data".toByteArray()).putInt(bytes.capacity() - 44)
        audio.writeBytes(bytes.array())
        val item =
            MediaItem
                .Builder()
                .setMediaId("restore-test-song")
                .setUri(audio.toURI().toString())
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle("Restored song")
                        .setDurationMs(30_000L)
                        .build(),
                ).build()
        storage.setHistoryMediaItems(listOf(item))
        storage.savePlaybackPosition(item.mediaId, 0, 12_345L)
        var first: SpicaPlayer? = null
        var reconnect: SpicaPlayer? = null
        try {
            instrumentation.runOnMainSync {
                first = SpicaPlayer(context, PlaybackRestoreTestService::class.java, storage)
                first!!.init()
            }
            await { first!!.isInitialized.value && first!!.currentDuration.value == 30_000L }
            instrumentation.runOnMainSync {
                assertFalse(first!!.isPlaying.value)
                assertEquals(item.mediaId, first!!.currentMediaItem.value?.mediaId)
                assertEquals(12_345L, first!!.currentPosition)
                first!!.doAction(PlayerAction.Play)
            }
            await { first!!.isPlaying.value && first!!.currentPosition > 12_500L }
            // Stale disk position must never rewind or pause a still-running service.
            storage.savePlaybackPosition(item.mediaId, 0, 0L)
            var livePosition = 0L
            instrumentation.runOnMainSync {
                livePosition = first!!.currentPosition
                reconnect = SpicaPlayer(context, PlaybackRestoreTestService::class.java, storage)
                reconnect!!.init()
            }
            await { reconnect!!.isInitialized.value }
            instrumentation.runOnMainSync {
                assertTrue(reconnect!!.isPlaying.value)
                assertTrue(reconnect!!.currentPosition >= livePosition)
                assertEquals(item.mediaId, reconnect!!.currentMediaItem.value?.mediaId)
                assertEquals(30_000L, reconnect!!.currentDuration.value)
            }
        } finally {
            instrumentation.runOnMainSync {
                reconnect?.release()
                first?.release()
            }
            context.stopService(Intent(context, PlaybackRestoreTestService::class.java))
            preferences.edit().clear().commit()
            audio.delete()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000L
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var done = false
            instrumentation.runOnMainSync { done = condition() }
            if (done) return
            Thread.sleep(50)
        }
        throw AssertionError("Playback restoration did not settle within 15 seconds")
    }
}
