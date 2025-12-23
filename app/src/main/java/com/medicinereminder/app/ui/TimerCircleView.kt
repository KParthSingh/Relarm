package com.medicinereminder.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Custom circular progress view for timer-style alarm cards.
 * Draws a stroked circle with two arcs:
 * - Remaining time (grey/onSurfaceVariant)
 * - Completed time (accent/primaryInverse)
 * 
 * @param progress Progress from 0.0 (start) to 1.0 (complete)
 * @param isExpired Whether the timer has expired (triggers blink animation)
 * @param modifier Modifier for the canvas
 */
@Composable
fun TimerCircleView(
    progress: Float,
    isExpired: Boolean,
    modifier: Modifier = Modifier
) {
    val remainingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val completedColor = MaterialTheme.colorScheme.primary
    val expiredColor = MaterialTheme.colorScheme.error
    
    // Blink animation when expired
    val blinkAlpha by animateFloatAsState(
        targetValue = if (isExpired) {
            // Animate between 0.3 and 1.0
            val infiniteTransition = rememberInfiniteTransition(label = "blink")
            infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "blinkAlpha"
            ).value
        } else 1.0f,
        label = "blinkAlpha"
    )
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val strokeWidth = 6.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val radius = diameter / 2
        val topLeft = Offset(
            x = (size.width - diameter) / 2,
            y = (size.height - diameter) / 2
        )
        
        val arcSize = Size(diameter, diameter)
        
        // Start at top (12 o'clock position)
        val startAngle = -90f
        val totalSweep = 360f
        
        if (isExpired) {
            // Full circle blink when expired
            drawArc(
                color = expiredColor.copy(alpha = blinkAlpha),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        } else {
            // Remaining arc (grey)
            val remainingSweep = totalSweep * (1f - progress)
            if (remainingSweep > 0) {
                drawArc(
                    color = remainingColor.copy(alpha = 0.3f),
                    startAngle = startAngle + (totalSweep * progress),
                    sweepAngle = remainingSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            
            // Completed arc (accent color)
            val completedSweep = totalSweep * progress
            if (completedSweep > 0) {
                drawArc(
                    color = completedColor,
                    startAngle = startAngle,
                    sweepAngle = completedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}
