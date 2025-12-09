package com.medicinereminder.app

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class ChainService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var countdownJob: Job? = null
    private var endTime: Long = 0
    private var currentIndex: Int = 0
    private var totalAlarms: Int = 0
    private var currentAlarmName: String = ""

    override fun onCreate() {
        super.onCreate()
        Log.d("ChainService", "Chain service created")
        NotificationHelper.createNotificationChannel(this)
    }



    private fun startCountdown() {
        // Start as foreground service with initial notification
        updateNotification()

        // Start countdown updates
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            while (isActive && System.currentTimeMillis() < endTime) {
                delay(1000) // Keep the loop to trigger alarm, but don't spam notifications
                if (!isActive) break
                
                // updateNotification() - Removed as per user request to simplify/forget notification
            }
            
            if (isActive) {
                // Countdown finished, trigger the alarm
                Log.d("ChainService", "Countdown finished, triggering alarm")
                triggerAlarm()
            }
        }
    }

    private fun updateNotification() {
        val remaining = ((endTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        
        val notification = NotificationHelper.buildChainNotification(
            this,
            currentIndex + 1,
            totalAlarms,
            remaining,
            currentAlarmName
        )
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationHelper.CHAIN_NOTIFICATION_ID, notification)
        
        // Ensure we are in foreground
        try {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("ChainService", "Error starting foreground", e)
        }
    }
    
    private fun triggerAlarm() {
        // Start the alarm ringing activity
        val intent = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Pass chain info so activity knows we are in a chain
            putExtra(EXTRA_IS_CHAIN, true)
        }
        startActivity(intent)
        
        // Start alarm sound service
        val serviceIntent = Intent(this, AlarmService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        serviceScope.cancel()
        
        // We do NOT cancel the notification here automatically if we want it to persist,
        // but since this service is destroyed when chain stops, we should clean up.
        // However, AlarmService will take over the notification slot or show its own.
        // For now, let's clear our specific ID.
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
        
        Log.d("ChainService", "Chain service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_CHAIN_ALARM = "com.medicinereminder.app.START_CHAIN_ALARM"
        const val ACTION_STOP_CHAIN = "com.medicinereminder.app.STOP_CHAIN"
        const val ACTION_PAUSE_CHAIN = "com.medicinereminder.app.PAUSE_CHAIN"
        const val ACTION_RESUME_CHAIN = "com.medicinereminder.app.RESUME_CHAIN"
        
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_TOTAL_ALARMS = "total_alarms"
        const val EXTRA_ALARM_NAME = "alarm_name"
        const val EXTRA_IS_CHAIN = "is_chain"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CHAIN_ALARM -> {
                endTime = intent.getLongExtra(EXTRA_END_TIME, 0)
                currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
                totalAlarms = intent.getIntExtra(EXTRA_TOTAL_ALARMS, 0)
                currentAlarmName = intent.getStringExtra(EXTRA_ALARM_NAME) ?: ""
                startCountdown()
            }
            ACTION_STOP_CHAIN -> {
                stopSelf()
            }
            ACTION_PAUSE_CHAIN -> handlePause()
            ACTION_RESUME_CHAIN -> handleResume()
        }
        return START_NOT_STICKY
    }

    private fun handlePause() {
        countdownJob?.cancel()
        val remaining = ((endTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        ChainManager(this).pauseChain(remaining * 1000)
        showPausedNotification()
    }

    private fun handleResume() {
        val remainingTimeStr = ChainManager(this).getPausedRemainingTime()
        // Reset end time relative to now
        endTime = System.currentTimeMillis() + remainingTimeStr
        ChainManager(this).resumeChain()
        startCountdown()
    }

    private fun showPausedNotification() {
         val notification = NotificationHelper.buildChainNotification(
            this,
            currentIndex + 1,
            totalAlarms,
            0, // Time irrelevant when paused, or could show saved remaining
            currentAlarmName,
            isPaused = true
        )
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationHelper.CHAIN_NOTIFICATION_ID, notification)
    }
}
