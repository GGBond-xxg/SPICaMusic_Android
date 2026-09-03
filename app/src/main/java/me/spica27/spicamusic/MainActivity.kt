package me.spica27.spicamusic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.jessyan.autosize.internal.CustomAdapt
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.player.api.IMusicPlayer
import me.spica27.spicamusic.player.api.PlayerAction
import me.spica27.spicamusic.ui.AppScaffold
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * 主 Activity
 */
class MainActivity :
    ComponentActivity(),
    CustomAdapt {
    private val musicPlayer: IMusicPlayer by inject()
    private val preferencesManager: PreferencesManager by inject()
    private var exitReceiverRegistered = false
    private var contentInstalled = false

    @Volatile
    private var firstComposeFrameReady = false

    private val exitReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == ACTION_EXIT_APP) finishAndRemoveTask()
            }
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Do not let Android remove the icon-free starting surface while the Activity still has
        // no Compose content. Without this guard the system reports the blank post-splash window
        // as "displayed", producing the visible skeleton -> white -> content sequence on a cold
        // process start. SideEffect below releases it only after the whole root tree composed.
        splashScreen.setKeepOnScreenCondition { !firstComposeFrameReady }
        splashScreen.setOnExitAnimationListener { splashView ->
            // The splash icon is transparent. Remove the system starting surface immediately
            // after Compose has produced the first frame so it cannot flash during locale updates.
            splashView.remove()
        }
        super.onCreate(savedInstanceState)

        ContextCompat.registerReceiver(
            this,
            exitReceiver,
            IntentFilter(ACTION_EXIT_APP),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        exitReceiverRegistered = true

        // 启用边缘到边缘显示
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                ),
        )

        // Match BondMail's launch ordering: keep the icon-free system starting surface until the
        // small settings mirror is authoritative, then construct the Compose tree exactly once.
        // In particular this prevents Finder from first composing the default hero and replacing
        // the whole first screen when DataStore publishes the saved hero source a frame later.
        lifecycleScope.launch {
            withTimeoutOrNull(STARTUP_PRELOAD_TIMEOUT_MS) {
                preferencesManager.preloadRenderCache()
            }
            installContent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        playExternalAudio(intent)
    }

    override fun onUserLeaveHint() {
        Timber.tag("MainActivity").i(
            "onUserLeaveHint playing=${musicPlayer.isPlaying.value} " +
                "initialized=${musicPlayer.isInitialized.value}",
        )
        super.onUserLeaveHint()
    }

    override fun onPause() {
        Timber.tag("MainActivity").i(
            "onPause playing=${musicPlayer.isPlaying.value} finishing=$isFinishing",
        )
        super.onPause()
    }

    override fun onStop() {
        Timber.tag("MainActivity").i(
            "onStop playing=${musicPlayer.isPlaying.value} finishing=$isFinishing " +
                "changingConfiguration=$isChangingConfigurations",
        )
        super.onStop()
    }

    override fun onDestroy() {
        if (exitReceiverRegistered) {
            unregisterReceiver(exitReceiver)
            exitReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun isBaseOnWidth(): Boolean = true

    /**
     * 设计稿基准尺寸（dp）
     * 竖屏：375dp（手机设计稿）
     * 横屏：1024dp（平板/横屏设计稿）
     */
    override fun getSizeInDp(): Float = 375f

    private fun installContent() {
        if (contentInstalled || isFinishing || isDestroyed) return
        contentInstalled = true
        setContent {
            AppScaffold()
            SideEffect {
                firstComposeFrameReady = true
            }
        }
        playExternalAudio(intent)
        // SpicaPlayer exposes the cached item before MediaBrowser exists and every playback action
        // initializes on demand. Avoid eagerly starting PlaybackService here: on a cold launch it
        // restores and prepares the previous remote stream, doing several seconds of main-thread
        // Media3 work even when the user only wants to browse the library.
    }

    private fun playExternalAudio(intent: Intent?) {
        val uri =
            when (intent?.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND ->
                    intent.clipData
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.uri
                        ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                else -> null
            } ?: return

        val title =
            runCatching {
                contentResolver
                    .query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        cursor
                            .takeIf { it.moveToFirst() }
                            ?.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    }
            }.getOrNull()
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringBeforeLast('.')
                ?: getString(R.string.app_name)

        val mediaItem =
            MediaItem
                .Builder()
                .setMediaId("external:$uri")
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build(),
                ).build()

        lifecycleScope.launch {
            musicPlayer.init()
            val initialized =
                withTimeoutOrNull(EXTERNAL_PLAYBACK_READY_TIMEOUT_MS) {
                    musicPlayer.isInitialized.filter { it }.first()
                } != null
            if (!initialized) return@launch
            musicPlayer.doAction(PlayerAction.PlayMediaItems(listOf(mediaItem)))
        }
    }

    companion object {
        const val ACTION_EXIT_APP = "me.spica27.spicamusic.action.EXIT_APP"
        const val STARTUP_PRELOAD_TIMEOUT_MS = 1_500L
        const val EXTERNAL_PLAYBACK_READY_TIMEOUT_MS = 5_000L
    }
}
