package com.zenith.tuner.dsp

import com.zenith.tuner.anc.AncOptimizationMode

/**
 * Filter types for parametric acoustic correction
 */
enum class FilterKind(val id: Int, val label: String) {
    PASS(0, "Pass"),
    HIGH_PASS(1, "High-Pass"),
    LOW_SHELF(2, "Low-Shelf"),
    PEAKING(3, "Peaking EQ"),
    HIGH_SHELF(4, "High-Shelf")
}

/**
 * Definition of a single parametric biquad stage
 */
data class ParametricStageConfig(
    val id: Int,
    val name: String,
    val description: String,
    val kind: FilterKind,
    val enabled: Boolean = true,
    val freq: Float,
    val q: Float,
    val gainDb: Float,
    val minFreq: Float = 10f,
    val maxFreq: Float = 20000f,
    val minGain: Float = -24f,
    val maxGain: Float = 12f,
    val minQ: Float = 0.1f,
    val maxQ: Float = 10f
)

/**
 * Built-in acoustic target tuning presets
 */
enum class AcousticPreset(val displayName: String, val subtitle: String) {
    ZENITH_REFERENCE("Zenith Reference", "Neutralizes bloat, ANC mud & AAC sizzle"),
    HARMAN_IN_EAR("Harman IE 2019", "Scientific consumer target with deep sub & smooth pinna"),
    VOCAL_INTIMACY("Vocal & Acoustic", "Intimate forward mids & acoustic presence"),
    BASSHEAD_SLAM("Basshead Slam", "Deep 50Hz sub punch without 145Hz mid-bass overhang"),
    TREBLE_SPARKLE("Treble Sparkle", "Top-octave air & micro-detail for orchestral tracks"),
    FLAT_STUDIO("Flat Studio", "Clinical reference monitor response"),
    CUSTOM("Custom User", "Fully customized parametric curve")
}

/**
 * DynamicsProcessing Multi-Band Compressor & Limiter settings
 */
data class MbcSettings(
    val damperAttackMs: Float = 3.5f,       // Band 1 (70Hz - 250Hz) attack time
    val damperReleaseMs: Float = 50.0f,     // Band 1 release time
    val damperThresholdDb: Float = -14.0f,  // Band 1 threshold
    val damperRatio: Float = 2.8f,          // Band 1 compression ratio
    val subAttackMs: Float = 12.0f,         // Band 0 (<70Hz)
    val midAttackMs: Float = 18.0f,         // Band 2 (250Hz - 4kHz)
    val highAttackMs: Float = 10.0f,        // Band 3 (4kHz - 20kHz)
    val limiterThresholdDb: Float = -0.2f,  // 0dBFS inter-sample peak protection
    val limiterAttackMs: Float = 1.0f
)

/**
 * UI Navigation tabs for ZenithTuner
 */
enum class TunerTab(val title: String) {
    EXPRESS("Express"),
    PARAMETRIC("Parametric Lab"),
    DYNAMICS("MBC Damper"),
    PRESETS("Presets")
}

/**
 * Complete immutable state of the ZenithTuner audio engine
 */
