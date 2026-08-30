package me.spica27.spicamusic.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCoverPlaceholderTest {
    @Test
    fun `full size cover caps visible icon at 64 dp`() {
        assertEquals(64f, coverPlaceholderIconSizeDp(containerMinDp = 320f, renderScale = 1f), 0.001f)
    }

    @Test
    fun `lyrics morph compensates outer scale without growing visible icon`() {
        val renderScale = 320f / 48f
        val measuredIcon = coverPlaceholderIconSizeDp(containerMinDp = 48f, renderScale = renderScale)

        assertEquals(64f, measuredIcon * renderScale, 0.001f)
    }

    @Test
    fun `mini player morph keeps icon proportional to visible cover`() {
        val renderScale = 56f / 320f
        val measuredIcon = coverPlaceholderIconSizeDp(containerMinDp = 320f, renderScale = renderScale)

        assertEquals(56f * 0.36f, measuredIcon * renderScale, 0.001f)
    }
}
