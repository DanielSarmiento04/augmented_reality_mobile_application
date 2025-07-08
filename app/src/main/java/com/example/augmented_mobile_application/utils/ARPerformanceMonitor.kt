package com.example.augmented_mobile_application.utils

import android.util.Log
import com.google.ar.core.Frame as ArFrame
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AR Performance Monitor for debugging and validation
 * Tracks FPS, Filament backend status, and ARCore tracking state
 */
class ARPerformanceMonitor(private val arSceneView: ARSceneView) {
    
    companion object {
        private const val TAG = "ARPerformanceMonitor"
        private const val FPS_SAMPLE_WINDOW = 60 // frames
    }
    
    // Performance metrics
    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps
    
    private val _filamentBackend = MutableStateFlow("")
    val filamentBackend: StateFlow<String> = _filamentBackend
    
    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState
    
    private val _lastFrameTime = MutableStateFlow(0L)
    val lastFrameTime: StateFlow<Long> = _lastFrameTime
    
    // FPS calculation
    private val frameTimes = mutableListOf<Long>()
    private var lastTimestamp = 0L
    
    // Timestamp validation
    private var previousFrameTimestamp = 0L
    private var timestampErrorCount = 0
    
    init {
        setupMonitoring()
    }
    
    private fun setupMonitoring() {
        try {
            // Monitor Filament backend
            val backend = arSceneView.engine.backend
            _filamentBackend.value = backend.toString()
            Log.i(TAG, "Filament backend: $backend")
            
            // Setup frame monitoring
            val originalOnFrame = arSceneView.onFrame
            arSceneView.onFrame = { frameTime ->
                // Call original handler first
                originalOnFrame?.invoke(frameTime)
                
                // Monitor performance
                monitorFrame(frameTime)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up performance monitoring: ${e.message}", e)
        }
    }
    
    private fun monitorFrame(frameTime: Long) {
        try {
            // Update frame time
            _lastFrameTime.value = frameTime
            
            // Calculate FPS
            calculateFPS(frameTime)
            
            // Monitor ARCore state
            monitorARCoreState()
            
        } catch (e: Exception) {
            // Silent monitoring - don't spam logs
            if (System.currentTimeMillis() % 30000 == 0L) {
                Log.w(TAG, "Frame monitoring error: ${e.message}")
            }
        }
    }
    
    private fun calculateFPS(frameTime: Long) {
        val currentTime = System.currentTimeMillis()
        frameTimes.add(currentTime)
        
        // Keep only recent frame times
        while (frameTimes.size > FPS_SAMPLE_WINDOW) {
            frameTimes.removeAt(0)
        }
        
        // Calculate FPS over sample window
        if (frameTimes.size >= 2) {
            val timeSpan = frameTimes.last() - frameTimes.first()
            if (timeSpan > 0) {
                val fps = (frameTimes.size - 1) * 1000f / timeSpan
                _fps.value = fps
            }
        }
    }
    
    private fun monitorARCoreState() {
        try {
            val frame = arSceneView.frame
            if (frame != null) {
                // Update tracking state
                val currentTrackingState = frame.camera.trackingState
                _trackingState.value = currentTrackingState
                
                // Validate timestamp monotonicity
                validateFrameTimestamp(frame)
            }
        } catch (e: Exception) {
            // Silent error handling
        }
    }
    
    private fun validateFrameTimestamp(frame: ArFrame) {
        val currentTimestamp = frame.timestamp
        
        if (previousFrameTimestamp > 0) {
            if (currentTimestamp <= previousFrameTimestamp) {
                timestampErrorCount++
                if (timestampErrorCount % 10 == 1) { // Log every 10th error
                    Log.w(TAG, "Timestamp order violation #$timestampErrorCount: " +
                           "$currentTimestamp <= $previousFrameTimestamp")
                }
            }
        }
        
        previousFrameTimestamp = currentTimestamp
    }
    
    /**
     * Get performance summary for logging
     */
    fun getPerformanceSummary(): String {
        return """
            |AR Performance Summary:
            |  FPS: ${"%.1f".format(_fps.value)}
            |  Filament Backend: ${_filamentBackend.value}
            |  Tracking State: ${_trackingState.value}
            |  Timestamp Errors: $timestampErrorCount
            |  Last Frame: ${_lastFrameTime.value}
        """.trimMargin()
    }
    
    /**
     * Log performance summary
     */
    fun logPerformanceSummary() {
        Log.i(TAG, getPerformanceSummary())
    }
}

/**
 * Extension function to add performance monitoring to ARSceneView
 */
fun ARSceneView.withPerformanceMonitoring(): ARPerformanceMonitor {
    return ARPerformanceMonitor(this)
}
