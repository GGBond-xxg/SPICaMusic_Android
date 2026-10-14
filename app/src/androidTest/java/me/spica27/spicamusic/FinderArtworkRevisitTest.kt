package me.spica27.spicamusic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.storage.api.IPlaylistRepository
import me.spica27.spicamusic.storage.api.ISongRepository
import me.spica27.spicamusic.ui.home.HomePage
import me.spica27.spicamusic.ui.home.HomeViewModel
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.core.context.GlobalContext
import java.io.File

/** Exercises the actual HomeScene and Finder lazy-item modifiers, not just the image widget. */
@RunWith(AndroidJUnit4::class)
class FinderArtworkRevisitTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun coverIsVisibleImmediatelyWhileDraggingAfterTabRevisits() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("playlistDeviceArtwork") == "true")
            val songs =
                GlobalContext
                    .get()
                    .get<ISongRepository>()
                    .getAllSongs()
                    .take(8)
            assumeTrue("Requires scanned local audio", songs.isNotEmpty())
            val playlists = GlobalContext.get().get<IPlaylistRepository>()
            val id = playlists.createPlaylist("Artwork regression ${System.nanoTime()}")
            val preferences = GlobalContext.get().get<PreferencesManager>()
            val originalStyle = preferences.getString(PreferencesManager.Keys.THEME_COLOR_STYLE, "textured").first()
            try {
                if (InstrumentationRegistry.getArguments().getString("glassAppearance") == "true") {
                    preferences.setString(PreferencesManager.Keys.THEME_COLOR_STYLE, "glass")
                }
                playlists.addSongsToPlaylist(id, songs.map { it.mediaStoreId })
                compose.waitUntil(15000) { compose.onAllNodesWithTag("finder_list").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("finder_list").performScrollToKey("playlists_rail")
                compose.waitUntil(
                    10000,
                ) { compose.onAllNodesWithTag("finder_cover_$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                // Populate the real thumbnail cache before testing reentry. The bug was in item alpha.
                Thread.sleep(800)
                assertCoverHasPixels(id, "initial")
                repeat(3) { revisit ->
                    compose.onNodeWithTag("finder_list").performScrollToIndex(0)
                    compose.runOnIdle { compose.activity.getViewModel<HomeViewModel>().navigateToPage(HomePage.Music) }
                    compose.waitForIdle()
                    compose.runOnIdle { compose.activity.getViewModel<HomeViewModel>().navigateToPage(HomePage.Library) }
                    compose.waitForIdle()
                    compose.runOnIdle { compose.activity.getViewModel<HomeViewModel>().navigateToPage(HomePage.Finder) }
                    compose.waitForIdle()
                    compose.mainClock.autoAdvance = false
                    compose.onNodeWithTag("finder_list").performScrollToKey("playlists_rail")
                    compose.mainClock.advanceTimeByFrame()
                    compose.onNodeWithTag("finder_list").performTouchInput {
                        down(center)
                        moveBy(Offset(0f, -8f), delayMillis = 16)
                    }
                    compose.mainClock.advanceTimeByFrame()
                    assertCoverHasPixels(id, "revisit-$revisit-dragging")
                    compose.onNodeWithTag("finder_list").performTouchInput { up() }
                    compose.mainClock.autoAdvance = true
                }
            } finally {
                compose.mainClock.autoAdvance = true
                // Delete only the temporary playlist created by this test, never the user's playlists.
                playlists.deletePlaylist(id)
                preferences.setString(PreferencesManager.Keys.THEME_COLOR_STYLE, originalStyle)
            }
        }

    private fun assertCoverHasPixels(
        id: Long,
        stage: String,
    ) {
        val image = compose.onNodeWithTag("finder_cover_$id", useUnmergedTree = true).captureToImage()
        val pixels = image.toPixelMap()
        val colors = mutableSetOf<Color>()
        for (x in pixels.width / 8 until pixels.width * 7 / 8 step 4) {
            for (y in pixels.height / 8 until pixels.height * 7 / 8 step 4) colors += pixels[x, y]
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.externalCacheDir, "finder-$stage.png").outputStream().use {
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue("$stage: actual Finder playlist cover must be visible before the drag stops", colors.size > 20)
    }
}
