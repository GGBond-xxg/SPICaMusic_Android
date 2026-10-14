package me.spica27.spicamusic.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.R
import me.spica27.spicamusic.common.entity.GlassSettings
import me.spica27.spicamusic.ui.about.AboutScaffold
import me.spica27.spicamusic.ui.theme.GlassCard
import org.koin.compose.viewmodel.koinViewModel

class GlassSettingsScene : StackScene() {
    @Composable
    override fun Content() {
        val viewModel: SettingsViewModel = koinViewModel()
        val effects by viewModel.glassEffects.collectAsStateWithLifecycle()
        AboutScaffold(title = stringResource(R.string.glass_effect_settings)) {
            item { GlassCard { GlassSettingsPanel(GlassSettings.decode(effects), viewModel::setGlassEffects) } }
        }
    }
}
