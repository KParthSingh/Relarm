package com.medicinereminder.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleDurationPicker(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onTimeChange: (Int, Int, Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hours
        SimpleWheel(
            value = hours,
            range = 0..23,
            label = "Hr",
            onValueChange = { onTimeChange(it, minutes, seconds) }
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Spacer(modifier = Modifier.width(16.dp))

        // Minutes
        SimpleWheel(
            value = minutes,
            range = 0..59,
            label = "Min",
            onValueChange = { onTimeChange(hours, it, seconds) }
        )

        Spacer(modifier = Modifier.width(16.dp))
        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Spacer(modifier = Modifier.width(16.dp))

        // Seconds
        SimpleWheel(
            value = seconds,
            range = 0..59,
            label = "Sec",
            onValueChange = { onTimeChange(hours, minutes, it) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleWheel(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = value)
    val itemHeight = 40.dp
    val visibleItems = 3 // Show 1 above, 1 selected, 1 below
    
    // Notify change when scrolling stops/snaps
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                onValueChange(index)
            }
    }
    
    // Sync external value changes (e.g. from presets) back to list position
    LaunchedEffect(value) {
        if (listState.firstVisibleItemIndex != value) {
            listState.scrollToItem(value)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Label above the wheel
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .width(60.dp)
                .height(itemHeight * visibleItems)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            // Highlight for center item
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            )

            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = itemHeight) // Pad so first/last items can be centered
            ) {
                // Add blank items at start/end so 0 and MAX can be centered
                // Wait, snapping behavior handles padding differently.
                // The easier way for a "finite" list is just padding.
                
                items(range.last + 1) { index ->
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%02d", index),
                            fontSize = 20.sp,
                            fontWeight = if (index == value) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == value) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
