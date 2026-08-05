package me.spica27.spicamusic.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build

object PlaybackAudioCapabilities {
    fun supportsFloatOutput(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val format =
            AudioFormat
                .Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(48_000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        val minimum =
            AudioTrack.getMinBufferSize(
                48_000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
        if (minimum <= 0) return false
        return runCatching {
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minimum.coerceAtLeast(4_096))
                .build()
                .useCompat { it.state == AudioTrack.STATE_INITIALIZED }
        }.getOrDefault(false)
    }

    fun usbOutput(context: Context): AudioDeviceInfo? {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return manager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull(::isUsbOutput)
    }

    fun displayName(device: AudioDeviceInfo?): String? =
        device
            ?.productName
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?: device?.let { "USB DAC" }

    fun isUsbOutput(device: AudioDeviceInfo): Boolean =
        device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY

    private inline fun <T> AudioTrack.useCompat(block: (AudioTrack) -> T): T =
        try {
            block(this)
        } finally {
            release()
        }
}
