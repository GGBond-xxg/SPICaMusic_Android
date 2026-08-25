package me.spica27.spicamusic.ui.widget

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

@Immutable
data class LyricsCustomFont(
    val id: String,
    val name: String,
)

object LyricsFontIds {
    const val DEFAULT = "default"
    const val MI_SANS = "mi_sans"
}

fun decodeLyricsCustomFonts(value: String): List<LyricsCustomFont> =
    runCatching {
        val array = JSONArray(value.ifBlank { "[]" })
        buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@repeat
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) add(LyricsCustomFont(id = id, name = name))
            }
        }
    }.getOrDefault(emptyList())

fun encodeLyricsCustomFonts(fonts: List<LyricsCustomFont>): String =
    JSONArray()
        .apply {
            fonts.forEach { font ->
                put(
                    JSONObject()
                        .put("id", font.id)
                        .put("name", font.name),
                )
            }
        }.toString()

fun customLyricsFontFile(
    context: Context,
    id: String,
): File = File(File(context.filesDir, LYRICS_FONT_DIRECTORY), "$id.ttf")

fun documentDisplayName(
    context: Context,
    uri: Uri,
): String {
    val name =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
    return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Custom font"
}

suspend fun importLyricsFont(
    context: Context,
    uri: Uri,
    displayName: String,
): LyricsCustomFont =
    withContext(Dispatchers.IO) {
        val resolvedName = displayName.trim().takeIf { it.isNotEmpty() } ?: documentDisplayName(context, uri)
        val directory = File(context.filesDir, LYRICS_FONT_DIRECTORY).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, ".$id.importing")
        val target = customLyricsFontFile(context, id)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open font file")
            require(temporary.length() in 1..MAX_CUSTOM_FONT_BYTES) { "Font file is too large" }
            Typeface.createFromFile(temporary)
            check(temporary.renameTo(target)) { "Unable to save font file" }
            LyricsCustomFont(id = id, name = resolvedName)
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

suspend fun deleteLyricsFont(
    context: Context,
    id: String,
) {
    withContext(Dispatchers.IO) {
        customLyricsFontFile(context, id).delete()
    }
}

private const val LYRICS_FONT_DIRECTORY = "lyrics_fonts"
private const val MAX_CUSTOM_FONT_BYTES = 64L * 1024L * 1024L
