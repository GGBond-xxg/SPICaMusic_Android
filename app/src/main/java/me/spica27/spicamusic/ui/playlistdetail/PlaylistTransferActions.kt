package me.spica27.spicamusic.ui.playlistdetail

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spica27.spicamusic.R
import me.spica27.spicamusic.playlist.M3uPlaylist

/** Keep the launchers composed while the menu is closed and the document picker is open. */
@Composable
internal fun rememberPlaylistTransferActions(
    title: String,
    viewModel: PlaylistDetailViewModel,
): @Composable () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
            if (uri != null) {
                scope.launch {
                    busy = true
                    try {
                        val count =
                            withContext(Dispatchers.IO) {
                                val tracks = viewModel.exportTracks()
                                require(tracks.isNotEmpty())
                                val document = M3uPlaylist.write(title, tracks)
                                checkNotNull(
                                    context.contentResolver.openOutputStream(uri, "wt"),
                                ).bufferedWriter(Charsets.UTF_8).use { it.write(document) }
                                tracks.size
                            }
                        Toast.makeText(context, context.getString(R.string.playlist_export_result, count), Toast.LENGTH_LONG).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.playlist_transfer_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                    }
                }
            }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    busy = true
                    try {
                        val result =
                            withContext(Dispatchers.IO) {
                                val entries =
                                    checkNotNull(
                                        context.contentResolver.openInputStream(uri),
                                    ).bufferedReader(Charsets.UTF_8).use(M3uPlaylist::read)
                                viewModel.importTracks(entries)
                            }
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.playlist_import_result, result.ids.size, result.missing, result.duplicates),
                                Toast.LENGTH_LONG,
                            ).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.playlist_transfer_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        busy = false
                    }
                }
            }
        }
    return {
        DropdownMenuItem(
            enabled = !busy,
            text = { Text(stringResource(R.string.playlist_import_m3u)) },
            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
            onClick = {
                viewModel.hideMoreOptionsMenu()
                import.launch(
                    arrayOf("audio/*", "application/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "application/octet-stream"),
                )
            },
        )
        DropdownMenuItem(
            enabled = !busy,
            text = { Text(stringResource(R.string.playlist_export_m3u)) },
            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
            onClick = {
                viewModel.hideMoreOptionsMenu()
                export.launch(
                    title
                        .map {
                            if (it in "\\/:*?\"<>|" ||
                                it.isISOControl()
                            ) {
                                '_'
                            } else {
                                it
                            }
                        }.joinToString("")
                        .take(80)
                        .ifBlank { "Playlist" } +
                        ".m3u8",
                )
            },
        )
    }
}
