package me.spica27.spicamusic.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.common.collect.ImmutableList
import me.spica27.navkit.path.LocalNavigationPath
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.AppLocaleController
import me.spica27.spicamusic.R
import me.spica27.spicamusic.common.entity.DynamicCoverType
import me.spica27.spicamusic.common.entity.DynamicSpectrumBackground
import me.spica27.spicamusic.common.entity.FinderHeroSource
import me.spica27.spicamusic.common.entity.ProgressBarStyle
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.common.entity.ThemeMode
import me.spica27.spicamusic.topdisplay.TopDisplayMode
import me.spica27.spicamusic.ui.about.AboutScene
import me.spica27.spicamusic.ui.audioeffects.AudioEffectsScene
import me.spica27.spicamusic.ui.theme.EaseOutEmphasized
import me.spica27.spicamusic.ui.theme.LayoutTokens
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.clickHighlight
import me.spica27.spicamusic.ui.widget.rememberIOSOverScrollEffect
import org.koin.compose.viewmodel.koinViewModel

class SettingsScene : StackScene() {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val path = LocalNavigationPath.current
        val context = LocalContext.current
        val viewModel: SettingsViewModel = koinViewModel()

        val themeModeValue by viewModel.themeMode.collectAsStateWithLifecycle()
        val themeMode = ThemeMode.fromString(themeModeValue)
        val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
        val topDisplayModeValue by viewModel.topDisplayMode.collectAsStateWithLifecycle()
        val spectrumValue by viewModel.dynamicSpectrumBackground.collectAsStateWithLifecycle()
        val coverTypeValue by viewModel.dynamicCoverType.collectAsStateWithLifecycle()
        val progressBarStyleValue by viewModel.progressBarStyle.collectAsStateWithLifecycle()
        val finderHeroSourceValue by viewModel.finderHeroSource.collectAsStateWithLifecycle()
        val themeColorStyleValue by viewModel.themeColorStyle.collectAsStateWithLifecycle()
        val circularRevealEnabled by viewModel.circularRevealEnabled.collectAsStateWithLifecycle()
        val spectrumTopGlowLabel = stringResource(R.string.dynamic_spectrum_top_glow)
        val spectrumLiquidAuroraLabel = stringResource(R.string.dynamic_spectrum_liquid_aurora)
        val spectrumFluidEffectLabel = stringResource(R.string.dynamic_spectrum_fluid_effect)
        val spectrumFluidCoverLabel = stringResource(R.string.dynamic_spectrum_fluid_cover)
        val spectrumBlurCoverLabel = stringResource(R.string.dynamic_spectrum_blur_cover)
        val spectrumOffLabel = stringResource(R.string.dynamic_spectrum_off)
        val spectrumLabels =
            mapOf(
                DynamicSpectrumBackground.TopGlow.value to spectrumTopGlowLabel,
                DynamicSpectrumBackground.LiquidAurora.value to spectrumLiquidAuroraLabel,
                DynamicSpectrumBackground.EffectShader.value to spectrumFluidEffectLabel,
                DynamicSpectrumBackground.FluidWarp.value to spectrumFluidCoverLabel,
                DynamicSpectrumBackground.BlurCover.value to spectrumBlurCoverLabel,
                DynamicSpectrumBackground.OFF.value to spectrumOffLabel,
            )
        val spectrumName = spectrumLabels[spectrumValue] ?: spectrumOffLabel
        val spectrumOptions =
            ImmutableList.copyOf(
                DynamicSpectrumBackground.presets.map { mode ->
                    SelectOption(mode.value, spectrumLabels.getValue(mode.value))
                },
            )
        val coverShiningStarsLabel = stringResource(R.string.dynamic_cover_shining_stars)
        val coverAudioCityLabel = stringResource(R.string.dynamic_cover_audio_city)
        val coverOffLabel = stringResource(R.string.dynamic_cover_off)
        val coverLabels =
            mapOf(
                DynamicCoverType.ShiningStars.value to coverShiningStarsLabel,
                DynamicCoverType.AudioCity.value to coverAudioCityLabel,
                DynamicCoverType.OFF.value to coverOffLabel,
            )
        val coverName = coverLabels[coverTypeValue] ?: coverShiningStarsLabel
        val texturedLabel = stringResource(R.string.theme_color_style_textured)
        val flatLabel = stringResource(R.string.theme_color_style_flat)
        val themeColorStyleName =
            when (ThemeColorStyle.fromString(themeColorStyleValue)) {
                ThemeColorStyle.Textured -> texturedLabel
                ThemeColorStyle.Flat -> flatLabel
            }
        val themeColorStyleOptions =
            remember(texturedLabel, flatLabel) {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption(ThemeColorStyle.Textured.value, texturedLabel),
                        SelectOption(ThemeColorStyle.Flat.value, flatLabel),
                    ),
                )
            }
        val themeModeSystemLabel = stringResource(R.string.settings_theme_mode_system)
        val themeModeLightLabel = stringResource(R.string.settings_theme_mode_light)
        val themeModeDarkLabel = stringResource(R.string.settings_theme_mode_dark)
        val themeModeOptions =
            remember(themeModeSystemLabel, themeModeLightLabel, themeModeDarkLabel) {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption(ThemeMode.SYSTEM.value, themeModeSystemLabel),
                        SelectOption(ThemeMode.LIGHT.value, themeModeLightLabel),
                        SelectOption(ThemeMode.DARK.value, themeModeDarkLabel),
                    ),
                )
            }
        val themeModeName =
            themeModeOptions.firstOrNull { it.value == themeMode.value }?.label
                ?: themeModeSystemLabel
        val languageSystemLabel = stringResource(R.string.settings_language_system)
        val languageEnglishLabel = stringResource(R.string.settings_language_english)
        val languageSimplifiedChineseLabel =
            stringResource(R.string.settings_language_simplified_chinese)
        val languageTraditionalChineseLabel =
            stringResource(R.string.settings_language_traditional_chinese)
        val languageOptions =
            remember(
                languageSystemLabel,
                languageEnglishLabel,
                languageSimplifiedChineseLabel,
                languageTraditionalChineseLabel,
            ) {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption(AppLocaleController.LANGUAGE_SYSTEM, languageSystemLabel),
                        SelectOption(AppLocaleController.LANGUAGE_ENGLISH, languageEnglishLabel),
                        SelectOption(
                            AppLocaleController.LANGUAGE_SIMPLIFIED_CHINESE,
                            languageSimplifiedChineseLabel,
                        ),
                        SelectOption(
                            AppLocaleController.LANGUAGE_TRADITIONAL_CHINESE,
                            languageTraditionalChineseLabel,
                        ),
                    ),
                )
            }
        val currentLanguage = AppLocaleController.currentLanguage(context)
        val currentLanguageName =
            languageOptions.firstOrNull { it.value == currentLanguage }?.label
                ?: languageSystemLabel
        val dynamicWaveformLabel = stringResource(R.string.progress_bar_style_dynamic_waveform)
        val timeDomainWaveformLabel = stringResource(R.string.progress_bar_style_time_domain_waveform)
        val expressiveWavyLabel = stringResource(R.string.progress_bar_style_expressive_wavy)
        val progressBarStyleName =
            when (ProgressBarStyle.fromString(progressBarStyleValue)) {
                ProgressBarStyle.ExpressiveWavy -> expressiveWavyLabel
                ProgressBarStyle.DynamicWaveform -> dynamicWaveformLabel
                ProgressBarStyle.TimeDomainWaveform -> timeDomainWaveformLabel
            }
        val progressBarStyleOptions =
            remember(expressiveWavyLabel, dynamicWaveformLabel, timeDomainWaveformLabel) {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption(ProgressBarStyle.ExpressiveWavy.value, expressiveWavyLabel),
                        SelectOption(ProgressBarStyle.DynamicWaveform.value, dynamicWaveformLabel),
                        SelectOption(ProgressBarStyle.TimeDomainWaveform.value, timeDomainWaveformLabel),
                    ),
                )
            }
        val finderFrequentLabel = stringResource(R.string.settings_finder_source_frequent)
        val finderDailyLabel = stringResource(R.string.settings_finder_source_daily)
        val finderHeroSourceOptions =
            remember(finderFrequentLabel, finderDailyLabel) {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption(FinderHeroSource.RECENT_FREQUENT.value, finderFrequentLabel),
                        SelectOption(FinderHeroSource.NETEASE_DAILY.value, finderDailyLabel),
                    ),
                )
            }
        val finderHeroSourceName =
            finderHeroSourceOptions
                .firstOrNull { it.value == finderHeroSourceValue }
                ?.label
                ?: finderFrequentLabel
        val topDisplayOffLabel = stringResource(R.string.top_display_mode_off)
        val topDisplayLyricLabel = stringResource(R.string.top_display_mode_status_lyric)
        val topDisplayLiveLabel = stringResource(R.string.top_display_mode_live_update)
        val topDisplayModeOptions =
            remember(topDisplayOffLabel, topDisplayLyricLabel, topDisplayLiveLabel) {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption(TopDisplayMode.OFF.value, topDisplayOffLabel),
                        SelectOption(TopDisplayMode.STATUS_LYRIC.value, topDisplayLyricLabel),
                        SelectOption(TopDisplayMode.LIVE_UPDATE.value, topDisplayLiveLabel),
                    ),
                )
            }
        val topDisplayMode = TopDisplayMode.fromString(topDisplayModeValue)
        val fadeDurationOptions =
            remember {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption("2000", "2 s"),
                        SelectOption("4000", "4 s"),
                        SelectOption("6000", "6 s"),
                        SelectOption("8000", "8 s"),
                    ),
                )
            }
        val cloudCacheOptions =
            remember {
                ImmutableList.copyOf(
                    listOf(
                        SelectOption("256", "256 MB"),
                        SelectOption("512", "512 MB"),
                        SelectOption("1024", "1 GB"),
                        SelectOption("2048", "2 GB"),
                        SelectOption("4096", "4 GB"),
                        SelectOption("8192", "8 GB"),
                    ),
                )
            }
        val topDisplaySubtitle =
            when (topDisplayMode) {
                TopDisplayMode.OFF -> stringResource(R.string.top_display_mode_off_subtitle)
                TopDisplayMode.STATUS_LYRIC -> stringResource(R.string.settings_lyricon_subtitle)
                TopDisplayMode.LIVE_UPDATE ->
                    if (!viewModel.liveUpdateSupported || !viewModel.promotedNotificationAllowed) {
                        stringResource(R.string.top_display_mode_live_update_unsupported)
                    } else {
                        stringResource(R.string.top_display_mode_live_update_subtitle)
                    }
            }
        // 与原项目一致：同一时间只在卡片内展开一组选项。
        var expandedRowKey by rememberSaveable { mutableStateOf<String?>(null) }
        val listState = rememberLazyListState()
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val mastheadGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = LayoutTokens.MusicHeaderHorizontalPadding,
                        end = LayoutTokens.MusicHeaderHorizontalPadding,
                        top = statusBarTop + 56.dp,
                        bottom = 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Large),
                overscrollEffect = rememberIOSOverScrollEffect(orientation = Orientation.Vertical),
            ) {
                item(key = "settings_masthead") {
                    SettingsMasthead(
                        modifier =
                            Modifier
                                .padding(top = Spacing.Large),
                    )
                }
                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_appearance),
                        subtitle = stringResource(R.string.settings_appearance_subtitle),
                    ) {
                        ModernSettingsSelectItem(
                            rowKey = "color_style",
                            title = stringResource(R.string.settings_theme_color_style),
                            subtitle = themeColorStyleName,
                            icon = Icons.Default.Palette,
                            options = themeColorStyleOptions,
                            currentValue = themeColorStyleValue,
                            expandedKey = expandedRowKey,
                            onExpandChange = { expandedRowKey = it },
                            onValueChange = viewModel::setThemeColorStyle,
                        )
                        SettingsItemDivider()
                        ModernSettingsSelectItem(
                            rowKey = "theme_mode",
                            title = stringResource(R.string.settings_theme_mode_title),
                            subtitle = themeModeName,
                            icon = Icons.Default.Brightness6,
                            options = themeModeOptions,
                            currentValue = themeMode.value,
                            expandedKey = expandedRowKey,
                            onExpandChange = { expandedRowKey = it },
                            onValueChange = viewModel::setThemeMode,
                        )
                        SettingsItemDivider()
                        ModernSettingsSelectItem(
                            rowKey = "finder_hero_source",
                            title = stringResource(R.string.settings_finder_source_title),
                            subtitle = finderHeroSourceName,
                            icon = Icons.Default.AutoAwesome,
                            options = finderHeroSourceOptions,
                            currentValue = finderHeroSourceValue,
                            expandedKey = expandedRowKey,
                            onExpandChange = { expandedRowKey = it },
                            onValueChange = viewModel::setFinderHeroSource,
                        )
                        SettingsItemDivider()
                        ModernSettingsSwitchItem(
                            title = stringResource(R.string.settings_circular_reveal_title),
                            subtitle =
                                stringResource(R.string.settings_circular_reveal_subtitle),
                            icon = Icons.Default.Animation,
                            checked = circularRevealEnabled,
                            onCheckedChange = viewModel::setCircularRevealEnabled,
                        )
                    }
                }

                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_player_page),
                        subtitle = stringResource(R.string.settings_player_page_subtitle),
                    ) {
                        ModernSettingsSelectItem(
                            rowKey = "player_background",
                            title = stringResource(R.string.settings_dynamic_spectrum),
                            subtitle = spectrumName,
                            icon = Icons.Default.LensBlur,
                            options = spectrumOptions,
                            currentValue = spectrumValue,
                            expandedKey = expandedRowKey,
                            onExpandChange = { expandedRowKey = it },
                            onValueChange = viewModel::setDynamicSpectrumBackground,
                        )
                        SettingsItemDivider()
                        ModernSettingsSelectItem(
                            rowKey = "progress_bar_style",
                            title = stringResource(R.string.settings_progress_bar_style),
                            subtitle = progressBarStyleName,
                            icon = Icons.Default.Percent,
                            options = progressBarStyleOptions,
                            currentValue = progressBarStyleValue,
                            expandedKey = expandedRowKey,
                            onExpandChange = { expandedRowKey = it },
                            onValueChange = viewModel::setProgressBarStyle,
                        )
                    }
                }

                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_playback),
                        subtitle = stringResource(R.string.settings_playback_subtitle),
                    ) {
                        SettingsRow(
                            title = stringResource(R.string.settings_sound_effects),
                            subtitle = stringResource(R.string.settings_sound_effects_subtitle),
                            icon = Icons.Default.GraphicEq,
                            selected = false,
                            onClick = { path.push(AudioEffectsScene()) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        SettingsItemDivider()
                        ModernSettingsSwitchItem(
                            title = stringResource(R.string.settings_keep_screen_on),
                            subtitle = stringResource(R.string.settings_keep_screen_on_subtitle),
                            icon = Icons.Default.Visibility,
                            checked = keepScreenOn,
                            onCheckedChange = viewModel::setKeepScreenOn,
                        )
                    }
                }

                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_other),
                        subtitle = stringResource(R.string.settings_other_subtitle),
                    ) {
                        ModernSettingsSelectItem(
                            rowKey = "language",
                            title = stringResource(R.string.settings_language_title),
                            subtitle = currentLanguageName,
                            icon = Icons.Default.Language,
                            options = languageOptions,
                            currentValue = currentLanguage,
                            expandedKey = expandedRowKey,
                            onExpandChange = { expandedRowKey = it },
                            onValueChange = { AppLocaleController.setLanguage(context, it) },
                        )
                        SettingsItemDivider()
                        ModernSettingsSelectItem(
                            rowKey = "notification_mode",
                            title = stringResource(R.string.top_display_mode_title),
                            subtitle = topDisplaySubtitle,
                            icon = Icons.Default.NotificationsActive,
                            options = topDisplayModeOptions,
                            currentValue = topDisplayMode.value,
                            expandedKey = expandedRowKey,
                            onExpandChange = { expandedRowKey = it },
                            onValueChange = viewModel::setTopDisplayMode,
                        )
                    }
                }

                item {
                    SettingsSectionCard(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_subtitle),
                    ) {
                        SettingsRow(
                            title = stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_about_subtitle),
                            icon = Icons.Default.Info,
                            selected = false,
                            onClick = { path.push(AboutScene()) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }

            SettingsTopBar(
                title = stringResource(R.string.finder_settings_title),
                listState = listState,
                solid = mastheadGone,
                onBack = { path.popTop() },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun SettingsMasthead(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.finder_settings_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_masthead_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsTopBar(
    title: String,
    listState: LazyListState,
    solid: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backgroundColor = MaterialTheme.colorScheme.background
    val collapse by
        remember(listState) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset / 96f).coerceIn(0f, 1f)
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarTop + 56.dp)
                .drawBehind { drawRect(color = backgroundColor.copy(alpha = collapse)) },
    ) {
        if (solid) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomStart),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
                    .padding(horizontal = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = collapse },
            )
        }
    }
}

