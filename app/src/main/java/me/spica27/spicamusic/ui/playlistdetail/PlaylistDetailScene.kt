package me.spica27.spicamusic.ui.playlistdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.spica27.navkit.geometry.GeometryTransition
import me.spica27.navkit.scene.StackScene
import me.spica27.spicamusic.common.entity.Playlist
import me.spica27.spicamusic.ui.widget.PlaylistCoverView

class PlaylistDetailScene(
    private val playlist: Playlist,
    private val coverAlbumIds: List<Long> = emptyList(),
    private val transition: GeometryTransition? = null,
) : StackScene() {
    private var skipGeometryOnExit = false
    override val geometryTransition: GeometryTransition? = transition
    override val transitionShadowEnabled: Boolean = transition == null
    override val transitionScaleEnabled: Boolean = transition == null
    override val transitionSlideEnabled: Boolean = transition == null
    override val transitionFadeEnabled: Boolean = transition != null
    override val compressesPreviousScene: Boolean = transition == null

    @Composable
    override fun FloatingContent() {
        PlaylistCoverView(albumIds = coverAlbumIds, modifier = Modifier.fillMaxSize(), iconSize = 52.dp)
    }

    @Composable
    override fun Content() {
        PlaylistDetailScreen(
            playlist = playlist,
            geometryTransition = transition,
            onPlaylistDeleted = { skipGeometryOnExit = true },
        )
    }

    override suspend fun onPush() {
        super.onPush()
        geometryTransition?.reset()
    }

    override suspend fun onAppear() {
        coroutineScope {
            launch { super.onAppear() }
            launch { geometryTransition?.animateForwardWhenTargetReady() }
        }
    }

    override suspend fun onDisappear() {
        coroutineScope {
            launch { super.onDisappear() }
            if (!skipGeometryOnExit) {
                launch { geometryTransition?.animateReverse() }
            }
        }
    }
}
