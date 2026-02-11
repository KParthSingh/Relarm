package com.relarm.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Helper object to open manufacturer-specific autostart/battery optimization settings.
 */
object AutostartHelper {
    
    private const val TAG = "AutostartHelper"
    
    /**
     * Open the autostart settings page for the current device manufacturer.
     * Falls back to general battery optimization settings if manufacturer-specific intent fails.
     */
    fun openAutostartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        DebugLogger.info(TAG, "Opening autostart settings for manufacturer: $manufacturer, brand: $brand")
        
        var success = false
        
        // Try manufacturer-specific intents
        when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || 
            brand.contains("redmi") || brand.contains("poco") -> {
                success = openXiaomiAutostart(context)
            }
            manufacturer.contains("oppo") || brand.contains("oppo") || 
            brand.contains("realme") -> {
                success = openOppoAutostart(context)
            }
            manufacturer.contains("vivo") || brand.contains("vivo") || 
            brand.contains("iqoo") -> {
                success = openVivoAutostart(context)
            }
            manufacturer.contains("huawei") || brand.contains("huawei") || 
            brand.contains("honor") -> {
                success = openHuaweiAutostart(context)
            }
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> {
                success = openOnePlusAutostart(context)
            }
        }
        
        // Fallback to general battery optimization settings
        if (!success) {
            DebugLogger.info(TAG, "Manufacturer-specific intent failed, falling back to battery settings")
            openBatteryOptimizationSettings(context)
        }
    }
    
    private fun openXiaomiAutostart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            DebugLogger.info(TAG, "Opened Xiaomi autostart settings")
            true
        } catch (e: Exception) {
            DebugLogger.warn(TAG, "Failed to open Xiaomi autostart settings: ${e.message}")
            false
        }
    }
    
    private fun openOppoAutostart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            DebugLogger.info(TAG, "Opened Oppo autostart settings")
            true
        } catch (e: Exception) {
            DebugLogger.warn(TAG, "Failed to open Oppo autostart settings, trying alternate: ${e.message}")
            // Try alternate Oppo intent
            try {
                val altIntent = Intent().apply {
                    component = ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(altIntent)
                DebugLogger.info(TAG, "Opened Oppo autostart settings (alternate)")
                true
            } catch (e2: Exception) {
                DebugLogger.warn(TAG, "Failed to open Oppo autostart settings (alternate): ${e2.message}")
                false
            }
        }
    }
    
    private fun openVivoAutostart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            DebugLogger.info(TAG, "Opened Vivo autostart settings")
            true
        } catch (e: Exception) {
            DebugLogger.warn(TAG, "Failed to open Vivo autostart settings: ${e.message}")
            false
        }
    }
    
    private fun openHuaweiAutostart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            DebugLogger.info(TAG, "Opened Huawei autostart settings")
            true
        } catch (e: Exception) {
            DebugLogger.warn(TAG, "Failed to open Huawei autostart settings: ${e.message}")
            false
        }
    }
    
    private fun openOnePlusAutostart(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            DebugLogger.info(TAG, "Opened OnePlus autostart settings")
            true
        } catch (e: Exception) {
            DebugLogger.warn(TAG, "Failed to open OnePlus autostart settings: ${e.message}")
            false
        }
    }
    
    private fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            DebugLogger.info(TAG, "Opened general battery optimization settings")
        } catch (e: Exception) {
            DebugLogger.error(TAG, "Failed to open battery optimization settings: ${e.message}")
            // Last resort: open app settings
            try {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                DebugLogger.info(TAG, "Opened app settings as fallback")
            } catch (e2: Exception) {
                DebugLogger.error(TAG, "Failed to open any settings: ${e2.message}")
            }
        }
    }
}
