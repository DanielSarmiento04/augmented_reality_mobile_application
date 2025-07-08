package com.example.augmented_mobile_application.ar

import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.Pose
import io.github.sceneview.ar.ARSceneView
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Surface Quality Checker for AR Model Placement
 * Validates surface stability, size, and tracking quality before allowing model placement
 */
class SurfaceQualityChecker {
    
    companion object {
        private const val TAG = "SurfaceQualityChecker"
        
        // Surface quality thresholds
        private const val MIN_PLANE_SIZE = 0.15f // 15cm minimum plane size
        private const val MIN_TRACKING_CONFIDENCE = 0.7f
        private const val MAX_PLANE_NORMAL_DEVIATION = 0.3f // Max deviation from horizontal/vertical
        private const val MIN_STABLE_TRACKING_FRAMES = 10 // Frames plane must be stable
        private const val MAX_DISTANCE_FROM_CAMERA = 3.0f // Maximum 3 meters
        private const val MIN_DISTANCE_FROM_CAMERA = 0.3f // Minimum 30cm
    }
    
    data class SurfaceQuality(
        val isGoodQuality: Boolean,
        val score: Float, // 0.0 to 1.0
        val issues: List<String>,
        val plane: Plane?,
        val recommendedPosition: Pose?
    )
    
    private val planeTrackingHistory = mutableMapOf<String, PlaneTrackingInfo>()
    
