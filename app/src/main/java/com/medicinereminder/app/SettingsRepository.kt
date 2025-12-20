package com.medicinereminder.app

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_CLOSE_ON_START = "close_on_start"
        const val KEY_HIDE_STOP_BUTTON = "hide_stop_button"
        const val KEY_DISMISSABLE_COUNTER = "dismissable_counter"
        const val KEY_BATTERY_WARNING_NEVER_SHOW = "battery_warning_never_show"
        const val KEY_DEFAULT_ALARM_TIME = "default_alarm_time"
        const val KEY_FORCE_BATTERY_WARNING = "debug_force_battery_warning"
        
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_AUTO = "auto"
    }
    
    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME, THEME_AUTO) ?: THEME_AUTO
    }
    
    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
    }
    
    fun getCloseOnStart(): Boolean {
        return prefs.getBoolean(KEY_CLOSE_ON_START, false)
    }
    
    fun setCloseOnStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOSE_ON_START, enabled).apply()
    }
    
    fun getHideStopButton(): Boolean {
        return prefs.getBoolean(KEY_HIDE_STOP_BUTTON, false)
    }
    
    fun setHideStopButton(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_STOP_BUTTON, enabled).apply()
    }
    
    fun getDismissableCounter(): Boolean {
        return prefs.getBoolean(KEY_DISMISSABLE_COUNTER, false)
    }
    
    fun setDismissableCounter(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISMISSABLE_COUNTER, enabled).apply()
    }
    
    fun getBatteryWarningNeverShow(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_WARNING_NEVER_SHOW, false)
    }
    
    fun setBatteryWarningNeverShow(neverShow: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_WARNING_NEVER_SHOW, neverShow).apply()
    }
    
    fun getDefaultAlarmTime(): Int {
        return prefs.getInt(KEY_DEFAULT_ALARM_TIME, 300) // Default 5 minutes (not set)
    }
    
    fun setDefaultAlarmTime(seconds: Int) {
        prefs.edit().putInt(KEY_DEFAULT_ALARM_TIME, seconds).apply()
    }
    
    fun getForceBatteryWarning(): Boolean {
        return prefs.getBoolean(KEY_FORCE_BATTERY_WARNING, false)
    }
    
    fun setForceBatteryWarning(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_BATTERY_WARNING, enabled).apply()
    }
}
