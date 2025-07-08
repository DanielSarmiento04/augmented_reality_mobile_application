package com.example.augmented_mobile_application.ar

import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Surface quality checker for AR placement
 * Validates plane detection and surface stability for reliable model placement
 */
class SurfaceChecker {
    
    companion object {
        private const val TAG = "SurfaceChecker"
        
        // Surface quality thresholds
        private const val MIN_PLANE_AREA = 0.25f // 0.25 square meters minimum
        private const val MIN_POLYGON_VERTICES = 6 // Minimum vertices for stable plane
        private const val MAX_PLANE_ANGLE = 30f // Maximum angle from horizontal (degrees)
        private const val STABILITY_FRAMES = 15 // Frames to check for stability
        private const val MIN_TRACKING_CONFIDENCE = 0.7f // Minimum tracking confidence
    }
    
    data class SurfaceQuality(
        val isGoodSurface: Boolean = false,
        val area: Float = 0f,
        val stability: Float = 0f,
        val angle: Float = 0f,
        val vertexCount: Int = 0,
        val trackingState: TrackingState = TrackingState.PAUSED,
        val qualityScore: Float = 0f,
        val issues: List<String> = emptyList()
    )
    
    data class DetectedSurface(
        val plane: Plane,
        val quality: SurfaceQuality,
        val centerX: Float,
        val centerY: Float,
        val lastSeenFrame: Long
    )
    
    private val _surfaceQuality = MutableStateFlow(SurfaceQuality())
    val surfaceQuality: StateFlow<SurfaceQuality> = _surfaceQuality
    
    private val _detectedSurfaces = MutableStateFlow<List<DetectedSurface>>(emptyList())
    val detectedSurfaces: StateFlow<List<DetectedSurface>> = _detectedSurfaces
    
    private val _bestSurface = MutableStateFlow<DetectedSurface?>(null)
    val bestSurface: StateFlow<DetectedSurface?> = _bestSurface
    
    // Tracking state
    private var frameCount = 0L
    private val planeStabilityHistory = mutableMapOf<Plane, MutableList<Float>>()
    
    /**
     * Analyze current frame for surface quality
     */
    fun analyzeSurfaces(frame: Frame?) {
        frameCount++
        
        if (frame == null || frame.camera.trackingState != TrackingState.TRACKING) {
            _surfaceQuality.value = SurfaceQuality(
                trackingState = frame?.camera?.trackingState ?: TrackingState.PAUSED,
                issues = listOf("Camera not tracking")
            )
            return
        }
        
        try {
            // Get all updated planes
            val updatedPlanes = frame.getUpdatedTrackables(Plane::class.java)
            val allPlanes = updatedPlanes.filter { it.trackingState == TrackingState.TRACKING }
            
            if (allPlanes.isEmpty()) {
                _surfaceQuality.value = SurfaceQuality(
                    trackingState = TrackingState.TRACKING,
                    issues = listOf("No planes detected")
                )
                _detectedSurfaces.value = emptyList()
                _bestSurface.value = null
                return
            }
            
            // Analyze each plane
            val analyzedSurfaces = allPlanes.mapNotNull { plane ->
                analyzePlane(plane, frame)
            }
            
            // Update surfaces list
            _detectedSurfaces.value = analyzedSurfaces
            
            // Find best surface
            val bestSurface = findBestSurface(analyzedSurfaces)
            _bestSurface.value = bestSurface
            
            // Update overall quality based on best surface
            _surfaceQuality.value = bestSurface?.quality ?: SurfaceQuality(
                trackingState = TrackingState.TRACKING,
                issues = listOf("No suitable surfaces found")
            )
            
            Log.d(TAG, "Analyzed ${analyzedSurfaces.size} surfaces, best quality: ${bestSurface?.quality?.qualityScore ?: 0f}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing surfaces: ${e.message}", e)
            _surfaceQuality.value = SurfaceQuality(
                issues = listOf("Analysis error: ${e.message}")
            )
        }
    }
    
    /**
     * Analyze individual plane quality
     */
    private fun analyzePlane(plane: Plane, frame: Frame): DetectedSurface? {
        return try {
            val polygon = plane.polygon
            val centerPose = plane.centerPose
            val issues = mutableListOf<String>()
            
            // Calculate area
            val area = calculatePolygonArea(polygon)
            if (area < MIN_PLANE_AREA) {
                issues.add("Surface too small (${String.format("%.2f", area)}m²)")
            }
            
            // Check vertex count
            val vertexCount = polygon.limit() / 2 // 2D points
            if (vertexCount < MIN_POLYGON_VERTICES) {
                issues.add("Insufficient vertices ($vertexCount)")
            }
            
            // Calculate angle from horizontal
            val normal = getNormalVector(centerPose)
            val angle = calculateAngleFromHorizontal(normal)
            if (abs(angle) > MAX_PLANE_ANGLE) {
                issues.add("Surface too steep (${String.format("%.1f", abs(angle))}°)")
            }
            
            // Check stability
            val stability = calculateStability(plane)
            if (stability < MIN_TRACKING_CONFIDENCE) {
                issues.add("Surface unstable (${String.format("%.2f", stability)})")
            }
            
            // Calculate quality score (0-1)
            val areaScore = minOf(area / (MIN_PLANE_AREA * 4), 1f) // Max score at 4x min area
            val vertexScore = minOf(vertexCount.toFloat() / MIN_POLYGON_VERTICES, 1f)
            val angleScore = maxOf(0f, 1f - (abs(angle) / MAX_PLANE_ANGLE))
            val stabilityScore = stability
            
            val qualityScore = (areaScore * 0.3f + vertexScore * 0.2f + angleScore * 0.2f + stabilityScore * 0.3f)
            
            val isGoodSurface = issues.isEmpty() && qualityScore > 0.7f
            
            // Calculate screen position of plane center
            val screenPos = worldToScreenPosition(centerPose, frame)
            
            DetectedSurface(
                plane = plane,
                quality = SurfaceQuality(
                    isGoodSurface = isGoodSurface,
                    area = area,
                    stability = stability,
                    angle = angle,
                    vertexCount = vertexCount,
                    trackingState = plane.trackingState,
                    qualityScore = qualityScore,
                    issues = issues
                ),
                centerX = screenPos?.first ?: 0f,
                centerY = screenPos?.second ?: 0f,
                lastSeenFrame = frameCount
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Error analyzing plane: ${e.message}")
            null
        }
    }
    
    /**
     * Calculate polygon area using shoelace formula
     */
    private fun calculatePolygonArea(polygon: java.nio.FloatBuffer): Float {
        polygon.rewind()
        var area = 0f
        val points = mutableListOf<Pair<Float, Float>>()
        
        while (polygon.hasRemaining()) {
            val x = polygon.get()
            val z = polygon.get() // Note: ARCore uses X-Z plane for ground
            points.add(Pair(x, z))
        }
        
        if (points.size < 3) return 0f
        
        // Shoelace formula
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].first * points[j].second
            area -= points[j].first * points[i].second
        }
        
