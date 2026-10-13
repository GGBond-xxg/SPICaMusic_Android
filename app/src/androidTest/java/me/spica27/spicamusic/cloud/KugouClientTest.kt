package me.spica27.spicamusic.cloud

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KugouClientTest {
    private val hash = "0123456789abcdef0123456789abcdef"
    private val account = RemoteMusicAccount("test", RemoteMusicProvider.KUGOU, "Test", secret = "private-token", userId = "12345678901")

    @Test fun searchAndPlaylistMetadataUseConsistentIdsAndDuration() {
        val search =
            parseKugouSongs(
                JSONArray().put(
                    JSONObject()
                        .put("FileHash", hash.uppercase())
                        .put("MixSongID", "42")
                        .put("SongName", "<em>Song</em>")
                        .put("SingerName", "Artist")
                        .put("Duration", 180)
                        .put("Image", "https://imge.kugou.com/{size}/cover.jpg"),
                ),
            ).single()
        val playlist =
            parseKugouSongs(
                JSONArray().put(
                    JSONObject()
                        .put("hash", hash)
                        .put("album_audio_id", 42)
                        .put("filename", "Artist - Song")
                        .put("timelen", 180000),
                ),
            ).single()
        assertEquals(search.id, playlist.id)
        assertEquals(180000L, search.durationMs)
        assertEquals(search.durationMs, playlist.durationMs)
        assertEquals("Song", search.title)
        assertEquals("https://imge.kugou.com/400/cover.jpg", search.artworkUrl)
        assertTrue(parseKugouSongs(JSONArray().put(JSONObject().put("hash", "bad/hash"))).isEmpty())
    }

    @Test fun loginPreservesLargeUserIdsAndRejectsMissingTokens() {
        assertEquals("12345678901", kugouAccount(JSONObject().put("userid", 12345678901L).put("token", "test")).userId)
        assertThrows(IllegalStateException::class.java) { kugouAccount(JSONObject().put("userid", 123)) }
        assertThrows(IllegalStateException::class.java) { kugouAccount(JSONObject().put("userid", 0).put("token", "test")) }
    }

    @Test fun streamNeverAcceptsInvalidUrlsAndRetainsPreviewFlag() {
        assertThrows(IllegalStateException::class.java) { parseKugouStream(JSONObject().put("url", "file:///private")) }
        assertThrows(IllegalStateException::class.java) { parseKugouStream(JSONObject().put("url", JSONArray())) }
        assertTrue(
            parseKugouStream(JSONObject().put("url", JSONArray().put("https://audio.kugou.com/test.mp3")).put("is_free_part", 1)).isPreview,
        )
        assertTrue(remoteStreamRequestHeaders(account, "https://audio.kugou.com/test.mp3").isEmpty())
    }

    @Test fun playlistPaginationUsesSubscriptionIdAndSignedBody() =
        runBlocking {
            var calls = 0
            val client =
                KugouClient(
                    OkHttpClient
                        .Builder()
                        .addInterceptor { chain ->
                            val request = chain.request()
                            assertEquals("cloudlist.service.kugou.com", request.header("x-router"))
                            val buffer = okio.Buffer()
                            request.body!!.writeTo(buffer)
                            val body = buffer.readUtf8()
                            val json = JSONObject(body)
                            assertEquals("999", json.getString("listid"))
                            assertEquals("private-token", json.getString("token"))
                            assertEquals(++calls, json.getInt("page"))
                            val params =
                                request.url.queryParameterNames
                                    .filterNot { it == "signature" }
                                    .associateWith { request.url.queryParameter(it)!! }
                            assertEquals(signKugouRequest(params, body), request.url.queryParameter("signature"))
                            val rows = JSONArray()
                            repeat(if (calls == 1) 100 else 1) { i ->
                                rows.put(
                                    JSONObject()
                                        .put("hash", hash)
                                        .put("album_audio_id", (calls - 1) * 100 + i)
                                        .put("filename", "Song"),
                                )
                            }
                            Response
                                .Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(
                                    JSONObject()
                                        .put("status", 1)
                                        .put("data", JSONObject().put("info", rows))
                                        .toString()
                                        .toResponseBody("application/json".toMediaType()),
                                ).build()
                        }.build(),
                )
            assertEquals(101, client.listPlaylistSongs(account, "999").size)
            assertEquals(2, calls)
        }

    @Test fun liveQrAndPublicSearch() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("kugouNetwork") == "true")
            val client = KugouClient(OkHttpClient())
            val key = client.createQr()
            assertTrue(key.isNotBlank())
            assertEquals(1, client.checkQr(key).status)
            val guest = account.copy(secret = "", userId = "0")
            val result = client.listSongs(guest, "钢琴", 0, 10)
            assertTrue("Public music search should return songs", result.songs.isNotEmpty())
            assertTrue(result.songs.first().durationMs > 0)
            assertEquals(10, result.nextOffset)
            val next = client.listSongs(guest, "钢琴", 10, 10)
            assertTrue(next.songs.isNotEmpty())
            assertNotEquals(result.songs.first().id, next.songs.first().id)
        }

    @Test fun livePublicAudioStream() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("kugouNetwork") == "true")
            val http = OkHttpClient()
            val client = KugouClient(http)
            val guest = account.copy(secret = "", userId = "0")
            // A sample independently confirmed to return audio to an unauthenticated client.
            val stream = client.resolveStream(guest, "97ed00fdb6b9838b7c1fb439bd75bd92_973408876")
            http
                .newCall(
                    Request
                        .Builder()
                        .url(stream.url)
                        .header("Range", "bytes=0-1023")
                        .build(),
                ).execute()
                .use {
                    assertTrue("Audio CDN HTTP ${it.code}", it.isSuccessful)
                    assertTrue(it.body.byteStream().read() >= 0)
                }
        }
}
