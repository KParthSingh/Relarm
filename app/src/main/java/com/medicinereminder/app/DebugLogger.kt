package com.relarm.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe debug logger that writes to persistent file storage.
 * Helps diagnose state management bugs in production.
 */
object DebugLogger {
    private const val TAG = "DebugLogger"
    private const val LOG_FILENAME = "debug_log.txt"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    private const val MAX_LOG_FILES = 3
    
    private var logFile: File? = null
    private var isEnabled: Boolean = false
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    enum class Level(val prefix: String) {
        INFO("INFO"),
        WARNING("WARN"),
        ERROR("ERROR"),
        STATE("STATE") // Special level for state snapshots
    }
    
    fun init(context: Context) {
        val repo = SettingsRepository(context)
        isEnabled = repo.getEnableDebugLogs()
        if (!isEnabled) {
             // Even if disabled, we initialize the file reference but don't log start
             logFile = File(context.cacheDir, LOG_FILENAME)
             return
        }
        
        logFile = File(context.cacheDir, LOG_FILENAME)
        log(Level.INFO, "DebugLogger", "=== Logger Initialized ===")
    }
    
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            log(Level.INFO, "DebugLogger", "=== Logger Enabled ===")
        }
    }
    
    /**
     * Log a message asynchronously to avoid blocking the caller.
     */
    fun log(level: Level, component: String, message: String) {
        if (!isEnabled) return
        
        scope.launch {
            try {
                mutex.withLock {
                    val file = logFile ?: return@launch
                    
                    // Check file size and rotate if needed
                    if (file.exists() && file.length() > MAX_LOG_SIZE) {
                        rotateLogFiles(file)
                    }
                    
                    // Format: [TIMESTAMP] [LEVEL] [COMPONENT] Message
                    val timestamp = dateFormat.format(Date())
                    val logLine = "[$timestamp] [${level.prefix}] [$component] $message\n"
                    
                    file.appendText(logLine)
                    
                    // Also output to logcat for development
                    when (level) {
                        Level.ERROR -> Log.e(component, message)
                        Level.WARNING -> Log.w(component, message)
                        else -> Log.d(component, message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log: ${e.message}", e)
            }
        }
    }
    
    /**
     * Convenience methods for different log levels
     */
    fun info(component: String, message: String) = log(Level.INFO, component, message)
    fun warn(component: String, message: String) = log(Level.WARNING, component, message)
    fun error(component: String, message: String, t: Throwable? = null) {
        val msg = if (t != null) "$message\n${t.stackTraceToString()}" else message
        log(Level.ERROR, component, msg)
    }
    fun state(component: String, message: String) = log(Level.STATE, component, message)
    
    /**
     * Log state snapshot with key-value pairs
     */
    fun logState(component: String, state: Map<String, Any?>) {
        val stateStr = state.entries.joinToString(", ") { "${it.key}=${it.value}" }
        state(component, stateStr)
    }
    
    /**
     * Rotate log files to prevent unlimited growth
     */
    private fun rotateLogFiles(currentFile: File) {
        try {
            val dir = currentFile.parentFile ?: return
            
            // Delete oldest backup
            val oldestBackup = File(dir, "$LOG_FILENAME.${MAX_LOG_FILES - 1}")
            if (oldestBackup.exists()) {
                oldestBackup.delete()
            }
            
            // Shift existing backups
            for (i in (MAX_LOG_FILES - 2) downTo 1) {
                val src = File(dir, "$LOG_FILENAME.$i")
                val dst = File(dir, "$LOG_FILENAME.${i + 1}")
                if (src.exists()) {
                    src.renameTo(dst)
                }
            }
            
            // Rotate current to .1
            val backup = File(dir, "$LOG_FILENAME.1")
            currentFile.renameTo(backup)
            
            // Create new empty log file
            currentFile.createNewFile()
            
            log(Level.INFO, TAG, "Log files rotated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate log files: ${e.message}", e)
        }
    }
    
    /**
     * Get the current log file for export
     */
    fun getLogFile(): File? = logFile
    
    /**
     * Get all log files (current + backups)
     */
    fun getAllLogFiles(): List<File> {
        val file = logFile ?: return emptyList()
        val dir = file.parentFile ?: return emptyList()
        val files = mutableListOf<File>()
        
        if (file.exists()) {
            files.add(file)
        }
        
        for (i in 1 until MAX_LOG_FILES) {
            val backup = File(dir, "$LOG_FILENAME.$i")
            if (backup.exists()) {
                files.add(backup)
            }
        }
        
        return files
    }
    
    /**
     * Clear all log files
     */
    fun clearLogs() {
        scope.launch {
            try {
                mutex.withLock {
                    getAllLogFiles().forEach { it.delete() }
                    logFile?.createNewFile()
                    log(Level.INFO, TAG, "=== Logs Cleared ===")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear logs: ${e.message}", e)
            }
        }
    }
    
    /**
     * Export all logs combined into a single file
     */
    fun exportLogs(context: Context): File? {
        try {
            val exportFile = File(context.cacheDir, "exported_debug_log.txt")
            exportFile.delete()
            
            val allFiles = getAllLogFiles().sortedByDescending { it.lastModified() }
            
            exportFile.appendText("=== Relarm Debug Log Export ===\n")
            exportFile.appendText("Export Date: ${dateFormat.format(Date())}\n")
            exportFile.appendText("Total Files: ${allFiles.size}\n")
            exportFile.appendText("==========================================\n\n")
            
            allFiles.reversed().forEach { file ->
                exportFile.appendText("--- File: ${file.name} (${file.lastModified()}) ---\n")
                if (file.exists()) {
                    exportFile.appendText(file.readText())
                }
                exportFile.appendText("\n")
            }
            
            return exportFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export logs: ${e.message}", e)
            return null
        }
    }
}
