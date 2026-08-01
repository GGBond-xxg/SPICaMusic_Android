package me.spica27.spicamusic.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudCatalogMergeTest {
    @Test
    fun firstPageRefreshDoesNotDiscardAlreadyLoadedSongs() {
        val cached = (1L..94L).map(::telegramSong)
        val refreshedFirstPage =
            (1L..60L).map { id ->
                telegramSong(id, title = "Updated $id")
            }

        val merged = mergeCatalogSongs(cached, refreshedFirstPage)

        assertEquals(94, merged.size)
        assertEquals("Updated 1", merged.first().title)
        assertEquals("Song 94", merged.last().title)
    }

    @Test
    fun newlyDiscoveredSongIsAppendedWithoutDuplicates() {
        val current = (1L..94L).map(::telegramSong)

        val merged = mergeCatalogSongs(current, listOf(telegramSong(95L)))

        assertEquals(95, merged.size)
        assertEquals(95, merged.map(CloudCatalogSong::stableId).distinct().size)
        assertEquals("telegram:42", merged.last().endpointKey())
    }

    private fun telegramSong(
        id: Long,
        title: String = "Song $id",
    ): CloudCatalogSong {
        val payload =
            TelegramSong(
                messageId = id,
                chatId = 42L,
                fileId = id.toInt(),
                fileSize = 1_024L,
                title = title,
                artist = "Artist",
                durationMs = 180_000L,
                mimeType = "audio/mpeg",
                coverFileId = null,
            )
        return CloudCatalogSong(
            stableId = "cloud:telegram:42:$id",
            source = CloudSongSource.TELEGRAM,
            accountName = "Channel",
            title = title,
            artist = payload.artist,
            album = "Channel",
            durationMs = payload.durationMs,
            artworkUri = null,
            payload = CloudCatalogPayload.Telegram(payload),
        )
    }
}
