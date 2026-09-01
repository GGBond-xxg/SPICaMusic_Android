package me.spica27.spicamusic.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ArtworkInputStreamTest {
    @Test
    fun `limited stream returns content within limit`() {
        val content = byteArrayOf(1, 2, 3, 4)

        val result = SizeLimitedInputStream(ByteArrayInputStream(content), content.size.toLong()).readBytes()

        assertArrayEquals(content, result)
    }

    @Test
    fun `limited stream rejects content beyond limit`() {
        val stream = SizeLimitedInputStream(ByteArrayInputStream(ByteArray(9)), 8)

        assertThrows(IOException::class.java) {
            stream.readBytes()
        }
    }
}
