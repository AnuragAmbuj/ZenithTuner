package com.zenith.tuner

import com.zenith.tuner.anc.AncOptimizationMode
import com.zenith.tuner.dsp.AcousticState
import com.zenith.tuner.dsp.ZenithNativeDsp
import org.junit.Assert.*
import org.junit.Test

class AcousticModelTest {

    @Test
    fun testAcousticStateEqualityIncludesDigitalOutputGain() {
        val baseState = AcousticState()
        val modifiedGainState = baseState.copy(digitalOutputGainDb = 6.0f)
        assertNotEquals(
            "State equality must detect digitalOutputGainDb changes to prevent slider snapping",
            baseState,
            modifiedGainState
        )
        assertNotEquals(baseState.hashCode(), modifiedGainState.hashCode())
    }

    @Test
    fun testAcousticStateEqualityIncludesVolumeRatioAndAncMode() {
        val baseState = AcousticState()
        val modifiedVolume = baseState.copy(volumeRatio = 0.9f)
        val modifiedAnc = baseState.copy(ancOptimizationMode = AncOptimizationMode.OFFICE_CHATTER)

        assertNotEquals(baseState, modifiedVolume)
        assertNotEquals(baseState, modifiedAnc)
    }

    @Test
    fun testKotlinFallbackPreEqCoversFull20HzTo20kHzSpectrum() {
        val outBuffer = FloatArray(ZenithNativeDsp.BUFFER_SIZE)
        ZenithNativeDsp.computeCustom(
            sampleRate = 48000.0,
            stages = AcousticState.defaultStages(),
            crossfeed = 0.0f,
            manualPreampDb = null,
            outBuffer = outBuffer
        )

        // Pre-EQ frequencies: buffer[0..31]
        val lowestPreEqFreq = outBuffer[0]
        val highestPreEqFreq = outBuffer[31]

        assertTrue("Lowest pre-EQ band should be ~20Hz, got $lowestPreEqFreq", lowestPreEqFreq in 19f..21f)
        assertTrue("Highest pre-EQ band should be ~20000Hz, got $highestPreEqFreq", highestPreEqFreq in 19000f..21000f)

        // Middle frequency (~band 16) should be around 600-700Hz
        val midPreEqFreq = outBuffer[16]
        assertTrue("Pre-EQ band 16 should be in mid frequencies (~600-700Hz), got $midPreEqFreq", midPreEqFreq in 500f..800f)

        // Post-EQ: buffer[64..95] frequencies, buffer[96..127] gains (all 0dB)
        for (i in 0 until 32) {
            assertEquals("Post-EQ gain at band $i must be 0.0dB flat", 0.0f, outBuffer[96 + i], 0.001f)
        }
    }
}
