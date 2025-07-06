package com.example.augmented_mobile_application.ar

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for AR-Compose communication
 */
interface ARViewBridge {
    
    /**
     * Load and display a GLB model at the tapped position
     */
    fun loadModel(glbPath: String)
    
    /**
     * Clear current model and reset AR scene
     */
    fun clearModel()
    
    /**
     * Get current AR session state
     */
    val isModelLoaded: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<String?>
    
    /**
     * Restart YOLO detection (when routine changes)
     */
    fun restartDetection()
}

/**
 * Events from AR to UI
 */
sealed class AREvent {
    object ModelLoaded : AREvent()
    object ModelCleared : AREvent()
    data class LoadError(val message: String) : AREvent()
    data class DetectionResult(val objectsDetected: Int) : AREvent()
}
