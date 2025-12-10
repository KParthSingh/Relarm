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
                delay(1000)
                if (!isActive) break
                
                // Update notification every second so timer shows live countdown
                updateNotification()
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
        
        // Update ChainManager with current remaining time (single source of truth)
        ChainManager(this).setCurrentRemainingTime(remaining * 1000L)
        
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
                startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("ChainService", "Error starting foreground", e)
        }
    }
    
    private fun triggerAlarm() {
        Log.d("ChainService", "triggerAlarm() - Exiting foreground mode")
        
        // FIRST: Cancel the countdown job to stop any further notification updates
        countdownJob?.cancel()
        countdownJob = null
        
        // SECOND: Exit foreground mode and remove the notification
        // This is KEY - while ChainService is foreground, Android keeps the notification alive
        // We exit foreground but keep the service running in background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        // Set alarm ringing state
        ChainManager(this).setAlarmRinging(true)
        
        // Get current alarm's sound URI
        val repository = AlarmRepository(this)
        val alarms = repository.loadAlarms()
        val soundUri = if (currentIndex < alarms.size) {
            alarms[currentIndex].soundUri
        } else null
        
        // THIRD: Start alarm sound service (which will create its own ALARM notification)
        val serviceIntent = Intent(this, AlarmService::class.java).apply {
            putExtra("soundUri", soundUri)
        }
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
        
        // Cancel only our unified notification
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
        const val ACTION_NEXT_ALARM = "com.medicinereminder.app.NEXT_ALARM"
        const val ACTION_PREV_ALARM = "com.medicinereminder.app.PREV_ALARM"
        
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_TOTAL_ALARMS = "total_alarms"
        const val EXTRA_ALARM_NAME = "alarm_name"
        const val EXTRA_IS_CHAIN = "is_chain"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ChainService", "============================================")
        Log.d("ChainService", "onStartCommand received! Action: ${intent?.action}")
        Log.d("ChainService", "Current state - Index: $currentIndex, EndTime: $endTime")
        Log.d("ChainService", "ChainManager state - Active: ${ChainManager(this).isChainActive()}, Paused: ${ChainManager(this).isChainPaused()}")
        
        when (intent?.action) {
            ACTION_START_CHAIN_ALARM -> {
                Log.d("ChainService", ">>> ACTION_START_CHAIN_ALARM received")
                endTime = intent.getLongExtra(EXTRA_END_TIME, 0)
                currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
                totalAlarms = intent.getIntExtra(EXTRA_TOTAL_ALARMS, 0)
                currentAlarmName = intent.getStringExtra(EXTRA_ALARM_NAME) ?: ""
                Log.d("ChainService", "Starting countdown for alarm $currentIndex/$totalAlarms, ends at $endTime")
                startCountdown()
            }
            ACTION_STOP_CHAIN -> {
                Log.d("ChainService", ">>> ACTION_STOP_CHAIN received")
                Log.d("ChainService", "Before stop - ChainActive: ${ChainManager(this).isChainActive()}")
                
                // CRITICAL: Cancel the scheduled AlarmManager alarm!
                val alarmScheduler = AlarmScheduler(this)
                val currentIdx = ChainManager(this).getCurrentIndex()
                val requestCode = currentIdx + 1
                Log.d("ChainService", "Canceling AlarmManager alarm with requestCode: $requestCode")
                alarmScheduler.cancelAlarm(requestCode)
                
                ChainManager(this).stopChain()
                
                // Clear all active alarms in the repository
                val repository = AlarmRepository(this)
                val alarms = repository.loadAlarms()
                val clearedAlarms = alarms.map { it.copy(isActive = false, scheduledTime = 0L) }
                repository.saveAlarms(clearedAlarms)
                Log.d("ChainService", "All alarms cleared")
                
                Log.d("ChainService", "After stop - ChainActive: ${ChainManager(this).isChainActive()}")
                
                // Also ensure any ringing alarm service is stopped directly
                val stopAlarmIntent = Intent(this, AlarmService::class.java)
                stopService(stopAlarmIntent)
                Log.d("ChainService", "AlarmService stop requested")
                
                // Dismiss both notification IDs for safety
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
                notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
                Log.d("ChainService", "Notifications canceled")
                
                Log.d("ChainService", "Calling stopSelf()...")
                stopSelf()
            }
            ACTION_PAUSE_CHAIN -> {
                Log.d("ChainService", ">>> ACTION_PAUSE_CHAIN received")
                handlePause()
            }
            ACTION_RESUME_CHAIN -> {
                Log.d("ChainService", ">>> ACTION_RESUME_CHAIN received")
                handleResume()
            }
            ACTION_NEXT_ALARM -> {
                Log.d("ChainService", ">>> ACTION_NEXT_ALARM received")
                handleNextAlarm()
            }
            ACTION_PREV_ALARM -> {
                Log.d("ChainService", ">>> ACTION_PREV_ALARM received")
                handlePrevAlarm()
            }
            else -> {
                Log.w("ChainService", "Unknown action received: ${intent?.action}")
            }
        }
        Log.d("ChainService", "============================================")
        return START_NOT_STICKY
    }

    private fun handlePause() {
        Log.d("ChainService", "handlePause() called")
        Log.d("ChainService", "Canceling countdown job...")
        countdownJob?.cancel()
        
        // CRITICAL: Cancel the scheduled AlarmManager alarm!
        val alarmScheduler = AlarmScheduler(this)
        val requestCode = currentIndex + 1
        Log.d("ChainService", "Canceling AlarmManager alarm with requestCode: $requestCode")
        alarmScheduler.cancelAlarm(requestCode)
        
        val remaining = ((endTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        Log.d("ChainService", "Remaining time: ${remaining}s (${remaining * 1000}ms)")
        
        Log.d("ChainService", "Before pause - isPaused: ${ChainManager(this).isChainPaused()}")
        ChainManager(this).pauseChain(remaining * 1000)
        Log.d("ChainService", "After pause - isPaused: ${ChainManager(this).isChainPaused()}")
        Log.d("ChainService", "Saved remaining time: ${ChainManager(this).getPausedRemainingTime()}ms")
        
        showPausedNotification()
        Log.d("ChainService", "Pause complete")
    }

    private fun handleResume() {
        Log.d("ChainService", "handleResume() called")
        val remainingTimeMs = ChainManager(this).getPausedRemainingTime()
        Log.d("ChainService", "Retrieved paused remaining time: ${remainingTimeMs}ms")
        
        // CRITICAL: Reschedule the AlarmManager alarm with remaining time!
        val alarmScheduler = AlarmScheduler(this)
        val requestCode = currentIndex + 1
        Log.d("ChainService", "Rescheduling AlarmManager alarm with requestCode: $requestCode, delay: ${remainingTimeMs}ms")
        alarmScheduler.scheduleAlarm(remainingTimeMs, requestCode)
        
        // Reset end time relative to now
        endTime = System.currentTimeMillis() + remainingTimeMs
        Log.d("ChainService", "New endTime calculated: $endTime")
        
        Log.d("ChainService", "Before resume - isPaused: ${ChainManager(this).isChainPaused()}")
        ChainManager(this).resumeChain()
        Log.d("ChainService", "After resume - isPaused: ${ChainManager(this).isChainPaused()}")
        
        Log.d("ChainService", "Restarting countdown...")
        startCountdown()
        Log.d("ChainService", "Resume complete")
    }

    private fun showPausedNotification() {
        val remainingTimeMs = ChainManager(this).getPausedRemainingTime()
        val remainingSeconds = (remainingTimeMs / 1000).toInt()
        
        // Update ChainManager with current remaining time (frozen while paused)
        ChainManager(this).setCurrentRemainingTime(remainingTimeMs)
        
        val notification = NotificationHelper.buildChainNotification(
            this,
            currentIndex + 1,
            totalAlarms,
            remainingSeconds,
            currentAlarmName,
            isPaused = true
        )
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NotificationHelper.CHAIN_NOTIFICATION_ID, notification)
    }
    
    private fun handleNextAlarm() {
        Log.d("ChainService", "handleNextAlarm() called")
        
        // Clear alarm ringing state
        ChainManager(this).setAlarmRinging(false)
        
        // Stop any currently ringing alarm
        val stopAlarmIntent = Intent(this, AlarmService::class.java)
        stopService(stopAlarmIntent)
        
        // Cancel current countdown
        countdownJob?.cancel()
        
        // Cancel scheduled alarm
        val alarmScheduler = AlarmScheduler(this)
        alarmScheduler.cancelAlarm(currentIndex + 1)
        
        // Get alarm repository
        val repository = AlarmRepository(this)
        val alarms = repository.loadAlarms()
        
        // Check if there's a next alarm
        val nextIndex = currentIndex + 1
        if (nextIndex < alarms.size) {
            Log.d("ChainService", "Moving to next alarm: index $nextIndex")
            
            // Update chain manager
            ChainManager(this).moveToNextAlarm()
            
            // Start the next alarm
            val nextAlarm = alarms[nextIndex]
            val delayMillis = nextAlarm.getTotalSeconds() * 1000L
            val newEndTime = System.currentTimeMillis() + delayMillis
            
            // Schedule with AlarmManager
            alarmScheduler.scheduleAlarm(delayMillis, nextIndex + 1)
            
            // Update service state
            currentIndex = nextIndex
            totalAlarms = alarms.size
            currentAlarmName = nextAlarm.name
            endTime = newEndTime
            
            // Update repository
            val updatedAlarms = alarms.toMutableList()
            updatedAlarms[nextIndex] = nextAlarm.copy(isActive = true, scheduledTime = newEndTime)
            repository.saveAlarms(updatedAlarms)
            
            // Start countdown for next alarm
            startCountdown()
            
            Log.d("ChainService", "Next alarm started successfully")
        } else {
            Log.d("ChainService", "No more alarms, stopping chain")
            // No more alarms, stop the chain
            val stopIntent = Intent(this, ChainService::class.java).apply {
                action = ACTION_STOP_CHAIN
            }
            startService(stopIntent)
        }
    }
    
    private fun handlePrevAlarm() {
        Log.d("ChainService", "handlePrevAlarm() called")
        
        // Clear alarm ringing state
        ChainManager(this).setAlarmRinging(false)
        
        // Stop any currently ringing alarm
        val stopAlarmIntent = Intent(this, AlarmService::class.java)
        stopService(stopAlarmIntent)
        
        // Cancel current countdown
        countdownJob?.cancel()
        
        // Cancel scheduled alarm
        val alarmScheduler = AlarmScheduler(this)
        alarmScheduler.cancelAlarm(currentIndex + 1)
        
        // Get alarm repository
        val repository = AlarmRepository(this)
        val alarms = repository.loadAlarms()
        
        // Check if there's a previous alarm
        val prevIndex = currentIndex - 1
        if (prevIndex >= 0) {
            Log.d("ChainService", "Moving to previous alarm: index $prevIndex")
            
            // Update chain manager to previous index
            val chainManager = ChainManager(this)
            chainManager.setCurrentIndex(prevIndex)
            
            // Start the previous alarm
            val prevAlarm = alarms[prevIndex]
            val delayMillis = prevAlarm.getTotalSeconds() * 1000L
            val newEndTime = System.currentTimeMillis() + delayMillis
            
            // Schedule with AlarmManager
            alarmScheduler.scheduleAlarm(delayMillis, prevIndex + 1)
            
            // Update service state
            currentIndex = prevIndex
            totalAlarms = alarms.size
            currentAlarmName = prevAlarm.name
            endTime = newEndTime
            
            // Update repository
            val updatedAlarms = alarms.toMutableList()
            updatedAlarms[prevIndex] = prevAlarm.copy(isActive = true, scheduledTime = newEndTime)
            repository.saveAlarms(updatedAlarms)
            
            // Start countdown for previous alarm
            startCountdown()
            
            Log.d("ChainService", "Previous alarm started successfully")
        } else {
            Log.d("ChainService", "Already at first alarm, cannot go back")
        }
    }
}
