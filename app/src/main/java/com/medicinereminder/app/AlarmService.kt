package com.medicinereminder.app

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        DebugLogger.info("AlarmService", "Service created")

        // Create notification channel
        NotificationHelper.createNotificationChannel(this)
        
        // Start as foreground service with ALARM notification
        val notification = NotificationHelper.buildAlarmNotification(this)
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLogger.info("AlarmService", "onStartCommand with action: ${intent?.action}")
        
        // Handle restore notification if user tries to swipe it away
        if (intent?.action == "RESTORE_NOTIFICATION") {
            DebugLogger.info("AlarmService", "Restoring notification after swipe attempt")
            val notification = NotificationHelper.buildAlarmNotification(this)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NotificationHelper.NOTIFICATION_ID, notification)
        } else {
            // Get custom sound URI if provided
            val soundUri = intent?.getStringExtra("soundUri")
            DebugLogger.info("AlarmService", "Starting with sound URI: $soundUri")
            
            // Start playing alarm sound
            playAlarmSound(soundUri)
            
            // Start vibration
            startVibration()
        }
        
        return START_NOT_STICKY
    }

    private fun playAlarmSound(customSoundUri: String? = null) {
        try {
            // Determine which URI to use
            val soundUri = if (customSoundUri != null) {
                try {
                    android.net.Uri.parse(customSoundUri)
                } catch (e: Exception) {
                    DebugLogger.error("AlarmService", "Invalid custom sound URI, using default", e)
                    null
                }
            } else {
                null
            }
            
            // Use custom sound if valid, otherwise use default
            val finalUri = soundUri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            DebugLogger.info("AlarmService", "Playing alarm sound from URI: $finalUri")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, finalUri)
                
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                setAudioAttributes(audioAttributes)
                isLooping = true
                prepare()
                start()
            }
            DebugLogger.info("AlarmService", "Alarm sound started successfully")
        } catch (e: Exception) {
            DebugLogger.error("AlarmService", "Error playing alarm sound", e)
            // If all else fails, try system default as last resort
            try {
                mediaPlayer = MediaPlayer().apply {
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    setDataSource(applicationContext, defaultUri)
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    isLooping = true
                    prepare()
                    start()
                }
                DebugLogger.info("AlarmService", "Fallback to default sound successful")
            } catch (fallbackException: Exception) {
                DebugLogger.error("AlarmService", "Failed to play any alarm sound", fallbackException)
            }
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 500) // Wait 0ms, vibrate 500ms, pause 500ms
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            DebugLogger.info("AlarmService", "Vibration started")
        } catch (e: Exception) {
            DebugLogger.error("AlarmService", "Error starting vibration", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.info("AlarmService", "Service destroyed - stopping sound and vibration")
        
        // Stop media player
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                    DebugLogger.info("AlarmService", "MediaPlayer stopped")
                }
                reset()
                release()
                DebugLogger.info("AlarmService", "MediaPlayer released")
            }
        } catch (e: Exception) {
            DebugLogger.error("AlarmService", "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null

        // Stop vibration
        try {
            vibrator?.cancel()
            DebugLogger.info("AlarmService", "Vibration canceled")
        } catch (e: Exception) {
            DebugLogger.error("AlarmService", "Error stopping vibration", e)
        }
        vibrator = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
