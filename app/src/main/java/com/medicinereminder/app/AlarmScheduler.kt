package com.medicinereminder.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)

    fun scheduleAlarm(delayMillis: Long, requestCode: Int = 0) {
        DebugLogger.info("AlarmScheduler", "=========================================")
        DebugLogger.info("AlarmScheduler", "scheduleAlarm() called")
        DebugLogger.info("AlarmScheduler", "  RequestCode: $requestCode")
        DebugLogger.info("AlarmScheduler", "  Delay: ${delayMillis}ms (${delayMillis/1000}s)")
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medicinereminder.app.ALARM_TRIGGERED"
            putExtra("REQUEST_CODE", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + delayMillis
        DebugLogger.info("AlarmScheduler", "  Trigger time: $triggerTime (now: ${System.currentTimeMillis()})")

        // Store the scheduled time
        prefs.edit().putLong("alarm_time_$requestCode", triggerTime).apply()

        try {
            // Use setExactAndAllowWhileIdle for reliable alarms even in Doze mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            DebugLogger.info("AlarmScheduler", "✓ Alarm scheduled successfully (EXACT)")
            DebugLogger.info("AlarmScheduler", "=========================================")
        } catch (e: SecurityException) {
            DebugLogger.error("AlarmScheduler", "✗ Permission denied for exact alarms", e)
            // CRITICAL FIX #7: Fallback to inexact alarm instead of failing silently
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    DebugLogger.info("AlarmScheduler", "✓ Fallback: Alarm scheduled (INEXACT) - may be delayed")
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    DebugLogger.info("AlarmScheduler", "✓ Fallback: Alarm scheduled (INEXACT) - may be delayed")
                }
            } catch (fallbackException: Exception) {
                DebugLogger.error("AlarmScheduler", "✗ Complete failure - alarm not scheduled!", fallbackException)
            }
            DebugLogger.info("AlarmScheduler", "=========================================")
        }
    }

    fun getScheduledTime(requestCode: Int = 0): String {
        val time = prefs.getLong("alarm_time_$requestCode", 0)
        if (time == 0L) return ""
        
        val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        return sdf.format(Date(time))
    }

    fun cancelAlarm(requestCode: Int = 0) {
        DebugLogger.info("AlarmScheduler", "=========================================")
        DebugLogger.info("AlarmScheduler", "cancelAlarm() called")
        DebugLogger.info("AlarmScheduler", "  RequestCode: $requestCode")
        
        val wasScheduled = prefs.contains("alarm_time_$requestCode")
        DebugLogger.info("AlarmScheduler", "  Was alarm scheduled: $wasScheduled")
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.medicinereminder.app.ALARM_TRIGGERED"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        DebugLogger.info("AlarmScheduler", "  AlarmManager.cancel() called")
        
        prefs.edit().remove("alarm_time_$requestCode").apply()
        DebugLogger.info("AlarmScheduler", "  Stored time removed from prefs")
        DebugLogger.info("AlarmScheduler", "✓ Alarm canceled")
        DebugLogger.info("AlarmScheduler", "=========================================")
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // No special permission needed before Android 12
        }
    }
}
