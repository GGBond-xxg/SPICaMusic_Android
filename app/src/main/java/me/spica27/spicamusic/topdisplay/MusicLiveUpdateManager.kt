package me.spica27.spicamusic.topdisplay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaButtonReceiver
import me.spica27.spicamusic.R
import timber.log.Timber
import java.util.Locale

data class MusicLiveUpdateState(
    val songId: String,
    val title: String,
    val artist: String,
    val lyric: String?,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val artworkUri: Uri?,
)

/**
 * Android 16+ promoted ongoing notification used in addition to the standard Media3 notification.
 *
 * This class deliberately has no player dependency. Playback state and controls continue to flow
 * through the existing MediaSession and MediaButtonReceiver.
 */
class MusicLiveUpdateManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 36

    fun canPostPromotedNotification(): Boolean {
        if (!isSupported() || !notificationsAllowed()) return false
        return runCatching {
            notificationManager.canPostPromotedNotifications()
        }.onFailure {
            Timber.tag(TAG).w(it, "Promoted notification capability check failed")
        }.getOrDefault(false)
    }

    fun showOrUpdate(state: MusicLiveUpdateState) {
        if (!isSupported() || !canPostPromotedNotification()) {
            cancel()
            return
        }
        runCatching {
            ensureChannel()
            notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
            Timber.tag(TAG).d(
                "Live Update published: songId=%s, playing=%s",
                state.songId,
                state.isPlaying,
            )
        }.onFailure {
            Timber.tag(TAG).w(it, "Live Update publish failed")
        }
    }

    fun updatePlaybackState(state: MusicLiveUpdateState) = showOrUpdate(state)

    fun updateLyric(state: MusicLiveUpdateState) = showOrUpdate(state)

    fun updatePosition(state: MusicLiveUpdateState) = showOrUpdate(state)

    fun cancel() {
        runCatching {
            notificationManager.cancel(NOTIFICATION_ID)
        }.onFailure {
            Timber.tag(TAG).w(it, "Live Update cancellation failed")
        }
        Timber.tag(TAG).d("Live Update cancelled")
    }

    private fun buildNotification(state: MusicLiveUpdateState): android.app.Notification {
        val progress =
            if (state.durationMs > 0L) {
                (
                    state.positionMs
                        .coerceIn(0L, state.durationMs)
                        .toDouble() /
                        state.durationMs.toDouble() *
                        PROGRESS_MAX
                ).toInt()
            } else {
                0
            }
        val remainingMs = (state.durationMs - state.positionMs).coerceAtLeast(0L)
        val shortText =
            if (state.isPlaying && state.durationMs > 0L) {
                formatRemaining(remainingMs)
            } else {
                appContext.getString(R.string.live_update_paused)
            }
        val body =
            state.lyric
                ?.takeIf(String::isNotBlank)
                ?: state.artist.takeIf(String::isNotBlank)
                ?: appContext.getString(R.string.live_update_playing)
        val style =
            NotificationCompat
                .ProgressStyle()
                .setProgress(progress)
                .setStyledByProgress(true)

        return NotificationCompat
            .Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(state.title.ifBlank { appContext.getString(R.string.live_update_playing) })
            .setContentText(body)
            .setSubText(state.artist)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(shortText)
            .setContentIntent(openPlayerPendingIntent())
            .addAction(
                android.R.drawable.ic_media_previous,
                appContext.getString(R.string.media3_controls_seek_to_previous_description),
                mediaButtonPendingIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, REQUEST_PREVIOUS),
            ).addAction(
                if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                appContext.getString(
                    if (state.isPlaying) {
                        R.string.media3_controls_pause_description
                    } else {
                        R.string.media3_controls_play_description
                    },
                ),
                mediaButtonPendingIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, REQUEST_PLAY_PAUSE),
            ).addAction(
                android.R.drawable.ic_media_next,
                appContext.getString(R.string.media3_controls_seek_to_next_description),
                mediaButtonPendingIntent(KeyEvent.KEYCODE_MEDIA_NEXT, REQUEST_NEXT),
            ).build()
    }

    private fun mediaButtonPendingIntent(
        keyCode: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(appContext, MediaButtonReceiver::class.java))
                .putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    KeyEvent(KeyEvent.ACTION_DOWN, keyCode),
                )
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openPlayerPendingIntent(): PendingIntent {
        val intent =
            appContext.packageManager
                .getLaunchIntentForPackage(appContext.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: Intent()
        return PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN_PLAYER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = appContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.live_update_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = appContext.getString(R.string.live_update_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun formatRemaining(remainingMs: Long): String {
        val totalSeconds = remainingMs / 1_000L
        return String.format(
            Locale.ROOT,
            "%d:%02d",
            totalSeconds / 60L,
            totalSeconds % 60L,
        )
    }

    private companion object {
        const val TAG = "MusicLiveUpdate"
        const val CHANNEL_ID = "spica_music_live_update"
        const val NOTIFICATION_ID = 2_028
        const val PROGRESS_MAX = 1_000
        const val REQUEST_OPEN_PLAYER = 20_281
        const val REQUEST_PREVIOUS = 20_282
        const val REQUEST_PLAY_PAUSE = 20_283
        const val REQUEST_NEXT = 20_284
    }
}
