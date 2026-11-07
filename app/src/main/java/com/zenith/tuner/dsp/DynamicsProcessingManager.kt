package com.zenith.tuner.dsp

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Android 12 DynamicsProcessing instances across active audio sessions.
 * Implements:
 * - 64-Band Split (32 Pre-EQ + 32 Post-EQ)
 * - Automatic Preamp Headroom or Manual Override
 * - 11mm Driver Transient Damper (4-band MBC with customizable 3.5ms attack)
 * - 0dBFS Peak Limiter
 * - Full Parametric Equalization & Acoustic Presets
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
    private var activePreset = AcousticPreset.ZENITH_REFERENCE

    @Volatile
    private var currentTab = TunerTab.EXPRESS

    @Volatile
    private var ancAntiMud = true

    @Volatile
    private var subBassWeight = 1.0f

    @Volatile
    private var vocalPresence = 1.0f

    @Volatile
    private var crossfeedAmount = 0.0f

    @Volatile
    private var autoPreampEnabled = true

    @Volatile
    private var manualPreampDb = -3.8f

    @Volatile
    private var mbcSettings = MbcSettings()

    private var stages: MutableList<ParametricStageConfig> = AcousticState.defaultStages().toMutableList()

    init {
        recomputeParameters()
    }

    @Synchronized
    fun attachSession(sessionId: Int): Boolean {
        if (activeEffects.containsKey(sessionId)) {
            Log.d(TAG, "Audio session $sessionId already active.")
            return true
        }

        try {
            val config = buildDynamicsProcessingConfig()
            val dp = DynamicsProcessing(0, sessionId, config).apply {
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

    @Synchronized
    fun setTab(tab: TunerTab) {
        this.currentTab = tab
    }

    @Synchronized
    fun applyPreset(preset: AcousticPreset): AcousticState {
        this.activePreset = preset

        when (preset) {
            AcousticPreset.ZENITH_REFERENCE -> {
                this.stages = AcousticState.defaultStages().toMutableList()
                this.ancAntiMud = true
                this.subBassWeight = 1.0f
                this.vocalPresence = 1.0f
                this.crossfeedAmount = 0.0f
            }
            AcousticPreset.HARMAN_IN_EAR -> {
                this.stages = AcousticState.defaultStages().toMutableList().apply {
                    this[0] = this[0].copy(freq = 20f, q = 0.7071f)
                    this[1] = this[1].copy(freq = 70f, q = 0.71f, gainDb = 5.5f) // Harman sub-bass shelf
                    this[2] = this[2].copy(gainDb = -4.0f)                       // Moderate 145Hz cut
                    this[3] = this[3].copy(gainDb = -1.5f)
                    this[4] = this[4].copy(freq = 1750f, q = 1.3f, gainDb = 4.2f) // Harman pinna slope
                    this[5] = this[5].copy(freq = 2900f, q = 1.7f, gainDb = 5.8f) // Harman pinna peak
                    this[6] = this[6].copy(freq = 6600f, q = 2.2f, gainDb = -2.5f)
                    this[7] = this[7].copy(freq = 11000f, q = 0.71f, gainDb = 1.5f)
                }
                this.subBassWeight = 1.4f
                this.vocalPresence = 1.5f
            }
            AcousticPreset.VOCAL_INTIMACY -> {
                this.stages = AcousticState.defaultStages().toMutableList().apply {
                    this[0] = this[0].copy(freq = 30f, q = 0.7071f)
                    this[1] = this[1].copy(gainDb = 1.0f)                          // Leaner sub-bass
                    this[2] = this[2].copy(gainDb = -6.5f)                         // Deep mid-bass cut
                    this[3] = this[3].copy(gainDb = -3.0f)
                    this[4] = this[4].copy(freq = 1600f, q = 1.3f, gainDb = 4.5f) // Forward vocals
                    this[5] = this[5].copy(freq = 3200f, q = 1.5f, gainDb = 4.8f)
                    this[6] = this[6].copy(gainDb = -3.5f)
                    this[7] = this[7].copy(gainDb = 3.5f)
                }
                this.subBassWeight = 0.5f
                this.vocalPresence = 1.6f
            }
            AcousticPreset.BASSHEAD_SLAM -> {
                this.stages = AcousticState.defaultStages().toMutableList().apply {
                    this[0] = this[0].copy(freq = 20f)
                    this[1] = this[1].copy(freq = 50f, q = 0.85f, gainDb = 7.5f)  // Extreme sub-bass punch
                    this[2] = this[2].copy(gainDb = -6.0f)                         // Aggressive anti-mud notch
                    this[3] = this[3].copy(gainDb = -2.0f)
                    this[4] = this[4].copy(gainDb = 2.0f)
                    this[5] = this[5].copy(gainDb = 2.2f)
                    this[6] = this[6].copy(gainDb = -3.0f)
                    this[7] = this[7].copy(gainDb = 2.0f)
                }
                this.subBassWeight = 2.0f
                this.vocalPresence = 0.8f
            }
            AcousticPreset.TREBLE_SPARKLE -> {
                this.stages = AcousticState.defaultStages().toMutableList().apply {
                    this[1] = this[1].copy(gainDb = 2.5f)
                    this[2] = this[2].copy(gainDb = -4.5f)
                    this[3] = this[3].copy(gainDb = -1.8f)
                    this[4] = this[4].copy(gainDb = 3.0f)
                    this[5] = this[5].copy(gainDb = 3.5f)
                    this[6] = this[6].copy(gainDb = -2.8f)
                    this[7] = this[7].copy(freq = 12000f, q = 0.71f, gainDb = 5.0f) // Airy soundstage
                }
                this.crossfeedAmount = 0.35f // Expanded acoustic stage
            }
            AcousticPreset.FLAT_STUDIO -> {
                this.stages = AcousticState.defaultStages().toMutableList().map { it.copy(gainDb = 0f) }.toMutableList()
                this.ancAntiMud = false
                this.subBassWeight = 0f
                this.vocalPresence = 0f
            }
            AcousticPreset.CUSTOM -> {
                // Keep current stages intact
            }
        }

        recomputeParameters()
        applyToActiveEffects()
        return getCurrentState()
    }

    @Synchronized
    fun updateExpressParameters(
        ancAntiMudEnabled: Boolean = this.ancAntiMud,
        subBass: Float = this.subBassWeight,
        vocals: Float = this.vocalPresence,
        crossfeed: Float = this.crossfeedAmount
    ): AcousticState {
        this.ancAntiMud = ancAntiMudEnabled
        this.subBassWeight = subBass
        this.vocalPresence = vocals
        this.crossfeedAmount = crossfeed

        // Sync with underlying stages
        val subGain = 3.6f * subBass.coerceIn(0.0f, 2.0f)
        val midBassGain = if (ancAntiMudEnabled) -5.2f else -1.5f
        val vocal1650Gain = 2.8f * vocals.coerceIn(0.0f, 2.0f)
        val vocal3100Gain = 3.2f * vocals.coerceIn(0.0f, 2.0f)

        stages[1] = stages[1].copy(gainDb = subGain)
        stages[2] = stages[2].copy(gainDb = midBassGain, enabled = ancAntiMudEnabled)
        stages[4] = stages[4].copy(gainDb = vocal1650Gain)
        stages[5] = stages[5].copy(gainDb = vocal3100Gain)

        recomputeParameters()
        applyToActiveEffects()
        return getCurrentState()
    }

    @Synchronized
    fun updateParametricStage(
        stageId: Int,
        enabled: Boolean? = null,
        freq: Float? = null,
        q: Float? = null,
        gainDb: Float? = null
    ): AcousticState {
        val index = stages.indexOfFirst { it.id == stageId }
        if (index != -1) {
            val curr = stages[index]
            stages[index] = curr.copy(
                enabled = enabled ?: curr.enabled,
                freq = freq ?: curr.freq,
                q = q ?: curr.q,
                gainDb = gainDb ?: curr.gainDb
            )
            this.activePreset = AcousticPreset.CUSTOM
            recomputeParameters()
            applyToActiveEffects()
        }
        return getCurrentState()
    }

    @Synchronized
    fun updatePreamp(auto: Boolean, manualDb: Float): AcousticState {
        this.autoPreampEnabled = auto
        this.manualPreampDb = manualDb
        recomputeParameters()
        applyToActiveEffects()
        return getCurrentState()
    }

    @Synchronized
    fun updateMbcSettings(settings: MbcSettings): AcousticState {
        this.mbcSettings = settings
        // Update hardware MBC across all sessions
        applyMbcToActiveEffects()
        return getCurrentState()
    }

    fun getCurrentState(): AcousticState {
        val preFreqs = FloatArray(32) { buffer[it] }
        val preGains = FloatArray(32) { buffer[32 + it] }
        val postFreqs = FloatArray(32) { buffer[64 + it] }
        val postGains = FloatArray(32) { buffer[96 + it] }
        val curve = FloatArray(64) { buffer[ZenithNativeDsp.INDEX_CURVE_START + it] }

        return AcousticState(
            isDspEnabled = isEngineEnabled,
            activePreset = activePreset,
            currentTab = currentTab,
            ancAntiMudEnabled = ancAntiMud,
            subBassWeight = subBassWeight,
            vocalPresence = vocalPresence,
            crossfeedAmount = crossfeedAmount,
            autoPreampEnabled = autoPreampEnabled,
            manualPreampDb = manualPreampDb,
            preampHeadroomDb = buffer[ZenithNativeDsp.INDEX_PREAMP],
            maxBoostDb = buffer[ZenithNativeDsp.INDEX_MAX_BOOST],
            mbcSettings = mbcSettings,
            stages = stages.toList(),
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
        val manualPreamp = if (!autoPreampEnabled) manualPreampDb else null
        ZenithNativeDsp.computeCustom(
            sampleRate = 48000.0,
            stages = stages,
            crossfeed = crossfeedAmount,
            manualPreampDb = manualPreamp,
            outBuffer = buffer
        )
    }

    private fun applyToActiveEffects() {
        val preampDb = buffer[ZenithNativeDsp.INDEX_PREAMP]

        activeEffects.values.forEach { dp ->
            try {
                dp.setInputGainAllChannelsTo(preampDb)
                for (b in 0 until PRE_EQ_BANDS) {
                    val f = buffer[b]
                    val g = buffer[32 + b]
                    dp.setPreEqBandAllChannelsTo(b, DynamicsProcessing.EqBand(true, f, g))
                }
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

    private fun applyMbcToActiveEffects() {
        activeEffects.values.forEach { dp ->
            try {
                // Update Band 1 (11mm Damper)
                val band1 = DynamicsProcessing.MbcBand(
                    true,
                    250.0f,
                    mbcSettings.damperAttackMs,
                    mbcSettings.damperReleaseMs,
                    mbcSettings.damperRatio,
                    mbcSettings.damperThresholdDb,
                    4.0f,
                    -60.0f,
                    1.0f,
                    0.0f,
                    0.0f
                )
                dp.setMbcBandAllChannelsTo(1, band1)

                // Update Limiter
                val limiter = DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    mbcSettings.limiterAttackMs,
                    40.0f,
                    10.0f,
                    mbcSettings.limiterThresholdDb,
                    0.0f
                )
                dp.setLimiterAllChannelsTo(limiter)
            } catch (e: Throwable) {
                Log.e(TAG, "Error applying MBC updates to hardware effect", e)
            }
        }
    }

    private fun buildDynamicsProcessingConfig(): DynamicsProcessing.Config {
        val preampDb = buffer[ZenithNativeDsp.INDEX_PREAMP]

        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT,
            true,
            PRE_EQ_BANDS,
            true,
            MBC_BANDS,
            true,
            POST_EQ_BANDS,
            true
        )

        builder.setPreferredFrameDuration(10.0f)
        builder.setInputGainAllChannelsTo(preampDb)

        // Pre-EQ
        val preEq = DynamicsProcessing.Eq(true, true, PRE_EQ_BANDS)
        for (b in 0 until PRE_EQ_BANDS) {
            val cutoff = buffer[b]
            val gain = buffer[32 + b]
            preEq.setBand(b, DynamicsProcessing.EqBand(true, cutoff, gain))
        }
        builder.setPreEqAllChannelsTo(preEq)

        // Post-EQ
        val postEq = DynamicsProcessing.Eq(true, true, POST_EQ_BANDS)
        for (b in 0 until POST_EQ_BANDS) {
            val cutoff = buffer[64 + b]
            val gain = buffer[96 + b]
            postEq.setBand(b, DynamicsProcessing.EqBand(true, cutoff, gain))
        }
        builder.setPostEqAllChannelsTo(postEq)

        // 4-Band Multi-Band Compressor
        val mbc = DynamicsProcessing.Mbc(true, true, MBC_BANDS)

        // Band 0: Sub-bass (< 70Hz)
        mbc.setBand(
            0,
            DynamicsProcessing.MbcBand(
                true,
                70.0f,
                mbcSettings.subAttackMs,
                100.0f,
                1.5f,
                -16.0f,
                6.0f,
                -60.0f,
                1.0f,
                0.0f,
                0.0f
            )
        )

        // Band 1: 11mm Driver Transient Damper (70Hz - 250Hz)
        mbc.setBand(
            1,
            DynamicsProcessing.MbcBand(
                true,
                250.0f,
                mbcSettings.damperAttackMs,
                mbcSettings.damperReleaseMs,
                mbcSettings.damperRatio,
                mbcSettings.damperThresholdDb,
                4.0f,
                -60.0f,
                1.0f,
                0.0f,
                0.0f
            )
        )

        // Band 2: Midrange (250Hz - 4000Hz)
        mbc.setBand(
            2,
            DynamicsProcessing.MbcBand(
                true,
                4000.0f,
                mbcSettings.midAttackMs,
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
                mbcSettings.highAttackMs,
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

        // Output Limiter
        val limiter = DynamicsProcessing.Limiter(
            true,
            true,
            0,
            mbcSettings.limiterAttackMs,
            40.0f,
            10.0f,
            mbcSettings.limiterThresholdDb,
            0.0f
        )
        builder.setLimiterAllChannelsTo(limiter)

        return builder.build()
    }
}
