package com.relarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            DebugLogger.info("BootReceiver", "Boot completed - alarms would be rescheduled here in future versions")
            // TODO: In future steps, we'll reschedule alarms from saved presets
        }
    }
}
