package com.zenith.tuner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log

/**
 * Intercepts audio session control broadcasts from media players.
 */
class AudioSessionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AudioSessionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)

        if (sessionId <= 0) return

        Log.d(TAG, "Received audio session broadcast: $action, sessionId: $sessionId")

        val service = ZenithDspService.getInstance()
        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                if (service != null) {
                    service.onAudioSessionOpened(sessionId)
                } else {
                    val serviceIntent = Intent(context, ZenithDspService::class.java).apply {
                        this.action = ZenithDspService.ACTION_SESSION_OPEN
                        putExtra(ZenithDspService.EXTRA_SESSION_ID, sessionId)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                service?.onAudioSessionClosed(sessionId)
            }
        }
    }
}
