package com.zenith.tuner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.tuner.dsp.AcousticState
import com.zenith.tuner.ui.theme.*

/**
 * Master DSP Toggle Card with active pulse indicator
 */
@Composable
fun MasterEngineCard(
    state: AcousticState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    GlassCard(
        modifier = modifier,
        borderGlow = state.isDspEnabled
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.isDspEnabled) ElectricCyan.copy(alpha = pulseAlpha)
                                else TitaniumMuted
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OBSIDIAN ACOUSTIC ENGINE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isDspEnabled) TextPrimary else TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.isDspEnabled)
                        "Hardware DynamicsProcessing Active • Preamp: ${String.format("%.1f", state.preampHeadroomDb)}dB"
                    else "Acoustic correction bypassed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isDspEnabled) ElectricCyan else TextTertiary
                )
            }

            Switch(
                checked = state.isDspEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = ElectricCyan,
                    uncheckedThumbColor = TitaniumGray,
                    uncheckedTrackColor = ObsidianBorder
                )
            )
        }
    }
}

/**
 * 50dB ANC Anti-Mud Compensation Card
 */
@Composable
fun AncAntiMudCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (enabled) AntiMudGreen else TitaniumGray,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "50dB ANC Anti-Mud",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (enabled) "-5.2dB Cut at 145Hz & HPF 24Hz Active" else "Driver Ringing Cut Bypassed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) AntiMudGreen else TextTertiary
                        )
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBlack,
                        checkedTrackColor = AntiMudGreen,
                        uncheckedThumbColor = TitaniumGray,
                        uncheckedTrackColor = ObsidianBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Neutralizes the heavy 100Hz–200Hz pressure wave pumped by active ANC error mics into the 11mm diaphragms, preventing muddy transients.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Sub-Bass Weight Control Card (55Hz Low-Shelf)
 */
@Composable
fun SubBassWeightCard(
    weight: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val gainDb = 3.6f * weight

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sub-Bass Punch (55Hz)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = String.format("%+.1f dB", gainDb),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = ElectricCyan,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = weight,
                onValueChange = onValueChange,
                valueRange = 0.0f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = ObsidianBorder
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Linear (0dB)", style = MaterialTheme.typography.labelSmall)
                Text("Nominal (+3.6dB)", style = MaterialTheme.typography.labelSmall)
                Text("Deep Slam (+7.2dB)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Vocal Presence Control Card (1650Hz & 3100Hz)
 */
@Composable
fun VocalPresenceCard(
    presence: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val gain1650 = 2.8f * presence
    val gain3100 = 3.2f * presence

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = ElectricAzure,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vocal Pinna Gain (1.65k & 3.1k)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = String.format("%+.1f / %+.1f dB", gain1650, gain3100),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = ElectricAzure,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = presence,
                onValueChange = onValueChange,
                valueRange = 0.0f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricAzure,
                    activeTrackColor = ElectricAzure,
                    inactiveTrackColor = ObsidianBorder
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recessed (0dB)", style = MaterialTheme.typography.labelSmall)
                Text("Forward (Nominal)", style = MaterialTheme.typography.labelSmall)
                Text("Intimate (+6dB)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 11mm Driver Transient Damper (MBC) Status Card
 */
@Composable
fun TransientDamperCard(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DamperAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Compress,
                    contentDescription = null,
                    tint = DamperAmber,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "11mm Transient Damper",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DamperAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "3.5ms MBC",
                            color = DamperAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Band 1 (70Hz–250Hz) dynamically clamps titanium diaphragm decay time to prevent bass overhang.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Firmware Neutralization Guide Checklist Card
 */
@Composable
fun FirmwareNeutralizationCard(modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }
    var step1Done by remember { mutableStateOf(true) }
    var step2Done by remember { mutableStateOf(true) }
    var step3Done by remember { mutableStateOf(true) }

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = TitaniumLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Earbud NVRAM Neutralization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Step 1: Clean onboard DSP state",
                            style = MaterialTheme.typography.labelSmall,
                            color = TitaniumGray
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TitaniumGray
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = ObsidianBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    ChecklistItem(
                        title = "1. Disable Dolby Audio / Spatial Sound",
                        subtitle = "Prevents phase cancellation & artificial reverb",
                        checked = step1Done,
                        onCheckedChange = { step1Done = it }
                    )
                    ChecklistItem(
                        title = "2. Disable Adaptive EQ",
                        subtitle = "Prevents firmware from shifting curves dynamically",
                        checked = step2Done,
                        onCheckedChange = { step2Done = it }
                    )
                    ChecklistItem(
                        title = "3. Set Sound Profile to Balanced / Natural",
                        subtitle = "Earbuds store this linear state permanently in NVRAM",
                        checked = step3Done,
                        onCheckedChange = { step3Done = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = ElectricCyan,
                uncheckedColor = TitaniumMuted,
                checkmarkColor = ObsidianBlack
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (checked) TextPrimary else TextSecondary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}
