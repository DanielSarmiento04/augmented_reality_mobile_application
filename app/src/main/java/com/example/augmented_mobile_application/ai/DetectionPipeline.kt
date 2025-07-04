package com.example.augmented_mobile_application.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.augmented_mobile_application.core.ResourceAdministrator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Enhanced high-performance detection pipeline with advanced resource management.
 * Manages frame processing for optimal real-time object detection performance
 * while ensuring proper resource cleanup and memory management.
 */
class DetectionPipeline(
    private val detector: YOLO11Detector,
    private val targetClassId: Int = 41,
    private val context: Context? = null
) : AutoCloseable {
    companion object {
        private const val TAG = "DetectionPipeline"
        private const val DETECTION_INTERVAL_MS = 50L // 20 FPS for detection
        private const val RESOURCE_TIMEOUT_MS = 300_000L // 5 minutes
    }

    // Resource management
    private val resourceAdmin = context?.let { ResourceAdministrator.getInstance(it) }
    private val resourceHandle = resourceAdmin?.registerResource(
        resourceId = "detection_pipeline_${hashCode()}",
        resource = this,
        priority = ResourceAdministrator.ResourcePriority.HIGH,
        timeoutMs = RESOURCE_TIMEOUT_MS,
        onCleanup = { close() }
    )

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
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Submit a frame for detection processing
     */
    fun submitFrame(bitmap: Bitmap) {
        if (!isActive) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL_MS) {
            return // Throttle detection rate
        }
        
        if (_isProcessing.value) {
            return // Skip if already processing
        }
        
        detectionJob?.cancel()
        detectionJob = processingScope.launch {
            try {
                _isProcessing.value = true
                
                // Run detection
                val (detections, inferenceTime) = detector.detect(bitmap)
                
                // Filter for target class and high confidence
                val filteredDetections = detections.filter { detection ->
                    detection.classId == targetClassId && detection.conf >= 0.5f
                }
                
                val hasTargetDetection = filteredDetections.isNotEmpty()
                
                // Update UI state on main dispatcher
                withContext(Dispatchers.Main.immediate) {
                    _detectionResults.value = detections
                    _isTargetDetected.value = hasTargetDetection
                    _inferenceTimeMs.value = inferenceTime
                }
                
                lastDetectionTime = currentTime
                
                if (hasTargetDetection) {
                    Log.d(TAG, "Target class $targetClassId detected with ${filteredDetections.size} instances")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed: ${e.message}", e)
                withContext(Dispatchers.Main.immediate) {
                    _detectionResults.value = emptyList()
                    _isTargetDetected.value = false
                }
            } finally {
                _isProcessing.value = false
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
        stop()
        processingScope.cancel()
        resourceHandle?.close()
        Log.i(TAG, "Detection pipeline closed and resources cleaned up")
    }

    override fun close() {
        close()
    }
}
