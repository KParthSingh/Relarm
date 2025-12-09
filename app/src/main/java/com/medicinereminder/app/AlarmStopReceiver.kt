package com.medicinereminder.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.medicinereminder.app.STOP_ALARM") {
            // Stop the alarm service
            val serviceIntent = Intent(context, AlarmService::class.java)
            context.stopService(serviceIntent)
            
            // Dismiss the notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NotificationHelper.NOTIFICATION_ID)
            
            // Stop countdown service from previous alarm
            val stopCountdownIntent = Intent(context, CountdownService::class.java)
            context.stopService(stopCountdownIntent)
            
            // Check if we're in a chain and need to start the next alarm
            val chainManager = ChainManager(context)
            if (chainManager.isChainActive()) {
                val alarmRepository = AlarmRepository(context)
                val alarms = alarmRepository.loadAlarms()
                
                // Move to next alarm
                chainManager.moveToNextAlarm()
                val nextIndex = chainManager.getCurrentIndex()
                
                if (nextIndex < alarms.size) {
                    // Start the next alarm in the chain
                    val nextAlarm = alarms[nextIndex]
                    val delayMillis = nextAlarm.getTotalSeconds() * 1000L
                    
                    val alarmScheduler = AlarmScheduler(context)
                    alarmScheduler.scheduleAlarm(delayMillis, nextIndex + 1)
                    
                    // Start countdown service for next alarm (after stopping the old one)
                    val countdownIntent = Intent(context, CountdownService::class.java).apply {
                        putExtra("triggerTime", System.currentTimeMillis() + delayMillis)
                    }
                    context.startForegroundService(countdownIntent)
                    
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
            
            Log.d("AlarmStopReceiver", "Alarm stopped and notification dismissed")
        }
    }
}
