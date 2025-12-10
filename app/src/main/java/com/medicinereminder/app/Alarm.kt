package com.medicinereminder.app

import java.util.UUID

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val hours: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 5,
    val isActive: Boolean = false,
    val scheduledTime: Long = 0L,
    val soundUri: String? = null // URI of custom alarm sound, null = use default
) {
    fun getTotalSeconds(): Int = hours * 3600 + minutes * 60 + seconds
    
    fun getFormattedTime(): String = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    
    fun clone(): Alarm = this.copy(id = UUID.randomUUID().toString(), isActive = false, scheduledTime = 0L)
}
