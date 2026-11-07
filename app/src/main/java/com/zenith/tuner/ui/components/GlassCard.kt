package com.zenith.tuner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zenith.tuner.ui.theme.ObsidianBorder
import com.zenith.tuner.ui.theme.ObsidianSurface

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderGlow: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = if (borderGlow) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF00E5FF).copy(alpha = 0.45f),
                Color(0xFF2979FF).copy(alpha = 0.20f),
                ObsidianBorder
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF33333F),
                ObsidianBorder,
                Color(0xFF1E1E24)
            )
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, borderBrush),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF181820).copy(alpha = 0.70f),
                            Color(0xFF0F0F14).copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            content()
        }
    }
}
