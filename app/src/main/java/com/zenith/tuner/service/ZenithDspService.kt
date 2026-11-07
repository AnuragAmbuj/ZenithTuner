package com.zenith.tuner.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.zenith.tuner.MainActivity
import com.zenith.tuner.R
import com.zenith.tuner.ZenithApplication
import com.zenith.tuner.dsp.AcousticState
import com.zenith.tuner.dsp.DynamicsProcessingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android 12+ Foreground Service hosting the DynamicsProcessing engine.
 * Hooks into global audio session (0) and intercepted player sessions.
 */
class ZenithDspService : LifecycleService() {

    companion object {
        private const val TAG = "ZenithDspService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.zenith.tuner.ACTION_START"
        const val ACTION_STOP = "com.zenith.tuner.ACTION_STOP"
        const val ACTION_TOGGLE_DSP = "com.zenith.tuner.ACTION_TOGGLE_DSP"
        const val ACTION_TOGGLE_ANTI_MUD = "com.zenith.tuner.ACTION_TOGGLE_ANTI_MUD"
        const val ACTION_SESSION_OPEN = "com.zenith.tuner.ACTION_SESSION_OPEN"
        const val ACTION_SESSION_CLOSE = "com.zenith.tuner.ACTION_SESSION_CLOSE"
        const val EXTRA_SESSION_ID = "extra_audio_session_id"

        @Volatile
        private var instance: ZenithDspService? = null

        fun getInstance(): ZenithDspService? = instance

        fun startService(context: Context) {
            val intent = Intent(context, ZenithDspService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val binder = LocalBinder()
    private val dynamicsManager = DynamicsProcessingManager()

    private val _stateFlow = MutableStateFlow(dynamicsManager.getCurrentState())
    val stateFlow: StateFlow<AcousticState> = _stateFlow.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ZenithDspService = this@ZenithDspService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "ZenithDspService created.")

        // Hook into global session 0 (captures overall media output)
        dynamicsManager.attachSession(DynamicsProcessingManager.GLOBAL_AUDIO_SESSION_ID)
        updateState()

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.action?.let { action ->
            when (action) {
                ACTION_START -> {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
                ACTION_TOGGLE_DSP -> {
                    val currentState = _stateFlow.value
                    setDspEnabled(!currentState.isDspEnabled)
                }
                ACTION_TOGGLE_ANTI_MUD -> {
                    val currentState = _stateFlow.value
                    setAntiMudEnabled(!currentState.ancAntiMudEnabled)
                }
                ACTION_SESSION_OPEN -> {
                    val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                    if (sessionId > 0) {
                        dynamicsManager.attachSession(sessionId)
                        updateState()
                    }
                }
                ACTION_SESSION_CLOSE -> {
                    val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                    if (sessionId > 0) {
                        dynamicsManager.detachSession(sessionId)
                        updateState()
                    }
                }
                ACTION_STOP -> {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    fun setDspEnabled(enabled: Boolean) {
        dynamicsManager.setEnabled(enabled)
        updateState()
        updateNotification()
    }

    fun setAntiMudEnabled(enabled: Boolean) {
        dynamicsManager.updateParameters(ancAntiMudEnabled = enabled)
        updateState()
        updateNotification()
    }

    fun setSubBassWeight(weight: Float) {
        dynamicsManager.updateParameters(subBass = weight)
        updateState()
    }

    fun setVocalPresence(presence: Float) {
        dynamicsManager.updateParameters(vocals = presence)
        updateState()
    }

    fun onAudioSessionOpened(sessionId: Int) {
        dynamicsManager.attachSession(sessionId)
        updateState()
    }

    fun onAudioSessionClosed(sessionId: Int) {
        dynamicsManager.detachSession(sessionId)
        updateState()
    }

    private fun updateState() {
        _stateFlow.value = dynamicsManager.getCurrentState()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val state = _stateFlow.value
        val openAppIntent = Intent(this, MainActivity::class.java).let { intent ->
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val toggleDspIntent = Intent(this, ZenithDspService::class.java).apply {
            action = ACTION_TOGGLE_DSP
        }.let { intent ->
            PendingIntent.getService(
                this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val toggleAntiMudIntent = Intent(this, ZenithDspService::class.java).apply {
            action = ACTION_TOGGLE_ANTI_MUD
        }.let { intent ->
            PendingIntent.getService(
                this, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val dspStatus = if (state.isDspEnabled) "DSP ACTIVE" else "BYPASS"
        val antiMudStatus = if (state.ancAntiMudEnabled) "50dB Anti-Mud ON" else "Anti-Mud OFF"

        return NotificationCompat.Builder(this, ZenithApplication.CHANNEL_ID)
            .setContentTitle("boAt Nirvana Zenith: $dspStatus")
            .setContentText("$antiMudStatus • Preamp: ${String.format("%.1f", state.preampHeadroomDb)}dB")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(
                0,
                if (state.isDspEnabled) "Bypass" else "Engage",
                toggleDspIntent
            )
            .addAction(
                0,
                if (state.ancAntiMudEnabled) "Anti-Mud Off" else "Anti-Mud On",
                toggleAntiMudIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        dynamicsManager.releaseAll()
        instance = null
        Log.i(TAG, "ZenithDspService destroyed.")
    }
}
