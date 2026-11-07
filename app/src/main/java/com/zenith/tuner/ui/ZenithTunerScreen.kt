package com.zenith.tuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.tuner.dsp.AcousticState
import com.zenith.tuner.ui.components.*
import com.zenith.tuner.ui.theme.*

@Composable
fun ZenithTunerScreen(
    state: AcousticState,
    onToggleDsp: (Boolean) -> Unit,
    onToggleAntiMud: (Boolean) -> Unit,
    onSubBassChange: (Float) -> Unit,
    onVocalPresenceChange: (Float) -> Unit,
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
                        text = "boAt Nirvana Zenith • Mystique Black",
                        style = MaterialTheme.typography.labelSmall,
                        color = TitaniumGray
                    )
                }

                // Architecture Tag
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

            // 120Hz Bode Plot Card
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
                            text = "64 BANDS • 120Hz",
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

            // Master Engine Switch Card
            MasterEngineCard(
                state = state,
                onToggle = onToggleDsp
            )

            // 50dB ANC Anti-Mud Card
            AncAntiMudCard(
                enabled = state.ancAntiMudEnabled,
                onToggle = onToggleAntiMud
            )

            // Sub-Bass Weight Card
            SubBassWeightCard(
                weight = state.subBassWeight,
                onValueChange = onSubBassChange
            )

            // Vocal Presence Card
            VocalPresenceCard(
                presence = state.vocalPresence,
                onValueChange = onVocalPresenceChange
            )

            // 11mm Transient Damper Card
            TransientDamperCard()

            // Firmware Neutralization Card
            FirmwareNeutralizationCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
