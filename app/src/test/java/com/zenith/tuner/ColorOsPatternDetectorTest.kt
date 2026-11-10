package com.zenith.tuner

import com.zenith.tuner.oem.ColorOsPatternDetector
import com.zenith.tuner.oem.ColorOsPatternReport
import com.zenith.tuner.oem.OemAudioFamily
import org.junit.Assert.*
import org.junit.Test

class ColorOsPatternDetectorTest {

    @Test
    fun testClassifyOemFamilyDetectsColorOsBrandsAndPackages() {
        assertEquals(
            OemAudioFamily.COLOR_OS_FAMILY,
            ColorOsPatternDetector.classifyOemFamily("realme", "realme", "RMX2117_11_C.14", emptyList())
        )
        assertEquals(
            OemAudioFamily.COLOR_OS_FAMILY,
            ColorOsPatternDetector.classifyOemFamily("OPPO", "OPPO", "CPH2449_13", emptyList())
        )
        assertEquals(
            OemAudioFamily.COLOR_OS_FAMILY,
            ColorOsPatternDetector.classifyOemFamily("OnePlus", "OnePlus", "NE2215", emptyList())
        )
        assertEquals(
            OemAudioFamily.COLOR_OS_FAMILY,
            ColorOsPatternDetector.classifyOemFamily(
                "Generic",
                "Generic",
                "Build-123",
                listOf("com.oplus.battery")
            )
        )
        assertEquals(
            OemAudioFamily.STANDARD_AOSP_OR_OTHER,
            ColorOsPatternDetector.classifyOemFamily("Google", "google", "AP2A.240905", emptyList())
        )
    }

    @Test
    fun testExtractOemAudioFxNamesFiltersVendorModulesOnly() {
        val detected = ColorOsPatternDetector.extractOemAudioFxNames(
            listOf(
                "Dynamics Processing" to "The Android Open Source Project",
                "RealSoundFX" to "Dirac Research AB",
                "Dolby Atmos" to "Dolby Laboratories",
                "RealSoundFX" to "Dirac Research AB"
            )
        )
        assertEquals(
            listOf(
                "RealSoundFX (Dirac Research AB)",
                "Dolby Atmos (Dolby Laboratories)"
            ),
            detected
        )
    }

    @Test
    fun testColorOsPatternReportFlagsRespondToSessionAndBatteryState() {
        val unbridgedColorOs = ColorOsPatternReport(
            oemFamily = OemAudioFamily.COLOR_OS_FAMILY,
            deviceSummary = "realme RMX2117",
            detectedOplusPackages = listOf("com.oplus.battery"),
            oemAudioFxImplementors = emptyList(),
            isBatteryOptimizationIgnored = false,
            hasActiveAppSession = false
        )
        assertTrue(unbridgedColorOs.isColorOsDetected)
        assertTrue(unbridgedColorOs.needsSessionBridge)
        assertTrue(unbridgedColorOs.needsBackgroundExemption)

        val bridgedAndUnrestricted = unbridgedColorOs.copy(
            isBatteryOptimizationIgnored = true,
            hasActiveAppSession = true
        )
        assertFalse(bridgedAndUnrestricted.needsSessionBridge)
        assertFalse(bridgedAndUnrestricted.needsBackgroundExemption)
    }
}
