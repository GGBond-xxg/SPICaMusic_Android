package me.spica27.spicamusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.spica27.spicamusic.cloud.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Explicit opt-in live smoke test; no account credentials or library data are touched. */
@RunWith(AndroidJUnit4::class)
class BilibiliNetworkTest {
    @Test fun publicMusicSearchAndAudioStream() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("bilibiliNetwork") == "true")
            val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
            val client = BilibiliClient(http)
            val account = RemoteMusicAccount("smoke", RemoteMusicProvider.BILIBILI, "Guest", secret = "")
            val ranking = client.listSongs(account, "", 0, 20)
            assertTrue("Music ranking should contain videos", ranking.songs.isNotEmpty())
            val song = ranking.songs.first()
            val detail = client.listSongs(account, song.id, 0, 20)
            assertTrue("Video details should contain playable parts", detail.songs.isNotEmpty())
            val search = client.listSongs(account, "钢琴", 0, 20)
            assertTrue("Search should return results", search.songs.isNotEmpty())
            val url = client.resolveStreamUrl(account, detail.songs.first().id)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Range", "bytes=0-1023")
                    .apply {
                        remoteStreamRequestHeaders(account, url).forEach { (name, value) -> header(name, value) }
                    }.build()
            http.newCall(request).execute().use { response ->
                assertTrue("Audio CDN should accept the stream request: ${response.code}", response.isSuccessful)
                assertTrue("Audio bytes should be available", response.body.byteStream().read() >= 0)
            }
        }
}