        return abs(area) / 2f
    }
    
    /**
     * Get normal vector from pose
     */
    private fun getNormalVector(pose: com.google.ar.core.Pose): FloatArray {
        val normalMatrix = FloatArray(16)
        pose.toMatrix(normalMatrix, 0)
        // Y-axis is the normal vector
        return floatArrayOf(normalMatrix[4], normalMatrix[5], normalMatrix[6])
    }
    
    /**
     * Calculate angle from horizontal plane
     */
    private fun calculateAngleFromHorizontal(normal: FloatArray): Float {
        val upVector = floatArrayOf(0f, 1f, 0f)
        val dot = normal[0] * upVector[0] + normal[1] * upVector[1] + normal[2] * upVector[2]
        val angle = Math.acos(dot.toDouble()) * 180.0 / Math.PI
        return (angle - 90.0).toFloat() // Convert to angle from horizontal
    }
    
    /**
     * Calculate plane stability based on tracking history
     */
    private fun calculateStability(plane: Plane): Float {
        val currentArea = calculatePolygonArea(plane.polygon)
        
        // Get or create history for this plane
        val history = planeStabilityHistory.getOrPut(plane) { mutableListOf() }
        history.add(currentArea)
        
        // Keep only recent history
        if (history.size > STABILITY_FRAMES) {
            history.removeAt(0)
        }
        
        if (history.size < 3) return 0.5f // Not enough data
        
        // Calculate variance in area
        val mean = history.average().toFloat()
        val variance = history.map { (it - mean) * (it - mean) }.average().toFloat()
        val stability = maxOf(0f, 1f - (variance / (mean * mean + 0.01f))) // Coefficient of variation
        
        return minOf(1f, stability)
    }
    
    /**
     * Convert world position to screen coordinates
     */
    private fun worldToScreenPosition(
        pose: com.google.ar.core.Pose, 
        frame: Frame
    ): Pair<Float, Float>? {
        return try {
            val viewMatrix = FloatArray(16)
            val projMatrix = FloatArray(16)
            frame.camera.getViewMatrix(viewMatrix, 0)
            frame.camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)
            
            // Simple projection - this is a basic implementation
            val worldPos = pose.translation
            val x = worldPos[0] * 100f + 500f // Rough screen mapping
            val y = worldPos[2] * 100f + 500f
            
            Pair(x, y)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Find the best surface for placement
     */
    private fun findBestSurface(surfaces: List<DetectedSurface>): DetectedSurface? {
        return surfaces
            .filter { it.quality.isGoodSurface }
            .maxByOrNull { it.quality.qualityScore }
            ?: surfaces.maxByOrNull { it.quality.qualityScore }
    }
    
    /**
     * Check if a specific point is on a good surface
     */
    fun isPointOnGoodSurface(screenX: Float, screenY: Float): Boolean {
        val bestSurface = _bestSurface.value ?: return false
        if (!bestSurface.quality.isGoodSurface) return false
        
        // Simple distance check to surface center
        val distance = kotlin.math.sqrt(
            (screenX - bestSurface.centerX) * (screenX - bestSurface.centerX) +
            (screenY - bestSurface.centerY) * (screenY - bestSurface.centerY)
        )
        
        return distance < 200f // 200 pixel radius
    }
    
    /**
     * Get surface quality description for UI
     */
    fun getSurfaceQualityDescription(): String {
        val quality = _surfaceQuality.value
        
        return when {
            quality.trackingState != TrackingState.TRACKING -> "Cámara no está rastreando"
            quality.issues.isNotEmpty() -> quality.issues.first()
            quality.isGoodSurface -> "Superficie excelente para colocación"
            quality.qualityScore > 0.5f -> "Superficie aceptable"
            else -> "Busque una superficie más estable y plana"
        }
    }
    
    /**
     * Clear tracking history
     */
    fun clearHistory() {
        planeStabilityHistory.clear()
        frameCount = 0
        _surfaceQuality.value = SurfaceQuality()
        _detectedSurfaces.value = emptyList()
        _bestSurface.value = null
    }
}
