package com.zenith.tuner.ui.components

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.tuner.dsp.AcousticState
import com.zenith.tuner.dsp.TunerTab
import com.zenith.tuner.ui.theme.*

/**
 * Material 3 Expressive Hero A/B Blind-Test & Master Engine Card.
 * Gives user an instant 0-latency toggle between the Improved Audiophile Master
 * (+6dB Sub-Slam, -7dB Anti-Mud, +4.8dB Pinna) and Stock boAt Hardware Bypass.
 */
@Composable
fun ExpressiveAbCompareCard(
    state: AcousticState,
    onToggleDsp: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isEngaged = state.isDspEnabled

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isEngaged) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "containerColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isEngaged) {
            MaterialTheme.colorScheme.primary.copy(alpha = pulseGlow)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        },
        animationSpec = tween(400),
        label = "borderColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isEngaged) 6.dp else 1.dp
        ),
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Status badge & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEngaged) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Column {
                        Text(
                            text = if (isEngaged) "AUDIOPHILE MASTER ENGINE" else "STOCK boAt HARDWARE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = if (isEngaged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isEngaged) "Active • 32-Band Pre-EQ + 11mm Damper + Limiter" else "DSP Bypassed • Factory Boomy/Muddy Curve",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = if (isEngaged) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = if (isEngaged) "A/B: ACTIVE" else "A/B: BYPASS",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEngaged) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // High-Impact Segmented Pill Switcher (One-Tap Instant Compare)
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    // Option 1: Audiophile Improved Target
                    val improvedBgModifier = if (isEngaged) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                    } else {
                        Modifier.background(Color.Transparent)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(percent = 50))
                            .then(improvedBgModifier)
                            .clickable {
                                if (!isEngaged) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleDsp(true)
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isEngaged) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "IMPROVED (+6dB)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEngaged) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Option 2: Pure Hardware Bypass (boAt stock)
                    val stockBgColor = if (!isEngaged) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(stockBgColor)
                            .clickable {
                                if (isEngaged) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleDsp(false)
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (!isEngaged) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "STOCK BYPASS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isEngaged) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Quick Hardware Profile Spec Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AcousticPill(label = "55Hz Sub", value = if (isEngaged) "+6.0dB" else "0.0dB")
                AcousticPill(label = "145Hz Mud", value = if (isEngaged) "-7.0dB" else "0.0dB")
                AcousticPill(label = "3.1k Pinna", value = if (isEngaged) "+4.8dB" else "0.0dB")
                val sessionPillValue = when (state.activeSessionCount) {
                    0 -> "0 (Mix)"
                    1 -> "1 App"
                    else -> "${state.activeSessionCount} Apps"
                }
                AcousticPill(label = "Sessions", value = sessionPillValue)
            }
        }
    }
}

/**
 * Material 3 Expressive Floating Pill Segmented Tab Navigation.
 * Features rounded pill indicators, icon pairings, and smooth spring physics.
 */
@Composable
fun ExpressiveSegmentedNavBar(
    selectedTab: TunerTab,
    onTabSelect: (TunerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (tab in TunerTab.values()) {
                val isSelected = tab == selectedTab
                val tabIcon: ImageVector = when (tab) {
                    TunerTab.EXPRESS -> Icons.Default.Tune
                    TunerTab.PARAMETRIC -> Icons.Default.Equalizer
                    TunerTab.DYNAMICS -> Icons.Default.Compress
                    TunerTab.PRESETS -> Icons.Default.Bookmarks
                }

                val pillBackground by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "tabPillBg"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(pillBackground)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelect(tab)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = tabIcon,
                            contentDescription = tab.title,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expressive Tactile Pill Slider with thick 14dp rounded track,
 * numeric value badge, and haptic feedback on adjustment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    steps: Int = 0,
    hapticFeedback: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Slider(
        value = value,
        onValueChange = {
            if (hapticFeedback) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onValueChange(it)
        },
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = modifier
    )
}

/**
 * Expressive One-Tap Streaming Audio Bridge Card.
 * Lets the user bridge YouTube Music / Spotify private audio sessions in 1 click.
 */
@Composable
fun ExpressiveAudioHookCard(
    activeSessions: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = if (activeSessions > 0) "Streaming Audio Hooked" else "Hook Streaming Audio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (activeSessions > 0) {
                            "Attached to $activeSessions dedicated app stream${if (activeSessions == 1) "" else "s"}"
                        } else {
                            "Global Mix (0) fallback • Tap Bridge for YT Music / Spotify"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                        context.startActivity(eqIntent)
                    } catch (_: Throwable) {
                        // Fallback to media sound settings
                        val soundIntent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                        context.startActivity(soundIntent)
                    }
                },
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Bridge",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AcousticPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
