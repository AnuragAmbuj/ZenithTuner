package com.zenith.tuner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.tuner.dsp.FilterKind
import com.zenith.tuner.dsp.ParametricStageConfig
import com.zenith.tuner.ui.theme.*

/**
 * Card for full parametric inspection and tuning of a single biquad stage
 */
@Composable
fun ParametricStageCard(
    stage: ParametricStageConfig,
    onEnabledChange: (Boolean) -> Unit,
    onFreqChange: (Float) -> Unit,
    onGainChange: (Float) -> Unit,
    onQChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    GlassCard(
        modifier = modifier,
        borderGlow = stage.enabled && isExpanded
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = stage.enabled,
                        onCheckedChange = onEnabledChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = ElectricCyan,
                            uncheckedColor = TitaniumMuted,
                            checkmarkColor = ObsidianBlack
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stage.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (stage.enabled) TextPrimary else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (stage.kind) {
                                            FilterKind.HIGH_PASS -> ElectricAzure.copy(alpha = 0.2f)
                                            FilterKind.LOW_SHELF -> ElectricCyan.copy(alpha = 0.2f)
                                            FilterKind.PEAKING -> DamperAmber.copy(alpha = 0.2f)
                                            FilterKind.HIGH_SHELF -> AntiMudGreen.copy(alpha = 0.2f)
                                            FilterKind.PASS -> TitaniumMuted.copy(alpha = 0.2f)
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stage.kind.label,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = when (stage.kind) {
                                        FilterKind.HIGH_PASS -> ElectricAzure
                                        FilterKind.LOW_SHELF -> ElectricCyan
                                        FilterKind.PEAKING -> DamperAmber
                                        FilterKind.HIGH_SHELF -> AntiMudGreen
                                        FilterKind.PASS -> TitaniumGray
                                    }
                                )
                            }
                        }
                        Text(
                            text = "${formatFreq(stage.freq)}  •  ${formatGain(stage.gainDb)}  •  Q: ${String.format("%.2f", stage.q)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (stage.enabled) ElectricCyan else TextTertiary
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
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stage.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Frequency Slider
                    SliderRow(
                        label = "Frequency",
                        valueStr = formatFreq(stage.freq),
                        value = stage.freq,
                        valueRange = stage.minFreq..stage.maxFreq,
                        onValueChange = onFreqChange,
                        activeColor = ElectricCyan
                    )

                    // 2. Gain Slider (if not High-Pass)
                    if (stage.kind != FilterKind.HIGH_PASS) {
                        Spacer(modifier = Modifier.height(6.dp))
                        SliderRow(
                            label = "Gain (dB)",
                            valueStr = formatGain(stage.gainDb),
                            value = stage.gainDb,
                            valueRange = stage.minGain..stage.maxGain,
                            onValueChange = onGainChange,
                            activeColor = if (stage.gainDb >= 0) ElectricCyan else DamperAmber
                        )
                    }

                    // 3. Q Factor Slider
                    Spacer(modifier = Modifier.height(6.dp))
                    SliderRow(
                        label = "Q Factor",
                        valueStr = String.format("%.2f", stage.q),
                        value = stage.q,
                        valueRange = stage.minQ..stage.maxQ,
                        onValueChange = onQChange,
                        activeColor = ElectricAzure
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueStr: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    activeColor: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TitaniumLight)
            Text(
                valueStr,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = activeColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = ObsidianBorder
            )
        )
    }
}

private fun formatFreq(freq: Float): String {
    return if (freq >= 1000f) {
        String.format("%.1fkHz", freq / 1000f)
    } else {
        String.format("%.0fHz", freq)
    }
}

private fun formatGain(gainDb: Float): String {
    return String.format("%+.1fdB", gainDb)
}
