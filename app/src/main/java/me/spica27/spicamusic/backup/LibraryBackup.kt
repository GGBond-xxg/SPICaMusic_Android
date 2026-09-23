package me.spica27.spicamusic.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.cloud.CloudAccountStore
import me.spica27.spicamusic.cloud.CloudPlaylistEntryStore
import me.spica27.spicamusic.cloud.CloudSongSource
import me.spica27.spicamusic.cloud.CloudUserPlaylistStore
import me.spica27.spicamusic.cloud.StoredCloudPlaylistSong
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.storage.impl.db.AppDatabase
import me.spica27.spicamusic.storage.impl.entity.PlaylistEntity
import me.spica27.spicamusic.storage.impl.entity.PlaylistSongCrossRefEntity
import me.spica27.spicamusic.storage.impl.entity.SongEntity
import java.util.UUID

@Keep
data class BackupSong(
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
)

@Keep
data class BackupPlaylist(
    val name: String,
    val songs: List<BackupSong> = emptyList(),
    val cloud: List<StoredCloudPlaylistSong> = emptyList(),
)

@Keep
data class BackupDocument(
    val format: String,
    val version: Int,
    val id: String,
    val playlists: List<BackupPlaylist>,
    val booleans: Map<String, Boolean>,
    val strings: Map<String, String>,
)

object BackupCodec {
    private val adapter =
        Moshi
            .Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
            .adapter(BackupDocument::class.java)
    const val MAX_BYTES = 16 * 1024 * 1024

    fun encode(document: BackupDocument): String = adapter.toJson(document)

    fun decode(text: String): BackupDocument {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val document = requireNotNull(adapter.fromJson(text))
        require(document.format == "spica-library" && document.version == 1)
        require(runCatching { UUID.fromString(document.id) }.isSuccess)
        require(document.playlists.size <= 2000)
        require(document.playlists.sumOf { it.songs.size + it.cloud.size } <= 100000)
        require(document.playlists.all { it.name.isNotBlank() && it.name.length <= 256 })
        require(document.strings.size <= 100 && document.booleans.size <= 100)
        require(document.strings.values.all { it.length <= 4096 })
        val numericSettings =
            setOf(
                "fade_duration_ms",
                "cloud_audio_cache_mib",
                "lyrics_text_scale",
                "lyrics_active_line_scale",
                "lyrics_line_spacing",
                "reverb_level",
                "reverb_room_size",
                "scan_min_duration_sec",
                "scan_max_duration_sec",
                "scan_min_file_size_kb",
            )
        require(
            document.strings
                .filterKeys { it in numericSettings }
                .values
                .all { it.toDoubleOrNull()?.isFinite() == true },
        )
        document.strings["eq_bands"]?.takeIf { it.isNotBlank() }?.let { bands ->
            require(bands.split(',').all { it.toDoubleOrNull()?.isFinite() == true })
        }
        require(
            document.playlists.all { playlist ->
                playlist.songs.all { it.duration in 0..604800000L && it.path.length <= 4096 && it.title.length <= 4096 } &&
                    playlist.cloud.all {
                        it.stableId.startsWith("cloud:") &&
                            it.itemId.isNotBlank() &&
                            it.payloadType in setOf("remote", "media", "telegram") &&
                            it.durationMs in 0..604800000L
                    }
            },
        )
        return document
    }
}

/** MediaStore IDs belong to a device. Match only a unique file or metadata identity. */
fun matchBackupSong(
    song: BackupSong,
    candidates: List<BackupSong>,
): Int? {
    val exact =
        candidates.indices.filter {
            candidates[it].path == song.path &&
                song.path.isNotBlank() &&
                candidates[it].title == song.title &&
                candidates[it].duration == song.duration
        }
    if (exact.size == 1) return exact.single()
    return candidates.indices
        .filter {
            val other = candidates[it]
            other.title == song.title &&
                other.artist == song.artist &&
                other.album == song.album &&
                kotlin.math.abs(other.duration - song.duration) <= 1000
        }.singleOrNull()
}

