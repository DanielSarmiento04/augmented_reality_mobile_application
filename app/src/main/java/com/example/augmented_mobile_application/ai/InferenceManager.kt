package com.example.augmented_mobile_application.ai

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CRITICAL: Dedicated HandlerThread-based inference manager for stable performance
 * This prevents JNI thread attachment issues and provides consistent performance
 */
class InferenceManager(private val detector: YOLO11Detector) {
    
    companion object {
        private const val TAG = "InferenceManager"
        private const val THREAD_NAME = "InferenceThread"
    }
    
    // HandlerThread for stable inference execution
    private val inferenceThread = HandlerThread(THREAD_NAME).apply { 
        start() 
        priority = Thread.NORM_PRIORITY // Normal priority to avoid blocking
    }
    private val inferenceHandler = Handler(inferenceThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // State management
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    private val _lastInferenceTime = MutableStateFlow(0L)
    val lastInferenceTime: StateFlow<Long> = _lastInferenceTime
    
    private val isDisposed = AtomicBoolean(false)
    
    // Callbacks for results
    var onDetectionResult: ((List<YOLO11Detector.Detection>, Long) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null
    
    /**
     * Submit a frame for inference processing
     */
    fun submitFrame(bitmap: Bitmap) {
        if (isDisposed.get() || _isProcessing.value) {
            bitmap.recycle()
            return
        }
        
        // Post to inference thread
        inferenceHandler.post {
            processFrame(bitmap)
        }
    }
    
    /**
     * Process frame on dedicated thread with JNI safety
     */
    private fun processFrame(bitmap: Bitmap) {
        if (isDisposed.get()) {
            bitmap.recycle()
            return
        }
        
        try {
            _isProcessing.value = true
            
            val startTime = System.currentTimeMillis()
            
            // Simplified detector call without JNI threading for now
            val detectionResult = try {
                detector.detect(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Detection failed", e)
                Pair(emptyList<YOLO11Detector.Detection>(), 0L)
            }
            
            // Extract detection results properly
            val detections = detectionResult.first
            val inferenceTime = detectionResult.second
            
            val totalTime = System.currentTimeMillis() - startTime
            
            // Update state on main thread
            mainHandler.post {
                if (!isDisposed.get()) {
                    _lastInferenceTime.value = totalTime
                    onDetectionResult?.invoke(detections, inferenceTime)
                }
            }
            
            Log.d(TAG, "Inference completed in ${totalTime}ms (inference: ${inferenceTime}ms)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            mainHandler.post {
                if (!isDisposed.get()) {
                    onError?.invoke(e)
                }
            }
        } finally {
            _isProcessing.value = false
            bitmap.recycle()
        }
    }
    
    /**
     * Cleanup resources
     */
    fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            Log.i(TAG, "Disposing InferenceManager")
            
            // Clear pending tasks
            inferenceHandler.removeCallbacksAndMessages(null)
            
            // Quit the thread
            inferenceThread.quitSafely()
            
            // Clear callbacks
            onDetectionResult = null
            onError = null
            
            Log.i(TAG, "InferenceManager disposed")
        }
    }
}
