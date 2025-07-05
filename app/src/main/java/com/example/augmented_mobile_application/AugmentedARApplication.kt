package com.example.augmented_mobile_application

import android.app.Application
import android.util.Log
import com.example.augmented_mobile_application.opencv.OpenCVInitializer
import com.example.augmented_mobile_application.core.FilamentEngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AugmentedARApplication : Application() {
    
    companion object {
        private const val TAG = "AugmentedARApp"
        lateinit var instance: AugmentedARApplication
            private set
    }
    
    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "Application starting - initializing core components")
        
        // Initialize OpenCV asynchronously to prevent ANR
        applicationScope.launch {
            try {
                OpenCVInitializer.initialize(this@AugmentedARApplication)
                Log.i(TAG, "OpenCV initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "OpenCV initialization failed", e)
            }
        }
        
        // Initialize Filament engine singleton
        FilamentEngineManager.initialize(this)
        
        // Configure memory management
        configureMemoryManagement()
        
        Log.i(TAG, "Application initialization complete")
    }
    
    private fun configureMemoryManagement() {
        // Set heap size recommendations
        val runtime = Runtime.getRuntime()
        Log.i(TAG, "Memory info - Max: ${runtime.maxMemory() / 1024 / 1024}MB, " +
                "Total: ${runtime.totalMemory() / 1024 / 1024}MB, " +
                "Free: ${runtime.freeMemory() / 1024 / 1024}MB")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "Application terminating - cleaning up resources")
        
        // Clean up Filament engine
        FilamentEngineManager.cleanup()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning - forcing garbage collection")
        System.gc()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "Trimming memory at level: $level")
        
        when (level) {
            TRIM_MEMORY_UI_HIDDEN,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_COMPLETE -> {
                // Aggressive cleanup for background states
                System.gc()
                Log.i(TAG, "Aggressive memory cleanup performed")
            }
        }
    }
}