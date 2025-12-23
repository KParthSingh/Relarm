package com.medicinereminder.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm triggered!")
        DebugLogger.warn("AlarmReceiver", "========== ALARM TRIGGERED ==========")
        
        // Acquire wake lock to ensure device wakes up
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "MedicineReminder::AlarmWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L) // 10 minutes max

        try {
            // Check if we're in a chain - if so, notify ChainService to trigger the alarm
            val chainManager = ChainManager(context)
            if (chainManager.isChainActive()) {
                DebugLogger.logState("AlarmReceiver", mapOf(
                    "chainActive" to true,
                    "currentIndex" to chainManager.getCurrentIndex(),
                    "isPaused" to chainManager.isChainPaused(),
                    "isAlarmRinging" to chainManager.isAlarmRinging()
                ))
                Log.d("AlarmReceiver", "Chain is active - Notifying ChainService to trigger alarm")
                
                // Send intent to ChainService to trigger the alarm
                val serviceIntent = Intent(context, ChainService::class.java).apply {
                    action = ChainService.ACTION_TRIGGER_ALARM
                }
                
                // CRITICAL: Must start as foreground service to ensure it runs even in background
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                return
            }

            // Not in a chain - this is a standalone alarm, proceed normally
            DebugLogger.info("AlarmReceiver", "Chain NOT active - starting standalone alarm")
            Log.d("AlarmReceiver", "Standalone alarm detected, proceeding with alarm service")
            
            // Start foreground service (it will show the notification with STOP button)
            val serviceIntent = Intent(context, AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d("AlarmReceiver", "Alarm service started")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Error starting alarm", e)
        } finally {
            // Release wake lock after a delay to ensure service is started
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            }, 5000)
        }
    }
}
