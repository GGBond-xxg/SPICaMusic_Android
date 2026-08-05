package me.spica27.spicamusic

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Receives audio intents from file managers and the system default-music-app role.
 *
 * Keeping this entry point separate from [MainActivity] matches Android's media-app
 * contract while still letting the single-task main activity own playback and UI state.
 */
class ExternalAudioActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(intent)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
