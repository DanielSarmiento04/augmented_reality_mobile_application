package com.example.augmented_mobile_application.ai

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-performance detection pipeline that manages frame processing
 * for optimal real-time object detection performance.
 */
class DetectionPipeline(
    private val detector: YOLO11Detector,
    private val targetClassId: Int = 41
) {
    companion object {
        private const val TAG = "DetectionPipeline"
        private const val DETECTION_INTERVAL_MS = 100L // 10 FPS for detection (reduced from 20 FPS)
    }

    // Detection state management
    private val _detectionResults = MutableStateFlow<List<YOLO11Detector.Detection>>(emptyList())
    val detectionResults: StateFlow<List<YOLO11Detector.Detection>> = _detectionResults

    private val _isTargetDetected = MutableStateFlow(false)
    val isTargetDetected: StateFlow<Boolean> = _isTargetDetected

    private val _inferenceTimeMs = MutableStateFlow(0L)
    val inferenceTimeMs: StateFlow<Long> = _inferenceTimeMs

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    // Pipeline control
    private var isActive = false
    private var lastDetectionTime = 0L
    private var detectionJob: Job? = null
    
    // CRITICAL: Use dedicated dispatcher with limited threads
    private val detectionDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val processingScope = CoroutineScope(detectionDispatcher + SupervisorJob())
    
    // Resource disposal safety flag
    private var isDisposed = false

    /**
     * Submit a frame for detection processing
     */
    fun submitFrame(bitmap: Bitmap) {
        if (!isActive || isDisposed) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL_MS) {
            bitmap.recycle() // CRITICAL: Clean up unused bitmaps
            return // Throttle detection rate
        }
        
        if (_isProcessing.value) {
            bitmap.recycle() // CRITICAL: Clean up if already processing
            return // Skip if already processing
        }
        
        // CRITICAL: Cancel any ongoing detection to prevent queue buildup
        detectionJob?.cancel()
        
        detectionJob = processingScope.launch {
            try {
                _isProcessing.value = true
                lastDetectionTime = currentTime
                
                // Additional safety check before detection
                if (isDisposed) {
                    Log.d(TAG, "Pipeline disposed, skipping detection")
                    return@launch
                }
                
                // CRITICAL: Run detection with strict timeout
                val detectionResult = withTimeoutOrNull(150L) { // 150ms timeout
                    detector.detect(bitmap)
                }
                
                if (detectionResult == null) {
                    Log.w(TAG, "Detection timed out after 150ms, skipping frame")
                    return@launch
                }
                
                val (detections, inferenceTime) = detectionResult
                
                // Filter for target class and high confidence
                val filteredDetections = detections.filter { detection ->
                    detection.classId == targetClassId && detection.conf >= 0.5f
                }
                
                val hasTargetDetection = filteredDetections.isNotEmpty()
                
                // CRITICAL: Update UI state on main dispatcher with immediate execution
                withContext(Dispatchers.Main.immediate) {
                    if (!isDisposed) { // Double-check before updating UI
                        _detectionResults.value = detections
                        _isTargetDetected.value = hasTargetDetection
                        _inferenceTimeMs.value = inferenceTime
                    }
                }
                
                lastDetectionTime = currentTime
                
                if (hasTargetDetection) {
                    Log.d(TAG, "Target class $targetClassId detected with ${filteredDetections.size} instances")
                }
                
            } catch (e: CancellationException) {
                // Expected when job is cancelled, don't log as error
                Log.d(TAG, "Detection cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed: ${e.message}", e)
                withContext(Dispatchers.Main.immediate) {
                    if (!isDisposed) {
                        _detectionResults.value = emptyList()
                        _isTargetDetected.value = false
                    }
                }
            } finally {
                _isProcessing.value = false
                // CRITICAL: Always recycle bitmap to prevent memory leaks
                try {
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Error recycling bitmap: ${e.message}")
                }
            }
        }
    }

    /**
     * Start detection processing
     */
    fun start() {
        if (!isActive) {
            isActive = true
            Log.d(TAG, "Detection pipeline started")
        }
    }

    /**
     * Stop detection processing
     */
    fun stop() {
        isActive = false
        detectionJob?.cancel()
        
        // Reset states
        _isProcessing.value = false
        _detectionResults.value = emptyList()
        _isTargetDetected.value = false
        
        Log.d(TAG, "Detection pipeline stopped")
    }

    /**
     * Close the detection pipeline and cleanup resources
     */
    fun close() {
        // Prevent double disposal
        if (isDisposed) {
            Log.d(TAG, "DetectionPipeline already disposed, skipping cleanup")
            return
        }
        
        isDisposed = true
        Log.i(TAG, "Starting DetectionPipeline disposal")
        
        stop()
        processingScope.cancel()
        
        // Dispose the detector
        try {
            detector.close()
            Log.d(TAG, "YOLO11Detector disposed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing YOLO11Detector", e)
        }
        
        Log.i(TAG, "Detection pipeline closed and resources cleaned up")
    }
}
