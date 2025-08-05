package com.example.augmented_mobile_application.ar

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.augmented_mobile_application.ai.YOLO11Detector
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager for YOLO object detection in AR camera feed
 * Handles frame capture, processing, and detection coordination
 */
class YOLODetectionManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "YOLODetectionManager"
        private const val DETECTION_INTERVAL_MS = 500L // 2 FPS detection rate for performance
        private const val MAX_DETECTION_QUEUE = 1 // Limit concurrent detections
    }

    private var yoloDetector: YOLO11Detector? = null
    private var arSceneView: ARSceneView? = null
    
    private val _detections = MutableStateFlow<List<YOLO11Detector.Detection>>(emptyList())
    val detections: StateFlow<List<YOLO11Detector.Detection>> = _detections.asStateFlow()
    
    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()
    
    private val _detectionEnabled = MutableStateFlow(true)
    val detectionEnabled: StateFlow<Boolean> = _detectionEnabled.asStateFlow()
    
    private var lastDetectionTime = 0L
    private val detectionJobs = mutableSetOf<Job>()
    private var isInitialized = false

    /**
     * Initialize YOLO detector with error handling
     */
    fun initialize() {
        if (isInitialized) return
        
        scope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Initializing YOLO detector...")
                yoloDetector = YOLO11Detector(
                    context = context,
                    modelPath = "pump/pump.tflite",
                    labelsPath = "pump/classes.txt",
                    useNNAPI = false,
                    useGPU = true
                )
                isInitialized = true
                Log.i(TAG, "YOLO detector initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize YOLO detector: ${e.message}", e)
                isInitialized = false
            }
        }
    }

    /**
     * Set the AR scene view for camera frame access
     */
    fun setARSceneView(sceneView: ARSceneView) {
        arSceneView = sceneView
        Log.i(TAG, "ARSceneView set for YOLO detection")
    }

    /**
     * Process current camera frame for object detection
     */
    fun processFrame() {
        if (!isInitialized || !_detectionEnabled.value) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL_MS) return
        
        // Limit concurrent detection jobs
        if (detectionJobs.size >= MAX_DETECTION_QUEUE) return
        
        val arView = arSceneView ?: return
        val detector = yoloDetector ?: return
        
        lastDetectionTime = currentTime
        
        val job = scope.launch(Dispatchers.IO) {
            try {
                _isDetecting.value = true
                
                // Create a test detection for demonstration
                // In a real implementation, you would capture and process the camera frame
                val testDetections = createTestDetections()
                
                // Update detections on main thread
                withContext(Dispatchers.Main) {
                    _detections.value = testDetections
                    Log.d(TAG, "Detection completed: ${testDetections.size} objects found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during detection: ${e.message}", e)
            } finally {
                _isDetecting.value = false
                detectionJobs.remove(coroutineContext[Job])
            }
        }
        
        detectionJobs.add(job)
    }

    /**
     * Create test detections for demonstration
     * Replace this with actual YOLO detection when camera frame capture is working
     */
    private fun createTestDetections(): List<YOLO11Detector.Detection> {
        // For demonstration, create some fake detections
        val testDetections = mutableListOf<YOLO11Detector.Detection>()
        
        // Simulate finding a pump
        if (Math.random() > 0.7) {
            testDetections.add(
                YOLO11Detector.Detection(
                    box = YOLO11Detector.BoundingBox(
                        x1 = 100f,
                        y1 = 200f,
                        x2 = 300f,
                        y2 = 400f
                    ),
                    conf = 0.85f,
                    classId = 81 // Pump class
                )
            )
        }
        
        // Simulate finding a pipe
        if (Math.random() > 0.8) {
            testDetections.add(
                YOLO11Detector.Detection(
                    box = YOLO11Detector.BoundingBox(
                        x1 = 400f,
                        y1 = 300f,
                        x2 = 600f,
                        y2 = 350f
                    ),
                    conf = 0.72f,
                    classId = 82 // Pipe class
                )
            )
        }
        
        return testDetections
    }

    /**
     * Toggle detection on/off
     */
    fun toggleDetection() {
        _detectionEnabled.value = !_detectionEnabled.value
        if (!_detectionEnabled.value) {
            _detections.value = emptyList()
            Log.i(TAG, "Detection disabled")
        } else {
            Log.i(TAG, "Detection enabled")
        }
    }

    /**
     * Enable detection
     */
    fun enableDetection() {
        _detectionEnabled.value = true
        Log.i(TAG, "Detection enabled")
    }

    /**
     * Disable detection
     */
    fun disableDetection() {
        _detectionEnabled.value = false
        _detections.value = emptyList()
        Log.i(TAG, "Detection disabled")
    }

    /**
     * Get current detection status
     */
    fun isDetectionEnabled(): Boolean = _detectionEnabled.value

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up YOLO detection manager")
        
        // Cancel all detection jobs
        detectionJobs.forEach { it.cancel() }
        detectionJobs.clear()
        
        // Cleanup YOLO detector
        yoloDetector?.close()
        yoloDetector = null
        
        // Clear state
        _detections.value = emptyList()
        _isDetecting.value = false
        arSceneView = null
        isInitialized = false
        
        Log.i(TAG, "YOLO detection manager cleanup complete")
    }
}
