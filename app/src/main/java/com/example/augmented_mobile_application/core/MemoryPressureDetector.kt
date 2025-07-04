package com.example.augmented_mobile_application.core

import android.app.ActivityManager
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.seconds

/**
 * Advanced memory pressure detection and monitoring system
 * 
 * Monitors system memory usage and provides callbacks for memory pressure events.
 * Uses both ActivityManager statistics and ComponentCallbacks2 for comprehensive monitoring.
 */
class MemoryPressureDetector(
    private val context: Context
) : ComponentCallbacks2 {
    
    companion object {
        private const val TAG = "MemoryPressureDetector"
        private const val MONITORING_INTERVAL_MS = 5000L // 5 seconds
        
        // Memory pressure thresholds
        private const val LOW_MEMORY_THRESHOLD = 0.7f
        private const val MODERATE_MEMORY_THRESHOLD = 0.8f
        private const val HIGH_MEMORY_THRESHOLD = 0.9f
        private const val CRITICAL_MEMORY_THRESHOLD = 0.95f
    }
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Memory pressure state
    private val _memoryPressure = MutableStateFlow(0.0f)
    val memoryPressure: StateFlow<Float> = _memoryPressure.asStateFlow()
    
    private val _memoryLevel = MutableStateFlow(MemoryLevel.NORMAL)
    val memoryLevel: StateFlow<MemoryLevel> = _memoryLevel.asStateFlow()
    
    private val _memoryStats = MutableStateFlow(MemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()
    
    private var isMonitoring = false
    
    enum class MemoryLevel {
        NORMAL,
        LOW,
        MODERATE,
        HIGH,
        CRITICAL
    }
    
    data class MemoryStats(
        val totalMemoryMB: Long = 0,
        val availableMemoryMB: Long = 0,
        val usedMemoryMB: Long = 0,
        val usedMemoryPercent: Float = 0.0f,
        val lowMemoryThreshold: Long = 0,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    init {
        context.registerComponentCallbacks(this)
        startMonitoring()
    }
    
    /**
     * Start continuous memory monitoring
     */
    private fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        scope.launch {
            while (isActive && isMonitoring) {
                try {
                    updateMemoryStats()
                    delay(MONITORING_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during memory monitoring", e)
                    delay(10.seconds)
                }
            }
        }
        
        Log.i(TAG, "Memory pressure monitoring started")
    }
    
    /**
     * Stop memory monitoring
     */
    fun stop() {
        isMonitoring = false
        scope.cancel()
        
        try {
            context.unregisterComponentCallbacks(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering component callbacks", e)
        }
        
        Log.i(TAG, "Memory pressure monitoring stopped")
    }
    
    /**
     * Get current memory pressure as a float between 0.0 and 1.0
     */
    fun getCurrentPressure(): Float = _memoryPressure.value
    
    /**
     * Get current memory level
     */
    fun getCurrentLevel(): MemoryLevel = _memoryLevel.value
    
    /**
     * Update memory statistics and pressure levels
     */
    private suspend fun updateMemoryStats() {
        try {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
            val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)
            val usedMemoryMB = totalMemoryMB - availableMemoryMB
            val usedMemoryPercent = if (totalMemoryMB > 0) {
                usedMemoryMB.toFloat() / totalMemoryMB.toFloat()
            } else 0.0f
            
            val stats = MemoryStats(
                totalMemoryMB = totalMemoryMB,
                availableMemoryMB = availableMemoryMB,
                usedMemoryMB = usedMemoryMB,
                usedMemoryPercent = usedMemoryPercent,
                lowMemoryThreshold = memoryInfo.threshold / (1024 * 1024),
                timestamp = System.currentTimeMillis()
            )
            
            _memoryStats.value = stats
            _memoryPressure.value = usedMemoryPercent
            
            // Update memory level based on usage
            val newLevel = when {
                usedMemoryPercent >= CRITICAL_MEMORY_THRESHOLD -> MemoryLevel.CRITICAL
                usedMemoryPercent >= HIGH_MEMORY_THRESHOLD -> MemoryLevel.HIGH
                usedMemoryPercent >= MODERATE_MEMORY_THRESHOLD -> MemoryLevel.MODERATE
                usedMemoryPercent >= LOW_MEMORY_THRESHOLD -> MemoryLevel.LOW
                else -> MemoryLevel.NORMAL
            }
            
            if (newLevel != _memoryLevel.value) {
                _memoryLevel.value = newLevel
                Log.i(TAG, "Memory level changed to: $newLevel (${(usedMemoryPercent * 100).toInt()}% used)")
            }
            
            // Log detailed stats periodically
            if (System.currentTimeMillis() % 30000 < MONITORING_INTERVAL_MS) {
                Log.d(TAG, "Memory Stats - Used: ${usedMemoryMB}MB/${totalMemoryMB}MB " +
                        "(${(usedMemoryPercent * 100).toInt()}%), Available: ${availableMemoryMB}MB, " +
                        "Level: $newLevel")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating memory stats", e)
        }
    }
    
    // ComponentCallbacks2 implementation for system memory callbacks
    
    override fun onTrimMemory(level: Int) {
        val memoryLevel = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryLevel.CRITICAL
            
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> MemoryLevel.HIGH
            
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryLevel.MODERATE
            
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryLevel.LOW
            
            else -> MemoryLevel.NORMAL
        }
        
        Log.w(TAG, "System memory trim callback - Level: $level, Mapped to: $memoryLevel")
        
        // Update our memory level if it's more severe than current detection
        if (memoryLevel.ordinal > _memoryLevel.value.ordinal) {
            _memoryLevel.value = memoryLevel
            
            // Force immediate memory stats update
            scope.launch {
                updateMemoryStats()
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        // Configuration changes might affect memory usage
        Log.d(TAG, "Configuration changed - triggering memory stats update")
        scope.launch {
            updateMemoryStats()
        }
    }
    
    override fun onLowMemory() {
        Log.w(TAG, "System low memory callback - setting critical level")
        _memoryLevel.value = MemoryLevel.CRITICAL
        
        // Force immediate update
        scope.launch {
            updateMemoryStats()
        }
    }
    
    /**
     * Get human-readable memory pressure description
     */
    fun getMemoryPressureDescription(): String {
        val pressure = getCurrentPressure()
        val level = getCurrentLevel()
        
        return when (level) {
            MemoryLevel.NORMAL -> "Normal (${(pressure * 100).toInt()}%)"
            MemoryLevel.LOW -> "Low pressure (${(pressure * 100).toInt()}%)"
            MemoryLevel.MODERATE -> "Moderate pressure (${(pressure * 100).toInt()}%)"
            MemoryLevel.HIGH -> "High pressure (${(pressure * 100).toInt()}%)"
            MemoryLevel.CRITICAL -> "Critical pressure (${(pressure * 100).toInt()}%)"
        }
    }
    
    /**
     * Check if memory cleanup should be aggressive based on current pressure
     */
    fun shouldUseAggressiveCleanup(): Boolean {
        return _memoryLevel.value in listOf(MemoryLevel.HIGH, MemoryLevel.CRITICAL)
    }
}
