package com.zenith.tuner.dsp

/**
 * State and parameters for the boAt Nirvana Zenith acoustic correction engine.
 */
data class AcousticState(
    val isDspEnabled: Boolean = true,
    val ancAntiMudEnabled: Boolean = true,
    val subBassWeight: Float = 1.0f, // 0.0 to 2.0 (1.0 = nominal +3.6dB punch at 55Hz)
    val vocalPresence: Float = 1.0f, // 0.0 to 2.0 (1.0 = nominal +2.8dB/+3.2dB ear-gain)
    val preampHeadroomDb: Float = -3.8f,
    val maxBoostDb: Float = 3.6f,
    val activeSessionCount: Int = 1,
    val isNativeLoaded: Boolean = false,
    val curveGains: FloatArray = FloatArray(64) { 0f },
    val preEqFreqs: FloatArray = FloatArray(32) { 0f },
    val preEqGains: FloatArray = FloatArray(32) { 0f },
    val postEqFreqs: FloatArray = FloatArray(32) { 0f },
    val postEqGains: FloatArray = FloatArray(32) { 0f }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AcousticState
        if (isDspEnabled != other.isDspEnabled) return false
        if (ancAntiMudEnabled != other.ancAntiMudEnabled) return false
        if (subBassWeight != other.subBassWeight) return false
        if (vocalPresence != other.vocalPresence) return false
        if (preampHeadroomDb != other.preampHeadroomDb) return false
        if (maxBoostDb != other.maxBoostDb) return false
        if (activeSessionCount != other.activeSessionCount) return false
        if (isNativeLoaded != other.isNativeLoaded) return false
        if (!curveGains.contentEquals(other.curveGains)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = isDspEnabled.hashCode()
        result = 31 * result + ancAntiMudEnabled.hashCode()
        result = 31 * result + subBassWeight.hashCode()
        result = 31 * result + vocalPresence.hashCode()
        result = 31 * result + preampHeadroomDb.hashCode()
        result = 31 * result + maxBoostDb.hashCode()
        result = 31 * result + activeSessionCount
        result = 31 * result + isNativeLoaded.hashCode()
        result = 31 * result + curveGains.contentHashCode()
        return result
    }
}
