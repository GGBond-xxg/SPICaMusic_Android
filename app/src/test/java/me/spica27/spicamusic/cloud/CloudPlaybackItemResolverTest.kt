package me.spica27.spicamusic.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudPlaybackItemResolverTest {
    @Test
    fun `parses telegram stable id`() {
        assertEquals(
            CloudMediaIdentity(
                provider = "telegram",
                accountOrChatId = "-1003541630150",
                songId = "93323264",
            ),
            parseCloudMediaIdentity("cloud:telegram:-1003541630150:93323264"),
        )
    }

    @Test
    fun `keeps provider song ids containing colons`() {
        assertEquals(
            "folder:disc:track",
            parseCloudMediaIdentity("cloud:subsonic:account-id:folder:disc:track")?.songId,
        )
    }

    @Test
    fun `rejects local and incomplete ids`() {
        assertNull(parseCloudMediaIdentity("12345"))
        assertNull(parseCloudMediaIdentity("cloud:telegram:chat"))
    }
}
