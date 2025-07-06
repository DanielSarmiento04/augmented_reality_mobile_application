package com.example.augmented_mobile_application.ar

import android.content.Context
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simplified ARView integration bridge for routine-based GLB loading
 */
class RoutineARBridge(
    private val context: Context
) {
    
    // State management
    private val _currentGlbPath = MutableStateFlow<String?>(null)
    val currentGlbPath: StateFlow<String?> = _currentGlbPath.asStateFlow()
    
    private val _shouldLoadModel = MutableStateFlow(false)
    val shouldLoadModel: StateFlow<Boolean> = _shouldLoadModel.asStateFlow()
    
    companion object {
        private const val TAG = "RoutineARBridge"
    }
    
    /**
     * Request to load a specific routine's GLB file
     * This will trigger the ARView to load the model when ready
     */
    fun requestModelLoad(glbPath: String) {
        Log.d(TAG, "Requesting model load: $glbPath")
        _currentGlbPath.value = glbPath
        _shouldLoadModel.value = true
    }
    
    /**
     * Clear model load request
     */
    fun clearModelRequest() {
        _shouldLoadModel.value = false
    }
    
    /**
     * Clear current model path
     */
    fun clearCurrentModel() {
        _currentGlbPath.value = null
        _shouldLoadModel.value = false
    }
    
    /**
     * Get the asset path without the file:// prefix for ARCore
     */
    fun getAssetPath(): String? {
        return _currentGlbPath.value?.removePrefix("file:///android_asset/")
    }
}

/**
 * Extension function to integrate RoutineARBridge with existing ARView
 */
fun ARSceneView.integrateRoutineBridge(bridge: RoutineARBridge): RoutineARBridge {
    // This would integrate with your existing ModelPlacementCoordinator
    // The ARView can observe bridge.shouldLoadModel and bridge.currentGlbPath
    // to load the appropriate model when requested
    return bridge
}
