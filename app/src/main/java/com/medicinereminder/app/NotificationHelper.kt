package com.medicinereminder.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ID = "medicine_reminder_channel"
    const val NOTIFICATION_ID = 1001
    const val CHAIN_NOTIFICATION_ID = 1003

    // ... existing methods ...

    fun buildChainNotification(
        context: Context,
        currentStep: Int,
        totalSteps: Int,
        remainingSeconds: Int,
        nextAlarmName: String,
        isPaused: Boolean = false
    ): android.app.Notification {
        val stopIntent = Intent(context, ChainService::class.java).apply {
            action = ChainService.ACTION_STOP_CHAIN
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, ChainService::class.java).apply {
            action = ChainService.ACTION_PAUSE_CHAIN
        }
        val pausePendingIntent = PendingIntent.getService(
            context,
            4,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(context, ChainService::class.java).apply {
            action = ChainService.ACTION_RESUME_CHAIN
        }
        val resumePendingIntent = PendingIntent.getService(
            context,
            5,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        
        val timeText = if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
                       else if (minutes > 0) String.format("%d:%02d", minutes, seconds)
                       else "${seconds}s"

        val title = "Sequence: $currentStep of $totalSteps ${if(isPaused) "(PAUSED)" else ""}"
        val content = if (isPaused) "Tap RESUME to continue." else "Next: ${if(nextAlarmName.isNotEmpty()) nextAlarmName else "Alarm"} in $timeText"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(Color.parseColor("#6750A4"))
            
        if (isPaused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "RESUME",
                resumePendingIntent
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "PAUSE",
                pausePendingIntent
            )
        }
            
        return builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP SEQUENCE",
                stopPendingIntent
            )
            .build()
    }
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null) 
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = Color.MAGENTA
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildAlarmNotification(context: Context): android.app.Notification {
        val intent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AlarmStopReceiver::class.java).apply {
            action = "com.medicinereminder.app.STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("AlarmPrefs", Context.MODE_PRIVATE)
        val scheduledTime = prefs.getLong("alarm_time_1", 0)
        val timeText = if (scheduledTime > 0) {
            val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            sdf.format(java.util.Date(scheduledTime))
        } else {
            "Now"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ " + context.getString(R.string.alarm_ringing))
            .setContentText(context.getString(R.string.alarm_ringing_subtitle))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${context.getString(R.string.alarm_ringing_subtitle)}\n\nScheduled for: $timeText"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(Color.parseColor("#6750A4")) // Match app primary color
            .addAction(
                android.R.drawable.ic_delete,
                "SNOOZE / STOP", // Clearer action text
                stopPendingIntent
            )
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
    }

    fun buildServiceNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.alarm_service_notification))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setColor(Color.parseColor("#6750A4"))
            .build()
    }


}
