package me.spica27.spicamusic.playlist

import java.io.Reader
import java.net.URI

/** Local playlist interchange only: never fetch URLs or export account stream credentials. */
internal object M3uPlaylist {
    const val MAX_BYTES = 1_048_576
    private const val MAX_ENTRIES = 10_000

    data class Track(
        val id: Long,
        val path: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
    )

    data class Match(
        val ids: List<Long>,
        val missing: Int,
        val duplicates: Int,
    )

    fun read(reader: Reader): List<String> {
        val text = StringBuilder()
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(text.length + count <= MAX_BYTES) { "Playlist is too large" }
            text.append(buffer, 0, count)
        }
        require(text.toString().toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Playlist is too large" }
        val lines =
            text
                .toString()
                .removePrefix("\uFEFF")
                .lineSequence()
                .map(String::trim)
                .toList()
        require(lines.none { it.startsWith("#EXT-X-", ignoreCase = true) }) { "HLS is not a local playlist" }
        val entries = lines.filter { it.isNotEmpty() && !it.startsWith('#') }
        require(entries.size <= MAX_ENTRIES) { "Too many playlist entries" }
        return entries
    }

    fun write(
        name: String,
        tracks: List<Track>,
    ): String =
        buildString {
            require(tracks.size <= MAX_ENTRIES) { "Too many playlist entries" }
            append("#EXTM3U\n#PLAYLIST:").append(singleLine(name)).append('\n')
            tracks.forEach { track ->
                val path = requireNotNull(normalizeLocalPath(track.path)) { "Not a local song" }
                require(!path.contains('\n') && !path.contains('\r')) { "Invalid path" }
                append("#EXTINF:").append((track.durationMs / 1000).coerceAtLeast(0)).append(',')
                append(singleLine("${track.artist} - ${track.title}")).append('\n')
                // URI encoding preserves literal #, percent signs, spaces and non-ASCII filenames.
                append(URI("file", "", if (path.startsWith('/')) path else "/$path", null).toASCIIString()).append('\n')
                require(length <= MAX_BYTES) { "Playlist is too large" }
            }
            require(toString().toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Playlist is too large" }
        }

    fun match(
        entries: List<String>,
        library: List<Track>,
    ): Match {
        val paths = library.mapNotNull { track -> normalizeLocalPath(track.path)?.let { it to track.id } }
        val exact = paths.groupBy({ it.first }, { it.second })
        val filenames = paths.groupBy({ it.first.substringAfterLast('/') }, { it.second })
        val ids = linkedSetOf<Long>()
        var missing = 0
        var duplicates = 0
        entries.forEach { entry ->
            val path = normalizeLocalPath(entry)
            val candidates = path?.let { exact[it] ?: filenames[it.substringAfterLast('/')] }
            val id = candidates?.distinct()?.singleOrNull()
            when {
                id == null -> missing++
                !ids.add(id) -> duplicates++
            }
        }
        return Match(ids.toList(), missing, duplicates)
    }

    private fun singleLine(value: String): String = value.replace('\r', ' ').replace('\n', ' ')

    private fun normalizeLocalPath(value: String): String? {
        val path = value.trim().replace('\\', '/')
        if (path.startsWith("file:", ignoreCase = true)) {
            val uri = runCatching { URI(path) }.getOrNull() ?: return null
            if (!uri.host.isNullOrEmpty() && uri.host != "localhost") return null
            return uri.path?.removePrefix("./")?.takeIf { it.isNotBlank() }
        }
        // A Windows drive letter is a local path, not a URL scheme.
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(path) &&
            !Regex("^[A-Za-z]:/").containsMatchIn(path)
        ) {
            return null
        }
        if (path.startsWith("//")) return null
        return path.removePrefix("./").takeIf { it.isNotBlank() }
    }
}
