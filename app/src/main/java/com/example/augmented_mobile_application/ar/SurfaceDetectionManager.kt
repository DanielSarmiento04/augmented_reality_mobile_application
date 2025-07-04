package com.example.augmented_mobile_application.ar

import android.util.Log
import com.google.ar.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Advanced surface detection manager for optimal plane detection and model placement
 */
class SurfaceDetectionManager {
    companion object {
        private const val TAG = "SurfaceDetection"
        private const val MIN_PLANE_SIZE = 0.5f // Minimum plane size in meters
        private const val MAX_DETECTION_DISTANCE = 10.0f // Maximum detection distance
    }

    private val _detectedPlanes = MutableStateFlow<List<DetectedPlane>>(emptyList())
    val detectedPlanes: StateFlow<List<DetectedPlane>> = _detectedPlanes

    private val _isSurfaceReady = MutableStateFlow(false)
    val isSurfaceReady: StateFlow<Boolean> = _isSurfaceReady

    data class DetectedPlane(
        val plane: Plane,
        val size: Float,
        val distance: Float,
        val type: Plane.Type,
        val confidence: Float
    )

    /**
     * Update detected planes from ARCore frame
     */
    fun updatePlanes(frame: Frame) {
        try {
            val allPlanes = frame.getUpdatedTrackables(Plane::class.java)
            val camera = frame.camera
            
            if (camera.trackingState != TrackingState.TRACKING) {
                _detectedPlanes.value = emptyList()
                _isSurfaceReady.value = false
                return
            }

            val validPlanes = allPlanes
                .filter { plane ->
                    plane.trackingState == com.google.ar.core.TrackingState.TRACKING &&
                    plane.type in listOf(
                        Plane.Type.HORIZONTAL_UPWARD_FACING,
                        Plane.Type.HORIZONTAL_DOWNWARD_FACING,
                        Plane.Type.VERTICAL
                    )
                }
                .mapNotNull { plane ->
                    try {
                        val center = plane.centerPose
                        val cameraPosition = camera.pose.translation
                        val distance = calculateDistance(cameraPosition, center.translation)
                        
                        if (distance <= MAX_DETECTION_DISTANCE) {
                            val polygon = plane.polygon
                            val size = calculatePlaneSize(polygon)
                            
                            if (size >= MIN_PLANE_SIZE) {
                                DetectedPlane(
                                    plane = plane,
                                    size = size,
                                    distance = distance,
                                    type = plane.type,
                                    confidence = calculatePlaneConfidence(plane, size, distance)
                                )
                            } else null
                        } else null
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing plane: ${e.message}")
                        null
                    }
                }
                .sortedByDescending { it.confidence }

            _detectedPlanes.value = validPlanes
            _isSurfaceReady.value = validPlanes.isNotEmpty()

            Log.d(TAG, "Detected ${validPlanes.size} valid planes")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating planes: ${e.message}", e)
        }
    }

    /**
     * Find the best plane for model placement at touch coordinates
     */
    fun findBestPlaneForPlacement(
        frame: Frame,
        touchX: Float,
        touchY: Float
    ): HitResult? {
        return try {
            val hitResults = frame.hitTest(touchX, touchY)
            
            hitResults
                .filter { hit ->
                    val trackable = hit.trackable
                    trackable is Plane &&
                    trackable.trackingState == com.google.ar.core.TrackingState.TRACKING &&
                    trackable.isPoseInPolygon(hit.hitPose) &&
                    hit.distance >= 0.2f &&
                    hit.distance <= MAX_DETECTION_DISTANCE
                }
                .minByOrNull { hit ->
                    // Prioritize horizontal upward facing planes
                    val plane = hit.trackable as Plane
                    val typeScore = when (plane.type) {
                        Plane.Type.HORIZONTAL_UPWARD_FACING -> 0.0f
                        Plane.Type.HORIZONTAL_DOWNWARD_FACING -> 1.0f
                        Plane.Type.VERTICAL -> 2.0f
                        else -> 3.0f
                    }
                    // Combine type preference with distance
                    typeScore + hit.distance * 0.1f
                }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error finding best plane: ${e.message}", e)
            null
        }
    }

