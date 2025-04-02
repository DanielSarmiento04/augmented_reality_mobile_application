package com.example.augmented_mobile_application.viewmodel

import android.graphics.Bitmap

/**
 * Represents the different states of the manual view
 */
sealed class ManualState {
    data object Loading : ManualState()
    data class Success(val pages: List<Bitmap>) : ManualState()
    data class Error(val message: String) : ManualState()
}

/**
 * Represents the zoom and pan state for the manual page view
 */
data class ZoomPanState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)
