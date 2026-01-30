package com.medicinereminder.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.medicinereminder.app.STOP_ALARM") {
            DebugLogger.warn("AlarmStopReceiver", "========== DISMISS ALARM CLICKED ==========")
            val chainManager = ChainManager(context)
            DebugLogger.logState("AlarmStopReceiver", mapOf(
                "chainActive" to chainManager.isChainActive(),
                "currentIndex" to chainManager.getCurrentIndex(),
                "wasAlarmRinging" to chainManager.isAlarmRinging()
            ))
            
            // Clear alarm ringing state
            chainManager.setAlarmRinging(false)
            
            // Stop the alarm service
            val serviceIntent = Intent(context, AlarmService::class.java)
            context.stopService(serviceIntent)
            
            // Dismiss both notification IDs for safety (we now use CHAIN_NOTIFICATION_ID)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
            notificationManager.cancel(NotificationHelper.CHAIN_NOTIFICATION_ID)
            
            // Broadcast to close any open AlarmRingingActivity
            val closeActivityIntent = Intent("com.medicinereminder.app.CLOSE_ALARM_ACTIVITY")
            context.sendBroadcast(closeActivityIntent)
            
            // DELEGATE NEXT STEP TO CHAIN SERVICE
            // ChainService.handleNextAlarm() will check isChainSequence() and decide 
            // whether to stop (Single Mode) or advance (Chain Mode).
            DebugLogger.info("AlarmStopReceiver", "Delegating next step to ChainService")
            val nextIntent = Intent(context, ChainService::class.java).apply {
                action = ChainService.ACTION_NEXT_ALARM
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                 context.startForegroundService(nextIntent)
            } else {
                 context.startService(nextIntent)
            }
            
            DebugLogger.info("AlarmStopReceiver", "Alarm stopped and ACTION_NEXT_ALARM sent")
        }
    }
}

