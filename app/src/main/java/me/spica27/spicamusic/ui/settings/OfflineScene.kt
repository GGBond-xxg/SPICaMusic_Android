package me.spica27.spicamusic.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.feature.player.domain.PlayerUseCases
import me.spica27.spicamusic.offline.OfflineStore
import me.spica27.spicamusic.player.api.PlayerAction
import me.spica27.spicamusic.ui.about.AboutScaffold
import me.spica27.spicamusic.ui.about.AboutSectionCard
import me.spica27.spicamusic.ui.theme.Spacing
import org.koin.compose.koinInject

@UnstableApi
class OfflineScene : StackScene() {
    @Composable
    override fun Content() {
        val store: OfflineStore = koinInject()
        val player: PlayerUseCases = koinInject()
        val tracks by store.tracks.collectAsStateWithLifecycle()
        val progress by store.progress.collectAsStateWithLifecycle()
        val limit by store.limitMiB.collectAsStateWithLifecycle()
        val current by player.currentMediaItem.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var deleteId by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf(false) }

        fun change(block: () -> Unit) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { block() }
                    error = false
                } catch (
                    e: CancellationException,
                ) {
                    throw e
                } catch (_: Exception) {
                    error =
                        true
                }
            }
        }
        AboutScaffold(stringResource(R.string.offline_title)) {
            item {
                AboutSectionCard(stringResource(R.string.offline_title), subtitle = stringResource(R.string.offline_description)) {
                    Text(
                        stringResource(
                            R.string.offline_usage,
                            Formatter.formatFileSize(
                                context,
                                tracks.sumOf {
                                    it.bytes
                                },
                            ),
                            limit,
                        ),
                        modifier = Modifier.padding(horizontal = Spacing.Large),
                    )
                    FlowRow(Modifier.padding(horizontal = Spacing.Medium)) {
                        listOf(256, 512, 1024, 2048, 4096).forEach { size ->
                            FilterChip(selected = size == limit, onClick = {
                                change { store.setLimit(size) }
                            }, label = { Text("$size MB") }, modifier = Modifier.padding(end = Spacing.Small))
                        }
                    }
                    TextButton(
                        enabled =
                            current?.mediaId?.startsWith("cloud:") == true &&
                                tracks.none { it.id == current?.mediaId } &&
                                progress[current?.mediaId]?.error != false,
                        onClick = { current?.let(store::enqueue) },
                    ) { Text(stringResource(R.string.offline_download_current)) }
                    TextButton(enabled = tracks.isNotEmpty() || progress.isNotEmpty(), onClick = {
                        deleteId = "*"
                    }) { Text(stringResource(R.string.offline_clear)) }
                    if (error) Text(stringResource(R.string.offline_operation_failed), modifier = Modifier.padding(Spacing.Large))
                }
            }
            items(progress.entries.toList(), key = { "progress:${it.key}" }) { (id, state) ->
                AboutSectionCard(
                    state.title,
                    subtitle =
                        state.errorMessage
                            ?: stringResource(
                                if (state.canceling) {
                                    R.string.offline_canceling
                                } else if (state.error) {
                                    R.string.offline_failed
                                } else {
                                    R.string.offline_downloading
                                },
                            ),
                ) {
                    if (!state.error) {
                        if (state.total > 0) {
                            LinearProgressIndicator(progress = {
                                (state.bytes.toFloat() / state.total).coerceIn(0f, 1f)
                            }, modifier = Modifier.padding(horizontal = Spacing.Large))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.padding(horizontal = Spacing.Large))
                        }
                        Text(Formatter.formatFileSize(context, state.bytes), modifier = Modifier.padding(horizontal = Spacing.Large))
                    }
                    TextButton(enabled = !state.canceling, onClick = { store.cancel(id) }) { Text(stringResource(R.string.cancel)) }
                }
            }
            if (tracks.isEmpty() && progress.isEmpty()) item { Text(stringResource(R.string.offline_empty)) }
            items(tracks, key = { it.id }) { track ->
                AboutSectionCard(track.title, subtitle = "${track.artist} · ${Formatter.formatFileSize(context, track.bytes)}") {
                    FlowRow {
                        TextButton(onClick = {
                            runCatching { player.doAction(PlayerAction.PlayMediaItems(listOf(store.mediaItem(track)))) }.onFailure {
                                error =
                                    true
                            }
                        }) { Text(stringResource(R.string.offline_play)) }
                        TextButton(onClick = { deleteId = track.id }) { Text(stringResource(R.string.offline_remove)) }
                    }
                }
            }
        }
        deleteId?.let { id ->
            AlertDialog(
                onDismissRequest = {
                    deleteId = null
                },
                title = { Text(stringResource(R.string.offline_remove)) },
                text = { Text(stringResource(R.string.offline_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleteId = null
                        change {
                            if (id ==
                                "*"
                            ) {
                                store.clear()
                            } else {
                                store.remove(id)
                            }
                        }
                    }) { Text(stringResource(R.string.offline_remove)) }
                },
                dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
