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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenithTunerScreen(
    state: AcousticState,
    volumeRatio: Float,
    digitalOutputGainDb: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onDigitalGainChange: (Float) -> Unit,
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ZenithTuner",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (state.isDspEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = if (state.isDspEnabled) "DSP ACTIVE" else "BYPASS",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isDspEnabled) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Text(
                            text = "boAt Nirvana Zenith • Precision Acoustic Suite",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (state.isNativeLoaded) "RUST ARM64" else "KOTLIN DSP",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isNativeLoaded) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 120Hz Bode Plot Card (Always visible across all tabs for real-time acoustic feedback)
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${state.activePreset.displayName} • 120Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
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

            // Material 3 Primary Tab Row
            PrimaryTabRow(
                selectedTabIndex = state.currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(state.currentTab.ordinal),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                TunerTab.values().forEach { tab ->
                    val isSelected = tab == state.currentTab
                    Tab(
                        selected = isSelected,
                        onClick = { onTabSelect(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Content per selected tab
            when (state.currentTab) {
                TunerTab.EXPRESS -> {
                    // Quick Preset Chips
                    PresetPillsRow(
                        activePreset = state.activePreset,
                        onPresetSelect = onPresetSelect
                    )

                    // Material 3 Media Volume & Digital Output Compensation Card
                    VolumeCard(
                        volumeRatio = volumeRatio,
                        digitalOutputGainDb = digitalOutputGainDb,
                        preampHeadroomDb = state.preampHeadroomDb,
                        onVolumeChange = onVolumeChange,
                        onToggleMute = onToggleMute,
                        onDigitalGainChange = onDigitalGainChange
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "f64 Precision",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
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
                    // Volume & Output Headroom Card
                    VolumeCard(
                        volumeRatio = volumeRatio,
                        digitalOutputGainDb = digitalOutputGainDb,
                        preampHeadroomDb = state.preampHeadroomDb,
                        onVolumeChange = onVolumeChange,
                        onToggleMute = onToggleMute,
                        onDigitalGainChange = onDigitalGainChange
                    )

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
                        color = MaterialTheme.colorScheme.onSurface
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
 * Card for selecting and exploring acoustic presets in the Vault
 */
@Composable
private fun PresetVaultCard(
    preset: AcousticPreset,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 3.dp else 1.dp
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isActive,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
