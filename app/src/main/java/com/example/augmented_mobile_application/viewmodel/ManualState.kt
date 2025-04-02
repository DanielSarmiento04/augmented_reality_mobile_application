package com.example.augmented_mobile_application.viewmodel

import android.graphics.Bitmap

/**
 * Represents the different states of the manual view
 */
sealed class ManualState {
    data object Loading : ManualState()
    
    /**
     * Initial state after PDF is loaded but before any pages are rendered
     */
    data class Initialized(val pageCount: Int) : ManualState()
    
    /**
     * Success state from the previous implementation for compatibility
     */
    data class Success(val pages: List<Bitmap>) : ManualState()
    
    /**
     * State representing a single rendered page
     */
    data class PageReady(val pageIndex: Int, val page: Bitmap) : ManualState()
    
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

/**
 * Configuration options for PDF rendering
 */
data class PdfRenderConfig(
    val renderQuality: RenderQuality = RenderQuality.DISPLAY,
    val useDynamicScale: Boolean = true,
    val maxScale: Float = 3.0f
)

enum class RenderQuality {
    DISPLAY, PRINT
}
