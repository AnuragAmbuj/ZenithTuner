package com.zenith.tuner.dsp

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Android 12 DynamicsProcessing instances across active audio sessions.
 * Implements:
 * - 64-Band Split (32 Pre-EQ + 32 Post-EQ)
 * - Automatic Preamp Headroom (index 128)
 * - 11mm Driver Transient Damper (4-band MBC, 3.5ms attack on Band 1)
 * - 0dBFS Peak Limiter
 */
class DynamicsProcessingManager {

    companion object {
        private const val TAG = "DynamicsProcMgr"
        const val GLOBAL_AUDIO_SESSION_ID = 0
        const val CHANNEL_COUNT = 2 // Stereo
        const val PRE_EQ_BANDS = 32
        const val POST_EQ_BANDS = 32
        const val MBC_BANDS = 4
    }

    private val activeEffects = ConcurrentHashMap<Int, DynamicsProcessing>()
    private val buffer = FloatArray(ZenithNativeDsp.BUFFER_SIZE)

    @Volatile
    private var isEngineEnabled = true

    @Volatile
    private var ancAntiMud = true

    @Volatile
    private var subBassWeight = 1.0f

    @Volatile
    private var vocalPresence = 1.0f

    init {
        // Initialize with default target curve
        recomputeParameters()
    }

    /**
     * Attaches DynamicsProcessing to an audio session.
     */
    @Synchronized
    fun attachSession(sessionId: Int): Boolean {
        if (activeEffects.containsKey(sessionId)) {
            Log.d(TAG, "Audio session $sessionId already active.")
            return true
        }

        try {
            val config = buildDynamicsProcessingConfig()
            val dp = DynamicsProcessing(sessionId, config).apply {
                enabled = isEngineEnabled
            }
            activeEffects[sessionId] = dp
            Log.i(TAG, "Attached DynamicsProcessing to session $sessionId (Enabled=$isEngineEnabled)")
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to attach DynamicsProcessing to session $sessionId", e)
            return false
        }
    }

    /**
     * Detaches DynamicsProcessing from an audio session.
     */
    @Synchronized
    fun detachSession(sessionId: Int) {
        activeEffects.remove(sessionId)?.let { dp ->
            try {
                dp.enabled = false
                dp.release()
                Log.i(TAG, "Released DynamicsProcessing for session $sessionId")
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing session $sessionId", e)
            }
        }
    }

    /**
     * Detaches all sessions on service teardown.
     */
    @Synchronized
    fun releaseAll() {
        activeEffects.forEach { (sessionId, dp) ->
            try {
                dp.enabled = false
                dp.release()
            } catch (e: Throwable) {
                Log.e(TAG, "Error releasing session $sessionId", e)
            }
        }
        activeEffects.clear()
    }

