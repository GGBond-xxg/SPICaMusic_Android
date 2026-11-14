package me.spica27.spicamusic.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class M3uPlaylistTest {
    private val tracks =
        listOf(
            M3uPlaylist.Track(1, "/storage/emulated/0/Music/歌 #100%.flac", "歌\n#attack", "Artist", 180123),
            M3uPlaylist.Track(2, "/storage/emulated/0/Music/next.mp3", "Next", "Artist", 123000),
        )

    @Test fun roundTripPreservesUnicodeAndEscapedPathsInOrder() {
        val entries = M3uPlaylist.read(M3uPlaylist.write("My\nplaylist", tracks.reversed()).reader())
        assertEquals(listOf(2L, 1L), M3uPlaylist.match(entries, tracks).ids)
        assertEquals(2, entries.size)
    }

    @Test fun bomCrLfRelativePathsAndDuplicates() {
        val entries = M3uPlaylist.read("\uFEFF#EXTM3U\r\n#EXTINF:123,Next\r\n./next.mp3\r\nnext.mp3\r\nmissing.mp3".reader())
        assertEquals(M3uPlaylist.Match(listOf(2), 1, 1), M3uPlaylist.match(entries, tracks))
    }

    @Test fun ambiguousBasenamesNeverPickAnArbitrarySong() {
        val library = tracks + tracks[1].copy(id = 3, path = "/other/next.mp3")
        assertEquals(M3uPlaylist.Match(listOf(2), 1, 0), M3uPlaylist.match(listOf("next.mp3", tracks[1].path), library))
    }

    @Test fun networkAndContentUrisCannotMatchLocalBasenames() {
        val entries =
            listOf("https://example.com/next.mp3?token=secret", "content://media/next.mp3", "file://remote/next.mp3", "//server/next.mp3")
        assertEquals(M3uPlaylist.Match(emptyList(), 4, 0), M3uPlaylist.match(entries, tracks))
    }

    @Test fun windowsAndMovedPathsMatchUniqueFiles() {
        assertEquals(listOf(2L), M3uPlaylist.match(listOf("D:\\Music\\next.mp3"), tracks).ids)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedDocuments() {
        M3uPlaylist.read("x".repeat(M3uPlaylist.MAX_BYTES + 1).reader())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHlsManifest() {
        M3uPlaylist.read("#EXTM3U\n#EXT-X-TARGETDURATION:6\nnext.mp3".reader())
    }
}