    data class PlaneTrackingInfo(
        var stableFrames: Int = 0,
        var lastPose: Pose? = null,
        var lastSize: Float = 0f,
        var firstSeenTimestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Check surface quality at given screen coordinates
     */
    fun checkSurfaceQuality(
        arSceneView: ARSceneView,
        screenX: Float,
        screenY: Float
    ): SurfaceQuality {
        return try {
            val frame = arSceneView.frame
            if (frame == null) {
                return SurfaceQuality(
                    isGoodQuality = false,
                    score = 0f,
                    issues = listOf("No AR frame available"),
                    plane = null,
                    recommendedPosition = null
                )
            }
            
            if (frame.camera.trackingState != TrackingState.TRACKING) {
                return SurfaceQuality(
                    isGoodQuality = false,
                    score = 0f,
                    issues = listOf("Camera tracking not available"),
                    plane = null,
                    recommendedPosition = null
                )
            }
            
            // Perform hit test
            val hitResults = frame.hitTest(screenX, screenY)
            val planeHit = hitResults.find { hit ->
                hit.trackable is Plane && hit.trackable.trackingState == TrackingState.TRACKING
            }
            
            if (planeHit == null) {
                return SurfaceQuality(
                    isGoodQuality = false,
                    score = 0f,
                    issues = listOf("No trackable surface found at touch point"),
                    plane = null,
                    recommendedPosition = null
                )
            }
            
            val plane = planeHit.trackable as Plane
            val hitPose = planeHit.hitPose
            
            // Evaluate surface quality
            evaluatePlaneQuality(frame, plane, hitPose)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking surface quality", e)
            SurfaceQuality(
                isGoodQuality = false,
                score = 0f,
                issues = listOf("Error during surface analysis: ${e.message}"),
                plane = null,
                recommendedPosition = null
            )
        }
    }
    
    /**
     * Get overall surface quality for all detected planes
     */
    fun getOverallSurfaceQuality(arSceneView: ARSceneView): SurfaceQuality {
        return try {
            val frame = arSceneView.frame ?: return SurfaceQuality(
                isGoodQuality = false,
                score = 0f,
                issues = listOf("No AR frame available"),
                plane = null,
                recommendedPosition = null
            )
            
            val planes = frame.getUpdatedTrackables(Plane::class.java)
                .filter { it.trackingState == TrackingState.TRACKING }
            
            if (planes.isEmpty()) {
                return SurfaceQuality(
                    isGoodQuality = false,
                    score = 0f,
                    issues = listOf("No trackable surfaces detected"),
                    plane = null,
                    recommendedPosition = null
                )
            }
            
            // Find the best quality plane
            var bestPlane: Plane? = null
            var bestScore = 0f
            var bestPose: Pose? = null
            
            planes.forEach { plane ->
                val centerPose = plane.centerPose
                val quality = evaluatePlaneQuality(frame, plane, centerPose)
                if (quality.score > bestScore) {
                    bestScore = quality.score
                    bestPlane = plane
                    bestPose = centerPose
                }
            }
            
            SurfaceQuality(
                isGoodQuality = bestScore >= 0.7f,
                score = bestScore,
                issues = if (bestScore < 0.7f) listOf("Surface quality below recommended threshold") else emptyList(),
                plane = bestPlane,
                recommendedPosition = bestPose
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting overall surface quality", e)
            SurfaceQuality(
                isGoodQuality = false,
                score = 0f,
                issues = listOf("Error during surface analysis: ${e.message}"),
                plane = null,
                recommendedPosition = null
            )
        }
    }
    
    /**
     * Evaluate the quality of a specific plane
     */
    private fun evaluatePlaneQuality(frame: Frame, plane: Plane, hitPose: Pose): SurfaceQuality {
        val issues = mutableListOf<String>()
        var score = 1.0f
        
        // Check plane size
        val planeSize = calculatePlaneSize(plane)
        if (planeSize < MIN_PLANE_SIZE) {
            issues.add("Surface too small (${String.format("%.2f", planeSize)}m)")
            score -= 0.3f
        }
        
        // Check distance from camera
        val distance = calculateDistanceFromCamera(frame.camera.pose, hitPose)
        if (distance > MAX_DISTANCE_FROM_CAMERA) {
            issues.add("Surface too far (${String.format("%.1f", distance)}m)")
            score -= 0.2f
        } else if (distance < MIN_DISTANCE_FROM_CAMERA) {
            issues.add("Surface too close (${String.format("%.1f", distance)}m)")
            score -= 0.2f
        }
        
        // Check plane orientation (prefer horizontal surfaces)
        val normalDeviation = calculateNormalDeviation(plane)
        if (normalDeviation > MAX_PLANE_NORMAL_DEVIATION) {
            issues.add("Surface not level (deviation: ${String.format("%.2f", normalDeviation)})")
            score -= 0.2f
        }
        
        // Check tracking stability
        val planeId = plane.hashCode().toString()
        val trackingInfo = updatePlaneTrackingHistory(planeId, plane)
        if (trackingInfo.stableFrames < MIN_STABLE_TRACKING_FRAMES) {
            issues.add("Surface tracking not stable (${trackingInfo.stableFrames} frames)")
            score -= 0.2f
        }
        
        // Check if plane is in polygon (for hit poses)
        if (!plane.isPoseInPolygon(hitPose)) {
            issues.add("Hit point outside detected surface boundary")
            score -= 0.3f
        }
        
        // Ensure score doesn't go below 0
        score = maxOf(0f, score)
        
        Log.d(TAG, "Surface quality: score=$score, size=${String.format("%.2f", planeSize)}m, " +
                "distance=${String.format("%.1f", distance)}m, stable=${trackingInfo.stableFrames} frames")
        
        return SurfaceQuality(
            isGoodQuality = score >= 0.7f && issues.isEmpty(),
            score = score,
            issues = issues,
            plane = plane,
            recommendedPosition = hitPose
        )
    }
    
    /**
     * Calculate approximate plane size
     */
    private fun calculatePlaneSize(plane: Plane): Float {
        return try {
            val polygon = plane.polygon
            if (polygon.remaining() < 6) return 0f // Need at least 3 points (x,z pairs)
            
            // Calculate approximate area using polygon vertices
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE
            
            polygon.rewind() // Reset position to start
            while (polygon.remaining() >= 2) {
                val x = polygon.get()
                val z = polygon.get()
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minZ = minOf(minZ, z)
                maxZ = maxOf(maxZ, z)
            }
            
            val width = maxX - minX
            val height = maxZ - minZ
            minOf(width, height) // Return smaller dimension as representative size
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating plane size: ${e.message}")
            0.2f // Default reasonable size
        }
    }
    
    /**
     * Calculate distance from camera to hit pose
     */
    private fun calculateDistanceFromCamera(cameraPose: Pose, hitPose: Pose): Float {
        val cameraPos = cameraPose.translation
        val hitPos = hitPose.translation
        
        val dx = hitPos[0] - cameraPos[0]
        val dy = hitPos[1] - cameraPos[1]
        val dz = hitPos[2] - cameraPos[2]
        
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Calculate how much the plane normal deviates from horizontal
     */
    private fun calculateNormalDeviation(plane: Plane): Float {
        val centerPose = plane.centerPose
        val normal = centerPose.yAxis // Y-axis is the normal vector
        
        // For horizontal surfaces, normal should point up (0, 1, 0)
        val upVector = floatArrayOf(0f, 1f, 0f)
        
        // Calculate dot product to find angle deviation
        val dotProduct = normal[0] * upVector[0] + normal[1] * upVector[1] + normal[2] * upVector[2]
        
        // Return deviation from perfectly horizontal (1.0 = perfectly horizontal, 0.0 = perfectly vertical)
        return 1.0f - abs(dotProduct)
    }
    
    /**
     * Update tracking history for a plane
     */
    private fun updatePlaneTrackingHistory(planeId: String, plane: Plane): PlaneTrackingInfo {
        val currentPose = plane.centerPose
        val currentSize = calculatePlaneSize(plane)
        
        val trackingInfo = planeTrackingHistory.getOrPut(planeId) {
            PlaneTrackingInfo()
        }
        
        // Check if plane is stable (pose and size haven't changed much)
        val isStable = trackingInfo.lastPose?.let { lastPose ->
            val positionChange = calculateDistanceFromCamera(lastPose, currentPose)
            val sizeChange = abs(currentSize - trackingInfo.lastSize)
            positionChange < 0.05f && sizeChange < 0.02f // 5cm position, 2cm size tolerance
        } ?: false
        
        if (isStable) {
            trackingInfo.stableFrames++
        } else {
            trackingInfo.stableFrames = 0
        }
        
        trackingInfo.lastPose = currentPose
        trackingInfo.lastSize = currentSize
        
        return trackingInfo
    }
    
    /**
     * Clear old tracking history to prevent memory leaks
     */
    fun clearOldTrackingHistory() {
        val currentTime = System.currentTimeMillis()
        val iterator = planeTrackingHistory.iterator()
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val trackingInfo = entry.value
            
            // Remove entries older than 30 seconds
            if (currentTime - trackingInfo.firstSeenTimestamp > 30000) {
                iterator.remove()
            }
        }
    }
    
    /**
     * Get human-readable quality description
     */
    fun getQualityDescription(quality: SurfaceQuality): String {
        return when {
            quality.score >= 0.9f -> "Excelente"
            quality.score >= 0.7f -> "Buena"
            quality.score >= 0.5f -> "Regular"
            quality.score >= 0.3f -> "Pobre"
            else -> "Muy pobre"
        }
    }
}