    /**
     * Enables or disables the acoustic correction pipeline globally.
     */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        isEngineEnabled = enabled
        activeEffects.values.forEach { dp ->
            try {
                dp.enabled = enabled
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to toggle enabled state", e)
            }
        }
    }

    /**
     * Updates acoustic tuning parameters and updates hardware effect registers.
     */
    @Synchronized
    fun updateParameters(
        ancAntiMudEnabled: Boolean = this.ancAntiMud,
        subBass: Float = this.subBassWeight,
        vocals: Float = this.vocalPresence
    ): AcousticState {
        this.ancAntiMud = ancAntiMudEnabled
        this.subBassWeight = subBass
        this.vocalPresence = vocals

        recomputeParameters()
        applyToActiveEffects()

        return getCurrentState()
    }

    /**
     * Returns the current computed acoustic state for UI observation.
     */
    fun getCurrentState(): AcousticState {
        val preFreqs = FloatArray(32) { buffer[it] }
        val preGains = FloatArray(32) { buffer[32 + it] }
        val postFreqs = FloatArray(32) { buffer[64 + it] }
        val postGains = FloatArray(32) { buffer[96 + it] }
        val curve = FloatArray(64) { buffer[ZenithNativeDsp.INDEX_CURVE_START + it] }

        return AcousticState(
            isDspEnabled = isEngineEnabled,
            ancAntiMudEnabled = ancAntiMud,
            subBassWeight = subBassWeight,
            vocalPresence = vocalPresence,
            preampHeadroomDb = buffer[ZenithNativeDsp.INDEX_PREAMP],
            maxBoostDb = buffer[ZenithNativeDsp.INDEX_MAX_BOOST],
            activeSessionCount = activeEffects.size.coerceAtLeast(1),
            isNativeLoaded = ZenithNativeDsp.isNativeLoaded,
            curveGains = curve,
            preEqFreqs = preFreqs,
            preEqGains = preGains,
            postEqFreqs = postFreqs,
            postEqGains = postGains
        )
    }

    private fun recomputeParameters() {
        ZenithNativeDsp.compute(
            sampleRate = 48000.0,
            ancAntiMud = ancAntiMud,
            subBassWeight = subBassWeight,
            vocalPresence = vocalPresence,
            outBuffer = buffer
        )
    }

    private fun applyToActiveEffects() {
        val preampDb = buffer[ZenithNativeDsp.INDEX_PREAMP]

        activeEffects.values.forEach { dp ->
            try {
                // Apply Preamp Gain
                dp.setInputGainAllChannelsTo(preampDb)

                // Apply Pre-EQ (32 bands)
                for (b in 0 until PRE_EQ_BANDS) {
                    val f = buffer[b]
                    val g = buffer[32 + b]
                    dp.setPreEqBandAllChannelsTo(b, DynamicsProcessing.EqBand(true, f, g))
                }

                // Apply Post-EQ (32 bands)
                for (b in 0 until POST_EQ_BANDS) {
                    val f = buffer[64 + b]
                    val g = buffer[96 + b]
                    dp.setPostEqBandAllChannelsTo(b, DynamicsProcessing.EqBand(true, f, g))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error applying dynamic parameters to hardware effect", e)
            }
        }
    }

    private fun buildDynamicsProcessingConfig(): DynamicsProcessing.Config {
        val preampDb = buffer[ZenithNativeDsp.INDEX_PREAMP]

        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.CONFIG_VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT,
            true, // Pre-EQ in use
            PRE_EQ_BANDS,
            true, // MBC in use
            MBC_BANDS,
            true, // Post-EQ in use
            POST_EQ_BANDS,
            true  // Limiter in use
        )

        builder.setPreferredFrameDuration(10.0f) // 10ms frame duration for minimal latency
        builder.setInputGainAllChannelsTo(preampDb)

        // 1. Pre-EQ Configuration (32 Bands)
        val preEq = DynamicsProcessing.Eq(true, true, PRE_EQ_BANDS)
        for (b in 0 until PRE_EQ_BANDS) {
            val cutoff = buffer[b]
            val gain = buffer[32 + b]
            preEq.setBand(b, DynamicsProcessing.EqBand(true, cutoff, gain))
        }
        builder.setPreEqAllChannelsTo(preEq)

        // 2. Post-EQ Configuration (32 Bands)
        val postEq = DynamicsProcessing.Eq(true, true, POST_EQ_BANDS)
        for (b in 0 until POST_EQ_BANDS) {
            val cutoff = buffer[64 + b]
            val gain = buffer[96 + b]
            postEq.setBand(b, DynamicsProcessing.EqBand(true, cutoff, gain))
        }
        builder.setPostEqAllChannelsTo(postEq)

        // 3. 11mm Driver Transient Damper (4-Band Multi-Band Compressor)
        val mbc = DynamicsProcessing.Mbc(true, true, MBC_BANDS)

        // Band 0: Sub-bass (< 70Hz)
        mbc.setBand(
            0,
            DynamicsProcessing.MbcBand(
                true,
                70.0f,
                12.0f,  // attackTime (ms)
                100.0f, // releaseTime (ms)
                1.5f,   // ratio
                -16.0f, // threshold (dB)
                6.0f,   // kneeWidth (dB)
                -60.0f, // noiseGateThreshold (dB)
                1.0f,   // expanderRatio
                0.0f,   // preGain (dB)
                0.0f    // postGain (dB)
            )
        )

        // Band 1: Mid-bass (70Hz - 250Hz) - The 11mm Driver Transient Damper!
        // Fast 3.5ms attack specifically clamps physical diaphragm ringing under 50dB ANC.
        mbc.setBand(
            1,
            DynamicsProcessing.MbcBand(
                true,
                250.0f,
                3.5f,   // 3.5ms FAST ATTACK
                50.0f,  // releaseTime (ms)
                2.8f,   // ratio
                -14.0f, // threshold (dB)
                4.0f,   // kneeWidth (dB)
                -60.0f, // noiseGateThreshold (dB)
                1.0f,   // expanderRatio
                0.0f,   // preGain (dB)
                0.0f    // postGain (dB)
            )
        )

        // Band 2: Midrange (250Hz - 4000Hz)
        mbc.setBand(
            2,
            DynamicsProcessing.MbcBand(
                true,
                4000.0f,
                18.0f,
                120.0f,
                1.8f,
                -18.0f,
                6.0f,
                -60.0f,
                1.0f,
                0.0f,
                0.0f
            )
        )

        // Band 3: Highs (4000Hz - 20000Hz)
        mbc.setBand(
            3,
            DynamicsProcessing.MbcBand(
                true,
                20000.0f,
                10.0f,
                80.0f,
                1.6f,
                -16.0f,
                6.0f,
                -60.0f,
                1.0f,
                0.0f,
                0.0f
            )
        )
        builder.setMbcAllChannelsTo(mbc)

        // 4. Output Limiter: 0dBFS inter-sample peak protection
        val limiter = DynamicsProcessing.Limiter(
            true,   // inUse
            true,   // enabled
            0,      // linkGroup
            1.0f,   // attackTime (ms)
            40.0f,  // releaseTime (ms)
            10.0f,  // ratio
            -0.2f,  // threshold (dB) - safe headroom below 0dBFS
            0.0f    // postGain (dB)
        )
        builder.setLimiterAllChannelsTo(limiter)

        return builder.build()
    }
}
