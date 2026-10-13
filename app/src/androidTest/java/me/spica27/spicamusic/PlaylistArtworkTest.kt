package me.spica27.spicamusic

import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.spica27.spicamusic.ui.cache.LocalPlaylistArtworkCache
import me.spica27.spicamusic.ui.cache.PlaylistArtworkCache
import me.spica27.spicamusic.ui.widget.PlaylistCoverView
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PlaylistArtworkTest {
    @get:Rule val compose = createComposeRule()

    private fun image(color: Int) = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.asImageBitmap()

    @Test fun leavingViewportDoesNotCancelOrDuplicateDecode() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val expected = image(android.graphics.Color.RED)
            val cache =
                PlaylistArtworkCache { _, _ ->
                    calls.incrementAndGet()
                    started.complete(Unit)
                    finish.await()
                    expected
                }
            withTimeout(5000) {
                val leaving = launch { cache.load(1, 256) }
                started.await()
                leaving.cancelAndJoin()
                val reentering = async { cache.load(1, 256) }
                finish.complete(Unit)
                assertSame(expected, reentering.await())
                assertSame(expected, cache.peek(1, 256))
                assertEquals(1, calls.get())
            }
        }

    @Test fun preloadedMosaicRemainsVisibleAfterRepeatedItemDisposal() =
        runBlocking {
            val colors =
                listOf(android.graphics.Color.RED, android.graphics.Color.GREEN, android.graphics.Color.BLUE, android.graphics.Color.YELLOW)
            val calls = AtomicInteger()
            val cache =
                PlaylistArtworkCache { id, _ ->
                    calls.incrementAndGet()
                    image(colors[(id - 1).toInt()])
                }
            coroutineScope { (1L..4L).forEach { launch { cache.load(it, 256) } } }
            val visible = mutableStateOf(true)
            compose.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalPlaylistArtworkCache provides cache) {
                        if (visible.value) PlaylistCoverView(listOf(1, 2, 3, 4), Modifier.size(200.dp).testTag("mosaic"))
                    }
                }
            }
            repeat(5) {
                val pixels = compose.onNodeWithTag("mosaic").captureToImage().toPixelMap()
                assertEquals(Color.Red, pixels[pixels.width / 4, pixels.height / 4])
                assertEquals(Color.Green, pixels[pixels.width * 3 / 4, pixels.height / 4])
                assertEquals(Color.Blue, pixels[pixels.width / 4, pixels.height * 3 / 4])
                assertEquals(Color.Yellow, pixels[pixels.width * 3 / 4, pixels.height * 3 / 4])
                compose.runOnIdle { visible.value = false }
                compose.waitForIdle()
                compose.runOnIdle { visible.value = true }
            }
            assertEquals(4, calls.get())
        }

    @Test fun changingAlbumReplacesPixelsAndFailureCanRetry() =
        runBlocking {
            var fail = true
            val red = image(android.graphics.Color.RED)
            val blue = image(android.graphics.Color.BLUE)
            val cache =
                PlaylistArtworkCache { id, _ ->
                    if (id == 1L) {
                        red
                    } else if (fail) {
                        null
                    } else {
                        blue
                    }
                }
            assertSame(red, cache.load(1, 512))
            assertNull(cache.load(2, 512))
            assertNull(cache.peek(2, 512))
            fail = false
            assertSame(blue, cache.load(2, 512))
            assertSame(red, cache.peek(1, 512))
        }

    @Test fun actualDeviceThumbnailsRemainVisibleDuringAnimatedScroll() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("playlistDeviceArtwork") == "true")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val ids = mutableSetOf<Long>()
            context.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext() && ids.size < 30) ids += cursor.getLong(0)
                }
            val cache = PlaylistArtworkCache()
            val available = mutableListOf<Long>()
            withTimeout(30000) {
                for (id in ids) {
                    if (cache.load(id, 256) != null) available += id
                    if (available.size == 4) break
                }
            }
            assumeTrue("Device needs local audio to run this integration test", ids.isNotEmpty())
            assertTrue("Local album covers must decode through the production pipeline", available.isNotEmpty())
            // Small device libraries can reuse an album in the fourth mosaic tile.
            val mosaicIds = List(4) { available[it % available.size] }
            val state = LazyListState()
            lateinit var scope: CoroutineScope
            compose.setContent {
                MaterialTheme {
                    scope = rememberCoroutineScope()
                    CompositionLocalProvider(LocalPlaylistArtworkCache provides cache) {
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                            item { Box(Modifier.height(100.dp)) }
                            item { PlaylistCoverView(mosaicIds, Modifier.size(220.dp).testTag("real_mosaic")) }
                            items(20) { Box(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { scope.launch { state.animateScrollBy(80f, tween(5000)) } }
            compose.mainClock.advanceTimeBy(1000)
            assertTrue("Capture must happen during scrolling", state.isScrollInProgress)
            val captured = compose.onNodeWithTag("real_mosaic").captureToImage()
            val pixels = captured.toPixelMap()
            val colors = mutableSetOf<Color>()
            for (x in 0 until pixels.width step 10) for (y in 0 until pixels.height step 10) colors += pixels[x, y]
            assertTrue("A blank placeholder must not replace the mosaic while scrolling", colors.size > 20)
            File(context.externalCacheDir, "playlist-artwork-scrolling.png").outputStream().use {
                captured.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.mainClock.advanceTimeBy(6000)
            compose.mainClock.autoAdvance = true
        }
}
