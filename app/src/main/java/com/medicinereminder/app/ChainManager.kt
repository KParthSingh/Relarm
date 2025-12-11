package com.medicinereminder.app

import android.content.Context

class ChainManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("chain_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_CHAIN_ACTIVE = "chain_active"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_IS_PAUSED = "is_paused"
        private const val KEY_REMAINING_TIME = "remaining_time"
        private const val KEY_CURRENT_REMAINING = "current_remaining_time"
        private const val KEY_IS_ALARM_RINGING = "is_alarm_ringing"
        private const val KEY_END_TIME = "end_time"
    }
    
    fun startChain() {
        prefs.edit()
            .putBoolean(KEY_CHAIN_ACTIVE, true)
            .putInt(KEY_CURRENT_INDEX, 0)
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
    }
    
    fun isChainActive(): Boolean {
        return prefs.getBoolean(KEY_CHAIN_ACTIVE, false)
    }
    
    fun getCurrentIndex(): Int {
        return prefs.getInt(KEY_CURRENT_INDEX, 0)
    }
    
    fun moveToNextAlarm() {
        val currentIndex = getCurrentIndex()
        prefs.edit()
            .putInt(KEY_CURRENT_INDEX, currentIndex + 1)
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
    }
    
    fun stopChain() {
        prefs.edit()
            .putBoolean(KEY_CHAIN_ACTIVE, false)
            .putInt(KEY_CURRENT_INDEX, 0)
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
    }

    fun pauseChain(remainingTime: Long) {
        prefs.edit()
            .putBoolean(KEY_IS_PAUSED, true)
            .putLong(KEY_REMAINING_TIME, remainingTime)
            .apply()
    }

    fun resumeChain() {
        prefs.edit()
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
    }

    fun isChainPaused(): Boolean {
        return prefs.getBoolean(KEY_IS_PAUSED, false)
    }

    fun getPausedRemainingTime(): Long {
        return prefs.getLong(KEY_REMAINING_TIME, 0L)
    }

    fun setCurrentRemainingTime(remainingTimeMs: Long) {
        prefs.edit()
            .putLong(KEY_CURRENT_REMAINING, remainingTimeMs)
            .apply()
    }

    fun getCurrentRemainingTime(): Long {
        return prefs.getLong(KEY_CURRENT_REMAINING, 0L)
    }
    
    fun setCurrentIndex(index: Int) {
        prefs.edit()
            .putInt(KEY_CURRENT_INDEX, index)
            .apply()
    }
    
    
    fun isAlarmRinging(): Boolean {
        return prefs.getBoolean(KEY_IS_ALARM_RINGING, false)
    }
    
    fun setAlarmRinging(isRinging: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_ALARM_RINGING, isRinging)
            .apply()
    }
    
    // Store absolute end time for battery optimization
    fun setEndTime(endTimeMs: Long) {
        prefs.edit()
            .putLong(KEY_END_TIME, endTimeMs)
            .apply()
    }
    
    fun getEndTime(): Long {
        return prefs.getLong(KEY_END_TIME, 0L)
    }
}
