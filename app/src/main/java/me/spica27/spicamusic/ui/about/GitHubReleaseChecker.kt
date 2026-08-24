package me.spica27.spicamusic.ui.about

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

internal data class GitHubRelease(
    val tagName: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
)

internal object GitHubReleaseChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/GGBond-xxg/SPICaMusic_Android/releases/latest"
    private val client = OkHttpClient()

    suspend fun fetchLatest(): GitHubRelease =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(LATEST_RELEASE_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SPICaMusic-Android")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .build()

            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub returned HTTP ${response.code}" }
                parseRelease(JSONObject(response.body.string()))
            }
        }

    private fun parseRelease(json: JSONObject): GitHubRelease {
        val assetsJson = json.optJSONArray("assets")
        val assets =
            buildList {
                if (assetsJson != null) {
                    for (index in 0 until assetsJson.length()) {
                        val asset = assetsJson.optJSONObject(index) ?: continue
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        if (name.isNotBlank() && url.isNotBlank()) {
                            add(ReleaseAsset(name = name, downloadUrl = url))
                        }
                    }
                }
            }
        return GitHubRelease(
            tagName = json.getString("tag_name"),
            pageUrl = json.getString("html_url"),
            assets = assets,
        )
    }
}

internal object IgnoredUpdateStore {
    private const val PREFERENCES_NAME = "app_updates"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    fun isIgnored(
        context: Context,
        version: String,
    ): Boolean =
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IGNORED_VERSION, null) == version

    fun ignore(
        context: Context,
        version: String,
    ) {
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IGNORED_VERSION, version)
            .apply()
    }
}

internal fun isVersionNewer(
    latest: String,
    current: String,
): Boolean {
    val latestParts = versionParts(latest)
    val currentParts = versionParts(current)
    val count = maxOf(latestParts.size, currentParts.size)
    for (index in 0 until count) {
        val latestPart = latestParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (latestPart != currentPart) return latestPart > currentPart
    }
    return false
}

internal fun preferredDownloadUrl(
    release: GitHubRelease,
    withTelegramApi: Boolean,
): String {
    val suffix =
        if (withTelegramApi) {
            "with-telegram-api.apk"
        } else {
            "no-telegram-api.apk"
        }
    return release.assets
        .firstOrNull { it.name.lowercase(Locale.ROOT).endsWith(suffix) }
        ?.downloadUrl
        ?: release.pageUrl
}

private fun versionParts(value: String): List<Int> {
    val normalized =
        value
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
    return normalized
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
