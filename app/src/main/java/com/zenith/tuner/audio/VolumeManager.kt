package com.zenith.tuner.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages bidirectional Android media stream volume control and observation.
 * Syncs seamlessly with hardware volume buttons, Bluetooth earbud taps, and UI slider.
 */
class VolumeManager(private val context: Context) {

    companion object {
        private const val TAG = "VolumeManager"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    val minVolume: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }

    private val _volumeLevel = MutableStateFlow(getCurrentVolumeRatio())
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    private var previousVolume: Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (streamType == AudioManager.STREAM_MUSIC || streamType == -1) {
                    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    if (current > 0) previousVolume = current
                    _volumeLevel.value = getCurrentVolumeRatio()
                }
            }
        }
    }

    fun startListening() {
        if (!isRegistered) {
            try {
                val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                context.registerReceiver(receiver, filter)
                isRegistered = true
                _volumeLevel.value = getCurrentVolumeRatio()
                Log.d(TAG, "Volume listener registered.")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to register volume receiver", e)
            }
        }
    }

    fun stopListening() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(receiver)
                isRegistered = false
                Log.d(TAG, "Volume listener unregistered.")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to unregister volume receiver", e)
            }
        }
    }

    fun setVolumeRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0f, 1f)
        val range = maxVolume - minVolume
        val target = (minVolume + (range * clamped)).toInt().coerceIn(minVolume, maxVolume)
        if (target > 0) previousVolume = target
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            _volumeLevel.value = clamped
        } catch (e: Throwable) {
            Log.e(TAG, "Error setting media volume", e)
        }
    }

    fun toggleMute() {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            previousVolume = current
            setVolumeRatio(0f)
        } else {
            val restoreRatio = (previousVolume.toFloat() / maxVolume).coerceIn(0.1f, 1f)
            setVolumeRatio(restoreRatio)
        }
    }

    private fun getCurrentVolumeRatio(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val range = (maxVolume - minVolume).toFloat()
        return if (range > 0f) {
            ((current - minVolume).toFloat() / range).coerceIn(0f, 1f)
        } else {
            0.5f
        }
    }
}
