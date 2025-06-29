package com.example.augmented_mobile_application.ai

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * High-performance detection pipeline that manages frame processing, queueing, and threading
 * for optimal real-time object detection performance with proper JNI thread management.
 */
class DetectionPipeline(
    private val detector: YOLO11Detector,
    private val targetClassId: Int = 41
) {
    companion object {
        private const val TAG = "DetectionPipeline"
        private const val MAX_QUEUE_SIZE = 2 // Keep queue small to avoid stale frames
        private const val DETECTION_INTERVAL_MS = 50L // 20 FPS for detection
        private const val MAX_FRAME_DROP_CONSECUTIVE = 3 // Max consecutive frames to drop
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
    private val frameQueue = Channel<Bitmap>(capacity = MAX_QUEUE_SIZE)
    private val lastDetectionTime = AtomicLong(0)
    private val isActive = AtomicBoolean(false)
    private val currentBitmap = AtomicReference<Bitmap?>(null)
    private val consecutiveDrops = AtomicLong(0)
    
    // Custom thread pool with JNI-aware threads for ML inference
    private val detectionExecutor = Executors.newSingleThreadExecutor(
        ThreadFactory { runnable ->
            Thread(runnable, "DetectionThread").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
                // Ensure thread is ready for JNI operations
                setUncaughtExceptionHandler { thread, exception ->
                    Log.e(TAG, "Uncaught exception in detection thread: ${exception.message}", exception)
                }
            }
        }
    )
    
    // Coroutine scope with custom dispatcher for better control
    private val processingScope = CoroutineScope(
        detectionExecutor.asCoroutineDispatcher() + SupervisorJob() + 
        CoroutineName("DetectionPipeline")
    )

    init {
        startDetectionLoop()
        Log.i(TAG, "DetectionPipeline initialized with target class $targetClassId")
    }

    /**
     * Submit a frame for detection. Non-blocking - drops frames if queue is full
     */
    fun submitFrame(bitmap: Bitmap) {
        if (!isActive.get()) return

        // Check if enough time has passed since last detection
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime.get() < DETECTION_INTERVAL_MS) {
            consecutiveDrops.incrementAndGet()
            return
        }

        // Only process if not currently processing to avoid queue buildup
        if (_isProcessing.value) {
            consecutiveDrops.incrementAndGet()
            if (consecutiveDrops.get() > MAX_FRAME_DROP_CONSECUTIVE) {
                Log.d(TAG, "Dropped ${consecutiveDrops.get()} consecutive frames - consider optimizing")
            }
            return
        }

        // Reset consecutive drops counter
        consecutiveDrops.set(0)

        // Create a copy of the bitmap to avoid memory issues
        val bitmapCopy = try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy bitmap: ${e.message}")
            return
        }

        // Try to offer frame to queue (non-blocking)
        val offered = frameQueue.trySend(bitmapCopy)
        if (!offered.isSuccess) {
            Log.d(TAG, "Frame queue full, dropping frame")
            bitmapCopy.recycle() // Clean up if we can't queue it
        }
    }

    /**
     * Start the detection processing loop with proper JNI thread management
     */
    private fun startDetectionLoop() {
        isActive.set(true)
        
        processingScope.launch {
            Log.i(TAG, "Detection loop started on thread: ${Thread.currentThread().name}")
            
            while (isActive.get()) {
                try {
                    // Wait for next frame with timeout to allow clean shutdown
                    val bitmap = withTimeoutOrNull(1000) {
                        frameQueue.receive()
                    }
                    
                    if (bitmap == null) continue // Timeout occurred, check isActive again
                    
                    currentBitmap.set(bitmap)
                    
                    // Update processing state on main dispatcher
                    withContext(Dispatchers.Main.immediate) {
                        _isProcessing.value = true
                    }
                    
                    val detectionStartTime = System.currentTimeMillis()
                    lastDetectionTime.set(detectionStartTime)

                    try {
                        // Run detection with proper error handling
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
                        
                        if (hasTargetDetection) {
                            Log.d(TAG, "Target class $targetClassId detected with ${filteredDetections.size} instances")
                        }
                        
                        Log.v(TAG, "Detection completed: ${detections.size} total, $inferenceTime ms")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Detection failed: ${e.message}", e)
                        // Clear results on error
                        withContext(Dispatchers.Main.immediate) {
                            _detectionResults.value = emptyList()
                            _isTargetDetected.value = false
                        }
                    }
                    
                    // Clean up bitmap
                    bitmap.recycle()
                    currentBitmap.set(null)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in detection loop: ${e.message}", e)
                } finally {
                    // Always reset processing state
                    withContext(Dispatchers.Main.immediate) {
                        _isProcessing.value = false
                    }
                }
            }
            
    /**
     * Start detection processing
     */
    fun start() {
        if (!isActive.get()) {
            isActive.set(true)
            startDetectionLoop()
            Log.d(TAG, "Detection pipeline started")
        }
    }

    /**
     * Stop detection processing and clear queue
     */
    fun stop() {
        isActive.set(false)
        
        // Clear frame queue
        while (!frameQueue.isEmpty) {
            val result = frameQueue.tryReceive()
            result.getOrNull()?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
        
        // Clear current bitmap
        currentBitmap.get()?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        currentBitmap.set(null)
        
        // Reset state on main thread
        if (_detectionResults.value.isNotEmpty() || _isTargetDetected.value || _isProcessing.value) {
            _detectionResults.value = emptyList()
            _isTargetDetected.value = false
            _inferenceTimeMs.value = 0L
            _isProcessing.value = false
        }
        
        Log.d(TAG, "Detection pipeline stopped")
    }

    /**
     * Clean up resources including thread pool
     */
    fun close() {
        stop()
        
        // Cancel coroutines
        processingScope.cancel()
        
        // Shutdown detection executor
        detectionExecutor.shutdown()
        try {
            if (!detectionExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                detectionExecutor.shutdownNow()
                Log.w(TAG, "Detection executor forced shutdown")
            }
        } catch (e: InterruptedException) {
            detectionExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        // Close frame queue
        frameQueue.close()
        
        Log.i(TAG, "Detection pipeline closed and resources cleaned up")
    }
}