data class AcousticState(
    val isDspEnabled: Boolean = true,
    val activePreset: AcousticPreset = AcousticPreset.ZENITH_REFERENCE,
    val currentTab: TunerTab = TunerTab.EXPRESS,

    // Express Controls
    val ancAntiMudEnabled: Boolean = true,
    val ancOptimizationMode: AncOptimizationMode = AncOptimizationMode.ULTRA_50DB,
    val subBassWeight: Float = 1.0f,        // 0.0 to 2.0 (1.0 = nominal +3.6dB at 55Hz)
    val vocalPresence: Float = 1.0f,        // 0.0 to 2.0 (1.0 = nominal +2.8/+3.2dB)
    val crossfeedAmount: Float = 0.0f,      // 0.0 (off) to 1.0 (natural spatial crossfeed)

    // Preamp Headroom & Volume
    val autoPreampEnabled: Boolean = true,
    val manualPreampDb: Float = -3.8f,
    val preampHeadroomDb: Float = -3.8f,
    val maxBoostDb: Float = 3.6f,
    val volumeRatio: Float = 0.7f,            // 0.0 to 1.0 (System Media Volume)
    val digitalOutputGainDb: Float = 0.0f,    // -12.0dB to +6.0dB output boost/cut

    // MBC & Dynamics Settings
    val mbcSettings: MbcSettings = MbcSettings(),

    // 8 Detailed Parametric Stages
    val stages: List<ParametricStageConfig> = defaultStages(),

    // System Status
    val activeSessionCount: Int = 1,
    val isNativeLoaded: Boolean = false,

    // Output Curves & Hardware Buffers
    val curveGains: FloatArray = FloatArray(64) { 0f },
    val preEqFreqs: FloatArray = FloatArray(32) { 0f },
    val preEqGains: FloatArray = FloatArray(32) { 0f },
    val postEqFreqs: FloatArray = FloatArray(32) { 0f },
    val postEqGains: FloatArray = FloatArray(32) { 0f }
) {
    companion object {
        fun defaultStages(): List<ParametricStageConfig> = listOf(
            ParametricStageConfig(
                id = 0,
                name = "HPF Sub-Rumble",
                description = "Strips sub-audible flutter below driver resonance under ANC",
                kind = FilterKind.HIGH_PASS,
                enabled = true,
                freq = 24f,
                q = 0.7071f,
                gainDb = 0f,
                minFreq = 15f,
                maxFreq = 50f
            ),
            ParametricStageConfig(
                id = 1,
                name = "Sub-Bass Slam",
                description = "Low-shelf punch restoring physical 11mm driver slam",
                kind = FilterKind.LOW_SHELF,
                enabled = true,
                freq = 55f,
                q = 0.71f,
                gainDb = 3.6f,
                minFreq = 30f,
                maxFreq = 90f,
                minGain = -12f,
                maxGain = 12f
            ),
            ParametricStageConfig(
                id = 2,
                name = "50dB ANC Anti-Mud",
                description = "Surgically cuts 11mm resonance & ANC error-mic feedback",
                kind = FilterKind.PEAKING,
                enabled = true,
                freq = 145f,
                q = 1.8f,
                gainDb = -5.2f,
                minFreq = 100f,
                maxFreq = 200f,
                minGain = -16f,
                maxGain = 0f
            ),
            ParametricStageConfig(
                id = 3,
                name = "Lower-Mid Boxiness",
                description = "Clears hollow boxiness and lower vocal masking",
                kind = FilterKind.PEAKING,
                enabled = true,
                freq = 320f,
                q = 1.2f,
                gainDb = -2.4f,
                minFreq = 220f,
                maxFreq = 480f,
                minGain = -8f,
                maxGain = 4f
            ),
            ParametricStageConfig(
                id = 4,
                name = "Vocal Pinna Gain 1",
                description = "Pulls recessed lead vocals out of the V-shape depression",
                kind = FilterKind.PEAKING,
                enabled = true,
                freq = 1650f,
                q = 1.4f,
                gainDb = 2.8f,
                minFreq = 1200f,
                maxFreq = 2200f,
                minGain = -6f,
                maxGain = 8f
            ),
            ParametricStageConfig(
                id = 5,
                name = "Pinna Ear-Gain 2",
                description = "Restores anatomical ear canal resonance and presence",
                kind = FilterKind.PEAKING,
                enabled = true,
                freq = 3100f,
                q = 1.8f,
                gainDb = 3.2f,
                minFreq = 2400f,
                maxFreq = 4000f,
                minGain = -6f,
                maxGain = 8f
            ),
            ParametricStageConfig(
                id = 6,
                name = "AAC Treble De-Sizzle",
                description = "Notches out metallic Bluetooth AAC cymbal sizzle",
                kind = FilterKind.PEAKING,
                enabled = true,
                freq = 6600f,
                q = 2.5f,
                gainDb = -3.8f,
                minFreq = 5500f,
                maxFreq = 7800f,
                minGain = -12f,
                maxGain = 0f
            ),
            ParametricStageConfig(
                id = 7,
                name = "Top-Octave Air",
                description = "Opens up soundstage width, separation, and micro-air",
                kind = FilterKind.HIGH_SHELF,
                enabled = true,
                freq = 11500f,
                q = 0.71f,
                gainDb = 2.5f,
                minFreq = 8000f,
                maxFreq = 16000f,
                minGain = -8f,
                maxGain = 8f
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AcousticState
        if (isDspEnabled != other.isDspEnabled) return false
        if (activePreset != other.activePreset) return false
        if (currentTab != other.currentTab) return false
        if (ancAntiMudEnabled != other.ancAntiMudEnabled) return false
        if (subBassWeight != other.subBassWeight) return false
        if (vocalPresence != other.vocalPresence) return false
        if (crossfeedAmount != other.crossfeedAmount) return false
        if (autoPreampEnabled != other.autoPreampEnabled) return false
        if (manualPreampDb != other.manualPreampDb) return false
        if (preampHeadroomDb != other.preampHeadroomDb) return false
        if (maxBoostDb != other.maxBoostDb) return false
        if (mbcSettings != other.mbcSettings) return false
        if (stages != other.stages) return false
        if (activeSessionCount != other.activeSessionCount) return false
        if (isNativeLoaded != other.isNativeLoaded) return false
        if (!curveGains.contentEquals(other.curveGains)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isDspEnabled.hashCode()
        result = 31 * result + activePreset.hashCode()
        result = 31 * result + currentTab.hashCode()
        result = 31 * result + ancAntiMudEnabled.hashCode()
        result = 31 * result + subBassWeight.hashCode()
        result = 31 * result + vocalPresence.hashCode()
        result = 31 * result + crossfeedAmount.hashCode()
        result = 31 * result + autoPreampEnabled.hashCode()
        result = 31 * result + manualPreampDb.hashCode()
        result = 31 * result + preampHeadroomDb.hashCode()
        result = 31 * result + maxBoostDb.hashCode()
        result = 31 * result + mbcSettings.hashCode()
        result = 31 * result + stages.hashCode()
        result = 31 * result + activeSessionCount
        result = 31 * result + isNativeLoaded.hashCode()
        result = 31 * result + curveGains.contentHashCode()
        return result
    }
}
