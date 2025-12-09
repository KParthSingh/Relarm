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
                    val endTime = System.currentTimeMillis() + delayMillis
                    
                    val alarmScheduler = AlarmScheduler(context)
                    alarmScheduler.scheduleAlarm(delayMillis, nextIndex + 1)
                    
                    // Start ChainService for next alarm
                    val chainIntent = Intent(context, ChainService::class.java).apply {
                        action = ChainService.ACTION_START_CHAIN_ALARM
                        putExtra(ChainService.EXTRA_END_TIME, endTime)
                        putExtra(ChainService.EXTRA_CURRENT_INDEX, nextIndex)
                        putExtra(ChainService.EXTRA_TOTAL_ALARMS, alarms.size)
                        putExtra(ChainService.EXTRA_ALARM_NAME, nextAlarm.name)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(chainIntent)
                    } else {
                        context.startService(chainIntent)
                    }
                    
                    // Update alarm state in repository
                    val updatedAlarms = alarms.toMutableList()
                    updatedAlarms[nextIndex] = nextAlarm.copy(
                        isActive = true,
                        scheduledTime = endTime
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
