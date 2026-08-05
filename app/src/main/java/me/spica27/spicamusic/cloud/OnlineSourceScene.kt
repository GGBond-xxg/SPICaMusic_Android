package me.spica27.spicamusic.cloud

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.ui.widget.AudioCover
import org.koin.compose.viewmodel.koinViewModel

class OnlineSourceScene : StackScene() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val viewModel: OnlineSourceViewModel = koinViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val listState = rememberLazyListState()
        var showUrlDialog by rememberSaveable { mutableStateOf(false) }
        val selectedSource =
            state.status.sources.firstOrNull { it.key == state.selectedSource }
        val searchSupported =
            selectedSource?.actions?.any { it == "musicSearch" || it == "search" } == true
        val picker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let(viewModel::import)
            }

        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                viewModel.clearMessage()
            }
        }
        val closeToEnd by remember {
            derivedStateOf {
                val last =
                    listState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 3
            }
        }
        LaunchedEffect(closeToEnd, state.songs.size, state.endReached) {
            if (closeToEnd && state.songs.isNotEmpty() && !state.endReached) viewModel.loadMore()
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.online_source_title)) },
                    navigationIcon = {
                        IconButton(onClick = { path.popTop() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = !state.importing,
                            onClick = { picker.launch(arrayOf("*/*")) },
                        ) {
                            Icon(Icons.Default.FileOpen, stringResource(R.string.online_source_import_file))
                        }
                        IconButton(
                            enabled = !state.importing,
                            onClick = { showUrlDialog = true },
                        ) {
                            Icon(Icons.Default.AddLink, stringResource(R.string.online_source_import_url))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                )
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 36.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "status") {
                    SourceStatusCard(
                        state = state,
                        onReload = viewModel::refresh,
                        onRemove = viewModel::remove,
                        onPickFile = {
                            picker.launch(arrayOf("*/*"))
                        },
                    )
                }

                if (state.status.ready) {
                    item(key = "source_picker") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.online_source_choose),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                state.status.sources.forEach { source ->
                                    FilterChip(
                                        selected = source.key == state.selectedSource,
                                        onClick = { viewModel.selectSource(source.key) },
                                        label = { Text(source.name) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "search") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = viewModel::setQuery,
                                modifier = Modifier.weight(1f),
                                enabled = searchSupported,
                                singleLine = true,
                                label = { Text(stringResource(R.string.online_source_search_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                keyboardOptions =
                                    androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction = ImeAction.Search,
                                    ),
                                keyboardActions =
                                    androidx.compose.foundation.text.KeyboardActions(
                                        onSearch = { viewModel.search() },
                                    ),
                            )
                            Button(
                                enabled = searchSupported && !state.loading && state.query.isNotBlank(),
                                onClick = viewModel::search,
                            ) {
                                Text(stringResource(R.string.search))
                            }
                        }
                    }
                    if (!searchSupported) {
                        item(key = "resolver_hint") {
                            Text(
                                text = stringResource(R.string.online_source_resolver_only),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (state.loading) {
                    item(key = "loading") {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                itemsIndexed(
                    items = state.songs,
                    key = { _, song -> "${song.source}:${song.id}" },
                    contentType = { _, _ -> "online_song" },
                ) { _, song ->
                    OnlineSongRow(song = song, onClick = { viewModel.play(song) })
                }

                if (state.loadingMore) {
                    item(key = "loading_more") {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        if (showUrlDialog) {
            ImportUrlDialog(
                onDismiss = { showUrlDialog = false },
                onImport = {
                    showUrlDialog = false
                    viewModel.import(it)
                },
            )
        }
    }
}

@Composable
private fun SourceStatusCard(
    state: OnlineSourceUiState,
    onReload: () -> Unit,
    onRemove: () -> Unit,
    onPickFile: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            state.status.script?.name
                                ?: stringResource(R.string.online_source_not_installed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            when {
                                state.importing -> stringResource(R.string.online_source_importing)
                                state.status.ready ->
                                    stringResource(
                                        R.string.online_source_ready_detail,
                                        state.status.script
                                            ?.version
                                            .orEmpty(),
                                        state.status.sources.size,
                                    )
                                state.status.installed -> state.status.error.orEmpty()
                                else -> stringResource(R.string.online_source_not_installed_detail)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.status.installed) {
                    IconButton(enabled = !state.importing, onClick = onReload) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
                    }
                    IconButton(enabled = !state.importing, onClick = onRemove) {
                        Icon(Icons.Default.DeleteOutline, stringResource(R.string.remove))
                    }
                }
            }
            if (!state.status.installed) {
                FilledTonalButton(
                    enabled = !state.importing,
                    onClick = onPickFile,
                ) {
                    Icon(Icons.Default.FileOpen, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.online_source_import_file))
                }
            }
            Text(
                text = stringResource(R.string.online_source_security_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnlineSongRow(
    song: OnlineSourceSong,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AudioCover(
            uri = song.artworkUrl?.let(Uri::parse),
            modifier =
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp)),
            placeHolder = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null)
                }
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (song.album.isNotBlank()) {
                Text(
                    text = song.album,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AssistChip(
            onClick = onClick,
            label = { Text(song.source.uppercase()) },
        )
    }
}

@Composable
private fun ImportUrlDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.online_source_import_url)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.online_source_url_hint))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("HTTPS URL") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.startsWith("https://") && url.length > "https://".length,
                onClick = { onImport(url) },
            ) {
                Text(stringResource(R.string.online_source_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
