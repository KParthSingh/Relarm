package com.medicinereminder.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
}