@Composable
private fun SettingsAmbientBackground() {
    val transition = rememberInfiniteTransition(label = "settings_ambient")
    val drift by transition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 4600),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "settings_orb_drift",
    )

    Box(Modifier.fillMaxSize()) {
        AmbientOrb(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 48.dp, y = (-28).dp)
                    .graphicsLayer {
                        translationY = drift
                        translationX = -drift * 0.5f
                    },
        )
        AmbientOrb(
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-72).dp)
                    .graphicsLayer {
                        translationY = -drift * 0.7f
                        translationX = drift * 0.35f
                    },
        )
    }
}

@Composable
private fun AmbientOrb(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(220.dp)
                .blur(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                color,
                                color.copy(alpha = 0f),
                            ),
                    ),
                ),
    )
}

@Composable
private fun SettingsHeroCard(
    themeMode: ThemeMode,
    spectrumName: String,
    coverName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Shapes.ExtraLarge1CornerBasedShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                ).padding(Spacing.ExtraLarge),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Large)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Brightness6,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    Text(
                        text = stringResource(R.string.settings_hero_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.settings_hero_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                SettingsPill(
                    label =
                        when (themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
                        },
                )
                SettingsPill(label = spectrumName)
                SettingsPill(label = coverName)
            }
        }
    }
}

