package me.spica27.spicamusic.storage.impl.scanner

/**
 * Limits a MediaStore scan to the folders explicitly selected by the user.
 *
 * [restricted] is kept separately from [pathPrefixes] because a persisted SAF folder can be
 * valid even when Android cannot expose its absolute path. In that case MediaStore must not fall
 * back to a device-wide scan; the SAF traversal imports that folder instead.
 */
internal data class ScanPathScope(
    val restricted: Boolean,
    val pathPrefixes: List<String>,
) {
    fun contains(path: String): Boolean {
        if (!restricted) return true
        if (path.isBlank()) return false
        return pathPrefixes.any(path::startsWith)
    }

    companion object {
        fun fromConfiguredFolders(
            configuredFolderCount: Int,
            rawPathPrefixes: List<String?>,
        ): ScanPathScope =
            ScanPathScope(
                restricted = configuredFolderCount > 0,
                pathPrefixes =
                    rawPathPrefixes
                        .asSequence()
                        .filterNotNull()
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                        .map { path -> if (path.endsWith('/')) path else "$path/" }
                        .distinct()
                        .toList(),
            )
    }
}
