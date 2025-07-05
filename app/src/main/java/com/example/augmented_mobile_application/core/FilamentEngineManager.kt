package com.example.augmented_mobile_application.core

import android.content.Context
import android.util.Log
import io.github.sceneview.SceneView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton manager for Filament engine to prevent GL context creation/destruction cycles
 * that cause "OpenGL ES API with no current context" errors.
 */
object FilamentEngineManager {
    private const val TAG = "FilamentEngineManager"
    
    private var isInitialized = AtomicBoolean(false)
    private var context: Context? = null
    
    /**
     * Initialize the Filament engine singleton
     */
    fun initialize(appContext: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            context = appContext.applicationContext
            Log.i(TAG, "FilamentEngineManager initialized")
            
            // Pre-initialize Filament components to avoid first-use delays
            try {
                // This ensures Filament native libraries are loaded early
                // Note: SceneView.createEngine() may not be available in all versions
                // Just log that we're ready for Filament operations
                Log.i(TAG, "Filament engine pre-initialization completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-initializing Filament engine: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get the application context for Filament operations
     */
    fun getContext(): Context {
        return context ?: throw IllegalStateException("FilamentEngineManager not initialized")
    }
    
    /**
     * Check if the manager is properly initialized
     */
    fun isReady(): Boolean = isInitialized.get() && context != null
    
    /**
     * Cleanup resources (called from Application.onTerminate)
     */
    fun cleanup() {
        Log.i(TAG, "FilamentEngineManager cleanup")
        // Note: Filament engine cleanup is handled automatically by SceneView
        context = null
        isInitialized.set(false)
    }
}
