package com.relarm.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Duration data class representing hours, minutes, and seconds.
 */
data class Duration(
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0
) {
    fun toTotalSeconds(): Int = hours * 3600 + minutes * 60 + seconds
    
    companion object {
        fun fromTotalSeconds(totalSeconds: Int): Duration {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return Duration(h, m, s)
        }
    }
}

private data class TimeUnit(
    val text: String,
    val value: Int,
    val index: Int
)

/**
 * Wheel-based duration picker with hours, minutes, and seconds.
 * Provides a scroll-wheel interface for selecting time durations.
 */
@Composable
fun WheelDurationPicker(
    modifier: Modifier = Modifier,
    startDuration: Duration = Duration(),
    size: DpSize = DpSize(256.dp, 150.dp),
    rowCount: Int = 3,
    textStyle: TextStyle = MaterialTheme.typography.titleLarge,
    textColor: Color = LocalContentColor.current,
    selectorProperties: SelectorProperties = WheelPickerDefaults.selectorProperties(),
    onDurationChanged: (Duration) -> Unit = {},
) {
    var currentDuration by remember { mutableStateOf(startDuration) }
    
    // Generate hour options (0-23)
    val hours = (0..23).map { TimeUnit(it.toString().padStart(2, '0'), it, it) }
    // Generate minute options (0-59)
    val minutes = (0..59).map { TimeUnit(it.toString().padStart(2, '0'), it, it) }
    // Generate second options (0-59)
    val seconds = (0..59).map { TimeUnit(it.toString().padStart(2, '0'), it, it) }
    
    val wheelWidth = size.width / 5  // Divide by 5 to account for labels
    
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Selector background
        if (selectorProperties.enabled().value) {
            Surface(
                modifier = Modifier.size(size.width, size.height / rowCount),
                shape = selectorProperties.shape().value,
                color = selectorProperties.color().value,
                border = selectorProperties.border().value
            ) {}
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Hours picker
            WheelTextPicker(
                size = DpSize(wheelWidth, size.height),
                texts = hours.map { it.text },
                rowCount = rowCount,
                style = textStyle,
                color = textColor,
                startIndex = hours.find { it.value == startDuration.hours }?.index ?: 0,
                selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false),
                onScrollFinished = { snappedIndex ->
                    val newHour = hours.find { it.index == snappedIndex }?.value ?: 0
                    currentDuration = currentDuration.copy(hours = newHour)
                    onDurationChanged(currentDuration)
                    snappedIndex
                }
            )
            
            // "h" label
            Text(
                text = "h",
                style = textStyle,
                color = textColor.copy(alpha = 0.6f)
            )
            
            // Minutes picker
            WheelTextPicker(
                size = DpSize(wheelWidth, size.height),
                texts = minutes.map { it.text },
                rowCount = rowCount,
                style = textStyle,
                color = textColor,
                startIndex = minutes.find { it.value == startDuration.minutes }?.index ?: 0,
                selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false),
                onScrollFinished = { snappedIndex ->
                    val newMinute = minutes.find { it.index == snappedIndex }?.value ?: 0
                    currentDuration = currentDuration.copy(minutes = newMinute)
                    onDurationChanged(currentDuration)
                    snappedIndex
                }
            )
            
            // "m" label
            Text(
                text = "m",
                style = textStyle,
                color = textColor.copy(alpha = 0.6f)
            )
            
            // Seconds picker
            WheelTextPicker(
                size = DpSize(wheelWidth, size.height),
                texts = seconds.map { it.text },
                rowCount = rowCount,
                style = textStyle,
                color = textColor,
                startIndex = seconds.find { it.value == startDuration.seconds }?.index ?: 0,
                selectorProperties = WheelPickerDefaults.selectorProperties(enabled = false),
                onScrollFinished = { snappedIndex ->
                    val newSecond = seconds.find { it.index == snappedIndex }?.value ?: 0
                    currentDuration = currentDuration.copy(seconds = newSecond)
                    onDurationChanged(currentDuration)
                    snappedIndex
                }
            )
            
            // "s" label
            Text(
                text = "s",
                style = textStyle,
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Simplified duration picker that matches the existing API used in the app.
 * Provides hours, minutes, seconds wheels with callback on time change.
 */
@Composable
fun SimpleDurationPicker(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onTimeChange: (hours: Int, minutes: Int, seconds: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    WheelDurationPicker(
        modifier = modifier,
        startDuration = Duration(hours, minutes, seconds),
        size = DpSize(280.dp, 150.dp),
        rowCount = 3,
        textStyle = MaterialTheme.typography.headlineMedium,
        onDurationChanged = { duration ->
            onTimeChange(duration.hours, duration.minutes, duration.seconds)
        }
    )
}
