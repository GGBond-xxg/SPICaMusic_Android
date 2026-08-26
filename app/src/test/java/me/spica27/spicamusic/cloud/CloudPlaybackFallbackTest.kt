package me.spica27.spicamusic.cloud

import androidx.media3.common.Player
import me.spica27.spicamusic.service.isRestrictedCloudHttpStatus
import me.spica27.spicamusic.service.shouldHandleCloudAudioUnderrun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudPlaybackFallbackTest {
    @Test
    fun `logged-in provider stream is tried before anonymous online source`() {
        assertEquals(
            listOf("http://account-stream", "http://online-source"),
            orderedStreamCandidates(
                onlineUrl = "http://online-source",
                fallbackUrl = "http://account-stream",
                preferFallback = true,
            ),
        )
    }

    @Test
    fun `plain online source keeps its resolved URL first`() {
        assertEquals(
            listOf("http://online-source", "http://fallback"),
            orderedStreamCandidates(
                onlineUrl = "http://online-source",
                fallbackUrl = "http://fallback",
                preferFallback = false,
            ),
        )
    }

    @Test
    fun `authenticated fallback does not wait for anonymous URL resolution`() {
        assertTrue(shouldDeferOnlineResolution(preferFallback = true, fallbackUrl = "http://account-stream"))
        assertFalse(shouldDeferOnlineResolution(preferFallback = false, fallbackUrl = "http://account-stream"))
        assertFalse(shouldDeferOnlineResolution(preferFallback = true, fallbackUrl = null))
    }

    @Test
    fun `cloud preview underrun skips only when decoded audio is exhausted despite buffered data`() {
        assertTrue(
            shouldHandleCloudAudioUnderrun(
                stillSameItem = true,
                isPlaying = true,
                playbackState = Player.STATE_READY,
                totalBufferedDurationMs = 30_000L,
                sinkHasPendingData = false,
            ),
        )
        assertFalse(
            shouldHandleCloudAudioUnderrun(
                stillSameItem = true,
                isPlaying = true,
                playbackState = Player.STATE_BUFFERING,
                totalBufferedDurationMs = 0L,
                sinkHasPendingData = false,
            ),
        )
        assertFalse(
            shouldHandleCloudAudioUnderrun(
                stillSameItem = true,
                isPlaying = true,
                playbackState = Player.STATE_READY,
                totalBufferedDurationMs = 30_000L,
                sinkHasPendingData = true,
            ),
        )
    }

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