class LibraryBackup(
    private val context: Context,
    private val db: AppDatabase,
    private val preferences: PreferencesManager,
    private val cloudEntries: CloudPlaylistEntryStore,
    private val accounts: CloudAccountStore,
    private val userPlaylists: CloudUserPlaylistStore,
) {
    data class RestoreResult(
        val playlists: Int,
        val missingSongs: Int,
        val alreadyRestored: Boolean = false,
    )

    private val history = context.getSharedPreferences("backup_imports", Context.MODE_PRIVATE)
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun export(uri: Uri) =
        withContext(Dispatchers.IO) {
            val local =
                db.withTransaction {
                    db.playlistDao().getPlaylistsWithSongs().map { entry ->
                        val id = requireNotNull(entry.playlist.playlistId)
                        BackupPlaylist(
                            entry.playlist.playlistName,
                            db.playlistDao().getSongsByPlaylistId(id).map {
                                it.portable()
                            },
                            cloudEntries.read(id).map { it.copy(artworkUrl = null) },
                        )
                    }
                }
            val remote =
                accounts.getRemoteAccounts().flatMap { account ->
                    userPlaylists.read(account.provider, account.id).map { playlist ->
                        BackupPlaylist(
                            playlist.name,
                            cloud =
                                playlist.songs.map { song ->
                                    StoredCloudPlaylistSong(
                                        stableId = "cloud:${account.provider.name.lowercase(
                                            java.util.Locale.ROOT,
                                        )}:${account.id}:${song.id}",
                                        source = CloudSongSource.valueOf(account.provider.name),
                                        accountName = account.displayName,
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album,
                                        durationMs = song.durationMs,
                                        artworkUrl = null,
                                        payloadType = "remote",
                                        accountId = account.id,
                                        itemId = song.id,
                                        mimeType = song.mimeType,
                                    )
                                },
                        )
                    }
                }
            val (booleans, strings) = preferences.exportPortableSettings()
            val text =
                BackupCodec.encode(
                    BackupDocument("spica-library", 1, UUID.randomUUID().toString(), local + remote, booleans, strings),
                )
            require(text.toByteArray().size <= BackupCodec.MAX_BYTES)
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
        }

    suspend fun read(uri: Uri): BackupDocument =
        withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= BackupCodec.MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                BackupCodec.decode(bytes.toString(Charsets.UTF_8))
            }
        }

    suspend fun restore(document: BackupDocument): RestoreResult =
        withContext(Dispatchers.IO) {
            mutex.lock()
            try {
                if (history.getBoolean(document.id, false)) return@withContext RestoreResult(0, 0, true)
                val created = mutableListOf<Long>()
                var missing = 0
                try {
                    db.withTransaction {
                        val songs = db.songDao().getAllSync()
                        val portable = songs.map { it.portable() }
                        val byPath = portable.withIndex().groupBy { it.value.path }
                        val byTitle = portable.withIndex().groupBy { it.value.title }
                        document.playlists.forEach { playlist ->
                            val id = db.playlistDao().insertPlaylistAndGetId(PlaylistEntity(playlistName = playlist.name))
                            created.add(id)
                            val matches =
                                playlist.songs
                                    .mapNotNull { song ->
                                        val subset = ((byPath[song.path].orEmpty()) + byTitle[song.title].orEmpty()).distinctBy { it.index }
                                        val match =
                                            matchBackupSong(
                                                song,
                                                subset.map { it.value },
                                            )?.let { songs[subset[it].index].mediaStoreId }
                                        if (match == null) missing++
                                        match
                                    }.distinct()
                            db.playlistDao().insertListItems(
                                matches.mapIndexed { index, mediaId ->
                                    PlaylistSongCrossRefEntity(
                                        id,
                                        mediaId,
                                        sortOrder =
                                            (
                                                matches.size -
                                                    index
                                            ).toLong(),
                                    )
                                },
                            )
                            cloudEntries.restore(id, playlist.cloud.map { reconnect(it) })
                        }
                        preferences.restorePortableSettings(document.booleans, document.strings)
                    }
                } catch (error: Throwable) {
                    created.forEach { cloudEntries.removePlaylist(it) }
                    throw error
                }
                history.edit().putBoolean(document.id, true).commit()
                RestoreResult(created.size, missing)
            } finally {
                mutex.unlock()
            }
        }

    private fun reconnect(song: StoredCloudPlaylistSong): StoredCloudPlaylistSong {
        val remote = accounts.getRemoteAccounts().filter { it.provider.name == song.source.name }
        val media = accounts.getAccounts().filter { it.type.name == song.source.name }
        val id =
            remote.firstOrNull { it.id == song.accountId }?.id
                ?: media.firstOrNull { it.id == song.accountId }?.id
                ?: remote.singleOrNull { it.displayName == song.accountName }?.id
                ?: media.singleOrNull { it.displayName == song.accountName }?.id
                ?: song.accountId
        return if (id != null &&
            id != song.accountId
        ) {
            song.copy(
                accountId = id,
                stableId = "cloud:${song.source.name.lowercase(java.util.Locale.ROOT)}:$id:${song.itemId}",
                artworkUrl = null,
            )
        } else {
            song.copy(artworkUrl = null)
        }
    }

    private fun SongEntity.portable() = BackupSong(path, displayName, artist, album, duration)
}