    /**
     * Get plane detection quality assessment
     */
    fun getDetectionQuality(): PlaneDetectionQuality {
        val planes = _detectedPlanes.value
        
        return when {
            planes.isEmpty() -> PlaneDetectionQuality.NONE
            planes.size == 1 && planes.first().confidence < 0.5f -> PlaneDetectionQuality.POOR
            planes.any { it.confidence > 0.8f } -> PlaneDetectionQuality.EXCELLENT
            planes.any { it.confidence > 0.6f } -> PlaneDetectionQuality.GOOD
            else -> PlaneDetectionQuality.FAIR
        }
    }

    /**
     * Calculate distance between two 3D points
     */
    private fun calculateDistance(point1: FloatArray, point2: FloatArray): Float {
        val dx = point1[0] - point2[0]
        val dy = point1[1] - point2[1]
        val dz = point1[2] - point2[2]
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Calculate approximate plane size from polygon vertices
     */
    private fun calculatePlaneSize(polygon: FloatBuffer): Float {
        if (polygon.remaining() < 6) return 0f // Need at least 3 vertices (6 floats)
        
        polygon.rewind()
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = Float.MIN_VALUE
        
        while (polygon.hasRemaining() && polygon.remaining() >= 2) {
            val x = polygon.get()
            val z = polygon.get()
            
            minX = kotlin.math.min(minX, x)
            maxX = kotlin.math.max(maxX, x)
            minZ = kotlin.math.min(minZ, z)
            maxZ = kotlin.math.max(maxZ, z)
        }
        
        val width = maxX - minX
        val length = maxZ - minZ
        return kotlin.math.max(width, length)
    }

    /**
     * Calculate confidence score for a plane based on various factors
     */
    private fun calculatePlaneConfidence(
        plane: Plane,
        size: Float,
        distance: Float
    ): Float {
        // Base confidence from ARCore plane tracking
        val baseConfidence = 0.7f // Assume reasonable base confidence for tracked planes
        
        // Size factor (larger planes are more reliable)
        val sizeBonus = kotlin.math.min(size / 2.0f, 0.2f) // Up to 0.2 bonus for 2m+ planes
        
        // Distance penalty (closer planes are preferred)
        val distancePenalty = kotlin.math.min(distance / MAX_DETECTION_DISTANCE, 0.1f)
        
        // Type bonus (horizontal upward facing preferred)
        val typeBonus = when (plane.type) {
            Plane.Type.HORIZONTAL_UPWARD_FACING -> 0.1f
            Plane.Type.HORIZONTAL_DOWNWARD_FACING -> 0.05f
            else -> 0.0f
        }
        
        return kotlin.math.min(1.0f, baseConfidence + sizeBonus - distancePenalty + typeBonus)
    }

    enum class PlaneDetectionQuality {
        NONE,
        POOR,
        FAIR,
        GOOD,
        EXCELLENT
    }

    /**
     * Get user-friendly guidance message based on detection quality
     */
    fun getGuidanceMessage(): String {
        return when (getDetectionQuality()) {
            PlaneDetectionQuality.NONE -> "Mueva el dispositivo lentamente para detectar superficies"
            PlaneDetectionQuality.POOR -> "Busque superficies más planas y con mejor iluminación"
            PlaneDetectionQuality.FAIR -> "Continúe moviendo el dispositivo para mejorar la detección"
            PlaneDetectionQuality.GOOD -> "Superficies detectadas - toque para colocar el modelo"
            PlaneDetectionQuality.EXCELLENT -> "Excelente detección - listo para colocar el modelo"
        }
    }

    /**
     * Reset detection state
     */
    fun reset() {
        _detectedPlanes.value = emptyList()
        _isSurfaceReady.value = false
    }
}
