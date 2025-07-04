package com.example.augmented_mobile_application

import android.app.Application
import android.util.Log
import com.example.augmented_mobile_application.core.ResourceAdministrator

/**
 * Enhanced Application class with advanced resource management
 * 
 * Initializes global resources and manages application-wide
 * resource administration for optimal performance and memory usage.
 */
class AugmentedARApplication : Application() {
    
    companion object {
        private const val TAG = "AugmentedARApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "Initializing Augmented AR Application")
        
        // Initialize Resource Administrator
        initializeResourceManagement()
        
        // Register global memory watchers
        registerGlobalMemoryWatchers()
        
        Log.i(TAG, "Application initialization complete")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        // Cleanup all resources before termination
        Log.i(TAG, "Application terminating - cleaning up resources")
        ResourceAdministrator.destroy()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        
        Log.w(TAG, "Low memory detected - performing emergency cleanup")
        
        // Get resource administrator and perform emergency cleanup
        val resourceAdmin = ResourceAdministrator.getInstance(this)
        resourceAdmin.emergencyCleanup()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        val resourceAdmin = ResourceAdministrator.getInstance(this)
        
        when (level) {
            TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.i(TAG, "Memory trimming: MODERATE - cleaning low priority resources")
                resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.LOW)
            }
            TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "Memory trimming: LOW - cleaning normal priority resources")
                resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.LOW)
                resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.NORMAL)
            }
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.e(TAG, "Memory trimming: CRITICAL - performing emergency cleanup")
                resourceAdmin.emergencyCleanup()
            }
            TRIM_MEMORY_UI_HIDDEN -> {
                Log.i(TAG, "UI hidden - cleaning low priority resources")
                resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.LOW)
            }
            TRIM_MEMORY_BACKGROUND -> {
                Log.i(TAG, "App backgrounded - performing moderate cleanup")
                resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.LOW)
                resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.NORMAL)
            }
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Heavy memory pressure - performing aggressive cleanup")
                resourceAdmin.emergencyCleanup()
            }
        }
    }
    
    private fun initializeResourceManagement() {
        try {
            // Initialize the singleton with application context
            val resourceAdmin = ResourceAdministrator.getInstance(this)
            
            Log.i(TAG, "Resource Administrator initialized successfully")
            
            // Log initial resource state
            val stats = resourceAdmin.getResourceStats()
            Log.d(TAG, "Initial resource state: $stats")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Resource Administrator", e)
        }
    }
    
    private fun registerGlobalMemoryWatchers() {
        try {
            val resourceAdmin = ResourceAdministrator.getInstance(this)
            
            // Global app memory watcher
            resourceAdmin.registerMemoryWatcher(
                watcherName = "global_app_memory",
                thresholdMB = 300, // Alert if app uses more than 300MB total
                onThresholdExceeded = {
                    Log.w(TAG, "Global memory threshold exceeded - performing cleanup")
                    resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.LOW)
                }
            )
            
            // Critical memory watcher
            resourceAdmin.registerMemoryWatcher(
                watcherName = "critical_memory",
                thresholdMB = 500, // Critical alert at 500MB
                onThresholdExceeded = {
                    Log.e(TAG, "Critical memory threshold exceeded - emergency cleanup")
                    resourceAdmin.emergencyCleanup()
                }
            )
            
            Log.i(TAG, "Global memory watchers registered")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register global memory watchers", e)
        }
    }
}
