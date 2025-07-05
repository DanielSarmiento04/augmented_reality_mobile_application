package com.example.augmented_mobile_application.ar

import android.util.Log
import com.google.ar.core.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import com.example.augmented_mobile_application.ai.YOLO11Detector
import kotlin.math.*

/**
 * Manages 3D model placement and positioning based on 2D object detection results.
 * Handles coordinate transformation from screen space to world space.
 */
class ModelPositioningManager(
    private val arSceneView: ARSceneView
) {
    companion object {
        private const val TAG = "ModelPositioning"
        private const val DEFAULT_MODEL_SCALE = 0.1f
        private const val MIN_DISTANCE_TO_CAMERA = 0.5f
        private const val MAX_DISTANCE_TO_CAMERA = 3.0f
        private const val MODEL_HEIGHT_OFFSET = 0.05f // Slight elevation above surface
    }

    private val activeModelNodes = mutableMapOf<Int, AnchorNode>()
    private var lastFrame: Frame? = null

    /**
     * Update model positions based on current detection results
     */
    fun updateModelPositions(
        detections: List<YOLO11Detector.Detection>,
        targetClassId: Int,
        modelNode: ModelNode?
    ) {
        val frame = arSceneView.frame ?: return
        lastFrame = frame

        if (modelNode == null) {
            Log.w(TAG, "Model node is null, cannot position models")
            return
        }

        // Filter for target detections with high confidence
        val targetDetections = detections.filter { 
            it.classId == targetClassId && it.conf >= 0.5f 
        }

        if (targetDetections.isEmpty()) {
            // Remove all models if no target detected
            clearAllModels()
            return
        }

        Log.d(TAG, "Positioning ${targetDetections.size} model(s) for target class $targetClassId")

        // Position model for each detection
        targetDetections.forEachIndexed { index, detection ->
            positionModelForDetection(detection, modelNode, index)
        }

        // Clean up models that are no longer detected
        cleanupStaleModels(targetDetections.size)
    }

    /**
     * Position a 3D model at the location of a 2D detection
     */
    private fun positionModelForDetection(
        detection: YOLO11Detector.Detection,
        modelTemplate: ModelNode,
        modelIndex: Int
    ) {
        val frame = lastFrame ?: return

        try {
            // Calculate center point of detection box
            val centerX = detection.box.centerX
            val centerY = detection.box.centerY

            Log.d(TAG, "Detection center: ($centerX, $centerY), Box: ${detection.box}")

            // Get camera intrinsics for proper projection
            val camera = frame.camera
            val intrinsics = camera.textureIntrinsics

            // Convert screen coordinates to normalized device coordinates
            val ndcX = (centerX / arSceneView.width) * 2.0f - 1.0f
            val ndcY = 1.0f - (centerY / arSceneView.height) * 2.0f

            Log.d(TAG, "NDC coordinates: ($ndcX, $ndcY)")

            // Perform hit test at detection center
            val hitResults = frame.hitTest(centerX, centerY)
            
            if (hitResults.isNotEmpty()) {
                // Use the closest valid hit result
                val bestHit = hitResults.firstOrNull { hit ->
                    val pose = hit.hitPose
                    val distance = hit.distance
                    distance >= MIN_DISTANCE_TO_CAMERA && distance <= MAX_DISTANCE_TO_CAMERA
                }

                if (bestHit != null) {
                    placeModelAtHit(bestHit, modelTemplate, modelIndex, detection)
                } else {
                    Log.d(TAG, "No valid hit results within distance range")
                    // Fallback: estimate position based on detection size
                    estimateModelPosition(detection, modelTemplate, modelIndex, ndcX, ndcY)
                }
            } else {
                Log.d(TAG, "No hit test results, using estimated position")
                estimateModelPosition(detection, modelTemplate, modelIndex, ndcX, ndcY)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error positioning model for detection: ${e.message}", e)
        }
    }

    /**
     * Place model at a valid hit test result
     */
    private fun placeModelAtHit(
        hit: HitResult,
        modelTemplate: ModelNode,
        modelIndex: Int,
        detection: YOLO11Detector.Detection
    ) {
        try {
            // Create anchor at hit location
            val anchor = hit.createAnchor()
            val anchorNode = AnchorNode(arSceneView.engine, anchor)

            // Clone the model for this detection
            val modelNode = createModelInstance(modelTemplate)
            
            // Calculate scale based on detection box size
            val scale = calculateModelScale(detection)
            modelNode.scale = Scale(scale, scale, scale)

            // Add slight height offset
            val hitPose = hit.hitPose
            val offsetPose = Pose(
                floatArrayOf(
                    hitPose.translation[0],
                    hitPose.translation[1] + MODEL_HEIGHT_OFFSET,
                    hitPose.translation[2]
                ),
                hitPose.rotationQuaternion
            )

            // Position the model
            anchorNode.position = Position(
                offsetPose.translation[0],
                offsetPose.translation[1],
                offsetPose.translation[2]
            )

            // Add to scene
            arSceneView.scene.addEntity(anchorNode.entity)
            arSceneView.scene.addEntity(modelNode.entity)
            modelNode.position = anchorNode.position

            // Store reference for cleanup
            activeModelNodes[modelIndex]?.let { oldNode ->
                arSceneView.scene.removeEntity(oldNode.entity)
                oldNode.anchor?.detach()
            }
            activeModelNodes[modelIndex] = anchorNode

            Log.d(TAG, "Model positioned at: ${anchorNode.transform.position} with scale: $scale")

        } catch (e: Exception) {
            Log.e(TAG, "Error placing model at hit result: ${e.message}", e)
        }
    }

    /**
     * Estimate model position when hit test fails
     */
    private fun estimateModelPosition(
        detection: YOLO11Detector.Detection,
        modelTemplate: ModelNode,
        modelIndex: Int,
        ndcX: Float,
        ndcY: Float
    ) {
        val frame = lastFrame ?: return

        try {
            // Estimate distance based on detection box size
            val boxArea = detection.box.width.toFloat() * detection.box.height.toFloat()
            val estimatedDistance = estimateDistanceFromBoxSize(boxArea)

            // Convert NDC to world position at estimated distance
            val camera = frame.camera
            val viewMatrix = FloatArray(16)
            val projectionMatrix = FloatArray(16)
            
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            // Calculate world position
            val worldPos = ndcToWorld(ndcX, ndcY, estimatedDistance, viewMatrix, projectionMatrix)

            // Create anchor at estimated position
            val pose = Pose(worldPos, floatArrayOf(0f, 0f, 0f, 1f))
            val anchor = arSceneView.session?.createAnchor(pose)

            if (anchor != null) {
                val anchorNode = AnchorNode(arSceneView.engine, anchor)
                val modelNode = createModelInstance(modelTemplate)
                
                val scale = calculateModelScale(detection)
                modelNode.scale = Scale(scale, scale, scale)

                anchorNode.position = Position(worldPos[0], worldPos[1], worldPos[2])
                
                arSceneView.scene.addEntity(anchorNode.entity)
                arSceneView.scene.addEntity(modelNode.entity)
                modelNode.position = anchorNode.position

                activeModelNodes[modelIndex]?.let { oldNode ->
                    arSceneView.scene.removeEntity(oldNode.entity)
                    oldNode.anchor?.detach()
                }
                activeModelNodes[modelIndex] = anchorNode

                Log.d(TAG, "Model estimated at: (${worldPos[0]}, ${worldPos[1]}, ${worldPos[2]})")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error estimating model position: ${e.message}", e)
        }
    }

    /**
     * Create a new instance of the model
     */
    private fun createModelInstance(template: ModelNode): ModelNode {
        return ModelNode(
            modelInstance = template.modelInstance,
            scaleToUnits = 1.0f
        ).apply {
            isShadowReceiver = false
            isShadowCaster = true
        }
    }

    /**
     * Calculate appropriate model scale based on detection box size
     */
    private fun calculateModelScale(detection: YOLO11Detector.Detection): Float {
        val boxArea = detection.box.width.toFloat() * detection.box.height.toFloat()
        val normalizedArea = boxArea / (640f * 640f) // Assuming 640x640 input
        
        // Scale based on detection size - larger detections get larger models
        val scaleMultiplier = sqrt(normalizedArea).coerceIn(0.5f, 2.0f)
        return DEFAULT_MODEL_SCALE * scaleMultiplier
    }

    /**
     * Estimate distance based on detection box size
     */
    private fun estimateDistanceFromBoxSize(boxArea: Float): Float {
        // Larger boxes suggest closer objects
        val normalizedArea = boxArea / (640f * 640f)
        val distance = (1.0f / sqrt(normalizedArea)).coerceIn(MIN_DISTANCE_TO_CAMERA, MAX_DISTANCE_TO_CAMERA)
        return distance
    }

    /**
     * Convert NDC coordinates to world space
     */
    private fun ndcToWorld(
        ndcX: Float,
        ndcY: Float,
        distance: Float,
        viewMatrix: FloatArray,
        projMatrix: FloatArray
    ): FloatArray {
        // This is a simplified transformation - in practice you'd want to use
        // proper inverse projection and view matrix multiplication
        val cameraPos = floatArrayOf(
            viewMatrix[12],
            viewMatrix[13],
            viewMatrix[14]
        )

        val direction = floatArrayOf(
            ndcX * distance * 0.5f,
            ndcY * distance * 0.5f,
            -distance
        )

        return floatArrayOf(
            cameraPos[0] + direction[0],
            cameraPos[1] + direction[1],
            cameraPos[2] + direction[2]
        )
    }

    /**
     * Remove models that are no longer detected
     */
    private fun cleanupStaleModels(activeDetectionCount: Int) {
        val modelsToRemove = activeModelNodes.keys.filter { it >= activeDetectionCount }
        modelsToRemove.forEach { index ->
            activeModelNodes[index]?.let { node ->
                arSceneView.scene.removeEntity(node.entity)
                node.anchor?.detach()
                activeModelNodes.remove(index)
            }
        }
    }

    /**
     * Clear all active models
     */
    fun clearAllModels() {
        activeModelNodes.values.forEach { node ->
            arSceneView.scene.removeEntity(node.entity)
            node.anchor?.detach()
        }
        activeModelNodes.clear()
        Log.d(TAG, "All models cleared")
    }

    /**
     * Get count of active models
     */
    fun getActiveModelCount(): Int = activeModelNodes.size
}
