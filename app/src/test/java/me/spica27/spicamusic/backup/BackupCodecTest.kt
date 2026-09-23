package me.spica27.spicamusic.backup

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BackupCodecTest {
    private val song = BackupSong("/Music/track.flac", "Track", "Artist", "Album", 180000)

    private fun document() =
        BackupDocument(
            "spica-library",
            1,
            UUID.randomUUID().toString(),
            listOf(BackupPlaylist("旅行 🎵", listOf(song))),
            mapOf(
                "keep_screen_on" to true,
            ),
            mapOf("theme_mode" to "dark"),
        )

    @Test fun roundTripPreservesUnicodePlaylistsAndSettings() {
        val document = document()
        assertEquals(document, BackupCodec.decode(BackupCodec.encode(document)))
    }

    @Test fun unknownVersionAndInvalidDocumentsAreRejected() {
        listOf(
            "{}",
            "not json",
            BackupCodec.encode(document().copy(version = 999)),
            BackupCodec.encode(document().copy(format = "other")),
            BackupCodec.encode(document().copy(id = "invalid")),
        ).forEach { text ->
            assertTrue(runCatching { BackupCodec.decode(text) }.isFailure)
        }
    }

    @Test fun oversizedSettingsAndBlankPlaylistNamesAreRejected() {
        assertTrue(
            runCatching {
                BackupCodec.decode(BackupCodec.encode(document().copy(strings = mapOf("theme_mode" to "x".repeat(4097)))))
            }.isFailure,
        )
        assertTrue(
            runCatching { BackupCodec.decode(BackupCodec.encode(document().copy(playlists = listOf(BackupPlaylist(" "))))) }.isFailure,
        )
    }

    @Test fun changedDevicePathsMatchUniqueMetadata() {
        assertEquals(0, matchBackupSong(song, listOf(song.copy(path = "/new/path.flac", duration = 180500))))
    }

    @Test fun duplicateMetadataNeverSelectsAnArbitrarySong() {
        assertNull(matchBackupSong(song, listOf(song.copy(path = "/a"), song.copy(path = "/b"))))
    }

    @Test fun pathMatchWinsButReusedPathDoesNotMatchDifferentAudio() {
        assertEquals(1, matchBackupSong(song, listOf(song.copy(path = "/a"), song)))
        assertNull(matchBackupSong(song, listOf(song.copy(title = "Other"))))
    }
}
