package me.spica27.spicamusic.cloud

import me.spica27.spicamusic.service.isRestrictedCloudHttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudPlaybackFallbackTest {
    @Test
    fun `rejects provider error pages but accepts audio and binary streams`() {
        assertTrue(isClearlyNonAudioContentType("text/html; charset=utf-8"))
        assertTrue(isClearlyNonAudioContentType("application/json"))
        assertFalse(isClearlyNonAudioContentType("audio/mpeg"))
        assertFalse(isClearlyNonAudioContentType("application/octet-stream"))
        assertFalse(isClearlyNonAudioContentType(null))
    }

    @Test
    fun `sends netease login only to official account domain`() {
        val account = remoteAccount(RemoteMusicProvider.NETEASE)

        assertEquals(
            "MUSIC_U=secret",
            remoteStreamRequestHeaders(account, "https://music.163.com/song.mp3")["Cookie"],
        )
        assertFalse(
            remoteStreamRequestHeaders(account, "https://m801.music.126.net/song.mp3")
                .containsKey("Cookie"),
        )
        assertTrue(
            remoteStreamRequestHeaders(account, "https://attacker.example/song.mp3").isEmpty(),
        )
    }

    @Test
    fun `sends qq login only to qq music stream domains`() {
        val account = remoteAccount(RemoteMusicProvider.QQ_MUSIC)

        assertEquals(
            "MUSIC_U=secret",
            remoteStreamRequestHeaders(account, "https://dl.stream.qqmusic.qq.com/song.m4a")["Cookie"],
        )
        assertTrue(
            remoteStreamRequestHeaders(account, "https://example.qq.com/song.m4a").isEmpty(),
        )
    }

    @Test
    fun `only access restriction statuses use the membership message`() {
        assertTrue(isRestrictedCloudHttpStatus(403))
        assertTrue(isRestrictedCloudHttpStatus(451))
        assertFalse(isRestrictedCloudHttpStatus(500))
        assertFalse(isRestrictedCloudHttpStatus(502))
        assertFalse(isRestrictedCloudHttpStatus(null))
    }

    private fun remoteAccount(provider: RemoteMusicProvider) =
        RemoteMusicAccount(
            id = "account",
            provider = provider,
            displayName = "test",
            secret = "MUSIC_U=secret",
        )
}
