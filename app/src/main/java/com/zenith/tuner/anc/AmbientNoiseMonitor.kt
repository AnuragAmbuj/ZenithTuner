package com.zenith.tuner.anc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

data class AmbientNoiseReading(
    val splDb: Float = 42.0f,
    val environmentCategory: String = "Quiet Ambience",
    val cancellationHeadroomPercent: Int = 94,
    val recommendedMode: AncOptimizationMode = AncOptimizationMode.ULTRA_50DB,
    val isMicActive: Boolean = false
)

enum class AncOptimizationMode(
    val title: String,
    val subtitle: String,
    val midBassCutDb: Float,
    val hpfCutoffHz: Float,
    val damperRatio: Float,
    val ambientTarget: String
) {
    ULTRA_50DB(
        title = "Ultra 50dB Commute",
        subtitle = "Maximizes diaphragm headroom for heavy train, bus & flight rumble",
        midBassCutDb = -7.2f,
        hpfCutoffHz = 28.0f,
        damperRatio = 3.2f,
        ambientTarget = "Subway, Bus & Jet Engine (< 250Hz)"
    ),
    OFFICE_CHATTER(
        title = "Office & Voices",
        subtitle = "Damps HVAC drone and background speech without ear-pressure fatigue",
        midBassCutDb = -5.5f,
        hpfCutoffHz = 24.0f,
        damperRatio = 2.2f,
        ambientTarget = "HVAC Hum & Conversational Voices"
    ),
    WIND_OUTDOOR(
        title = "Wind Shield",
        subtitle = "Clamps turbulence buffeting on exterior feedforward microphones",
        midBassCutDb = -6.0f,
        hpfCutoffHz = 35.0f,
        damperRatio = 3.5f,
        ambientTarget = "Outdoor Wind Turbulence (< 80Hz)"
    ),
    ADAPTIVE_SMART(
        title = "Adaptive 6-Mic AI",
        subtitle = "Automatically adapts decoupling depth to real-time ambient sound",
        midBassCutDb = -6.5f,
        hpfCutoffHz = 26.0f,
        damperRatio = 2.6f,
        ambientTarget = "Dynamic Multi-Environment Calibration"
    )
}

/**
 * Monitors ambient room noise levels when ZenithTuner is in foreground.
 * Evaluates real-time sound pressure levels (SPL) to maximize 50dB Hybrid ANC cancellation.
 */
class AmbientNoiseMonitor(private val context: Context) {

    companion object {
        private const val TAG = "AmbientNoiseMonitor"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 1024
    }

    private val monitorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _readingFlow = MutableStateFlow(AmbientNoiseReading())
    val readingFlow: StateFlow<AmbientNoiseReading> = _readingFlow.asStateFlow()

    private var smoothedSpl = 42.0f

    fun startMonitoring() {
        if (recordingJob?.isActive == true) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.d(TAG, "Audio record permission not granted. Running acoustic telemetry simulation.")
            startSimulationLoop()
            return
        }

        recordingJob = monitorScope.launch {
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(BUFFER_SIZE * 2)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord could not initialize. Falling back to telemetry.")
                    startSimulationLoop()
                    return@launch
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(BUFFER_SIZE)

                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i].toDouble()
                            sumSq += sample * sample
                        }
                        val rms = sqrt(sumSq / read)
                        val rawSpl = if (rms > 1.0) {
                            (20.0 * log10(rms / 32767.0) + 90.0).toFloat().coerceIn(30.0f, 95.0f)
                        } else {
                            32.0f
                        }

                        // Exponential smoothing filter (alpha = 0.25)
                        smoothedSpl = (0.25f * rawSpl) + (0.75f * smoothedSpl)

                        val category = when {
                            smoothedSpl < 45.0f -> "Quiet Room Ambience"
                            smoothedSpl < 60.0f -> "Office & Moderate Noise"
                            smoothedSpl < 75.0f -> "Loud Environment / City"
                            else -> "Subway / High Noise Transit"
                        }

                        val recommended = when {
                            smoothedSpl > 68.0f -> AncOptimizationMode.ULTRA_50DB
                            smoothedSpl > 52.0f -> AncOptimizationMode.OFFICE_CHATTER
                            else -> AncOptimizationMode.ADAPTIVE_SMART
                        }

                        // Estimate diaphragm headroom percentage preserved
                        val headroom = (98 - (smoothedSpl * 0.15f)).toInt().coerceIn(75, 98)

                        _readingFlow.value = AmbientNoiseReading(
                            splDb = smoothedSpl,
                            environmentCategory = category,
                            cancellationHeadroomPercent = headroom,
                            recommendedMode = recommended,
                            isMicActive = true
                        )
                    }
                    delay(120) // Throttle to 8 updates per second for maximum battery efficiency
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in ambient noise audio recorder", e)
                startSimulationLoop()
            } finally {
                cleanupAudioRecord()
            }
        }
    }

    fun stopMonitoring() {
        recordingJob?.cancel()
        recordingJob = null
        cleanupAudioRecord()
        _readingFlow.value = _readingFlow.value.copy(isMicActive = false)
    }

    private fun startSimulationLoop() {
        recordingJob = monitorScope.launch {
            var counter = 0
            while (isActive) {
                val cycle = (kotlin.math.sin(counter.toDouble() * 0.05) * 5.0).toFloat()
                val simulatedSpl = (46.0f + cycle).coerceIn(38.0f, 62.0f)

                _readingFlow.value = AmbientNoiseReading(
                    splDb = simulatedSpl,
                    environmentCategory = if (simulatedSpl < 48f) "Quiet Room" else "Office Ambience",
                    cancellationHeadroomPercent = 92,
                    recommendedMode = AncOptimizationMode.ULTRA_50DB,
                    isMicActive = false
                )
                counter++
                delay(250)
            }
        }
    }

    private fun cleanupAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {}
        audioRecord = null
    }
}
