package me.spica27.spicamusic.cloud

import android.content.Context

/**
 * Persists the last fully enumerated size of each cloud endpoint.
 *
 * The catalog itself remains paged, but a cold start must not make an already known total fall
 * back to the first page size. Only endpoint identifiers and integer counts are stored here; no
 * account credentials or stream URLs are written to disk.
 */
class CloudCatalogCountStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            "cloud_catalog_counts",
            Context.MODE_PRIVATE,
        )

    fun get(endpointKey: String): Int? =
        if (preferences.contains(endpointKey)) {
            preferences.getInt(endpointKey, 0).coerceAtLeast(0)
        } else {
            null
        }

    fun put(
        endpointKey: String,
        count: Int,
    ) {
        preferences.edit().putInt(endpointKey, count.coerceAtLeast(0)).apply()
    }

    fun retain(endpointKeys: Set<String>) {
        val obsoleteKeys = preferences.all.keys - endpointKeys
        if (obsoleteKeys.isEmpty()) return
        preferences
            .edit()
            .apply {
                obsoleteKeys.forEach(::remove)
            }.apply()
    }
}
