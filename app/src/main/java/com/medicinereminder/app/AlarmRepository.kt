package com.relarm.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

class AlarmRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun saveAlarms(alarms: List<Alarm>) {
        val json = gson.toJson(alarms)
        prefs.edit().putString("alarm_list", json).apply()
    }
    
    fun loadAlarms(): List<Alarm> {
        val json = prefs.getString("alarm_list", null) ?: return emptyList()
        val type = object : TypeToken<List<Alarm>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun clearAlarms() {
        prefs.edit().remove("alarm_list").apply()
    }
    
    fun clearAllAlarms() {
        prefs.edit().clear().apply()
    }

    fun getAlarmsFlow(): kotlinx.coroutines.flow.Flow<List<Alarm>> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "alarm_list") {
                trySend(loadAlarms())
            }
        }
        
        // Emit initial value
        trySend(loadAlarms())
        
        prefs.registerOnSharedPreferenceChangeListener(listener)
        
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.flowOn(Dispatchers.IO)
}
