package me.spica27.spicamusic

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.spica27.spicamusic.backup.LibraryBackup
import me.spica27.spicamusic.cloud.CloudAccountStore
import me.spica27.spicamusic.cloud.CloudPlaylistEntryStore
import me.spica27.spicamusic.cloud.CloudUserPlaylistStore
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.offline.OfflineStore
import me.spica27.spicamusic.storage.impl.db.AppDatabase
import me.spica27.spicamusic.storage.impl.entity.PlaylistEntity
import me.spica27.spicamusic.storage.impl.entity.PlaylistSongCrossRefEntity
import me.spica27.spicamusic.storage.impl.entity.SongEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
@UnstableApi
class NewFeaturesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun backupRoundTripKeepsOrderSettingsAndAvoidsDuplicates() =
        runBlocking(Dispatchers.IO) {
            val isolated = TestContext(context)
            val db = Room.inMemoryDatabaseBuilder(isolated, AppDatabase::class.java).build()
            try {
                val preferences = PreferencesManager(isolated)
                val entries = CloudPlaylistEntryStore(isolated)
                val backup =
                    LibraryBackup(isolated, db, preferences, entries, CloudAccountStore(isolated), CloudUserPlaylistStore(isolated))
                val song =
                    SongEntity(
                        mediaStoreId = 77,
                        path = "/Music/test.wav",
                        displayName = "测试歌曲",
                        artist = "Artist",
                        size = 123,
                        like = false,
                        duration = 2000,
                        sort = 0,
                        sortName = "test",
                        mimeType = "audio/wav",
                        albumId = 0,
                        album = "Album",
                        sampleRate = 8000,
                        bitRate = 128000,
                        channels = 1,
                        digit = 16,
                        isIgnore = false,
                        codec = "pcm",
                    )
                db.songDao().insert(listOf(song, song.copy(mediaStoreId = 88, path = "/Music/second.wav", displayName = "Second")))
                val id = db.playlistDao().insertPlaylistAndGetId(PlaylistEntity(playlistName = "测试歌单"))
                db.playlistDao().insertListItems(
                    listOf(PlaylistSongCrossRefEntity(id, 88, sortOrder = 2), PlaylistSongCrossRefEntity(id, 77, sortOrder = 1)),
                )
                preferences.setString(PreferencesManager.Keys.THEME_MODE, "dark")
                val file = File(isolated.filesDir, "backup.json")
                backup.export(Uri.fromFile(file))
                val document = backup.read(Uri.fromFile(file))
                assertEquals(
                    listOf("Second", "测试歌曲"),
                    document.playlists
                        .single()
                        .songs
                        .map { it.title },
                )
                preferences.setString(PreferencesManager.Keys.THEME_MODE, "light")
                val result = backup.restore(document)
                assertEquals(1, result.playlists)
                assertEquals(0, result.missingSongs)
                assertEquals("dark", preferences.getString(PreferencesManager.Keys.THEME_MODE).first())
                assertTrue(backup.restore(document).alreadyRestored)
                assertEquals(2, db.playlistDao().getPlaylistsWithSongs().size)
                assertFalse(file.readText().contains("accessToken"))
                assertFalse(file.readText().contains("secret"))
                // Unknown preferences must never cross the whitelist.
                preferences.restorePortableSettings(emptyMap(), mapOf("arbitrary_secret" to "ignored"))
                assertFalse(preferences.exportPortableSettings().second.containsKey("arbitrary_secret"))
            } finally {
                db.close()
            }
        }

    @Test fun offlineStoreRejectsCanceledFilesAndPersistsCompletedFiles() {
        val isolated = TestContext(context)
        val store = OfflineStore(isolated)
        val item =
            MediaItem
                .Builder()
                .setMediaId(
                    "cloud:test:account:one",
                ).setUri("https://example.invalid/test.wav")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Offline test").build())
                .build()
        val part = store.partial(item.mediaId)
        part.writeBytes(ByteArray(128))
        store.cancel(item.mediaId)
        assertTrue(runCatching { store.complete(item, part) }.isFailure)
        assertNull(store.fileFor(item.mediaId))
        val other = item.buildUpon().setMediaId("cloud:test:account:two").build()
        val otherPart = store.partial(other.mediaId)
        otherPart.writeBytes(ByteArray(256))
        store.complete(other, otherPart)
        assertEquals(256L, OfflineStore(isolated).fileFor(other.mediaId)?.length())
        store.clear()
        assertNull(store.fileFor(other.mediaId))
        assertTrue(store.tracks.value.isEmpty())
    }

    @Test fun foregroundDownloadPlaysAfterServerIsStopped() =
        runBlocking {
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            val store = GlobalContext.get().get<OfflineStore>()
            val id = "cloud:fixture:local:${UUID.randomUUID()}"
            val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
            val audio = wave()
            val worker =
                thread {
                    runCatching {
                        server.accept().use { socket ->
                            val reader = socket.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val output = socket.getOutputStream()
                            output.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${audio.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            output.write(audio)
                            output.flush()
                        }
                    }
                }
            var player: ExoPlayer? = null
            try {
                val proxy = GlobalContext.get().get<me.spica27.spicamusic.cloud.OnlineSourceStreamProxy>()
                val streamUrl =
                    proxy.streamUrl(
                        source = "wy",
                        songInfoJson = "{\"id\":\"$id\"}",
                        fallbackUrl = "http://127.0.0.1:${server.localPort}/test.wav",
                        preferFallback = true,
                    )
                val item =
                    MediaItem
                        .Builder()
                        .setMediaId(
                            id,
                        ).setUri(
                            streamUrl,
                        ).setMimeType(
                            "audio/wav",
                        ).setMediaMetadata(
                            MediaMetadata
                                .Builder()
                                .setTitle("Codex offline smoke test")
                                .setDurationMs(2000)
                                .build(),
                        ).build()
                instrumentation.runOnMainSync { store.enqueue(item) }
                withTimeout(30000) {
                    while (store.fileFor(id) ==
                        null
                    ) {
                        check(store.progress.value[id]?.error != true) { "Download failed" }
                        delay(100)
                    }
                }
                server.close()
                assertArrayEquals(audio, requireNotNull(store.fileFor(id)).readBytes())
                val resolver = GlobalContext.get().get<me.spica27.spicamusic.cloud.CloudPlaybackItemResolver>()
                val resolved = resolver.resolve(item)
                assertEquals("file", resolved.localConfiguration?.uri?.scheme)
                instrumentation.runOnMainSync {
                    player =
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(resolved)
                            prepare()
                        }
                }
                withTimeout(10000) {
                    while (true) {
                        val ready =
                            withContext(Dispatchers.Main) {
                                check(player?.playerError == null)
                                player?.playbackState ==
                                    Player.STATE_READY
                            }
                        if (ready) break
                        delay(100)
                    }
                }
            } finally {
                server.close()
                worker.join(2000)
                instrumentation.runOnMainSync { player?.release() }
                withContext(Dispatchers.IO) { store.remove(id) }
                scenario.close()
            }
        }

    private fun wave(): ByteArray {
        val dataSize = 8000 * 2 * 2
        return ByteBuffer
            .allocate(44 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray())
                putInt(36 + dataSize)
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(8000)
                putInt(16000)
                putShort(2)
                putShort(16)
                put("data".toByteArray())
                putInt(dataSize)
                put(ByteArray(dataSize))
            }.array()
    }

    private class TestContext(
        base: Context,
    ) : ContextWrapper(base) {
        private val prefix = "feature-test-${UUID.randomUUID()}"
        private val root = File(base.cacheDir, prefix).apply { mkdirs() }

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }

        override fun getNoBackupFilesDir(): File = File(root, "no_backup").apply { mkdirs() }

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ) = super.getSharedPreferences("$prefix-$name", mode)
    }
}
