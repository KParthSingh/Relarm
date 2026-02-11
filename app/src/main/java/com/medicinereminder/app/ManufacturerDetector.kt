package com.relarm.app

import android.os.Build

/**
 * Utility object to detect if the device manufacturer is known for aggressive battery optimization
 * that affects alarm reliability.
 */
object ManufacturerDetector {
    
    private val AGGRESSIVE_BATTERY_MANUFACTURERS = setOf(
        // Vivo brands
        "vivo", "iqoo",
        
        // Oppo brands
        "oppo", "realme",
        
        // OnePlus
        "oneplus",
        
        // Xiaomi brands
        "xiaomi", "redmi", "poco",
        
        // Huawei brands
        "huawei", "honor"
    )
    
    /**
     * Check if the current device is from a manufacturer known for aggressive battery optimization.
     * @return true if the device requires autostart permission guidance
     */
    fun requiresAutostartWarning(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        return AGGRESSIVE_BATTERY_MANUFACTURERS.any { 
            manufacturer.contains(it) || brand.contains(it)
        }
    }
    
    /**
     * Get the manufacturer name for display purposes.
     */
    fun getManufacturerName(): String {
        return Build.MANUFACTURER
    }
}
