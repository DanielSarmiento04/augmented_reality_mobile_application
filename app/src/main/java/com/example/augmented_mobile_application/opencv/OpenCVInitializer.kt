package com.example.augmented_mobile_application.opencv

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Helper class to initialize OpenCV library.
 * This provides synchronous initialization using the OpenCV library.
 */
object OpenCVInitializer {
    private const val TAG = "OpenCVInitializer"

    /**
     * Initialize OpenCV synchronously.
     * @return true if initialization was successful, false otherwise.
     */
    fun initSync(): Boolean {
        try {
            // Try to load native libraries
            System.loadLibrary("opencv_java4")
            
            // Initialize OpenCV
            val result = OpenCVLoader.initDebug()
            if (result) {
                Log.d(TAG, "OpenCV initialized successfully")
            } else {
                Log.e(TAG, "OpenCV initialization failed")
            }
            return result
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing OpenCV: ${e.message}")
            return false
        }
    }

    /**
     * Initialize OpenCV and run callback when done.
     * This is a simplified version since OpenCVLoader.initAsync isn't available.
     */
    fun initAsync(context: Context, callback: (() -> Unit)? = null) {
        // Run initialization on a background thread
        Thread {
            val success = initSync()
            if (success) {
                callback?.invoke()
            }
        }.start()
    }

    /**
     * Initialize OpenCV with context (for compatibility)
     */
    fun initialize(context: Context): Boolean {
        return initSync()
    }
}
