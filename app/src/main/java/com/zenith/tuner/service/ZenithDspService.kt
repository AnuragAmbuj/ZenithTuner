package com.zenith.tuner.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.zenith.tuner.MainActivity
import com.zenith.tuner.R
import com.zenith.tuner.ZenithApplication
import com.zenith.tuner.dsp.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android 12+ Foreground Service hosting the DynamicsProcessing engine.
 * Full customization options for presets, parametric tuning, and MBC dynamics.
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

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            var sessionCountChanged = false
            for (config in configs) {
                val sessionId = extractSessionId(config)
                if (sessionId > 0) {
                    if (dynamicsManager.attachSession(sessionId)) {
                        sessionCountChanged = true
                    }
                }
            }
            if (sessionCountChanged) {
                updateState()
                updateNotification()
            }
        }
    }

    private fun extractSessionId(config: AudioPlaybackConfiguration): Int {
        // 1. Try getSessionId
        try {
            val method = config.javaClass.getMethod("getSessionId")
            val id = method.invoke(config) as? Int
            if (id != null && id > 0) return id
        } catch (_: Throwable) {}

        // 2. Try getClientAudioSessionId
        try {
            val method = config.javaClass.getMethod("getClientAudioSessionId")
            val id = method.invoke(config) as? Int
            if (id != null && id > 0) return id
        } catch (_: Throwable) {}

        // 3. Try reflection on mSessionId or mClientAudioSessionId
        for (fieldName in listOf("mSessionId", "mClientAudioSessionId")) {
            try {
                val field = config.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                val id = field.getInt(config)
                if (id > 0) return id
            } catch (_: Throwable) {}
        }

        // 4. Try parsing config.toString() with robust regex
        // Matches: "client sessionId: 2169", "sessionId=2169", "session:2169", etc.
        try {
            val str = config.toString()
            val match = Regex("(?i)session(?:Id)?\\s*[:=]\\s*(\\d+)").find(str)
            if (match != null) {
                val id = match.groupValues[1].toInt()
                if (id > 0) return id
            }
        } catch (_: Throwable) {}

        return -1
    }

    fun scanActiveSessions() {
        try {
            val activeConfigs = audioManager.activePlaybackConfigurations
            var changed = false
            for (config in activeConfigs) {
                val sessionId = extractSessionId(config)
                if (sessionId > 0) {
                    if (dynamicsManager.attachSession(sessionId)) {
                        changed = true
                        Log.i(TAG, "Discovered and attached to active player audio session $sessionId")
                    }
                }
            }
            if (changed) {
                updateState()
                updateNotification()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not query active playback configurations", e)
        }
    }

    private val _stateFlow = MutableStateFlow(dynamicsManager.getCurrentState())
    val stateFlow: StateFlow<AcousticState> = _stateFlow.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ZenithDspService = this@ZenithDspService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "ZenithDspService created.")

        // 1. Attach global session
        dynamicsManager.attachSession(DynamicsProcessingManager.GLOBAL_AUDIO_SESSION_ID)

        // 2. Query already active media playback sessions (Spotify, YouTube, Poweramp, etc.)
        scanActiveSessions()

        // 3. Register live playback listener for incoming media streams
        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register playback callback", e)
        }

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
                        updateNotification()
                    }
                }
                ACTION_SESSION_CLOSE -> {
                    val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                    if (sessionId > 0) {
                        dynamicsManager.detachSession(sessionId)
                        updateState()
                        updateNotification()
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

    fun setTab(tab: TunerTab) {
        dynamicsManager.setTab(tab)
        updateState()
    }

    fun applyPreset(preset: AcousticPreset) {
        dynamicsManager.applyPreset(preset)
        updateState()
        updateNotification()
    }

    fun setAntiMudEnabled(enabled: Boolean) {
        dynamicsManager.updateExpressParameters(ancAntiMudEnabled = enabled)
        updateState()
        updateNotification()
    }

    fun setAncOptimizationMode(mode: com.zenith.tuner.anc.AncOptimizationMode) {
        dynamicsManager.setAncOptimizationMode(mode)
        updateState()
        updateNotification()
    }

    fun setSubBassWeight(weight: Float) {
        dynamicsManager.updateExpressParameters(subBass = weight)
        updateState()
    }

    fun setVocalPresence(presence: Float) {
        dynamicsManager.updateExpressParameters(vocals = presence)
        updateState()
    }

    fun setCrossfeedAmount(amount: Float) {
        dynamicsManager.updateExpressParameters(crossfeed = amount)
        updateState()
    }

    fun updateParametricStage(
        stageId: Int,
        enabled: Boolean? = null,
        freq: Float? = null,
        q: Float? = null,
        gainDb: Float? = null
    ) {
        dynamicsManager.updateParametricStage(stageId, enabled, freq, q, gainDb)
        updateState()
    }

    fun updatePreamp(auto: Boolean, manualDb: Float) {
        dynamicsManager.updatePreamp(auto, manualDb)
        updateState()
    }

    fun setDigitalOutputGain(gainDb: Float) {
        dynamicsManager.updateDigitalOutputGain(gainDb)
        updateState()
    }

    fun setVolumeRatio(ratio: Float) {
        dynamicsManager.updateVolumeRatio(ratio)
        updateState()
    }

    fun updateMbcSettings(settings: MbcSettings) {
        dynamicsManager.updateMbcSettings(settings)
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

        val dspStatus = if (state.isDspEnabled) "ACTIVE" else "BYPASS"
        val presetName = state.activePreset.displayName

        return NotificationCompat.Builder(this, ZenithApplication.CHANNEL_ID)
            .setContentTitle("ZenithTuner: $presetName ($dspStatus)")
            .setContentText("Preamp: ${String.format("%.1f", state.preampHeadroomDb)}dB • Sessions: ${state.activeSessionCount}")
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
                if (state.ancAntiMudEnabled) "Anti-Mud On" else "Anti-Mud Off",
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
        try {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to unregister playback callback", e)
        }
        dynamicsManager.releaseAll()
        instance = null
        Log.i(TAG, "ZenithDspService destroyed.")
    }
}
