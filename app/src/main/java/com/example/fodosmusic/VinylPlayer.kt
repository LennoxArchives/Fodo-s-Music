package com.example.fodosmusic

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VinylDisc(
    albumArt: Bitmap?,
    isPlaying: Boolean,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 9000, easing = LinearEasing)
                )
            }
        }
    }

    val discSize = size - 40.dp
    val labelSize = discSize * 0.6f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Halo lembut di belakang seluruh piringan, ini yang bikin kesan "glow" kayak referensi
        Box(
            modifier = Modifier
                .size(size * 1.2f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Ring progress dengan lapisan glow + titik terang di ujung progress
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 3.dp.toPx()
            val diameter = this.size.minDimension - strokeWidth
            val topLeft = Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            val sweep = 360f * progress.coerceIn(0f, 1f)

            // Track redup di belakang
            drawArc(
                color = Color.White.copy(alpha = 0.14f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            // Lapisan glow: makin lebar strokenya, makin kecil alpha-nya, biar keliatan "menyala"
            val glowLayers = listOf(
                14.dp.toPx() to 0.05f,
                9.dp.toPx() to 0.09f,
                5.dp.toPx() to 0.15f
            )
            glowLayers.forEach { (width, alpha) ->
                if (sweep > 0f) {
                    drawArc(
                        color = Color.White.copy(alpha = alpha),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = width)
                    )
                }
            }

            // Arc progress utama, tajam & terang
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            // Titik glow tepat di ujung progress, mirip highlight di referensi
            if (sweep > 0f) {
                val angleRad = Math.toRadians((sweep - 90f).toDouble())
                val radius = diameter / 2f
                val center = Offset(topLeft.x + radius, topLeft.y + radius)
                val dotCenter = Offset(
                    center.x + radius * cos(angleRad).toFloat(),
                    center.y + radius * sin(angleRad).toFloat()
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                        center = dotCenter,
                        radius = 10.dp.toPx()
                    ),
                    radius = 10.dp.toPx(),
                    center = dotCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = dotCenter
                )
            }
        }

        // Piringan vinyl yang berputar
        Box(
            modifier = Modifier
                .size(discSize)
                .rotate(rotation.value)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2C2C30),
                            Color(0xFF121214),
                            Color(0xFF000000)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Alur-alur vinyl (groove rings) konsentris
            Canvas(modifier = Modifier.matchParentSize()) {
                val ringCount = 12
                val maxRadius = this.size.minDimension / 2f
                val minRadius = maxRadius * 0.4f
                for (i in 0 until ringCount) {
                    val t = i / (ringCount - 1).toFloat()
                    val r = minRadius + (maxRadius - minRadius) * t
                    drawCircle(
                        color = Color.White.copy(alpha = if (i % 3 == 0) 0.09f else 0.04f),
                        radius = r,
                        style = Stroke(width = 1.1f)
                    )
                }
                // Rim light tipis di tepi luar piringan
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = maxRadius - 1.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Kilau diagonal (specular highlight) ala vinyl asli kena cahaya
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.White.copy(alpha = 0.05f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(900f, 900f)
                        )
                    )
            )

            // Label / album art di tengah
            Box(
                modifier = Modifier
                    .size(labelSize)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        painter = BitmapPainter(albumArt.asImageBitmap()),
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                // Vignette tipis biar label nyatu sama piringan, nggak keliatan nempel doang
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )
            }

            // Lubang spindle di tengah
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF050505))
            )
        }
    }
}