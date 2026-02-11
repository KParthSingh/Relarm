package com.relarm.app

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

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
        
        // ChainService runtime state for recovery after kill
        private const val KEY_SERVICE_CURRENT_INDEX = "service_current_index"
        private const val KEY_SERVICE_END_TIME = "service_end_time"
        private const val KEY_SERVICE_TOTAL_ALARMS = "service_total_alarms"
        private const val KEY_SERVICE_ALARM_NAME = "service_alarm_name"
        private const val KEY_IS_CHAIN_SEQUENCE = "is_chain_sequence"
    }
    
    fun setChainSequence(isChain: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_CHAIN_SEQUENCE, isChain)
            .apply()
    }

    fun isChainSequence(): Boolean {
        // Default to true (chain mode) if not specified, to match legacy behavior
        return prefs.getBoolean(KEY_IS_CHAIN_SEQUENCE, true)
    }
    
    fun startChain() {
        DebugLogger.info("ChainManager", "startChain() called")
        prefs.edit()
            .putBoolean(KEY_CHAIN_ACTIVE, true)
            .putInt(KEY_CURRENT_INDEX, 0)
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
        DebugLogger.logState("ChainManager", mapOf(
            "action" to "startChain",
            "chainActive" to true,
            "currentIndex" to 0,
            "isPaused" to false
        ))
    }
    
    fun isChainActive(): Boolean {
        return prefs.getBoolean(KEY_CHAIN_ACTIVE, false)
    }

    fun setChainActive(isActive: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CHAIN_ACTIVE, isActive)
            .apply()
    }
    
    fun getCurrentIndex(): Int {
        return prefs.getInt(KEY_CURRENT_INDEX, 0)
    }
    
    fun moveToNextAlarm() {
        val currentIndex = getCurrentIndex()
        val nextIndex = currentIndex + 1
        DebugLogger.info("ChainManager", "moveToNextAlarm() called: $currentIndex -> $nextIndex")
        prefs.edit()
            .putInt(KEY_CURRENT_INDEX, nextIndex)
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
        DebugLogger.logState("ChainManager", mapOf(
            "action" to "moveToNextAlarm",
            "previousIndex" to currentIndex,
            "newIndex" to nextIndex,
            "isPaused" to false
        ))
    }
    
    fun moveToPrevAlarm() {
        val currentIndex = getCurrentIndex()
        val prevIndex = currentIndex - 1
        DebugLogger.info("ChainManager", "moveToPrevAlarm() called: $currentIndex -> $prevIndex")
        prefs.edit()
            .putInt(KEY_CURRENT_INDEX, prevIndex)
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
        DebugLogger.logState("ChainManager", mapOf(
            "action" to "moveToPrevAlarm",
            "previousIndex" to currentIndex,
            "newIndex" to prevIndex,
            "isPaused" to false
        ))
    }
    
    fun stopChain() {
        DebugLogger.info("ChainManager", "stopChain() called")
        prefs.edit()
            .putBoolean(KEY_CHAIN_ACTIVE, false)
            .putInt(KEY_CURRENT_INDEX, 0)
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
        DebugLogger.logState("ChainManager", mapOf(
            "action" to "stopChain",
            "chainActive" to false,
            "currentIndex" to 0,
            "isPaused" to false
        ))
    }

    fun pauseChain(remainingTime: Long) {
        DebugLogger.info("ChainManager", "pauseChain() called with remainingTime=$remainingTime ms")
        prefs.edit()
            .putBoolean(KEY_IS_PAUSED, true)
            .putLong(KEY_REMAINING_TIME, remainingTime)
            .apply()
        DebugLogger.logState("ChainManager", mapOf(
            "action" to "pauseChain",
            "isPaused" to true,
            "remainingTime" to remainingTime
        ))
    }

    fun resumeChain() {
        val wasRemainingTime = getPausedRemainingTime()
        DebugLogger.info("ChainManager", "resumeChain() called, was paused with $wasRemainingTime ms remaining")
        prefs.edit()
            .putBoolean(KEY_IS_PAUSED, false)
            .putLong(KEY_REMAINING_TIME, 0L)
            .apply()
        DebugLogger.logState("ChainManager", mapOf(
            "action" to "resumeChain",
            "isPaused" to false,
            "wasRemainingTime" to wasRemainingTime
        ))
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
        DebugLogger.warn("ChainManager", "setAlarmRinging($isRinging) called - CRITICAL STATE CHANGE")
        prefs.edit()
            .putBoolean(KEY_IS_ALARM_RINGING, isRinging)
            .apply()
        DebugLogger.logState("ChainManager", mapOf(
            "action" to "setAlarmRinging",
            "isAlarmRinging" to isRinging,
            "currentIndex" to getCurrentIndex(),
            "chainActive" to isChainActive()
        ))
    }
    
    // Store absolute end time for battery optimization
    fun setEndTime(endTimeMs: Long) {
        DebugLogger.info("ChainManager", "setEndTime($endTimeMs) - current time: ${System.currentTimeMillis()}")
        prefs.edit()
            .putLong(KEY_END_TIME, endTimeMs)
            .apply()
    }
    
    fun getEndTime(): Long {
        return prefs.getLong(KEY_END_TIME, 0L)
    }
    
    // ChainService state persistence for recovery
    fun saveServiceState(currentIndex: Int, endTime: Long, totalAlarms: Int, alarmName: String) {
        DebugLogger.info("ChainManager", "saveServiceState: index=$currentIndex, endTime=$endTime, total=$totalAlarms")
        prefs.edit()
            .putInt(KEY_SERVICE_CURRENT_INDEX, currentIndex)
            .putLong(KEY_SERVICE_END_TIME, endTime)
            .putInt(KEY_SERVICE_TOTAL_ALARMS, totalAlarms)
            .putString(KEY_SERVICE_ALARM_NAME, alarmName)
            .apply()
    }
    
    fun getServiceCurrentIndex(): Int {
        return prefs.getInt(KEY_SERVICE_CURRENT_INDEX, 0)
    }
    
    fun getServiceEndTime(): Long {
        return prefs.getLong(KEY_SERVICE_END_TIME, 0L)
    }
    
    fun getServiceTotalAlarms(): Int {
        return prefs.getInt(KEY_SERVICE_TOTAL_ALARMS, 0)
    }
    
    fun getServiceAlarmName(): String {
        return prefs.getString(KEY_SERVICE_ALARM_NAME, "") ?: ""
    }

    fun getChainState(): ChainState {
        return ChainState(
            isChainActive = isChainActive(),
            isPaused = isChainPaused(),
            currentIndex = getCurrentIndex(),
            isAlarmRinging = isAlarmRinging(),
            isChainSequence = isChainSequence(),
            endTime = getEndTime(),
            pausedRemainingTime = getPausedRemainingTime()
        )
    }

    fun getChainStateFlow(): kotlinx.coroutines.flow.Flow<ChainState> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CHAIN_ACTIVE || 
                key == KEY_CURRENT_INDEX || 
                key == KEY_IS_PAUSED || 
                key == KEY_IS_ALARM_RINGING || 
                key == KEY_IS_CHAIN_SEQUENCE ||
                key == KEY_END_TIME ||
                key == KEY_REMAINING_TIME) {
                trySend(getChainState())
            }
        }
        
        // Emit initial value
        trySend(getChainState())
        
        prefs.registerOnSharedPreferenceChangeListener(listener)
        
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.flowOn(Dispatchers.IO)
}

data class ChainState(
    val isChainActive: Boolean = false,
    val isPaused: Boolean = false,
    val currentIndex: Int = 0,
    val isAlarmRinging: Boolean = false,
    val isChainSequence: Boolean = true,
    val endTime: Long = 0L,
    val pausedRemainingTime: Long = 0L
)
