package com.medicinereminder.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
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
        const val EXTRA_ALARM_HOURS = "extra_alarm_hours"
        const val EXTRA_ALARM_MINUTES = "extra_alarm_minutes"
        const val EXTRA_ALARM_SECONDS = "extra_alarm_seconds"
        const val ACTION_CLOSE = "com.medicinereminder.app.CLOSE_ALARM_ACTIVITY"
    }
    
    // Listener to detect when alarm is dismissed externally (via notification)
    private var chainPrefs: android.content.SharedPreferences? = null
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "is_alarm_ringing") {
            val chainManager = ChainManager(this)
            if (!chainManager.isAlarmRinging()) {
                // Alarm was dismissed via notification, close this activity
                finish()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure app content does NOT draw under system navigation bar
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        // Register SharedPreferences listener to detect external dismiss
        chainPrefs = getSharedPreferences("chain_prefs", Context.MODE_PRIVATE)
        chainPrefs?.registerOnSharedPreferenceChangeListener(prefsListener)
        
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
        val alarmHours = intent.getIntExtra(EXTRA_ALARM_HOURS, 0)
        val alarmMinutes = intent.getIntExtra(EXTRA_ALARM_MINUTES, 0)
        val alarmSeconds = intent.getIntExtra(EXTRA_ALARM_SECONDS, 5)
        
        val settingsRepository = SettingsRepository(this)
        val themeMode = settingsRepository.getThemeMode()

        setContent {
            MedicineReminderTheme(themeMode = themeMode) {
                AlarmRingingScreen(
                    alarmName = alarmName,
                    alarmHours = alarmHours,
                    alarmMinutes = alarmMinutes,
                    alarmSeconds = alarmSeconds,
                    onDismiss = {
                        dismissAlarm()
                    },
                    onDismissAndOpenApp = {
                        dismissAlarmAndOpenApp()
                    }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        chainPrefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun dismissAlarm() {
        val chainManager = ChainManager(this)
        
        // Check if the alarm was already dismissed (e.g., via notification)
        // If so, just close the activity without triggering the next alarm logic
        if (!chainManager.isAlarmRinging()) {
            finish()
            return
        }
        
        // Clear alarm ringing state
        chainManager.setAlarmRinging(false)
        
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

    private fun dismissAlarmAndOpenApp() {
        val chainManager = ChainManager(this)
        
        // Check if the alarm was already dismissed (e.g., via notification)
        // If so, just open the app and close this activity without triggering next alarm logic
        if (!chainManager.isAlarmRinging()) {
            // Launch the main activity
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(mainIntent)
            finish()
            return
        }
        
        // Clear alarm ringing state
        chainManager.setAlarmRinging(false)
        
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
        
        // Launch the main activity
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        
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
    alarmHours: Int,
    alarmMinutes: Int,
    alarmSeconds: Int,
    onDismiss: () -> Unit,
    onDismissAndOpenApp: () -> Unit
) {
    val view = LocalView.current
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = -300f
    
    // Format the alarm duration like in the alarm card
    val formattedDuration = remember(alarmHours, alarmMinutes, alarmSeconds) {
        when {
            alarmHours > 0 -> {
                String.format("%d:%02d:%02d", alarmHours, alarmMinutes, alarmSeconds)
            }
            alarmMinutes > 0 -> {
                String.format("%d:%02d", alarmMinutes, alarmSeconds)
            }
            else -> {
                "$alarmSeconds Sec"
            }
        }
    }
    
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
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = swipeOffset }
                .padding(horizontal = 32.dp)
                .systemBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Top section with custom alarm name (if set) and time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Only show alarm name if it's custom (not default)
                if (alarmName.isNotBlank() && alarmName != "Alarm") {
                    Text(
                        text = alarmName,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // If no custom name, just show time large
                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Center section with timer circle - takes remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Timer circle in ringing/expired state (red blinking)
                    Box(
                        modifier = Modifier.size(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.medicinereminder.app.ui.TimerCircleView(
                            scheduledTime = 0L,
                            totalDuration = 1000L,
                            isExpired = true,  // This makes it red and blinking
                            isPaused = false,
                            pausedRemainingMs = 0L,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Time display in the center (like alarm card UI)
                        Text(
                            text = formattedDuration,
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Show "Alarm Ringing!" title only if no custom name
                    if (alarmName.isBlank() || alarmName == "Alarm") {
                        Text(
                            text = "Alarm Ringing!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Bottom section with dismiss button and swipe hint
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Dismiss Button
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Dismiss & Open App Button
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onDismissAndOpenApp()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Text(
                        text = "DISMISS & OPEN APP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
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
