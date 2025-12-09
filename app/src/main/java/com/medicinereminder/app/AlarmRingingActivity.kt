package com.medicinereminder.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
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

        setContent {
            AlarmRingingScreen(
                onDismiss = {
                    dismissAlarm()
                }
            )
        }
    }

    private fun dismissAlarm() {
        // Dismiss notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
        
        // Stop the alarm service
        val serviceIntent = Intent(this, AlarmService::class.java)
        stopService(serviceIntent)
        
        // Stop countdown service from previous alarm
        val stopCountdownIntent = Intent(this, CountdownService::class.java)
        stopService(stopCountdownIntent)
        
        // Check if we're in a chain and need to start the next alarm
        val chainManager = ChainManager(this)
        if (chainManager.isChainActive()) {
            val alarmRepository = AlarmRepository(this)
            val alarms = alarmRepository.loadAlarms()
            
            // Move to next alarm
            chainManager.moveToNextAlarm()
            val nextIndex = chainManager.getCurrentIndex()
            
            if (nextIndex < alarms.size) {
                // Start the next alarm in the chain
                val nextAlarm = alarms[nextIndex]
                val delayMillis = nextAlarm.getTotalSeconds() * 1000L
                
                val alarmScheduler = AlarmScheduler(this)
                alarmScheduler.scheduleAlarm(delayMillis, nextIndex + 1)
                
                // Start countdown service for next alarm (after stopping the old one)
                val countdownIntent = Intent(this, CountdownService::class.java).apply {
                    putExtra("triggerTime", System.currentTimeMillis() + delayMillis)
                }
                startForegroundService(countdownIntent)
                
                // Update alarm state in repository
                val updatedAlarms = alarms.toMutableList()
                updatedAlarms[nextIndex] = nextAlarm.copy(
                    isActive = true,
                    scheduledTime = System.currentTimeMillis() + delayMillis
                )
                alarmRepository.saveAlarms(updatedAlarms)
            } else {
                // Chain complete
                chainManager.stopChain()
            }
        }
        
        // Close this activity
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing alarm
        // User must click the dismiss button
    }
}

@Composable
fun AlarmRingingScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF6B6B)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Alarm icon
            Text(
                text = "⏰",
                fontSize = 120.sp
            )

            // Title
            Text(
                text = "Alarm!",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Message
            Text(
                text = "Time to take your medicine",
                fontSize = 20.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935)
                )
            ) {
                Text(
                    text = "DISMISS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
