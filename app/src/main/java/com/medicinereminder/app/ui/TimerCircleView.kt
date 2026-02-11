package com.relarm.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * Custom circular progress view for timer-style alarm cards.
 * Draws a stroked circle with two arcs:
 * - Remaining time (grey/onSurfaceVariant)
 * - Completed time (accent/primaryInverse)
 * 
 * OPTIMIZED: Calculates progress inside drawBehind to avoid recomposition.
 * Only triggers Draw Phase, not Composition Phase.
 * 
 * @param scheduledTime The timestamp when the timer will complete (0 if not running)
 * @param totalDuration Total duration of the timer in milliseconds
 * @param isExpired Whether the timer has expired (triggers blink animation)
 * @param isPaused Whether the timer is paused
 * @param pausedRemainingMs Remaining time when paused (only used if isPaused is true)
 * @param modifier Modifier for the canvas
 */
@Composable
fun TimerCircleView(
    scheduledTime: Long,
    totalDuration: Long,
    isExpired: Boolean,
    isPaused: Boolean = false,
    pausedRemainingMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val remainingColor = MaterialTheme.colorScheme.onSurfaceVariant
    val completedColor = MaterialTheme.colorScheme.primary
    val expiredColor = MaterialTheme.colorScheme.error
    
    // Frame invalidation ticker - triggers redraw without recomposition
    var frameInvalidator by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(scheduledTime, isPaused, isExpired) {
        // Only tick if timer is running (not paused, not expired, and has valid scheduledTime)
        if (!isPaused && !isExpired && scheduledTime > 0) {
            while (isActive) {
                withFrameMillis { frameTime ->
                    frameInvalidator = frameTime
                }
            }
        }
    }
    
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
    
    // Trigger recomposition on frame updates (Canvas will redraw)
    // Removed external read to rely on draw-phase read below
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .drawBehind {
                // CRITICAL: Read state inside draw scope to trigger redraws on change
                val tick = frameInvalidator
                
                // OPTIMIZATION: Calculate progress HERE (in draw phase)
                val progress = when {
                    isExpired -> 1f
                    isPaused -> {
                        // When paused, calculate from paused remaining time
                        if (totalDuration > 0) {
                            val elapsed = totalDuration - pausedRemainingMs
                            (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                    }
                    scheduledTime > 0 -> {
                        // When running, calculate from scheduledTime
                        val currentTime = System.currentTimeMillis()
                        val remainingMs = (scheduledTime - currentTime).coerceAtLeast(0)
                        if (totalDuration > 0) {
                            val elapsed = totalDuration - remainingMs
                            (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                    }
                    else -> 0f
                }
                
                val strokeWidth = 6.dp.toPx()
                val diameter = size.minDimension - strokeWidth
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
    )
}
