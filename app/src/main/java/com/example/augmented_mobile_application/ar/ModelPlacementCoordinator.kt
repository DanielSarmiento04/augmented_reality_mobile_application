package com.example.augmented_mobile_application.ar

import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Pose
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Enhanced 3D model placement coordinator with improved positioning and lifecycle management
 */
class ModelPlacementCoordinator(
    private val arSceneView: ARSceneView
) {
    companion object {
        private const val TAG = "ModelPlacement"
        private const val DEFAULT_MODEL_SCALE = 0.3f
        private const val MODEL_HEIGHT_OFFSET = 0.05f // 5cm above surface
    }

    private var currentAnchorNode: AnchorNode? = null
    private var currentModelNode: ModelNode? = null
    private var modelTemplate: ModelNode? = null
    
    // Cache manager for optimized model loading
    private val cacheManager = ModelCacheManager.getInstance()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded

    private val _isModelPlaced = MutableStateFlow(false)
    val isModelPlaced: StateFlow<Boolean> = _isModelPlaced

    private val _placementPosition = MutableStateFlow<Position?>(null)
    val placementPosition: StateFlow<Position?> = _placementPosition

    /**
     * Load the 3D model template with enhanced GLBModelLoader
     */
    suspend fun loadModel(modelPath: String): Boolean {
        return try {
            // Clear any existing model first
            clearModel()
            
            Log.i(TAG, "Loading 3D model using GLBModelLoader: $modelPath")
            
            // Use enhanced GLB loader with proper thread management
            modelTemplate = GLBModelLoader.loadGLBModel(
                arSceneView = arSceneView,
                modelPath = modelPath,
                scale = DEFAULT_MODEL_SCALE
            )
            
            val success = modelTemplate != null
            _isModelLoaded.value = success
            
            if (success) {
                Log.i(TAG, "Model loaded successfully with GLBModelLoader: $modelPath")
            } else {
                Log.e(TAG, "Failed to load model with GLBModelLoader: $modelPath")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load 3D model from $modelPath: ${e.message}", e)
            _isModelLoaded.value = false
            false
        }
    }
    
    /**
     * Clear the current model and free resources
     */
    fun clearModel() {
        try {
            modelTemplate?.destroy()
            modelTemplate = null
            _isModelLoaded.value = false
            Log.i(TAG, "Model template cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing model: ${e.message}", e)
        }
    }

    /**
     * Place model at hit result with enhanced positioning
     */
    fun placeModelAtHitResult(hitResult: HitResult): Boolean {
        return try {
            if (modelTemplate == null) {
                Log.w(TAG, "Cannot place model - template not loaded")
                return false
            }

            // Remove any existing model
            removeCurrentModel()

            // Create anchor with enhanced positioning
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(
                engine = arSceneView.engine,
                anchor = anchor
            )

            // Clone the model template
            val modelNode = createModelInstance(modelTemplate!!)

            // Calculate enhanced position with surface offset
            val hitPose = hitResult.hitPose
            val enhancedPosition = Position(
                hitPose.translation[0],
                hitPose.translation[1] + MODEL_HEIGHT_OFFSET,
                hitPose.translation[2]
            )

            // Set positions
            anchorNode.position = enhancedPosition
            modelNode.position = enhancedPosition

            // Apply appropriate rotation based on surface normal
            val surfaceRotation = calculateSurfaceRotation(hitPose)
            modelNode.rotation = surfaceRotation

            // Add to scene
            arSceneView.scene.addEntity(anchorNode.entity)
            arSceneView.scene.addEntity(modelNode.entity)

            // Store references
            currentAnchorNode = anchorNode
            currentModelNode = modelNode

            // Update state
            _isModelPlaced.value = true
            _placementPosition.value = enhancedPosition

            // Start animation when model is placed
            startModelAnimation(modelNode)

            Log.i(TAG, "Model placed successfully at: $enhancedPosition")
            Log.i(TAG, "Surface distance: ${hitResult.distance}m")
            
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to place model: ${e.message}", e)
            false
        }
    }

    /**
     * Place model at estimated position (fallback when hit test fails)
     */
    fun placeModelAtEstimatedPosition(
        screenX: Float,
        screenY: Float,
        estimatedDistance: Float = 1.5f
    ): Boolean {
        return try {
            if (modelTemplate == null) {
                Log.w(TAG, "Cannot place model - template not loaded")
                return false
            }

            // Remove any existing model
            removeCurrentModel()

            // Convert screen coordinates to world position
            val worldPosition = screenToWorldPosition(screenX, screenY, estimatedDistance)
            
            if (worldPosition != null) {
                // Create anchor at estimated position
                val pose = Pose(worldPosition, floatArrayOf(0f, 0f, 0f, 1f))
                val anchor = arSceneView.session?.createAnchor(pose)

                if (anchor != null) {
                    val anchorNode = AnchorNode(arSceneView.engine, anchor)
                    val modelNode = createModelInstance(modelTemplate!!)

                    val position = Position(worldPosition[0], worldPosition[1], worldPosition[2])
                    anchorNode.position = position
                    modelNode.position = position

                    // Add to scene
                    arSceneView.scene.addEntity(anchorNode.entity)
                    arSceneView.scene.addEntity(modelNode.entity)

                    // Store references
                    currentAnchorNode = anchorNode
                    currentModelNode = modelNode

                    // Update state
                    _isModelPlaced.value = true
                    _placementPosition.value = position

                    // Start animation when model is placed
                    startModelAnimation(modelNode)

                    Log.i(TAG, "Model placed at estimated position: $position")
                    return true
                }
            }

            Log.w(TAG, "Failed to estimate model position")
            false

        } catch (e: Exception) {
            Log.e(TAG, "Failed to place model at estimated position: ${e.message}", e)
            false
        }
    }

    /**
     * Remove currently placed model
     */
    fun removeCurrentModel() {
        try {
            currentModelNode?.let { modelNode ->
                arSceneView.scene.removeEntity(modelNode.entity)
                Log.d(TAG, "Removed model node from scene")
            }

            currentAnchorNode?.let { anchorNode ->
                arSceneView.scene.removeEntity(anchorNode.entity)
                anchorNode.anchor.detach()
                Log.d(TAG, "Removed anchor node and detached anchor")
            }

            currentAnchorNode = null
            currentModelNode = null
            _isModelPlaced.value = false
            _placementPosition.value = null

        } catch (e: Exception) {
            Log.e(TAG, "Error removing current model: ${e.message}", e)
        }
    }

    /**
     * Get current model node for external manipulation
     */
    fun getCurrentModelNode(): ModelNode? = currentModelNode

    /**
     * Get current anchor node
     */
    fun getCurrentAnchorNode(): AnchorNode? = currentAnchorNode

    /**
     * Update model scale
     */
    fun updateModelScale(scale: Float) {
        currentModelNode?.scale = Scale(scale, scale, scale)
        Log.d(TAG, "Model scale updated to: $scale")
    }

    /**
     * Update model rotation
     */
    fun updateModelRotation(rotationY: Float) {
        currentModelNode?.rotation = Rotation(0f, rotationY, 0f)
        Log.d(TAG, "Model rotation updated to: $rotationY degrees")
    }

    /**
     * Create a new model instance from template
     */
    private fun createModelInstance(template: ModelNode): ModelNode {
        return ModelNode(
            modelInstance = template.modelInstance,
            scaleToUnits = 1.0f
        ).apply {
            isShadowReceiver = false
            isShadowCaster = true
            scale = template.scale
        }
    }

    /**
     * Setup model animation with simplified approach
     */
    private fun setupModelAnimation(modelInstance: io.github.sceneview.model.ModelInstance) {
        try {
            val animator = modelInstance.animator
            if (animator != null && animator.animationCount > 0) {
                Log.i(TAG, "Model has ${animator.animationCount} animations available")
                
                // Simply log that animations are available - let SceneView handle playback
                Log.i(TAG, "Animation setup complete - animations will auto-play")
            } else {
                Log.d(TAG, "Model has no animations")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not setup model animation: ${e.message}", e)
        }
    }

    /**
     * Start animation for the placed model using GLBModelLoader
     */
    private fun startModelAnimation(modelNode: ModelNode) {
        try {
            GLBModelLoader.startAnimations(arSceneView, modelNode)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting model animation: ${e.message}", e)
        }
    }

    /**
     * Calculate appropriate rotation based on surface normal
     */
    private fun calculateSurfaceRotation(hitPose: Pose): Rotation {
        // Extract rotation from hit pose
        val quaternion = hitPose.rotationQuaternion
        
        // Convert quaternion to Euler angles (simplified)
        val yaw = kotlin.math.atan2(
            2.0f * (quaternion[3] * quaternion[1] + quaternion[0] * quaternion[2]),
            1.0f - 2.0f * (quaternion[1] * quaternion[1] + quaternion[2] * quaternion[2])
        ) * 180.0f / kotlin.math.PI.toFloat()

        return Rotation(0f, yaw, 0f)
    }

    /**
     * Convert screen coordinates to world position
     */
    private fun screenToWorldPosition(
        screenX: Float,
        screenY: Float,
        distance: Float
    ): FloatArray? {
        return try {
            val frame = arSceneView.frame ?: return null
            val camera = frame.camera

            // Convert screen to NDC
            val ndcX = (screenX / arSceneView.width) * 2.0f - 1.0f
            val ndcY = 1.0f - (screenY / arSceneView.height) * 2.0f

            // Get camera matrices
            val viewMatrix = FloatArray(16)
            val projMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

            // Calculate world position (simplified transformation)
            val cameraPos = camera.pose.translation
            val direction = floatArrayOf(
                ndcX * distance * 0.5f,
                ndcY * distance * 0.5f,
                -distance
            )

            floatArrayOf(
                cameraPos[0] + direction[0],
                cameraPos[1] + direction[1],
                cameraPos[2] + direction[2]
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error converting screen to world position: ${e.message}", e)
            null
        }
    }

    /**
     * Cleanup resources with proper thread management
     */
    fun cleanup() {
        try {
            // Clear current model first
            removeCurrentModel()
            
            // Clear template on render thread if available
            modelTemplate?.let { template ->
                // Post cleanup to render thread
                arSceneView.post {
                    try {
                        template.destroy()
                        Log.d(TAG, "Model template destroyed on render thread")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error destroying model template: ${e.message}", e)
                    }
                }
            }
            
            modelTemplate = null
            _isModelLoaded.value = false
            
            Log.i(TAG, "ModelPlacementCoordinator cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }
}
