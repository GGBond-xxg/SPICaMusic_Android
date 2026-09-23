package me.spica27.spicamusic.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.backup.BackupDocument
import me.spica27.spicamusic.backup.LibraryBackup
import me.spica27.spicamusic.ui.about.AboutScaffold
import me.spica27.spicamusic.ui.about.AboutSectionCard
import me.spica27.spicamusic.ui.theme.Spacing
import org.koin.compose.koinInject

class BackupScene : StackScene() {
    @Composable
    override fun Content() {
        val backup: LibraryBackup = koinInject()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        var preview by remember { mutableStateOf<BackupDocument?>(null) }

        fun runOperation(operation: suspend () -> Unit) {
            if (busy) return
            busy = true
            scope.launch {
                try {
                    operation()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    message = context.getString(R.string.backup_failed)
                } finally {
                    busy = false
                }
            }
        }
        val export =
            rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) {
                    runOperation {
                        backup.export(uri)
                        message = context.getString(R.string.backup_exported)
                    }
                }
            }
        val restore =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runOperation { preview = backup.read(uri) }
            }
        AboutScaffold(stringResource(R.string.backup_title)) {
            item {
                AboutSectionCard(stringResource(R.string.backup_title), subtitle = stringResource(R.string.backup_description)) {
                    TextButton(enabled = !busy, onClick = {
                        export.launch("spica-backup-${System.currentTimeMillis()}.json")
                    }) { Text(stringResource(R.string.backup_export)) }
                    TextButton(enabled = !busy, onClick = {
                        restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }) { Text(stringResource(R.string.backup_restore)) }
                    if (busy) CircularProgressIndicator(Modifier.padding(Spacing.Large))
                    message?.let { Text(it, modifier = Modifier.padding(Spacing.Large)) }
                }
            }
        }
        preview?.let { document ->
            AlertDialog(
                onDismissRequest = { preview = null },
                title = { Text(stringResource(R.string.backup_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.backup_preview,
                            document.playlists.size,
                            document.playlists.sumOf {
                                it.songs.size +
                                    it.cloud.size
                            },
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        preview = null
                        runOperation {
                            val result = backup.restore(document)
                            message =
                                if (result.alreadyRestored) {
                                    context.getString(
                                        R.string.backup_already_restored,
                                    )
                                } else {
                                    context.getString(R.string.backup_restored, result.playlists, result.missingSongs)
                                }
                        }
                    }) { Text(stringResource(R.string.backup_restore)) }
                },
                dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
