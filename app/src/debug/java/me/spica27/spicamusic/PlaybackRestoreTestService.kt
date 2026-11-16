package me.spica27.spicamusic

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession

/** Isolated, silent engine for device restoration tests. Never uses the user's playback service. */
class PlaybackRestoreTestService : MediaLibraryService() {
    private lateinit var session: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply { volume = 0f }
        session = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {}).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session.player.release()
        session.release()
        super.onDestroy()
    }
}
