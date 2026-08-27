package me.spica27.spicamusic.ui.player

import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import me.spica27.spicamusic.R
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.ui.widget.LyricsCustomFont
import me.spica27.spicamusic.ui.widget.LyricsDisplayMode
import me.spica27.spicamusic.ui.widget.LyricsEditorSheet
import me.spica27.spicamusic.ui.widget.LyricsFontIds
import me.spica27.spicamusic.ui.widget.LyricsTextAlignment
import me.spica27.spicamusic.ui.widget.LyricsUI
import me.spica27.spicamusic.ui.widget.customLyricsFontFile
import me.spica27.spicamusic.ui.widget.decodeLyricsCustomFonts
import me.spica27.spicamusic.ui.widget.deleteLyricsFont
import me.spica27.spicamusic.ui.widget.documentDisplayName
import me.spica27.spicamusic.ui.widget.encodeLyricsCustomFonts
import me.spica27.spicamusic.ui.widget.importLyricsFont
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

/**
 * 歌词面板
 *
 * 通用歌词展示组件，通过 [displayMode] 适配不同容器：
 * 全屏页面（[LyricsDisplayMode.Fullscreen]）与封面卡片等小尺寸场景（[LyricsDisplayMode.Compact]）
 *
 * 功能：
 * - 自动搜索歌词，优先使用缓存
 * - 歌词偏移量调节（持久化到数据库）
 * - 多歌词源切换（通过预览面板选择后缓存）
 */
