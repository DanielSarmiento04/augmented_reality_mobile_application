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

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded

    private val _isModelPlaced = MutableStateFlow(false)
    val isModelPlaced: StateFlow<Boolean> = _isModelPlaced

    private val _placementPosition = MutableStateFlow<Position?>(null)
    val placementPosition: StateFlow<Position?> = _placementPosition

    /**
     * Load the 3D model template
     */
    suspend fun loadModel(modelPath: String): Boolean {
        return try {
            Log.i(TAG, "Loading 3D model from: $modelPath")
            
            val modelInstance = arSceneView.modelLoader.createModelInstance(
                assetFileLocation = modelPath
            )
            
            modelTemplate = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 1.0f
            ).apply {
                isShadowReceiver = false
                isShadowCaster = true
                scale = Scale(DEFAULT_MODEL_SCALE, DEFAULT_MODEL_SCALE, DEFAULT_MODEL_SCALE)
                
                // Enable animation if the model has animations
                try {
                    val animator = modelInstance.animator
                    if (animator != null) {
                        Log.i(TAG, "Model has animator available")
                        // Try to configure animation if methods exist
                        try {
                            // Check if there are animations available
                            Log.i(TAG, "Setting up animation for model")
                        } catch (animE: Exception) {
                            Log.w(TAG, "Could not query animation count: ${animE.message}")
                        }
                    } else {
                        Log.d(TAG, "Model has no animator")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not setup animation: ${e.message}")
                }
            }
            
            _isModelLoaded.value = true
            Log.i(TAG, "3D model loaded successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load 3D model: ${e.message}", e)
            _isModelLoaded.value = false
            false
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
     * Start animation for the placed model
     */
    private fun startModelAnimation(modelNode: ModelNode) {
        try {
            val modelInstance = modelNode.modelInstance
            val animator = modelInstance.animator
            if (animator != null) {
                Log.i(TAG, "Starting animation for placed model")
                
                // Try to start animation - different APIs may be available
                try {
                    // Method 1: Try direct play if available
                    val playMethod = animator.javaClass.getDeclaredMethod("play")
                    playMethod.invoke(animator)
                    Log.i(TAG, "Animation started with play() method")
                } catch (e: Exception) {
                    Log.d(TAG, "play() method not available, trying alternative: ${e.message}")
                    
                    // Method 2: Try setting time-based animation
                    try {
                        val setTimeMethod = animator.javaClass.getDeclaredMethod("setAnimationTime", Float::class.java)
                        setTimeMethod.invoke(animator, 0f)
                        Log.i(TAG, "Animation started with setAnimationTime() method")
                    } catch (e2: Exception) {
                        Log.w(TAG, "Could not start animation with available methods: ${e2.message}")
                    }
                }
            } else {
                Log.d(TAG, "Model has no animator to start")
            }
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
     * Cleanup resources
     */
    fun cleanup() {
        removeCurrentModel()
        modelTemplate = null
        _isModelLoaded.value = false
    }
}