@Composable
private fun SettingsPill(label: String) {
    AnimatedContent(
        targetState = label,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "settings_pill_content",
    ) { targetLabel ->
        Text(
            text = targetLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f))
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(Shapes.ExtraLargeCornerBasedShape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f))
                .padding(vertical = Spacing.Large),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Small))
        content()
    }
}

@Composable
private fun ModernSettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val iconScale by animateFloatAsState(
        targetValue = if (checked) 1.08f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "settings_switch_icon_scale",
    )

    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        selected = checked,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        iconModifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale),
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun ModernSettingsSelectItem(
    rowKey: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    options: ImmutableList<SelectOption>,
    currentValue: String,
    expandedKey: String?,
    onExpandChange: (String?) -> Unit,
    onValueChange: (String) -> Unit,
) {
    val expanded = expandedKey == rowKey
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
        label = "settings_inline_chevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            selected = expanded,
            onClick = { onExpandChange(if (expanded) null else rowKey) },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                )
            },
        )

        AnimatedVisibility(
            visible = expanded,
            enter =
                expandVertically(
                    animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
                ) + fadeIn(tween(durationMillis = 180)),
            exit =
                shrinkVertically(
                    animationSpec = tween(durationMillis = 180, easing = EaseOutEmphasized),
                ) + fadeOut(tween(durationMillis = 140)),
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        start = 78.dp,
                        end = Spacing.Large,
                        top = Spacing.ExtraSmall,
                        bottom = Spacing.Small,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                options.forEach { option ->
                    InlineOptionCard(
                        rowKey = rowKey,
                        option = option,
                        icon = icon,
                        selected = option.value == currentValue,
                        onClick = { onValueChange(option.value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit,
) {
    val iconBackground by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        label = "settings_icon_background",
    )
    val iconTint by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "settings_icon_tint",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(Shapes.LargeCornerBasedShape)
                    .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = iconModifier,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailingContent()
    }
}

@Composable
private fun SettingsItemDivider() {
    Box(
        modifier =
            Modifier
                .padding(start = 78.dp, end = Spacing.Large)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun InlineOptionCard(
    rowKey: String,
    option: SelectOption,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
            },
        animationSpec = tween(durationMillis = 200, easing = EaseOutEmphasized),
        label = "settings_inline_option_background",
    )
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(Shapes.MediumCornerBasedShape)
                .background(background)
                .clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Icon(
            imageVector = optionIcon(rowKey, option.value, icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = selected,
            enter =
                fadeIn(tween(durationMillis = 180)) +
                    scaleIn(
                        animationSpec = tween(durationMillis = 220, easing = EaseOutEmphasized),
                        initialScale = 0.82f,
                    ),
            exit = fadeOut(tween(durationMillis = 120)),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 为每个展开选项提供独立图标，避免所有选项重复使用设置行图标。 */
private fun optionIcon(
    rowKey: String,
    value: String,
    fallback: ImageVector,
): ImageVector =
    when (rowKey) {
        "color_style" ->
            when (ThemeColorStyle.fromString(value)) {
                ThemeColorStyle.Textured -> Icons.Default.AutoAwesome
                ThemeColorStyle.Flat -> Icons.Default.Layers
            }
        "theme_mode" ->
            when (ThemeMode.fromString(value)) {
                ThemeMode.SYSTEM -> Icons.Default.Brightness6
                ThemeMode.LIGHT -> Icons.Default.WbSunny
                ThemeMode.DARK -> Icons.Default.DarkMode
            }
        "player_background" ->
            when (DynamicSpectrumBackground.fromString(value)) {
                DynamicSpectrumBackground.TopGlow -> Icons.Default.WbSunny
                DynamicSpectrumBackground.LiquidAurora -> Icons.Default.Waves
                DynamicSpectrumBackground.EffectShader -> Icons.Default.BlurOn
                DynamicSpectrumBackground.FluidWarp -> Icons.Default.LensBlur
                DynamicSpectrumBackground.BlurCover -> Icons.Default.Layers
                DynamicSpectrumBackground.OFF -> Icons.Default.PowerSettingsNew
            }
        "progress_bar_style" ->
            when (ProgressBarStyle.fromString(value)) {
                ProgressBarStyle.ExpressiveWavy -> Icons.Default.Waves
                ProgressBarStyle.DynamicWaveform -> Icons.Default.GraphicEq
                ProgressBarStyle.TimeDomainWaveform -> Icons.AutoMirrored.Filled.ShowChart
            }
        "language" ->
            when (value) {
                AppLocaleController.LANGUAGE_SYSTEM -> Icons.Default.Language
                AppLocaleController.LANGUAGE_ENGLISH -> Icons.Default.Translate
                AppLocaleController.LANGUAGE_SIMPLIFIED_CHINESE -> Icons.Default.LocationCity
                AppLocaleController.LANGUAGE_TRADITIONAL_CHINESE -> Icons.Default.Layers
                else -> fallback
            }
        "notification_mode" ->
            when (TopDisplayMode.fromString(value)) {
                TopDisplayMode.OFF -> Icons.Default.PowerSettingsNew
                TopDisplayMode.STATUS_LYRIC -> Icons.Default.NotificationsActive
                TopDisplayMode.LIVE_UPDATE -> Icons.Default.AutoAwesome
            }
        else -> fallback
    }
