package com.zenith.tuner.dsp

import android.util.Log
import kotlin.math.*

/**
 * JNI bridge to panic-safe Rust DSP core (libzenith_dsp.so)
 * with transparent pure-Kotlin fallback.
 */
object ZenithNativeDsp {

    private const val TAG = "ZenithNativeDsp"
    const val BUFFER_SIZE = 194 // 32 pre freqs + 32 pre gains + 32 post freqs + 32 post gains + 1 preamp + 1 maxBoost + 64 curve
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

    /**
     * Native JNI signature matching:
     * Java_com_zenith_tuner_dsp_ZenithNativeDsp_computeAcousticCorrection
     */
    external fun computeAcousticCorrection(
        sampleRate: Double,
        ancAntiMud: Boolean,
        subBassWeight: Float,
        vocalPresence: Float,
        outputBuffer: FloatArray
    ): Int

    /**
     * Computes the complete 64-band split, preamp headroom, and Bode plot spline.
     * Uses Rust native binary if loaded, or transparent Kotlin fallback.
     */
    fun compute(
        sampleRate: Double = 48000.0,
        ancAntiMud: Boolean = true,
        subBassWeight: Float = 1.0f,
        vocalPresence: Float = 1.0f,
        outBuffer: FloatArray = FloatArray(BUFFER_SIZE)
    ): FloatArray {
        if (outBuffer.size < BUFFER_SIZE) {
            throw IllegalArgumentException("Buffer size must be at least $BUFFER_SIZE floats")
        }

        if (isNativeLoaded) {
            try {
                val status = computeAcousticCorrection(
                    sampleRate,
                    ancAntiMud,
                    subBassWeight,
                    vocalPresence,
                    outBuffer
                )
                if (status == 0) {
                    return outBuffer
                }
                Log.w(TAG, "Native compute returned error status: $status, falling back to Kotlin")
            } catch (e: Throwable) {
                Log.e(TAG, "Error invoking native DSP", e)
            }
        }

        // Pure-Kotlin mirror fallback
        computeKotlinFallback(sampleRate, ancAntiMud, subBassWeight, vocalPresence, outBuffer)
        return outBuffer
    }

    /**
     * Pure-Kotlin exact implementation of Robert Bristow-Johnson Audio EQ Cookbook
     * for running when the native library is not present.
     */
    private fun computeKotlinFallback(
        sampleRate: Double,
        ancAntiMud: Boolean,
        subBassWeight: Float,
        vocalPresence: Float,
        out: FloatArray
    ) {
        val sr = sampleRate.coerceIn(8000.0, 384000.0)
        val subScale = subBassWeight.coerceIn(0.0f, 2.0f).toDouble()
        val vocScale = vocalPresence.coerceIn(0.0f, 2.0f).toDouble()

        // 8-stage target filters
        val f1 = makeHpf(24.0, 0.7071, sr)
        val f2 = makeLowShelf(55.0, 0.71, 3.6 * subScale, sr)
        val midBassGain = if (ancAntiMud) -5.2 else -1.5
        val f3 = makePeaking(145.0, 1.8, midBassGain, sr)
        val f4 = makePeaking(320.0, 1.2, -2.4, sr)
        val f5 = makePeaking(1650.0, 1.4, 2.8 * vocScale, sr)
        val f6 = makePeaking(3100.0, 1.8, 3.2 * vocScale, sr)
        val f7 = makePeaking(6600.0, 2.5, -3.8, sr)
        val f8 = makeHighShelf(11500.0, 0.71, 2.5, sr)

        val filters = arrayOf(f1, f2, f3, f4, f5, f6, f7, f8)

        // 64 logarithmic frequencies from 20Hz to 20kHz
        val freqs = FloatArray(64)
        val curve = FloatArray(64)
        var maxBoost = 0.0

        for (i in 0 until 64) {
            val exp = i.toDouble() / 63.0
            val f = 20.0 * (20000.0 / 20.0).pow(exp)
            freqs[i] = f.toFloat()

            var totalDb = 0.0
            for (filter in filters) {
                totalDb += evalDb(filter, f, sr)
            }
            curve[i] = totalDb.toFloat()
            if (totalDb > maxBoost) maxBoost = totalDb
        }

        // Additional dense peak scan
        for (step in 0 until 128) {
            val exp = step.toDouble() / 127.0
            val testF = 20.0 * (20000.0 / 20.0).pow(exp)
            var totalDb = 0.0
            for (filter in filters) {
                totalDb += evalDb(filter, testF, sr)
            }
            if (totalDb > maxBoost) maxBoost = totalDb
        }

        // Preamp Headroom at index 128
        val preampDb = if (maxBoost > 0.0) -(maxBoost + 0.2).toFloat() else 0.0f

        // Pre-EQ: Bands 0..31
        for (i in 0 until 32) {
            out[i] = freqs[i]
            out[32 + i] = curve[i]
        }

        // Post-EQ: Bands 32..63
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
