package com.zenith.tuner.oem

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Classifies host ROM audio and power-management lineage.
 */
enum class OemAudioFamily {
    /** ColorOS, Realme UI, or OxygenOS (OPPO / realme / OnePlus Oplus stack). */
    COLOR_OS_FAMILY,

    /** Standard AOSP or other vendor stack. */
    STANDARD_AOSP_OR_OTHER
}

/**
 * Snapshot of ColorOS / OEM behavioral patterns affecting audio session routing
 * and background DSP continuity.
 */
data class ColorOsPatternReport(
    val oemFamily: OemAudioFamily,
    val deviceSummary: String,
    val detectedOplusPackages: List<String>,
    val oemAudioFxImplementors: List<String>,
    val isBatteryOptimizationIgnored: Boolean,
    val hasActiveAppSession: Boolean
) {
    val isColorOsDetected: Boolean
        get() = oemFamily == OemAudioFamily.COLOR_OS_FAMILY

    val needsSessionBridge: Boolean
        get() = isColorOsDetected && !hasActiveAppSession

    val needsBackgroundExemption: Boolean
        get() = !isBatteryOptimizationIgnored
}

/**
 * Detects ColorOS / Realme UI / Oplus patterns solely through documented Android SDK APIs
 * (Build metadata, declared PackageManager queries, AudioEffect.queryEffects(), and PowerManager).
 */
object ColorOsPatternDetector {

    private const val TAG = "ColorOsPatternDetector"

    private val OPLUS_BRAND_KEYWORDS = setOf("oppo", "realme", "oneplus")

    internal val COLOROS_SYSTEM_PACKAGES = listOf(
        "com.oplus.battery",
        "com.coloros.oppoguardelf",
        "com.oplus.athena",
        "com.oplus.audio.effectcenter",
        "com.coloros.soundrecorder",
        "com.oplus.sau"
    )

    private val OEM_AUDIO_FX_KEYWORDS = listOf(
        "dirac", "dolby", "oplus", "oppo", "realme", "realsound", "waves", "dts"
    )

    /**
     * Classifies whether the host device belongs to the ColorOS / Oplus ROM lineage.
     */
    internal fun classifyOemFamily(
        manufacturer: String,
        brand: String,
        display: String,
        detectedPackages: Collection<String>
    ): OemAudioFamily {
        val mfg = manufacturer.lowercase()
        val br = brand.lowercase()
        val disp = display.lowercase()
        if (OPLUS_BRAND_KEYWORDS.any { it in mfg || it in br }) {
            return OemAudioFamily.COLOR_OS_FAMILY
        }
        if ("coloros" in disp || "realmeui" in disp || detectedPackages.isNotEmpty()) {
            return OemAudioFamily.COLOR_OS_FAMILY
        }
        return OemAudioFamily.STANDARD_AOSP_OR_OTHER
    }

    /**
     * Filters AudioEffect descriptor (name, implementor) pairs for vendor sound processors
     * (e.g., Dirac, Dolby, Real Sound / Oplus AudioFX) that may run parallel to DynamicsProcessing.
     */
    internal fun extractOemAudioFxNames(
        descriptors: List<Pair<String, String>>
    ): List<String> {
        return descriptors.mapNotNull { (name, implementor) ->
            val combined = "$name $implementor".lowercase()
            if (OEM_AUDIO_FX_KEYWORDS.any { it in combined }) {
                val cleanName = name.trim()
                val cleanImpl = implementor.trim()
                when {
                    cleanName.isNotEmpty() && cleanImpl.isNotEmpty() -> "$cleanName ($cleanImpl)"
                    cleanName.isNotEmpty() -> cleanName
                    cleanImpl.isNotEmpty() -> cleanImpl
                    else -> null
                }
            } else {
                null
            }
        }.distinct()
    }

    /**
     * Inspects the running device for ColorOS routing, Hans freezer risk, and vendor AudioFX patterns.
     */
    fun inspect(context: Context, activeSessionCount: Int): ColorOsPatternReport {
        val detectedPackages = queryColorOsPackages(context.packageManager)
        val oemFamily = classifyOemFamily(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            display = Build.DISPLAY.orEmpty(),
            detectedPackages = detectedPackages
        )

        val effectPairs = try {
            AudioEffect.queryEffects()
                ?.map { (it.name ?: "") to (it.implementor ?: "") }
                ?: emptyList()
        } catch (e: Throwable) {
            Log.w(TAG, "AudioEffect.queryEffects() unavailable", e)
            emptyList()
        }

        val batteryIgnored = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } catch (e: Throwable) {
            Log.w(TAG, "PowerManager battery optimization query failed", e)
            true
        }

        val brandLabel = Build.BRAND?.takeIf { it.isNotBlank() } ?: Build.MANUFACTURER.orEmpty()
        val modelLabel = Build.MODEL.orEmpty()
        val deviceSummary = "$brandLabel $modelLabel".trim().ifEmpty { "Android Device" }

        return ColorOsPatternReport(
            oemFamily = oemFamily,
            deviceSummary = deviceSummary,
            detectedOplusPackages = detectedPackages,
            oemAudioFxImplementors = extractOemAudioFxNames(effectPairs),
            isBatteryOptimizationIgnored = batteryIgnored,
            hasActiveAppSession = activeSessionCount > 0
        )
    }

    private fun queryColorOsPackages(pm: PackageManager): List<String> {
        return COLOROS_SYSTEM_PACKAGES.filter { pkg ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (e: Throwable) {
                Log.d(TAG, "Package check skipped for $pkg", e)
                false
            }
        }
    }

    /**
     * Opens system battery optimization controls so the user can exempt ZenithTuner
     * from ColorOS OplusHansManager background freezing on screen-off.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val candidates = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = packageUri },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = packageUri }
        )

        for (intent in candidates) {
            try {
                if (context !is Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Throwable) {
                Log.w(TAG, "Battery settings intent failed: ${intent.action}", e)
            }
        }
    }
}
