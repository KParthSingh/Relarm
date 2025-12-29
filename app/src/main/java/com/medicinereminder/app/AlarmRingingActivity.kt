package com.medicinereminder.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medicinereminder.app.ui.theme.MedicineReminderTheme
import java.text.SimpleDateFormat
import java.util.*

class AlarmRingingActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_ALARM_NAME = "extra_alarm_name"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set window flags to show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        
        val alarmName = intent.getStringExtra(EXTRA_ALARM_NAME) ?: "Alarm"
        
        setContent {
            MedicineReminderTheme {
                AlarmRingingScreen(
                    alarmName = alarmName,
                    onDismiss = {
                        dismissAlarm()
                    }
                )
            }
        }
    }

    private fun dismissAlarm() {
        // Dismiss both notification IDs for safety (we now use CHAIN_NOTIFICATION_ID)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
        notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
        
        val serviceIntent = Intent(this, AlarmService::class.java)
        stopService(serviceIntent)
        
        // DELEGATE NEXT STEP TO CHAIN SERVICE
        // ChainService will check isChainSequence() and stop if false.
        val nextIntent = Intent(this, ChainService::class.java).apply {
            action = ChainService.ACTION_NEXT_ALARM
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(nextIntent)
        } else {
            startService(nextIntent)
        }
        
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent back button from dismissing alarm
    }
}

@Composable
fun AlarmRingingScreen(
    alarmName: String,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = -300f
    
    // Pulsing animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    // Get current time
    val currentTime = remember {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date())
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (swipeOffset < swipeThreshold) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onDismiss()
                        } else {
                            swipeOffset = 0f
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        swipeOffset = (swipeOffset + dragAmount).coerceAtMost(0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = swipeOffset }
                .padding(32.dp)
        ) {
            // Top section with time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Center section with icon and alarm info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing icon
                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(scale),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Alarm title
                Text(
                    text = "Alarm Ringing!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Alarm name
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = alarmName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
            
            // Bottom section with dismiss button and swipe hint
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                // Dismiss Button
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Text(
                        text = "DISMISS",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Swipe hint
                Text(
                    text = "Swipe up to dismiss",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
