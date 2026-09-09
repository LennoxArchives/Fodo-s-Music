package com.example.fodosmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Dark theme palette
val BackgroundColor = Color(0xFF0D0F12)
val BackgroundColorSecondary = Color(0xFF1A1D22)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFFF5F5F5).copy(alpha = 0.6f)
val AccentColor = Color(0xFFFFFFFF)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun FloatingGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 28,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.06f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BackgroundColorSecondary,
                        BackgroundColor
                    )
                )
            )
    ) {
        content()
    }
}

fun Modifier.clickableSong(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }