package com.medicinereminder.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.medicinereminder.app.ui.theme.MedicineReminderTheme

class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... (window flags code)
        
        setContent {
            MedicineReminderTheme {
                AlarmRingingScreen(
                    onDismiss = {
                        dismissAlarm()
                    }
                )
            }
        }
    }

    private fun dismissAlarm() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
        
        val serviceIntent = Intent(this, AlarmService::class.java)
        stopService(serviceIntent)
        
        // Check if we're in a chain and need to start the next alarm
        val chainManager = ChainManager(this)
        if (chainManager.isChainActive()) {
            val alarmRepository = AlarmRepository(this)
            val alarms = alarmRepository.loadAlarms()
            
            chainManager.moveToNextAlarm()
            val nextIndex = chainManager.getCurrentIndex()
            
            if (nextIndex < alarms.size) {
                val nextAlarm = alarms[nextIndex]
                val delayMillis = nextAlarm.getTotalSeconds() * 1000L
                val endTime = System.currentTimeMillis() + delayMillis
                
                // Start ChainService for next alarm
                val chainIntent = Intent(this, ChainService::class.java).apply {
                    action = ChainService.ACTION_START_CHAIN_ALARM
                    putExtra(ChainService.EXTRA_END_TIME, endTime)
                    putExtra(ChainService.EXTRA_CURRENT_INDEX, nextIndex)
                    putExtra(ChainService.EXTRA_TOTAL_ALARMS, alarms.size)
                    putExtra(ChainService.EXTRA_ALARM_NAME, nextAlarm.name)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(chainIntent)
                } else {
                    startService(chainIntent)
                }
                
                val updatedAlarms = alarms.toMutableList()
                updatedAlarms[nextIndex] = nextAlarm.copy(
                    isActive = true,
                    scheduledTime = endTime
                )
                alarmRepository.saveAlarms(updatedAlarms)
            } else {
                // Chain finished
                chainManager.stopChain()
                val stopChainIntent = Intent(this, ChainService::class.java).apply {
                    action = ChainService.ACTION_STOP_CHAIN
                }
                startService(stopChainIntent)
            }
        } else {
            // Not a chain, ensure ChainService is stopped just in case
             val stopChainIntent = Intent(this, ChainService::class.java).apply {
                action = ChainService.ACTION_STOP_CHAIN
            }
            startService(stopChainIntent)
        }
        
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing alarm
    }
}

@Composable
fun AlarmRingingScreen(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6750A4), // Deep Purple
                        Color(0xFF21005D)  // Darker Purple
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
               Icon(
                   Icons.Default.Notifications,
                   contentDescription = null,
                   tint = Color.White,
                   modifier = Modifier.size(64.dp)
               )
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // Text
            Text(
                text = stringResource(R.string.alarm_ringing),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.alarm_ringing_subtitle),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))
            
            // Dismiss Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RoundedCornerShape(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEADDFF),
                    contentColor = Color(0xFF21005D)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.dismiss_alarm).uppercase(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
