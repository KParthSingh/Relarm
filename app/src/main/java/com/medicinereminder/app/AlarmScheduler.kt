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
        Log.d("AlarmScheduler", "=========================================")
        Log.d("AlarmScheduler", "scheduleAlarm() called")
        Log.d("AlarmScheduler", "  RequestCode: $requestCode")
        Log.d("AlarmScheduler", "  Delay: ${delayMillis}ms (${delayMillis/1000}s)")
        
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
        Log.d("AlarmScheduler", "  Trigger time: $triggerTime (now: ${System.currentTimeMillis()})")

        // Store the scheduled time
        prefs.edit().putLong("alarm_time_$requestCode", triggerTime).apply()

        try {
            // CRITICAL: Use setAlarmClock for maximum reliability!
            // This is the MOST reliable way to schedule alarms because:
            // 1. Shows in system alarm UI (user sees the alarm)
            // 2. Gets special treatment from Android - survives app kills
            // 3. Wakes device even from Doze mode
            // 4. Not affected by battery optimization
            val alarmClockInfo = AlarmManager.AlarmClockInfo(
                triggerTime,
                pendingIntent  // PendingIntent to show when user taps alarm in status bar
            )
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d("AlarmScheduler", "✓ Alarm scheduled successfully (ALARM_CLOCK - MOST RELIABLE)")
            Log.d("AlarmScheduler", "=========================================")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "✗ Permission denied for setAlarmClock, falling back", e)
            // Fallback to setExactAndAllowWhileIdle
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d("AlarmScheduler", "✓ Fallback: Alarm scheduled (EXACT)")
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.d("AlarmScheduler", "✓ Fallback: Alarm scheduled (EXACT - pre-M)")
                }
            } catch (fallbackException: Exception) {
                Log.e("AlarmScheduler", "✗ Complete failure - alarm not scheduled!", fallbackException)
            }
            Log.d("AlarmScheduler", "=========================================")
        }
    }

    fun getScheduledTime(requestCode: Int = 0): String {
        val time = prefs.getLong("alarm_time_$requestCode", 0)
        if (time == 0L) return ""
        
        val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        return sdf.format(Date(time))
    }

    fun cancelAlarm(requestCode: Int = 0) {
        Log.d("AlarmScheduler", "=========================================")
        Log.d("AlarmScheduler", "cancelAlarm() called")
        Log.d("AlarmScheduler", "  RequestCode: $requestCode")
        
        val wasScheduled = prefs.contains("alarm_time_$requestCode")
        Log.d("AlarmScheduler", "  Was alarm scheduled: $wasScheduled")
        
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
        Log.d("AlarmScheduler", "  AlarmManager.cancel() called")
        
        prefs.edit().remove("alarm_time_$requestCode").apply()
        Log.d("AlarmScheduler", "  Stored time removed from prefs")
        Log.d("AlarmScheduler", "✓ Alarm canceled")
        Log.d("AlarmScheduler", "=========================================")
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // No special permission needed before Android 12
        }
    }
}