@Composable
fun LyricsPanel(
    modifier: Modifier = Modifier,
    displayMode: LyricsDisplayMode = LyricsDisplayMode.Fullscreen,
    showEditor: Boolean = false,
    onEditorDismiss: () -> Unit = {},
) {
    // Activity 作用域共享实例：与 mini 歌词同源，
    // 此处切换歌词源 / 调整偏移量会同步反映到 mini 歌词
    val viewModel: LyricsViewModel = koinActivityViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferencesManager: PreferencesManager = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val alignmentValue by
        preferencesManager
            .getString(
                PreferencesManager.Keys.LYRICS_TEXT_ALIGNMENT,
                LyricsTextAlignment.Start.value,
            ).collectAsStateWithLifecycle(initialValue = LyricsTextAlignment.Start.value)
    val textAlignment = remember(alignmentValue) { LyricsTextAlignment.fromValue(alignmentValue) }
    val textScale by
        preferencesManager
            .getFloat(PreferencesManager.Keys.LYRICS_TEXT_SCALE, 1f)
            .collectAsStateWithLifecycle(initialValue = 1f)
    val lineSpacing by
        preferencesManager
            .getFloat(PreferencesManager.Keys.LYRICS_LINE_SPACING, 1f)
            .collectAsStateWithLifecycle(initialValue = 1f)
    val selectedFontId by
        preferencesManager
            .getString(PreferencesManager.Keys.LYRICS_FONT_ID, LyricsFontIds.DEFAULT)
            .collectAsStateWithLifecycle(initialValue = LyricsFontIds.DEFAULT)
    val customFontsValue by
        preferencesManager
            .getString(PreferencesManager.Keys.LYRICS_CUSTOM_FONTS, "[]")
            .collectAsStateWithLifecycle(initialValue = "[]")
    val customFonts = remember(customFontsValue) { decodeLyricsCustomFonts(customFontsValue) }
    val lyricsFontFamily =
        remember(context, selectedFontId, customFonts) {
            when (selectedFontId) {
                LyricsFontIds.DEFAULT -> null
                LyricsFontIds.MI_SANS -> FontFamily(Font(R.font.misans_regular))
                else ->
                    customFonts
                        .firstOrNull { it.id == selectedFontId }
                        ?.let { customFont ->
                            runCatching {
                                FontFamily(Typeface.createFromFile(customLyricsFontFile(context, customFont.id)))
                            }.getOrNull()
                        }
            }
        }

    var pendingFontUri by remember { mutableStateOf<Uri?>(null) }
    var pendingFontName by remember { mutableStateOf("") }
    var fontImporting by remember { mutableStateOf(false) }
    var fontImportError by remember { mutableStateOf<String?>(null) }
    val fontPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pendingFontUri = uri
                pendingFontName = documentDisplayName(context, uri)
                fontImportError = null
            }
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val isAppInForeground =
        remember(lifecycleState) {
            lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        }

    // 当前播放时间（帧级更新，保留在 Composable 中因为依赖 awaitFrame）
    var currentTime by remember { mutableLongStateOf(viewModel.getCurrentPositionMs()) }
    LaunchedEffect(isAppInForeground) {
        if (!isAppInForeground) return@LaunchedEffect
        while (isAppInForeground) {
            awaitFrame()
            currentTime = viewModel.getCurrentPositionMs()
        }
    }

    if (showEditor) {
        LyricsEditorSheet(
            offsetMs = uiState.lyricsOffsetMs,
            textAlignment = textAlignment,
            textScale = textScale,
            lineSpacing = lineSpacing,
            selectedFontId = selectedFontId,
            customFonts = customFonts,
            lyricSources = uiState.allLyricSources,
            currentLyricSourceIndex = uiState.currentSourceIndex,
            onOffsetChange = viewModel::updateOffset,
            onTextAlignmentChange = { alignment ->
                coroutineScope.launch {
                    preferencesManager.setString(
                        PreferencesManager.Keys.LYRICS_TEXT_ALIGNMENT,
                        alignment.value,
                    )
                }
            },
            onTextScaleChange = { scale ->
                coroutineScope.launch {
                    preferencesManager.setFloat(PreferencesManager.Keys.LYRICS_TEXT_SCALE, scale)
                }
            },
            onLineSpacingChange = { spacing ->
                coroutineScope.launch {
                    preferencesManager.setFloat(PreferencesManager.Keys.LYRICS_LINE_SPACING, spacing)
                }
            },
            onFontSelected = { id ->
                coroutineScope.launch {
                    preferencesManager.setString(PreferencesManager.Keys.LYRICS_FONT_ID, id)
                }
            },
            onFontDeleted = { font ->
                coroutineScope.launch {
                    if (selectedFontId == font.id) {
                        preferencesManager.setString(
                            PreferencesManager.Keys.LYRICS_FONT_ID,
                            LyricsFontIds.DEFAULT,
                        )
                    }
                    preferencesManager.setString(
                        PreferencesManager.Keys.LYRICS_CUSTOM_FONTS,
                        encodeLyricsCustomFonts(customFonts.filterNot { it.id == font.id }),
                    )
                    deleteLyricsFont(context, font.id)
                }
            },
            onAddFont = {
                fontPicker.launch(
                    arrayOf(
                        "font/ttf",
                        "font/sfnt",
                        "font/collection",
                        "application/x-font-ttf",
                        "application/x-font-ttc",
                        "application/font-sfnt",
                    ),
                )
            },
            onLyricSourceSelected = viewModel::selectAndSaveLyricSource,
            onDismiss = onEditorDismiss,
        )
    }

    pendingFontUri?.let { uri ->
        AlertDialog(
            onDismissRequest = {
                if (!fontImporting) pendingFontUri = null
            },
            title = { Text(stringResource(R.string.name_font)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pendingFontName,
                        onValueChange = {
                            pendingFontName = it
                            fontImportError = null
                        },
                        enabled = !fontImporting,
                        singleLine = true,
                        label = { Text(stringResource(R.string.font_name)) },
                    )
                    fontImportError?.let { error ->
                        Text(
                            text = stringResource(R.string.font_import_failed, error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !fontImporting,
                    onClick = { pendingFontUri = null },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pendingFontName.isNotBlank() && !fontImporting,
                    onClick = {
                        fontImporting = true
                        fontImportError = null
                        coroutineScope.launch {
                            runCatching {
                                importLyricsFont(context, uri, pendingFontName)
                            }.onSuccess { importedFont: LyricsCustomFont ->
                                val updatedFonts = customFonts + importedFont
                                preferencesManager.setString(
                                    PreferencesManager.Keys.LYRICS_CUSTOM_FONTS,
                                    encodeLyricsCustomFonts(updatedFonts),
                                )
                                preferencesManager.setString(
                                    PreferencesManager.Keys.LYRICS_FONT_ID,
                                    importedFont.id,
                                )
                                pendingFontUri = null
                            }.onFailure { error ->
                                fontImportError = error.message ?: error.javaClass.simpleName
                            }
                            fontImporting = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage!!,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
            uiState.lyrics != null -> {
                LyricsUI(
                    modifier = Modifier.fillMaxSize(),
                    lyric = ImmutableList.copyOf(uiState.lyrics!!),
                    currentTime = currentTime + uiState.lyricsOffsetMs,
                    displayMode = displayMode,
                    textAlignment = textAlignment,
                    textScale = textScale,
                    lineSpacing = lineSpacing,
                    fontFamily = lyricsFontFamily,
                    onSeekToTime = { posMs ->
                        viewModel.seekToAndPlay((posMs - uiState.lyricsOffsetMs).coerceAtLeast(0L))
                    },
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.waiting_to_play),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
