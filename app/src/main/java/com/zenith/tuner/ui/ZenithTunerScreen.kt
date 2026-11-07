package com.zenith.tuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.tuner.dsp.*
import com.zenith.tuner.ui.components.*
import com.zenith.tuner.ui.theme.*

@Composable
fun ZenithTunerScreen(
    state: AcousticState,
    onToggleDsp: (Boolean) -> Unit,
    onTabSelect: (TunerTab) -> Unit,
    onPresetSelect: (AcousticPreset) -> Unit,
    onToggleAntiMud: (Boolean) -> Unit,
    onSubBassChange: (Float) -> Unit,
    onVocalPresenceChange: (Float) -> Unit,
    onCrossfeedChange: (Float) -> Unit,
    onParametricChange: (Int, Boolean?, Float?, Float?, Float?) -> Unit,
    onPreampChange: (Boolean, Float) -> Unit,
    onMbcChange: (MbcSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ZenithTuner",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "boAt Nirvana Zenith • Precision Acoustic Suite",
                        style = MaterialTheme.typography.labelSmall,
                        color = TitaniumGray
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianSurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (state.isNativeLoaded) "RUST ARM64-v8a" else "KOTLIN DSP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isNativeLoaded) ElectricCyan else DamperAmber
                    )
                }
            }

            // 120Hz Bode Plot Card (Always visible across all tabs for live feedback!)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                borderGlow = state.isDspEnabled
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACOUSTIC TRANSFER FUNCTION H(f)",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            color = TitaniumLight
                        )
                        Text(
                            text = "${state.activePreset.displayName} • 120Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    BodePlotCanvas(
                        curveGains = state.curveGains,
                        isDspActive = state.isDspEnabled,
                        preampDb = state.preampHeadroomDb
                    )
                }
            }

            // Tab Selector Bar
            TabSelectorBar(
                selectedTab = state.currentTab,
                onTabSelect = onTabSelect
            )

            // Content per selected tab
            when (state.currentTab) {
                TunerTab.EXPRESS -> {
                    // Preset Quick Switch Pills
                    PresetPillsRow(
                        activePreset = state.activePreset,
                        onPresetSelect = onPresetSelect
                    )

                    // Master Engine Switch
                    MasterEngineCard(
                        state = state,
                        onToggle = onToggleDsp
                    )

                    // 50dB ANC Anti-Mud Card
                    AncAntiMudCard(
                        enabled = state.ancAntiMudEnabled,
                        onToggle = onToggleAntiMud
                    )

                    // Sub-Bass Punch Slider
                    SubBassWeightCard(
                        weight = state.subBassWeight,
                        onValueChange = onSubBassChange
                    )

                    // Vocal Presence Slider
                    VocalPresenceCard(
                        presence = state.vocalPresence,
                        onValueChange = onVocalPresenceChange
                    )

                    // Binaural Crossfeed Card
                    CrossfeedCard(
                        amount = state.crossfeedAmount,
                        onValueChange = onCrossfeedChange
                    )

                    // Preamp Card
                    PreampHeadroomCard(
                        autoEnabled = state.autoPreampEnabled,
                        manualDb = state.manualPreampDb,
                        calculatedDb = state.preampHeadroomDb,
                        onAutoToggle = { onPreampChange(it, state.manualPreampDb) },
                        onManualChange = { onPreampChange(false, it) }
                    )

                    // Earbud NVRAM Neutralization Card
                    FirmwareNeutralizationCard()
                }

                TunerTab.PARAMETRIC -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "8-STAGE RBJ PARAMETRIC BIQUADS",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = TitaniumLight
                        )
                        Text(
                            text = "f64 Precision",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    state.stages.forEach { stage ->
                        ParametricStageCard(
                            stage = stage,
                            onEnabledChange = { onParametricChange(stage.id, it, null, null, null) },
                            onFreqChange = { onParametricChange(stage.id, null, it, null, null) },
                            onGainChange = { onParametricChange(stage.id, null, null, null, it) },
                            onQChange = { onParametricChange(stage.id, null, null, it, null) }
                        )
                    }
                }

                TunerTab.DYNAMICS -> {
                    // MBC Studio
                    MbcDynamicsStudioCard(
                        settings = state.mbcSettings,
                        onSettingsChange = onMbcChange
                    )

                    // Limiter Peak Guard
                    LimiterPeakGuardCard(
                        settings = state.mbcSettings,
                        onSettingsChange = onMbcChange
                    )
                }

                TunerTab.PRESETS -> {
                    Text(
                        text = "ACOUSTIC TARGET VAULT",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp,
                        color = TitaniumLight
                    )

                    AcousticPreset.values().forEach { preset ->
                        PresetVaultCard(
                            preset = preset,
                            isActive = preset == state.activePreset,
                            onSelect = { onPresetSelect(preset) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    PreampHeadroomCard(
                        autoEnabled = state.autoPreampEnabled,
                        manualDb = state.manualPreampDb,
                        calculatedDb = state.preampHeadroomDb,
                        onAutoToggle = { onPreampChange(it, state.manualPreampDb) },
                        onManualChange = { onPreampChange(false, it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Segmented Obsidian Tab Bar
 */
@Composable
private fun TabSelectorBar(
    selectedTab: TunerTab,
    onTabSelect: (TunerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TunerTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) ObsidianSurfaceElevated else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onTabSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) ElectricCyan else TextSecondary
                )
            }
        }
    }
}

/**
 * Card for selecting and exploring acoustic presets in the Vault
 */
@Composable
private fun PresetVaultCard(
    preset: AcousticPreset,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        borderGlow = isActive
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) ElectricCyan else TextPrimary
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElectricCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = ElectricCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            RadioButton(
                selected = isActive,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = ElectricCyan,
                    unselectedColor = TitaniumMuted
                )
            )
        }
    }
}
