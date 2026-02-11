package com.relarm.app

import java.util.UUID

enum class AlarmState {
    RESET,    // Initial state, not started
    RUNNING,  // Active countdown
    PAUSED,   // Paused countdown
    EXPIRED   // Time's up, ringing
}

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 5,
    val isActive: Boolean = false,
    val scheduledTime: Long = 0L,
    val soundUri: String? = null, // URI of custom alarm sound, null = use default
    val state: AlarmState = AlarmState.RESET,
    val totalDuration: Long = 0L, // Total duration in milliseconds for progress calculation
    val startTime: Long = 0L, // When alarm started (for progress calculation)
    val pausedRemainingMs: Long = 0L // Remaining time when paused (0 if not paused)
) {
    fun getTotalSeconds(): Int = hours * 3600 + minutes * 60 + seconds
    
    fun getFormattedTime(): String {
        return when {
            hours > 0 -> {
                // Show hours and minutes: "1:05:30" or "2:00:00"
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            }
            minutes > 0 -> {
                // Show minutes and seconds: "5:30" or "1:00"
                String.format("%d:%02d", minutes, seconds)
            }
            else -> {
                // Show seconds only: "30 sec" or "5 sec"
                "$seconds Sec"
            }
        }
    }
    
    fun clone(): Alarm = this.copy(id = UUID.randomUUID().toString(), isActive = false, scheduledTime = 0L, state = AlarmState.RESET, pausedRemainingMs = 0L)
    
    // Calculate progress from 0.0 (start) to 1.0 (complete)
    fun getProgress(): Float {
        if (state == AlarmState.RESET) return 0f
        if (state == AlarmState.EXPIRED) return 1f
        
        // Get the original total duration from hours/minutes/seconds
        val originalTotalMs = getTotalSeconds() * 1000L
        if (originalTotalMs <= 0) return 0f
        
        val remainingMs = if (state == AlarmState.PAUSED) {
            // When paused, use saved paused time
            pausedRemainingMs
        } else {
            // When running, calculate from scheduledTime (same as time display!)
            (scheduledTime - System.currentTimeMillis()).coerceAtLeast(0)
        }
        
        val elapsed = originalTotalMs - remainingMs
        return (elapsed.toFloat() / originalTotalMs.toFloat()).coerceIn(0f, 1f)
    }
}
