package me.spica27.spicamusic.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseCheckerTest {
    @Test
    fun versionComparisonHandlesPrefixAndDifferentPartCounts() {
        assertTrue(isVersionNewer(latest = "v1.3.9", current = "1.3.8"))
        assertTrue(isVersionNewer(latest = "2.0", current = "1.9.12"))
        assertFalse(isVersionNewer(latest = "v1.3.8", current = "1.3.8"))
        assertFalse(isVersionNewer(latest = "1.3.7", current = "1.3.8"))
    }

    @Test
    fun downloadSelectionMatchesCurrentBuildVariant() {
        val release =
            GitHubRelease(
                tagName = "v1.3.9",
                pageUrl = "https://example.test/release",
                assets =
                    listOf(
                        ReleaseAsset(
                            name = "SPICaMusic-1.3.9-no-telegram-api.apk",
                            downloadUrl = "https://example.test/no-telegram.apk",
                        ),
                        ReleaseAsset(
                            name = "SPICaMusic-1.3.9-with-telegram-api.apk",
                            downloadUrl = "https://example.test/with-telegram.apk",
                        ),
                    ),
            )

        assertEquals(
            "https://example.test/no-telegram.apk",
            preferredDownloadUrl(release, withTelegramApi = false),
        )
        assertEquals(
            "https://example.test/with-telegram.apk",
            preferredDownloadUrl(release, withTelegramApi = true),
        )
    }

    @Test
    fun downloadSelectionFallsBackToReleasePage() {
        val release =
            GitHubRelease(
                tagName = "v1.3.9",
                pageUrl = "https://example.test/release",
                assets = emptyList(),
            )

        assertEquals(
            "https://example.test/release",
            preferredDownloadUrl(release, withTelegramApi = true),
        )
    }
}
