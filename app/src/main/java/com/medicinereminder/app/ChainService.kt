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
    // REMOVED: countdownJob - no more polling loop!
    private var endTime: Long = 0
    private var currentIndex: Int = 0
    private var totalAlarms: Int = 0
    private var currentAlarmName: String = ""
    private var notificationDismissed: Boolean = false
    private var isDismissableMode: Boolean = false
    
    // Synchronization lock to prevent double alarm triggers
    @Volatile
    private var isAlarmTriggering: Boolean = false

    override fun onCreate() {
        super.onCreate()
        DebugLogger.info("ChainService", "Service onCreate() called")
        NotificationHelper.createNotificationChannel(this)
    }





    private fun startCountdown(resetDismissalState: Boolean = true) {
        // Check if we're in dismissable mode
        val settingsRepository = SettingsRepository(this)
        isDismissableMode = settingsRepository.getDismissableCounter()
        
        // Only reset dismissal state if requested (true for new sequences, false for resume)
        if (resetDismissalState) {
            notificationDismissed = false
        }
        
        // CRITICAL FIX: Clear alarm ringing state when starting new countdown
        // This prevents stale "isAlarmRinging=true" from previous alarm persisting
        val chainManager = ChainManager(this)
        if (chainManager.isAlarmRinging()) {
            DebugLogger.warn("ChainService", "BUG FIX: Clearing stale isAlarmRinging=true at countdown start")
            chainManager.setAlarmRinging(false)
        }
        
        // Store endTime in ChainManager for recalculation after backgrounding
        chainManager.setEndTime(endTime)
        
        // CRITICAL FIX: Explicitly update ChainManager state!
        // The UI reads from here, so we MUST ensure these are set correctly.
        chainManager.setChainActive(true)
        chainManager.setCurrentIndex(currentIndex)
        
        // CRITICAL FIX: Save service state for recovery if app is killed
        chainManager.saveServiceState(currentIndex, endTime, totalAlarms, currentAlarmName)
        
        // CRITICAL FIX #1: Schedule the alarm with AlarmManager!
        // This was missing - alarms were never scheduled!
        val remainingMs = endTime - System.currentTimeMillis()
        if (remainingMs > 0) {
            val alarmScheduler = AlarmScheduler(this)
            val requestCode = currentIndex + 1
            alarmScheduler.scheduleAlarm(remainingMs, requestCode)
            DebugLogger.info("ChainService", "✓ Scheduled alarm: requestCode=$requestCode, delay=${remainingMs}ms (${remainingMs/1000}s)")
        } else {
            DebugLogger.warn("ChainService", "⚠ Cannot schedule alarm - endTime is in the past (remaining: ${remainingMs}ms)")
        }
        
        DebugLogger.logState("ChainService", mapOf(
            "action" to "startCountdown",
            "endTime" to endTime,
            "currentIndex" to currentIndex,
            "totalAlarms" to totalAlarms,
            "alarmName" to currentAlarmName,
            "isDismissableMode" to isDismissableMode,
            "notificationDismissed" to notificationDismissed
        ))
        DebugLogger.info("BatteryOpt", "Timer started - endTime stored: $endTime, hideCounter: $isDismissableMode")
        
        // Start as foreground service with initial notification
        // No loop needed! System Chronometer handles the UI.
        ChainManager(this).setCurrentRemainingTime(((endTime - System.currentTimeMillis()) / 1000).toInt() * 1000L) // Initial sync
        
        // CRITICAL FIX #3: Always call startForeground() to satisfy Android requirement
        // Then optionally remove if in dismissable mode
        showNotification()
        
        // The Service now just sits IDLE (or minimal memory) waiting for AlarmManager to trigger 'ACTION_TRIGGER_ALARM'
        // No wakelocks, no CPU usage.
    }
    
    // Single source of truth for remaining time
    private fun getRemainingTime(): Int {
        return ((endTime - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
    }
    
    // DEPRECATED: UI should calculate own time. Kept only for initial sync if needed.
    private fun updateChainManager() {
        ChainManager(this).setCurrentRemainingTime(getRemainingTime() * 1000L)
    }
    
    // Check if we should show notification (respects dismissal)
    private fun shouldShowNotification(): Boolean {
        if (!isDismissableMode) {
            return true // Always show in non-dismissable mode
        }
        
        // In dismissable mode, check if user dismissed it
        if (notificationDismissed) {
            return false // Don't show if dismissed
        }
        
        // Check if notification is still active
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val activeNotifications = notificationManager.activeNotifications
        val isNotificationActive = activeNotifications.any { it.id == NotificationHelper.CHAIN_NOTIFICATION_ID }
        
        if (!isNotificationActive) {
            // User dismissed it
            notificationDismissed = true
            return false
        }
        
        return true // Show notification
    }
    
    // Updated showNotification to use isChainSequence
    private fun showNotification(isPaused: Boolean = false) {
        val isChain = ChainManager(this).isChainSequence()
        val notification = NotificationHelper.buildChainNotification(
            this,
            currentIndex + 1,
            totalAlarms,
            endTime, // Pass endTime for Chronometer
            currentAlarmName,
            isPaused,
            isChain, // PASS FLAG
            endTime // Pass endTime as trigger time for nameless alarms
        )
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        // IMPORTANT: Always call startForeground() first to satisfy Android requirement
        // when service is started with startForegroundService()
        try {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            DebugLogger.error("ChainService", "Error starting foreground", e)
        }
        
        // If in dismissable mode, immediately stop foreground to hide notification
        if (isDismissableMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            DebugLogger.info("BatteryOpt", "Notification hidden (dismissable mode ON)")
        }
    }
    
    private fun triggerAlarm() {
        // CRITICAL: Prevent double triggering (AlarmManager + countdown loop could both call this)
        synchronized(this) {
            if (isAlarmTriggering) {
                DebugLogger.warn("ChainService", "triggerAlarm() already in progress - skipping duplicate call")
                return
            }
            isAlarmTriggering = true
        }
        
        DebugLogger.info("ChainService", "triggerAlarm() - Exiting foreground mode")
        
        // FIRST: No loop to cancel anymore
        // countdownJob?.cancel()
        
        // SECOND: Exit foreground mode and remove the notification
        // This is KEY - while ChainService is foreground, Android keeps the notification alive
        // We exit foreground but keep the service running in background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        // THIRD: Check if we're in a chain
        val chainManager = ChainManager(this)
        if (!chainManager.isChainActive()) {
            DebugLogger.warn("ChainService", "Chain not active in triggerAlarm, stopping service")
            // Reset flag before returning
            isAlarmTriggering = false
            return
        }
        
        // Set alarm ringing state
        DebugLogger.warn("ChainService", "triggerAlarm() - Setting alarm ringing to TRUE")
        chainManager.setAlarmRinging(true)
        
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
        
        // Reset the triggering flag now that alarm has been started
        // Next alarm in sequence can trigger after this one is dismissed
        isAlarmTriggering = false
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.warn("ChainService", "Service onDestroy() called")
        DebugLogger.logState("ChainService", mapOf(
            "chainActive" to ChainManager(this).isChainActive(),
            "currentIndex" to currentIndex,
            "isAlarmRinging" to ChainManager(this).isAlarmRinging()
        ))
        // countdownJob?.cancel()
        serviceScope.cancel()
        
        // Cancel only our unified notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
        
        DebugLogger.info("ChainService", "Chain service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_CHAIN_ALARM = "com.medicinereminder.app.START_CHAIN_ALARM"
        const val ACTION_STOP_CHAIN = "com.medicinereminder.app.STOP_CHAIN"
        const val ACTION_PAUSE_CHAIN = "com.medicinereminder.app.PAUSE_CHAIN"
        const val ACTION_RESUME_CHAIN = "com.medicinereminder.app.RESUME_CHAIN"
        const val ACTION_NEXT_ALARM = "com.medicinereminder.app.NEXT_ALARM"
        const val ACTION_PREV_ALARM = "com.medicinereminder.app.PREV_ALARM"
        const val ACTION_JUMP_TO_ALARM = "com.medicinereminder.app.JUMP_TO_ALARM"
        const val ACTION_PAUSE_COUNTDOWN_LOOP = "com.medicinereminder.app.PAUSE_COUNTDOWN_LOOP"
        const val ACTION_RESUME_COUNTDOWN_LOOP = "com.medicinereminder.app.RESUME_COUNTDOWN_LOOP"
        const val ACTION_TRIGGER_ALARM = "com.medicinereminder.app.TRIGGER_ALARM"
        
        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_TOTAL_ALARMS = "total_alarms"
        const val EXTRA_ALARM_NAME = "alarm_name"
        const val EXTRA_IS_CHAIN = "is_chain"
        const val EXTRA_TARGET_INDEX = "target_index"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.info("ChainService", "========== onStartCommand: ${intent?.action} ==========")
        DebugLogger.logState("ChainService", mapOf(
            "action" to (intent?.action ?: "null"),
            "currentIndex" to currentIndex,
            "endTime" to endTime,
            "chainActive" to ChainManager(this).isChainActive(),
            "chainPaused" to ChainManager(this).isChainPaused(),
            "isAlarmRinging" to ChainManager(this).isAlarmRinging()
        ))
        DebugLogger.info("ChainService", "============================================")
        DebugLogger.info("ChainService", "onStartCommand received! Action: ${intent?.action}")
        DebugLogger.info("ChainService", "Current state - Index: $currentIndex, EndTime: $endTime")
        DebugLogger.info("ChainService", "ChainManager state - Active: ${ChainManager(this).isChainActive()}, Paused: ${ChainManager(this).isChainPaused()}")
        
        // CRITICAL FIX: Proactive State Restoration
        // If service is fresh (endTime=0) but ChainManager says we should be running, restore state immediately.
        // This ensures actions like ACTION_PAUSE_CHAIN work even if service was killed and recreated.
        val chainManager = ChainManager(this)
        if (endTime == 0L && chainManager.isChainActive()) {
             val savedEndTime = chainManager.getServiceEndTime()
             // Only restore if valid. Note: savedEndTime is absolute time.
             if (savedEndTime > 0) {
                 endTime = savedEndTime
                 currentIndex = chainManager.getServiceCurrentIndex()
                 totalAlarms = chainManager.getServiceTotalAlarms()
                 currentAlarmName = chainManager.getServiceAlarmName()
                 DebugLogger.warn("ChainService", "STATE RESTORED in onStartCommand (Pre-Action): index=$currentIndex, endTime=$endTime")
             }
        }

        when (intent?.action) {
            ACTION_START_CHAIN_ALARM -> {
                DebugLogger.info("ChainService", ">>> ACTION_START_CHAIN_ALARM received")
                endTime = intent.getLongExtra(EXTRA_END_TIME, 0)
                currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
                totalAlarms = intent.getIntExtra(EXTRA_TOTAL_ALARMS, 0)
                currentAlarmName = intent.getStringExtra(EXTRA_ALARM_NAME) ?: ""
                
                // CRITICAL: Read Chain Mode flag
                val isChain = intent.getBooleanExtra(EXTRA_IS_CHAIN, true)
                ChainManager(this).setChainSequence(isChain)
                
                DebugLogger.info("ChainService", "START_CHAIN_ALARM: index=$currentIndex, isChain=$isChain")
                DebugLogger.info("ChainService", "Starting countdown for alarm $currentIndex/$totalAlarms, ends at $endTime")
                startCountdown()
            }
            ACTION_STOP_CHAIN -> {
                 DebugLogger.warn("ChainService", "STOP_CHAIN requested")
                 DebugLogger.info("ChainService", ">>> ACTION_STOP_CHAIN received")
                 DebugLogger.info("ChainService", "Before stop - ChainActive: ${ChainManager(this).isChainActive()}")
                 
                 // CRITICAL FIX: Cancel ALL possible scheduled alarms defensively
                 val alarmScheduler = AlarmScheduler(this)
                 val repository = AlarmRepository(this)
                 val alarms = repository.loadAlarms()
                 
                 // Cancel alarm for current index
                 val currentIdx = ChainManager(this).getCurrentIndex()
                 val requestCode = currentIdx + 1
                 DebugLogger.info("ChainService", "Canceling current alarm with requestCode: $requestCode")
                 alarmScheduler.cancelAlarm(requestCode)
                 
                 // DEFENSIVE: Cancel all possible alarm codes (in case of desync)
                 for (i in 0 until alarms.size) {
                     alarmScheduler.cancelAlarm(i + 1)
                 }
                 DebugLogger.info("ChainService", "Canceled all ${alarms.size} possible alarm codes defensively")
                 
                 ChainManager(this).stopChain()
                 
                 // Clear all active alarms in the repository (reuse repository and alarms from above)
                 val clearedAlarms = alarms.map { it.copy(isActive = false, scheduledTime = 0L) }
                 repository.saveAlarms(clearedAlarms)
                 DebugLogger.info("ChainService", "All alarms cleared")
                 
                 DebugLogger.info("ChainService", "After stop - ChainActive: ${ChainManager(this).isChainActive()}")
                 
                 // Also ensure any ringing alarm service is stopped directly
                 val stopAlarmIntent = Intent(this, AlarmService::class.java)
                 stopService(stopAlarmIntent)
                 DebugLogger.info("ChainService", "AlarmService stop requested")
                 
                 // Dismiss both notification IDs for safety
                 val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                 notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
                 notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
                 DebugLogger.info("ChainService", "Notifications canceled")
                 
                 DebugLogger.info("ChainService", "Calling stopSelf()...")
                 stopSelf()
            }
            ACTION_PAUSE_CHAIN -> {
                DebugLogger.info("ChainService", ">>> ACTION_PAUSE_CHAIN received")
                handlePause()
            }
            ACTION_RESUME_CHAIN -> {
                DebugLogger.info("ChainService", ">>> ACTION_RESUME_CHAIN received")
                handleResume()
            }
            ACTION_NEXT_ALARM -> {
                DebugLogger.info("ChainService", ">>> ACTION_NEXT_ALARM received")
                
                // CRITICAL: Must call startForeground() when service started with startForegroundService()
                // This is required by Android when AlarmStopReceiver starts ChainService
                val tempNotification = NotificationHelper.buildChainNotification(
                    this,
                    currentIndex + 1,
                    totalAlarms,
                    System.currentTimeMillis(),
                    currentAlarmName
                )
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, tempNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, tempNotification)
                    }
                } catch (e: Exception) {
                    DebugLogger.error("ChainService", "Error starting foreground in NEXT_ALARM", e)
                }
                
                handleNextAlarm()
            }
            ACTION_PREV_ALARM -> {
                DebugLogger.info("ChainService", ">>> ACTION_PREV_ALARM received")
                handlePrevAlarm()
            }
            ACTION_JUMP_TO_ALARM -> {
                val targetIndex = intent.getIntExtra(EXTRA_TARGET_INDEX, -1)
                DebugLogger.info("ChainService", ">>> ACTION_JUMP_TO_ALARM received, targetIndex=$targetIndex")
                if (targetIndex >= 0) {
                    handleJumpToAlarm(targetIndex)
                }
            }
            ACTION_PAUSE_COUNTDOWN_LOOP -> {
                DebugLogger.info("ChainService", ">>> ACTION_PAUSE_COUNTDOWN_LOOP received")
                handlePauseCountdownLoop()
            }
            ACTION_RESUME_COUNTDOWN_LOOP -> {
                DebugLogger.info("ChainService", ">>> ACTION_RESUME_COUNTDOWN_LOOP received")
                handleResumeCountdownLoop()
            }
            ACTION_TRIGGER_ALARM -> {
                DebugLogger.warn("ChainService", "========== TRIGGER_ALARM from AlarmManager ==========")
                DebugLogger.logState("ChainService", mapOf(
                    "currentIndex" to currentIndex,
                    "isAlarmRinging" to ChainManager(this).isAlarmRinging(),
                    "chainActive" to ChainManager(this).isChainActive()
                ))
                DebugLogger.info("BatteryOpt", "[TRIGGER] AlarmManager triggered alarm - Calling triggerAlarm()")
                
                // CRITICAL: Must call startForeground() when service started with startForegroundService()
                // Create temporary notification just to satisfy Android requirement
                val tempNotification = NotificationHelper.buildChainNotification(
                    this,
                    currentIndex + 1,
                    totalAlarms,
                    System.currentTimeMillis(), // Show 00:00 or expired time
                    currentAlarmName
                )
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, tempNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(NotificationHelper.CHAIN_NOTIFICATION_ID, tempNotification)
                    }
                } catch (e: Exception) {
                    DebugLogger.error("ChainService", "Error starting foreground in TRIGGER_ALARM", e)
                }
                
                // Now trigger the alarm (which will exit foreground and start AlarmService)
                triggerAlarm()
            }
            else -> {
                DebugLogger.warn("ChainService", "Unknown action received: ${intent?.action}")
                
                // CRITICAL FIX: If service restarted by Android with no action but chain is active,
                // restore state from SharedPreferences
                val chainManager = ChainManager(this)
                if (currentIndex == 0 && endTime == 0L && chainManager.isChainActive()) {
                    currentIndex = chainManager.getServiceCurrentIndex()
                    endTime = chainManager.getServiceEndTime()
                    totalAlarms = chainManager.getServiceTotalAlarms()
                    currentAlarmName = chainManager.getServiceAlarmName()
                    
                    DebugLogger.warn("ChainService", "STATE RESTORED from SharedPreferences (service restarted)")
                    DebugLogger.logState("ChainService", mapOf(
                        "restoredIndex" to currentIndex,
                        "restoredEndTime" to endTime,
                        "restoredTotalAlarms" to totalAlarms
                    ))
                    
                    // Restart countdown with restored state
                    if (endTime > System.currentTimeMillis()) {
                        startCountdown(resetDismissalState = false)
                    } else {
                        DebugLogger.warn("ChainService", "Restored endTime is in the past - alarm missed")
                    }
                }
            }
        }
        DebugLogger.info("ChainService", "============================================")
        return START_NOT_STICKY
    }

    private fun handlePause() {
        DebugLogger.info("ChainService", "========== PAUSE TIMER ==========")
        DebugLogger.info("ChainService", "handlePause() called")
        // No loop to cancel
        // countdownJob?.cancel()
        
        val remaining = getRemainingTime()
        
        // CRITICAL FIX: Don't pause if time is already expired or very close to 0
        if (remaining <= 1) {
            DebugLogger.warn("ChainService", "BUG FIX: Refusing to pause with $remaining seconds remaining (too close to 0)")
            DebugLogger.warn("ChainService", "Cannot pause - remaining time is $remaining seconds, too close to trigger")
            return
        }
        
        // CRITICAL: Cancel the scheduled AlarmManager alarm!
        val alarmScheduler = AlarmScheduler(this)
        val requestCode = currentIndex + 1
        DebugLogger.info("ChainService", "Canceling AlarmManager alarm with requestCode: $requestCode")
        alarmScheduler.cancelAlarm(requestCode)
        
        DebugLogger.info("ChainService", "Remaining time: ${remaining}s (${remaining * 1000}ms)")
        DebugLogger.info("TimerSync", "[PAUSE] Saving remaining time: ${remaining * 1000}ms")
        
        DebugLogger.info("ChainService", "Before pause - isPaused: ${ChainManager(this).isChainPaused()}")
        ChainManager(this).pauseChain(remaining * 1000L)
        DebugLogger.info("ChainService", "After pause - isPaused: ${ChainManager(this).isChainPaused()}")
        DebugLogger.info("ChainService", "Saved remaining time: ${ChainManager(this).getPausedRemainingTime()}ms")
        
        DebugLogger.info("TimerSync", "PAUSED: savedTime=${remaining * 1000}ms, endTime=$endTime cleared")
        
        // Only show paused notification if not dismissed
        if (shouldShowNotification()) {
            showNotification(isPaused = true)
        }
        DebugLogger.info("ChainService", "Pause complete")
        DebugLogger.info("ChainService", "=================================")
    }

    private fun handleResume() {
        DebugLogger.info("ChainService", "========== RESUME TIMER ==========")
        DebugLogger.info("ChainService", "handleResume() called")
        val remainingTimeMs = ChainManager(this).getPausedRemainingTime()
        DebugLogger.info("ChainService", "Retrieved paused remaining time: ${remainingTimeMs}ms")
        DebugLogger.info("TimerSync", "[RESUME] Reading saved time: ${remainingTimeMs}ms")
        
        // CRITICAL FIX: Don't resume if paused time is 0 or negative
        if (remainingTimeMs <= 1000) {
            DebugLogger.error("ChainService", "BUG FIX: Refusing to resume with ${remainingTimeMs}ms - would create instant alarm")
            DebugLogger.error("ChainService", "Cannot resume - paused time is ${remainingTimeMs}ms, too low to schedule alarm")
            // Just unpause without scheduling
            ChainManager(this).resumeChain()
            return
        }
        
        // CRITICAL: Reschedule the AlarmManager alarm with remaining time!
        val alarmScheduler = AlarmScheduler(this)
        val requestCode = currentIndex + 1
        DebugLogger.info("ChainService", "Rescheduling AlarmManager alarm with requestCode: $requestCode, delay: ${remainingTimeMs}ms")
        alarmScheduler.scheduleAlarm(remainingTimeMs, requestCode)
        
        // Reset end time relative to now
        val oldEndTime = endTime
        endTime = System.currentTimeMillis() + remainingTimeMs
        DebugLogger.info("ChainService", "New endTime calculated: $endTime (was: $oldEndTime)")
        DebugLogger.info("TimerSync", "[RESUME] Calculated new endTime: now=${System.currentTimeMillis()} + ${remainingTimeMs}ms = $endTime")
        
        DebugLogger.info("ChainService", "Before resume - isPaused: ${ChainManager(this).isChainPaused()}")
        ChainManager(this).resumeChain()
        DebugLogger.info("ChainService", "After resume - isPaused: ${ChainManager(this).isChainPaused()}")
        
        // CRITICAL FIX #2: Restart countdown to show notification and persist state
        DebugLogger.info("ChainService", "Calling startCountdown() after resume")
        DebugLogger.info("ChainService", "Restarting countdown (preserving dismissal state)...")
        startCountdown(resetDismissalState = false)
        DebugLogger.info("ChainService", "Resume complete")
        DebugLogger.info("ChainService", "==================================")
    }

    
    private fun handleNextAlarm() {
        DebugLogger.info("ChainService", "handleNextAlarm() called")
        
        // Clear alarm ringing state
        ChainManager(this).setAlarmRinging(false)
        
        // Stop any currently ringing alarm
        val stopAlarmIntent = Intent(this, AlarmService::class.java)
        stopService(stopAlarmIntent)
        
        // No loop to cancel
        // countdownJob?.cancel()
        
        // Cancel scheduled alarm
        val alarmScheduler = AlarmScheduler(this)
        alarmScheduler.cancelAlarm(currentIndex + 1)
        
        // CHECK CHAIN MODE
        val isChain = ChainManager(this).isChainSequence()
        if (!isChain) {
            DebugLogger.info("ChainService", "Single Alarm Mode - Stopping service after alarm")
            // Directly stop the chain instead of sending intent (more reliable)
            ChainManager(this).stopChain()
            
            // Cancel all alarms defensively
            val alarmScheduler = AlarmScheduler(this)
            alarmScheduler.cancelAlarm(currentIndex + 1)
            
            // CRITICAL FIX: Clear alarm active states in repository
            // This ensures the Start Sequence button is re-enabled when app is reopened
            val repository = AlarmRepository(this)
            val alarms = repository.loadAlarms()
            val clearedAlarms = alarms.map { it.copy(isActive = false, scheduledTime = 0L) }
            repository.saveAlarms(clearedAlarms)
            DebugLogger.info("ChainService", "All alarms cleared (single alarm mode)")
            
            // Clear notifications
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
            notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
            
            DebugLogger.info("ChainService", "Single alarm mode cleanup complete, stopping service")
            stopSelf()
            return
        }
        
        // Get alarm repository
        val repository = AlarmRepository(this)
        val alarms = repository.loadAlarms()
        
        // Check if there's a next alarm
        val nextIndex = currentIndex + 1
        if (nextIndex < alarms.size) {
            DebugLogger.info("ChainService", "Moving to next alarm: index $nextIndex")
            
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
            
            DebugLogger.info("ChainService", "Next alarm started successfully")
        } else {
            DebugLogger.info("ChainService", "No more alarms, stopping chain")
            // Directly stop the chain instead of sending intent (more reliable)
            ChainManager(this).stopChain()
            
            // Cancel all alarms defensively
            val alarmScheduler = AlarmScheduler(this)
            alarmScheduler.cancelAlarm(currentIndex + 1)
            
            // CRITICAL FIX: Clear alarm active states in repository
            // This ensures the Start Sequence button is re-enabled when app is reopened
            val clearedAlarms = alarms.map { it.copy(isActive = false, scheduledTime = 0L) }
            repository.saveAlarms(clearedAlarms)
            DebugLogger.info("ChainService", "All alarms cleared (chain sequence complete)")
            
            // Clear notifications
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
            notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
            
            DebugLogger.info("ChainService", "Chain sequence complete, stopping service")
            stopSelf()
        }
    }
    
    private fun handlePrevAlarm() {
        DebugLogger.info("ChainService", "handlePrevAlarm() called")
        
        // Clear alarm ringing state
        ChainManager(this).setAlarmRinging(false)
        
        // Stop any currently ringing alarm
        val stopAlarmIntent = Intent(this, AlarmService::class.java)
        stopService(stopAlarmIntent)
        
        // No loop to cancel
        // countdownJob?.cancel()
        
        // Cancel scheduled alarm
        val alarmScheduler = AlarmScheduler(this)
        alarmScheduler.cancelAlarm(currentIndex + 1)
        
        // Get alarm repository
        val repository = AlarmRepository(this)
        val alarms = repository.loadAlarms()
        
        // Check if there's a previous alarm
        val prevIndex = currentIndex - 1
        if (prevIndex >= 0) {
            DebugLogger.info("ChainService", "Moving to previous alarm: index $prevIndex")
            
            // CRITICAL FIX #4: Use moveToPrevAlarm() to reset pause state properly
            val chainManager = ChainManager(this)
            chainManager.moveToPrevAlarm()
            
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
            
            DebugLogger.info("ChainService", "Previous alarm started successfully")
        } else {
            DebugLogger.info("ChainService", "Already at first alarm, cannot go back")
        }
    }
    
    private fun handleJumpToAlarm(targetIndex: Int) {
        DebugLogger.info("ChainService", "handleJumpToAlarm() called with targetIndex=$targetIndex")
        
        // Clear alarm ringing state
        ChainManager(this).setAlarmRinging(false)
        
        // Stop any currently ringing alarm
        val stopAlarmIntent = Intent(this, AlarmService::class.java)
        stopService(stopAlarmIntent)
        
        // Cancel current scheduled alarm
        val alarmScheduler = AlarmScheduler(this)
        alarmScheduler.cancelAlarm(currentIndex + 1)
        
        // Get alarm repository
        val repository = AlarmRepository(this)
        val alarms = repository.loadAlarms()
        
        // Validate target index
        if (targetIndex < 0 || targetIndex >= alarms.size) {
            DebugLogger.error("ChainService", "Invalid target index: $targetIndex (total alarms: ${alarms.size})")
            return
        }
        
        DebugLogger.info("ChainService", "Jumping to alarm at index $targetIndex")
        
        // Update ChainManager with new index
        val chainManager = ChainManager(this)
        chainManager.setCurrentIndex(targetIndex)
        chainManager.resumeChain() // Clear any pause state
        
        // Start the target alarm
        val targetAlarm = alarms[targetIndex]
        val delayMillis = targetAlarm.getTotalSeconds() * 1000L
        val newEndTime = System.currentTimeMillis() + delayMillis
        
        // Schedule with AlarmManager
        alarmScheduler.scheduleAlarm(delayMillis, targetIndex + 1)
        
        // Update service state
        currentIndex = targetIndex
        totalAlarms = alarms.size
        currentAlarmName = targetAlarm.name
        endTime = newEndTime
        
        // Update repository
        val updatedAlarms = alarms.toMutableList()
        updatedAlarms[targetIndex] = targetAlarm.copy(isActive = true, scheduledTime = newEndTime)
        repository.saveAlarms(updatedAlarms)
        
        // Start countdown for target alarm
        startCountdown()
        
        DebugLogger.info("ChainService", "Successfully jumped to alarm at index $targetIndex")
    }
    
    private fun handlePauseCountdownLoop() {
        DebugLogger.info("BatteryOpt", "[PAUSE] No-op: Polling loop removed for optimization")
        // No loop to pause, Service is already efficient
    }
    
    private fun handleResumeCountdownLoop() {
        DebugLogger.info("BatteryOpt", "[RESUME] No-op: Polling loop removed for optimization")
        // No loop to resume, Notification Cronometer handles it
    }
}
