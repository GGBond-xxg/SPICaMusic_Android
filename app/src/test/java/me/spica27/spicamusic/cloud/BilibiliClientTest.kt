package me.spica27.spicamusic.cloud

import org.junit.Assert.*
import org.junit.Test

class BilibiliClientTest {
    @Test fun `extracts BV from links but rejects malformed ids`() {
        assertEquals("BV1xx411c7mD", extractBilibiliVideoId("https://www.bilibili.com/video/BV1xx411c7mD/?p=2"))
        assertEquals("BV1xx411c7mD", extractBilibiliVideoId("BV1xx411c7mD"))
        assertNull(extractBilibiliVideoId("BV123"))
        assertNull(extractBilibiliVideoId("BV1xx411c7mDextra"))
    }

    @Test fun `duration preserves hours and seconds`() {
        assertEquals(239000L, parseBilibiliDuration("3:59"))
        assertEquals(3723000L, parseBilibiliDuration("1:02:03"))
        assertEquals(0L, parseBilibiliDuration(""))
    }

    @Test fun `wbi key and signing match published vector`() {
        val key = bilibiliMixinKey("7cd084941338484aae1ad9425b84077c4932caff0ff746eab6f01bf08b70ac45")
        assertEquals("ea1db124af3c7062474693fa704f4ff8", key)
        val signed = signBilibiliQuery(mapOf("foo" to "114", "bar" to "514", "zab" to "1919810"), key, 1702204169)
        assertEquals("8f6f2b5b3d485fe1886cec6a0be8c5d4", signed["w_rid"])
    }

    @Test fun `cdn gets referer but never the login cookie`() {
        val account = RemoteMusicAccount("test", RemoteMusicProvider.BILIBILI, "test", secret = "SESSDATA=private")
        assertEquals(BILIBILI_REFERER, remoteStreamRequestHeaders(account, "https://upos.bilivideo.com/audio.m4s")["Referer"])
        assertFalse(remoteStreamRequestHeaders(account, "https://upos.bilivideo.com/audio.m4s").containsKey("Cookie"))
        assertTrue(remoteStreamRequestHeaders(account, "https://bilivideo.com.evil.example/audio").isEmpty())
    }
}
