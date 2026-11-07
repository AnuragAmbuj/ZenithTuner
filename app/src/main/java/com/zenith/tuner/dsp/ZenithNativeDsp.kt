package com.zenith.tuner.dsp

import android.util.Log
import kotlin.math.*

/**
 * JNI bridge to panic-safe Rust DSP core (libzenith_dsp.so)
 * with transparent pure-Kotlin fallback for custom parametric tuning.
 */
object ZenithNativeDsp {

    private const val TAG = "ZenithNativeDsp"
    const val BUFFER_SIZE = 194
    const val PARAMS_BUFFER_SIZE = 42 // 8 stages * 5 params + 1 crossfeed + 1 preamp
    const val INDEX_PREAMP = 128
    const val INDEX_MAX_BOOST = 129
    const val INDEX_CURVE_START = 130

    var isNativeLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("zenith_dsp")
            isNativeLoaded = true
            Log.i(TAG, "Rust DSP core (libzenith_dsp.so) loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            isNativeLoaded = false
            Log.w(TAG, "Native library libzenith_dsp.so not yet installed; using Kotlin fallback core.", e)
        }
    }

    external fun computeAcousticCorrection(
        sampleRate: Double,
        ancAntiMud: Boolean,
        subBassWeight: Float,
        vocalPresence: Float,
        outputBuffer: FloatArray
    ): Int

    external fun computeCustomTuning(
        sampleRate: Double,
        paramsArray: FloatArray,
        outputBuffer: FloatArray
    ): Int

    /**
     * Computes acoustic correction using full custom parametric stages, crossfeed, and preamp override.
     */
    fun computeCustom(
        sampleRate: Double = 48000.0,
        stages: List<ParametricStageConfig>,
        crossfeed: Float = 0.0f,
        manualPreampDb: Float? = null,
        outBuffer: FloatArray = FloatArray(BUFFER_SIZE)
    ): FloatArray {
        if (outBuffer.size < BUFFER_SIZE) {
            throw IllegalArgumentException("Buffer size must be at least $BUFFER_SIZE floats")
        }

        // Pack parameters into flat array
        val params = FloatArray(PARAMS_BUFFER_SIZE)
        for (i in 0 until min(stages.size, 8)) {
            val s = stages[i]
            val offset = i * 5
            params[offset] = if (s.enabled) 1.0f else 0.0f
            params[offset + 1] = s.kind.id.toFloat()
            params[offset + 2] = s.freq
            params[offset + 3] = s.q
            params[offset + 4] = s.gainDb
        }
        params[40] = crossfeed.coerceIn(0.0f, 1.0f)
        params[41] = manualPreampDb ?: -99.0f

        if (isNativeLoaded) {
            try {
                val status = computeCustomTuning(sampleRate, params, outBuffer)
                if (status == 0) return outBuffer
                Log.w(TAG, "Native computeCustom returned $status, falling back to Kotlin")
            } catch (e: Throwable) {
                Log.e(TAG, "Error invoking native custom DSP", e)
            }
        }

        computeCustomKotlin(sampleRate, stages, crossfeed, manualPreampDb, outBuffer)
        return outBuffer
    }

    /**
     * Pure-Kotlin mirror fallback for custom parametric tuning.
     */
    private fun computeCustomKotlin(
        sampleRate: Double,
        stages: List<ParametricStageConfig>,
        crossfeed: Float,
        manualPreampDb: Float?,
        out: FloatArray
    ) {
        val sr = sampleRate.coerceIn(8000.0, 384000.0)
        val biquads = stages.map { stage ->
            if (!stage.enabled) {
                BiquadCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
            } else {
                when (stage.kind) {
                    FilterKind.PASS -> BiquadCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
                    FilterKind.HIGH_PASS -> makeHpf(stage.freq.toDouble(), stage.q.toDouble(), sr)
                    FilterKind.LOW_SHELF -> makeLowShelf(stage.freq.toDouble(), stage.q.toDouble(), stage.gainDb.toDouble(), sr)
                    FilterKind.PEAKING -> makePeaking(stage.freq.toDouble(), stage.q.toDouble(), stage.gainDb.toDouble(), sr)
                    FilterKind.HIGH_SHELF -> makeHighShelf(stage.freq.toDouble(), stage.q.toDouble(), stage.gainDb.toDouble(), sr)
                }
            }
        }

        val freqs = FloatArray(64)
        val curve = FloatArray(64)
        var maxBoost = 0.0

        for (i in 0 until 64) {
            val exp = i.toDouble() / 63.0
            val f = 20.0 * (20000.0 / 20.0).pow(exp)
            freqs[i] = f.toFloat()

            var totalDb = 0.0
            for (filter in biquads) {
                totalDb += evalDb(filter, f, sr)
            }

            // Crossfeed effect modeling
            if (crossfeed > 0.01f) {
                if (f < 700.0) {
                    totalDb += 0.75 * crossfeed * (1.0 - f / 700.0)
                } else if (f > 6000.0) {
                    totalDb += -0.6 * crossfeed * ((f - 6000.0) / 14000.0).coerceIn(0.0, 1.0)
                }
            }

            curve[i] = totalDb.toFloat()
            if (totalDb > maxBoost) maxBoost = totalDb
        }

        // Additional dense scan
        for (step in 0 until 128) {
            val exp = step.toDouble() / 127.0
            val testF = 20.0 * (20000.0 / 20.0).pow(exp)
            var totalDb = 0.0
            for (filter in biquads) {
                totalDb += evalDb(filter, testF, sr)
            }
            if (crossfeed > 0.01f) {
                if (testF < 700.0) totalDb += 0.75 * crossfeed * (1.0 - testF / 700.0)
                else if (testF > 6000.0) totalDb += -0.6 * crossfeed * ((testF - 6000.0) / 14000.0).coerceIn(0.0, 1.0)
            }
            if (totalDb > maxBoost) maxBoost = totalDb
        }

        val preampDb = manualPreampDb ?: (if (maxBoost > 0.0) -(maxBoost + 0.2).toFloat() else 0.0f)

        for (i in 0 until 32) {
            out[i] = freqs[i]
            out[32 + i] = curve[i]
        }
        for (i in 0 until 32) {
            out[64 + i] = freqs[32 + i]
            out[96 + i] = curve[32 + i]
        }

        out[INDEX_PREAMP] = preampDb
        out[INDEX_MAX_BOOST] = maxBoost.toFloat()
        for (i in 0 until 64) {
            out[INDEX_CURVE_START + i] = curve[i]
        }
    }

    private data class BiquadCoeffs(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double
    )

    private fun evalDb(c: BiquadCoeffs, f: Double, sr: Double): Double {
        val omega = 2.0 * PI * f.coerceIn(1.0, sr * 0.499) / sr
        val cosW = cos(omega)
        val sinW = sin(omega)
        val cos2W = cos(2.0 * omega)
        val sin2W = sin(2.0 * omega)

        val numRe = c.b0 + c.b1 * cosW + c.b2 * cos2W
        val numIm = -c.b1 * sinW - c.b2 * sin2W
        val denRe = 1.0 + c.a1 * cosW + c.a2 * cos2W
        val denIm = -c.a1 * sinW - c.a2 * sin2W

        val numMagSq = numRe * numRe + numIm * numIm
        val denMagSq = denRe * denRe + denIm * denIm
        if (denMagSq < 1e-12) return 0.0
        val magSq = numMagSq / denMagSq
        return 10.0 * log10(max(magSq, 1e-12))
    }

    private fun makeHpf(f: Double, q: Double, sr: Double): BiquadCoeffs {
        val omega = 2.0 * PI * f.coerceIn(10.0, sr * 0.499) / sr
        val alpha = sin(omega) / (2.0 * q.coerceIn(0.05, 50.0))
        val a0 = 1.0 + alpha
        val cosW = cos(omega)
        return BiquadCoeffs(
            b0 = (1.0 + cosW) / (2.0 * a0),
            b1 = -(1.0 + cosW) / a0,
            b2 = (1.0 + cosW) / (2.0 * a0),
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha) / a0
        )
    }

    private fun makePeaking(f: Double, q: Double, gainDb: Double, sr: Double): BiquadCoeffs {
        if (abs(gainDb) < 1e-4) return BiquadCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
        val aVal = 10.0.pow(gainDb.coerceIn(-36.0, 36.0) / 40.0)
        val omega = 2.0 * PI * f.coerceIn(10.0, sr * 0.499) / sr
        val alpha = sin(omega) / (2.0 * q.coerceIn(0.05, 50.0))
        val a0 = 1.0 + alpha / aVal
        val cosW = cos(omega)
        return BiquadCoeffs(
            b0 = (1.0 + alpha * aVal) / a0,
            b1 = (-2.0 * cosW) / a0,
            b2 = (1.0 - alpha * aVal) / a0,
            a1 = (-2.0 * cosW) / a0,
            a2 = (1.0 - alpha / aVal) / a0
        )
    }

    private fun makeLowShelf(f: Double, q: Double, gainDb: Double, sr: Double): BiquadCoeffs {
        if (abs(gainDb) < 1e-4) return BiquadCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
        val aVal = 10.0.pow(gainDb.coerceIn(-36.0, 36.0) / 40.0)
        val omega = 2.0 * PI * f.coerceIn(10.0, sr * 0.499) / sr
        val sinW = sin(omega)
        val cosW = cos(omega)
        val beta = sqrt(aVal) * sinW / q.coerceIn(0.05, 50.0)
        val a0 = (aVal + 1.0) + (aVal - 1.0) * cosW + beta
        return BiquadCoeffs(
            b0 = (aVal * ((aVal + 1.0) - (aVal - 1.0) * cosW + beta)) / a0,
            b1 = (2.0 * aVal * ((aVal - 1.0) - (aVal + 1.0) * cosW)) / a0,
            b2 = (aVal * ((aVal + 1.0) - (aVal - 1.0) * cosW - beta)) / a0,
            a1 = (-2.0 * ((aVal - 1.0) + (aVal + 1.0) * cosW)) / a0,
            a2 = ((aVal + 1.0) + (aVal - 1.0) * cosW - beta) / a0
        )
    }

    private fun makeHighShelf(f: Double, q: Double, gainDb: Double, sr: Double): BiquadCoeffs {
        if (abs(gainDb) < 1e-4) return BiquadCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
        val aVal = 10.0.pow(gainDb.coerceIn(-36.0, 36.0) / 40.0)
        val omega = 2.0 * PI * f.coerceIn(10.0, sr * 0.499) / sr
        val sinW = sin(omega)
        val cosW = cos(omega)
        val beta = sqrt(aVal) * sinW / q.coerceIn(0.05, 50.0)
        val a0 = (aVal + 1.0) - (aVal - 1.0) * cosW + beta
        return BiquadCoeffs(
            b0 = (aVal * ((aVal + 1.0) + (aVal - 1.0) * cosW + beta)) / a0,
            b1 = (-2.0 * aVal * ((aVal - 1.0) + (aVal + 1.0) * cosW)) / a0,
            b2 = (aVal * ((aVal + 1.0) + (aVal - 1.0) * cosW - beta)) / a0,
            a1 = (2.0 * ((aVal - 1.0) - (aVal + 1.0) * cosW)) / a0,
            a2 = ((aVal + 1.0) - (aVal - 1.0) * cosW - beta) / a0
        )
    }
}
