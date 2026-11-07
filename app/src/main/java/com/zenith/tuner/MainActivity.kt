package com.zenith.tuner

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.zenith.tuner.dsp.AcousticState
import com.zenith.tuner.service.ZenithDspService
import com.zenith.tuner.ui.ZenithTunerScreen
import com.zenith.tuner.ui.theme.ZenithTunerTheme

class MainActivity : ComponentActivity() {

    private var dspService: ZenithDspService? by mutableStateOf(null)
    private var isBound by mutableStateOf(false)

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
        ActivityResultContracts.RequestPermission()
    ) { /* Gracefully handle notification permission */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkNotificationPermission()
        startAndBindService()

        setContent {
            ZenithTunerTheme {
                val service = dspService
                val state by if (service != null) {
                    service.stateFlow.collectAsState()
                } else {
                    remember { mutableStateOf(AcousticState()) }
                }

                ZenithTunerScreen(
                    state = state,
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

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startAndBindService() {
        ZenithDspService.startService(this)
        val intent = Intent(this, ZenithDspService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
