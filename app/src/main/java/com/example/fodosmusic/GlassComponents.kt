package com.example.fodosmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
val FavoriteColor = Color(0xFFFF5C7A)

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

/**
 * Tombol bundar bergaya glass, dipakai buat tombol favorite yang berdiri sendiri
 * di Now Playing screen. Kalau [active] true, latar & border-nya lebih terang.
 */
@Composable
fun CircleGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 46,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (active) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (active) 0.30f else 0.14f),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Panel/bottom-sheet kaca yang muncul menempel di bawah layar Now Playing,
 * dipakai buat konten "Info" dan "Antrian".
 */
@Composable
fun GlassSheetContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C1F24).copy(alpha = 0.94f),
                        Color(0xFF0D0F12).copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        content = content
    )
}

fun Modifier.clickableSong(onClick: () -> Unit): Modifier =
    this.clickable { onClick() }