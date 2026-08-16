package me.spica27.spicamusic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import me.spica27.spicamusic.service.PlaybackService

/** Requests the system overlay permission before asking the playback service to show lyrics. */
class DesktopLyricsPermissionActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) sendPlaybackAction(PlaybackService.ACTION_SHOW_DESKTOP_LYRICS)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            sendPlaybackAction(PlaybackService.ACTION_TOGGLE_DESKTOP_LYRICS)
            finish()
            return
        }
        permissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun sendPlaybackAction(action: String) {
        startService(Intent(this, PlaybackService::class.java).setAction(action))
    }
}
