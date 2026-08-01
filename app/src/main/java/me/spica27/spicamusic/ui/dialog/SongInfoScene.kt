package me.spica27.spicamusic.ui.dialog

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.skydoves.landscapist.image.LandscapistImage
import kotlinx.coroutines.launch
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.navkit.path.LocalScene
import me.spica27.navkit.scene.DialogScene
import me.spica27.spicamusic.App
import me.spica27.spicamusic.R
import me.spica27.spicamusic.cloud.CloudCatalogPayload
import me.spica27.spicamusic.cloud.CloudCatalogSong
import me.spica27.spicamusic.common.entity.Song
import me.spica27.spicamusic.common.entity.getAlbumCoverUri
import me.spica27.spicamusic.common.entity.getCoverUri
import me.spica27.spicamusic.ui.player.formatTime
import me.spica27.spicamusic.ui.widget.CoverFallback

class SongInfoScene private constructor(
    private val details: SongInfoDetails,
) : DialogScene() {
    constructor(song: Song) : this(SongInfoDetails.from(song))

    constructor(song: CloudCatalogSong) : this(SongInfoDetails.from(song))

    /**
     * 重写 Content()，将默认的"从中心缩放"替换为"从底部上滑 + 淡入/淡出"。
     * enterProgress 由父类 DialogScene 驱动（push→1f，pop→0f），无需额外声明。
     */
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val scene = LocalScene.current
        val density = LocalDensity.current
        // 预先在 Composition 阶段把 dp 转成 px，避免在 graphicsLayer 里读取 CompositionLocal
        val slideOffsetPx = with(density) { 72.dp.toPx() }
        val dismissDistancePx = with(density) { 72.dp.toPx() }
        val dismissVelocityPx = with(density) { 1_250.dp.toPx() }
        val screenHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
        var dragOffsetPx by remember { mutableFloatStateOf(0f) }
        val handleDragState =
            rememberDraggableState { delta ->
                dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
            }
        val handleDragModifier =
            Modifier.draggable(
                state = handleDragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragOffsetPx >= dismissDistancePx || velocity >= dismissVelocityPx) {
                        path.pop(scene)
                    } else {
                        animate(
                            initialValue = dragOffsetPx,
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        ) { value, _ ->
                            dragOffsetPx = value
                        }
                    }
                },
            )

        Box(
            Modifier
                .zIndex(3f)
                .fillMaxSize(),
        ) {
            // ── 半透明遮罩：随进度渐显，点击关闭 ──
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val dragProgress =
                                (dragOffsetPx / (screenHeightPx * 0.65f))
                                    .coerceIn(0f, 1f)
                            alpha = enterProgress.value * (1f - dragProgress)
                        }
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { path.pop(scene) },
            )

            // ── 卡片：从底部上滑 + 淡入/淡出 ──
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            val p = enterProgress.value
                            alpha = p
                            // p=0 时向下偏移 slideOffsetPx，p=1 时归位
                            translationY = (1f - p) * slideOffsetPx + dragOffsetPx
                        },
            ) {
                SongInfoSheet(handleDragModifier)
            }
        }
    }

    @Composable
    override fun DialogContent() {
        SongInfoSheet()
    }

    @Composable
    private fun SongInfoSheet(handleDragModifier: Modifier = Modifier) {
        val path = LocalNavigationPath.current
        val scene = LocalScene.current
        val density = LocalDensity.current
        val screenHeight =
            with(density) {
                LocalWindowInfo.current.containerSize.height
                    .toDp()
            }
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight - statusBarTop - 8.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .then(handleDragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(44.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 3.dp,
                    ) {
                        LandscapistImage(
                            imageModel = { details.artworkUri },
                            modifier = Modifier.fillMaxSize(),
                            failure = {
                                CoverFallback(
                                    fallbackUri = details.fallbackArtworkUri,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.song_info_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = details.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        IconButton(onClick = { path.pop(scene) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoItem(Icons.Default.MusicNote, stringResource(R.string.song_displayname), details.title)
                    InfoItem(Icons.Default.Person, stringResource(R.string.song_artist), details.artist)
                    InfoItem(Icons.Default.Album, stringResource(R.string.song_album), details.album)
                    InfoItem(Icons.Default.Schedule, stringResource(R.string.song_duration), formatTime(details.durationMs))
                    details.source?.let {
                        InfoItem(Icons.Default.DataUsage, stringResource(R.string.info_cloud_source), it)
                    }
                    details.account?.let {
                        InfoItem(Icons.Default.Folder, stringResource(R.string.info_cloud_account), it)
                    }
                    details.location?.let {
                        InfoItem(
                            Icons.Default.Folder,
                            stringResource(R.string.info_file_path),
                            it,
                            isMultiline = true,
                        )
                    }
                    details.fileSizeBytes?.let {
                        InfoItem(
                            Icons.Default.DataUsage,
                            stringResource(R.string.info_file_size),
                            "${it / 1024 / 1024} MB",
                        )
                    }
                    InfoItem(Icons.Default.Info, stringResource(R.string.info_file_format), details.format)
                    details.stableId?.let {
                        InfoItem(
                            Icons.Default.Info,
                            stringResource(R.string.info_media_id),
                            it,
                            isMultiline = true,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { path.pop(scene) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(R.string.close))
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

private data class SongInfoDetails(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: android.net.Uri?,
    val fallbackArtworkUri: android.net.Uri?,
    val source: String?,
    val account: String?,
    val location: String?,
    val fileSizeBytes: Long?,
    val format: String,
    val stableId: String?,
) {
    companion object {
        fun from(song: Song): SongInfoDetails =
            SongInfoDetails(
                title = song.displayName,
                artist = song.artist,
                album = song.album,
                durationMs = song.duration,
                artworkUri = song.getCoverUri(),
                fallbackArtworkUri = song.getAlbumCoverUri(),
                source = null,
                account = null,
                location = song.path,
                fileSizeBytes = song.size,
                format = song.codec.ifBlank { song.mimeType },
                stableId = song.mediaStoreId.toString(),
            )

        fun from(song: CloudCatalogSong): SongInfoDetails {
            val provider: String
            val format: String
            val fileSize: Long?
            when (val payload = song.payload) {
                is CloudCatalogPayload.Telegram -> {
                    provider = "Telegram"
                    format = payload.song.mimeType
                    fileSize = payload.song.fileSize
                }

                is CloudCatalogPayload.MediaServer -> {
                    provider = payload.account.type.name
                    format = payload.song.mimeType
                    fileSize = null
                }

                is CloudCatalogPayload.Remote -> {
                    provider = payload.account.provider.name
                    format = payload.song.mimeType
                    fileSize = null
                }
            }
            return SongInfoDetails(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                artworkUri = song.artworkUri,
                fallbackArtworkUri = null,
                source = provider,
                account = song.accountName,
                location = null,
                fileSizeBytes = fileSize,
                format = format,
                stableId = song.stableId,
            )
        }
    }
}

@Composable
private fun InfoItem(
    icon: ImageVector,
    title: String,
    content: String,
    isMultiline: Boolean = false,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isMultiline) 2 else 1,
                )
            }
            IconButton(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(40.dp),
                colors =
                    IconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                onClick = {
                    scope.launch {
                        clipboardManager.setClipEntry(
                            ClipData
                                .newPlainText(
                                    title,
                                    content,
                                ).toClipEntry(),
                        )
                        Toast
                            .makeText(
                                App.getInstance(),
                                App.getInstance().getString(R.string.copy_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                },
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
