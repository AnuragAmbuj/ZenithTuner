package com.zenith.tuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.tuner.dsp.MbcSettings
import com.zenith.tuner.ui.theme.*

/**
 * Full controls for Android 12 DynamicsProcessing Multi-Band Compression & 11mm Transient Damping
 */
@Composable
fun MbcDynamicsStudioCard(
    settings: MbcSettings,
    onSettingsChange: (MbcSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DamperAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = null,
                        tint = DamperAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "11mm Transient Damper (MBC)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Band 1: 70Hz–250Hz Diaphragm Ringing Clamp",
                        style = MaterialTheme.typography.labelSmall,
                        color = DamperAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Attack Time Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Damper Attack Time", style = MaterialTheme.typography.labelSmall)
                    Text(
                        String.format("%.1f ms (Nominal: 3.5ms)", settings.damperAttackMs),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = DamperAmber,
                        fontSize = 12.sp
                    )
                }
                Slider(
                    value = settings.damperAttackMs,
                    onValueChange = { onSettingsChange(settings.copy(damperAttackMs = it)) },
                    valueRange = 1.0f..15.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = DamperAmber,
                        activeTrackColor = DamperAmber,
                        inactiveTrackColor = ObsidianBorder
                    )
                )
                Text(
                    text = "A fast 3.5ms attack instantly clamps diaphragm ringing under 50dB ANC. Longer attack lets more bass bloom through.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 11.sp,
                    color = TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Threshold Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Damper Threshold", style = MaterialTheme.typography.labelSmall)
                    Text(
                        String.format("%.1f dB", settings.damperThresholdDb),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontSize = 12.sp
                    )
                }
                Slider(
                    value = settings.damperThresholdDb,
                    onValueChange = { onSettingsChange(settings.copy(damperThresholdDb = it)) },
                    valueRange = -28.0f..-6.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = ObsidianBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Compression Ratio Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Compression Ratio", style = MaterialTheme.typography.labelSmall)
                    Text(
                        String.format("%.1f:1", settings.damperRatio),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ElectricAzure,
                        fontSize = 12.sp
                    )
                }
                Slider(
                    value = settings.damperRatio,
                    onValueChange = { onSettingsChange(settings.copy(damperRatio = it)) },
                    valueRange = 1.5f..5.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricAzure,
                        activeTrackColor = ElectricAzure,
                        inactiveTrackColor = ObsidianBorder
                    )
                )
            }
        }
    }
}

/**
 * Hardware Limiter & 0dBFS True-Peak Guard Card
 */
@Composable
fun LimiterPeakGuardCard(
    settings: MbcSettings,
    onSettingsChange: (MbcSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AntiMudGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AntiMudGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Hardware Output Limiter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "0dBFS DAC Inter-Sample Peak Guard",
                        style = MaterialTheme.typography.labelSmall,
                        color = AntiMudGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Limiter Threshold", style = MaterialTheme.typography.labelSmall)
                Text(
                    String.format("%.2f dBFS", settings.limiterThresholdDb),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = AntiMudGreen,
                    fontSize = 12.sp
                )
            }

            Slider(
                value = settings.limiterThresholdDb,
                onValueChange = { onSettingsChange(settings.copy(limiterThresholdDb = it)) },
                valueRange = -1.0f..0.0f,
                colors = SliderDefaults.colors(
                    thumbColor = AntiMudGreen,
                    activeTrackColor = AntiMudGreen,
                    inactiveTrackColor = ObsidianBorder
                )
            )

            Text(
                text = "Guarantees that combined equalization peaks never induce digital DAC clipping or harmonic distortion on Bluetooth AAC.",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = TextTertiary
            )
        }
    }
}
