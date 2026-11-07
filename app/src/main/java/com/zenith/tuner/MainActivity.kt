package com.zenith.tuner

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.zenith.tuner.audio.VolumeManager
import com.zenith.tuner.dsp.AcousticState
import com.zenith.tuner.service.ZenithDspService
import com.zenith.tuner.ui.ZenithTunerScreen
import com.zenith.tuner.ui.theme.ZenithTunerTheme

class MainActivity : ComponentActivity() {

    private var dspService: ZenithDspService? by mutableStateOf(null)
    private var isBound by mutableStateOf(false)
    private lateinit var volumeManager: VolumeManager

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? ZenithDspService.LocalBinder
            dspService = localBinder?.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dspService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Gracefully handle notification and bluetooth permissions */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure hardware volume rocker always directs to media stream
        volumeControlStream = AudioManager.STREAM_MUSIC

        volumeManager = VolumeManager(this)
        volumeManager.startListening()

        enableEdgeToEdge()

        checkPermissions()
        startAndBindService()

        setContent {
            ZenithTunerTheme {
                val service = dspService
                val state by if (service != null) {
                    service.stateFlow.collectAsState()
                } else {
                    remember { mutableStateOf(AcousticState()) }
                }
                val volumeRatio by volumeManager.volumeLevel.collectAsState()

                ZenithTunerScreen(
                    state = state,
                    volumeRatio = volumeRatio,
                    digitalOutputGainDb = state.digitalOutputGainDb,
                    onVolumeChange = { ratio ->
                        volumeManager.setVolumeRatio(ratio)
                        service?.setVolumeRatio(ratio)
                    },
                    onToggleMute = {
                        volumeManager.toggleMute()
                    },
                    onDigitalGainChange = { gainDb ->
                        service?.setDigitalOutputGain(gainDb)
                    },
                    onToggleDsp = { enabled -> service?.setDspEnabled(enabled) },
                    onTabSelect = { tab -> service?.setTab(tab) },
                    onPresetSelect = { preset -> service?.applyPreset(preset) },
                    onToggleAntiMud = { enabled -> service?.setAntiMudEnabled(enabled) },
                    onSubBassChange = { weight -> service?.setSubBassWeight(weight) },
                    onVocalPresenceChange = { presence -> service?.setVocalPresence(presence) },
                    onCrossfeedChange = { amount -> service?.setCrossfeedAmount(amount) },
                    onParametricChange = { id, enabled, freq, q, gainDb ->
                        service?.updateParametricStage(id, enabled, freq, q, gainDb)
                    },
                    onPreampChange = { auto, manualDb ->
                        service?.updatePreamp(auto, manualDb)
                    },
                    onMbcChange = { settings ->
                        service?.updateMbcSettings(settings)
                    }
                )
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startAndBindService() {
        ZenithDspService.startService(this)
        val intent = Intent(this, ZenithDspService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        volumeManager.stopListening()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
