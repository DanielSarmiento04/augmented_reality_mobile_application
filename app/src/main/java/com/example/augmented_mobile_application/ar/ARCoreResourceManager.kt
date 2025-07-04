package com.example.augmented_mobile_application.ar

import android.util.Log
import com.example.augmented_mobile_application.core.ResourceAdministrator
import com.google.ar.core.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Advanced ARCore Resource Manager
 * 
 * Manages ARCore-specific resources including:
 * - Anchor lifecycle management
 * - Model node cleanup
 * - Session resource optimization
 * - Memory pressure handling
 * 
 * @author Senior Android Developer
 */
class ARCoreResourceManager(
    private val arSceneView: ARSceneView,
    private val resourceAdmin: ResourceAdministrator
) {
    companion object {
        private const val TAG = "ARCoreResourceManager"
        private const val MAX_ANCHORS = 50 // Maximum anchors to keep active
        private const val MAX_MODEL_NODES = 20 // Maximum model nodes
        private const val ANCHOR_TIMEOUT_MS = 600_000L // 10 minutes
    }

    // Resource tracking
    private val activeAnchors = ConcurrentHashMap<String, ManagedAnchor>()
    private val activeModelNodes = ConcurrentHashMap<String, ManagedModelNode>()
    private val nextAnchorId = AtomicLong(0)
    private val nextModelId = AtomicLong(0)

    // Resource management handle
    private val resourceHandle = resourceAdmin.registerResource(
        resourceId = "arcore_resource_manager_${hashCode()}",
        resource = this,
        priority = ResourceAdministrator.ResourcePriority.HIGH,
        onCleanup = { cleanup() }
    )

    init {
        // Register memory watcher for ARCore resources
        resourceAdmin.registerMemoryWatcher(
            watcherName = "arcore_memory_watcher",
            thresholdMB = 150, // Alert if ARCore uses more than 150MB
            onThresholdExceeded = {
                Log.w(TAG, "ARCore memory usage high - performing cleanup")
                performMemoryOptimization()
            }
        )
        
        Log.i(TAG, "ARCore Resource Manager initialized")
    }

    /**
     * Create and manage an anchor with automatic cleanup
     */
    fun createManagedAnchor(
        pose: Pose,
        session: Session,
        priority: ResourceAdministrator.ResourcePriority = ResourceAdministrator.ResourcePriority.NORMAL
    ): String? {
        return try {
            // Check if we're at anchor limit
            if (activeAnchors.size >= MAX_ANCHORS) {
                cleanupOldestAnchors(5) // Remove 5 oldest anchors
            }

            val anchor = session.createAnchor(pose)
            val anchorId = "anchor_${nextAnchorId.incrementAndGet()}"
            
            val managedAnchor = ManagedAnchor(
                id = anchorId,
                anchor = anchor,
                createdAt = System.currentTimeMillis(),
                priority = priority
            )
            
            activeAnchors[anchorId] = managedAnchor
            
            // Register anchor with resource administrator
            resourceAdmin.registerResource(
                resourceId = anchorId,
                resource = anchor,
                priority = priority,
                timeoutMs = ANCHOR_TIMEOUT_MS,
                onCleanup = { detachAnchor(anchorId) }
            )
            
            Log.d(TAG, "Created managed anchor: $anchorId")
            anchorId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create managed anchor", e)
            null
        }
    }

    /**
     * Create and manage a model node with automatic cleanup
     */
    fun createManagedModelNode(
        anchorId: String,
        modelPath: String,
        priority: ResourceAdministrator.ResourcePriority = ResourceAdministrator.ResourcePriority.NORMAL
    ): String? {
        val managedAnchor = activeAnchors[anchorId] ?: return null
        
        return try {
            // Check if we're at model limit
            if (activeModelNodes.size >= MAX_MODEL_NODES) {
                cleanupOldestModelNodes(3) // Remove 3 oldest models
            }

            val anchorNode = AnchorNode(managedAnchor.anchor)
            val modelNode = ModelNode()
            
            val modelId = "model_${nextModelId.incrementAndGet()}"
            
            val managedModelNode = ManagedModelNode(
                id = modelId,
                modelNode = modelNode,
                anchorNode = anchorNode,
                anchorId = anchorId,
                createdAt = System.currentTimeMillis(),
                priority = priority
            )
            
            activeModelNodes[modelId] = managedModelNode
            
            // Add to scene
            anchorNode.addChildNode(modelNode)
            arSceneView.scene.addChildNode(anchorNode)
            
            // Register model with resource administrator
            resourceAdmin.registerResource(
                resourceId = modelId,
                resource = modelNode,
                priority = priority,
                onCleanup = { removeModelNode(modelId) }
            )
            
            Log.d(TAG, "Created managed model node: $modelId")
            modelId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create managed model node", e)
            null
        }
    }

    /**
     * Get anchor by ID
     */
    fun getAnchor(anchorId: String): Anchor? {
        return activeAnchors[anchorId]?.anchor
    }

    /**
     * Get model node by ID
     */
    fun getModelNode(modelId: String): ModelNode? {
        return activeModelNodes[modelId]?.modelNode
    }

    /**
     * Detach and remove an anchor
     */
    fun detachAnchor(anchorId: String): Boolean {
        val managedAnchor = activeAnchors.remove(anchorId) ?: return false
        
        try {
            // Remove associated model nodes first
            activeModelNodes.values
                .filter { it.anchorId == anchorId }
                .forEach { removeModelNode(it.id) }
            
            // Detach anchor
            managedAnchor.anchor.detach()
            Log.d(TAG, "Detached anchor: $anchorId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detach anchor: $anchorId", e)
            return false
        }
    }

    /**
     * Remove a model node
     */
    fun removeModelNode(modelId: String): Boolean {
        val managedModelNode = activeModelNodes.remove(modelId) ?: return false
        
        try {
            // Remove from scene
            arSceneView.scene.removeChildNode(managedModelNode.anchorNode)
            Log.d(TAG, "Removed model node: $modelId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove model node: $modelId", e)
            return false
        }
    }

    /**
     * Get resource statistics
     */
    fun getResourceStats(): ARCoreResourceStats {
        return ARCoreResourceStats(
            activeAnchors = activeAnchors.size,
            activeModelNodes = activeModelNodes.size,
            maxAnchors = MAX_ANCHORS,
            maxModelNodes = MAX_MODEL_NODES
        )
    }

    /**
     * Perform memory optimization during high pressure
     */
    private fun performMemoryOptimization() {
        // Remove low priority resources first
        val lowPriorityAnchors = activeAnchors.values
            .filter { it.priority == ResourceAdministrator.ResourcePriority.LOW }
            .map { it.id }
        
        lowPriorityAnchors.forEach { detachAnchor(it) }
        
        val lowPriorityModels = activeModelNodes.values
            .filter { it.priority == ResourceAdministrator.ResourcePriority.LOW }
            .map { it.id }
        
        lowPriorityModels.forEach { removeModelNode(it) }
        
        // If still over limit, remove oldest normal priority resources
        if (activeAnchors.size > MAX_ANCHORS * 0.8) {
            cleanupOldestAnchors((MAX_ANCHORS * 0.2).toInt())
        }
        
        if (activeModelNodes.size > MAX_MODEL_NODES * 0.8) {
            cleanupOldestModelNodes((MAX_MODEL_NODES * 0.2).toInt())
        }
        
        Log.i(TAG, "Memory optimization completed - Anchors: ${activeAnchors.size}, Models: ${activeModelNodes.size}")
    }

    /**
     * Cleanup oldest anchors
     */
    private fun cleanupOldestAnchors(count: Int) {
        val oldestAnchors = activeAnchors.values
            .sortedBy { it.createdAt }
            .take(count)
            .map { it.id }
        
        oldestAnchors.forEach { detachAnchor(it) }
    }

    /**
     * Cleanup oldest model nodes
     */
    private fun cleanupOldestModelNodes(count: Int) {
        val oldestModels = activeModelNodes.values
            .sortedBy { it.createdAt }
            .take(count)
            .map { it.id }
        
        oldestModels.forEach { removeModelNode(it) }
    }

    /**
     * Full cleanup of all resources
     */
    private fun cleanup() {
        Log.i(TAG, "Performing full ARCore resource cleanup")
        
        // Remove all model nodes
        activeModelNodes.keys.toList().forEach { removeModelNode(it) }
        
        // Detach all anchors
        activeAnchors.keys.toList().forEach { detachAnchor(it) }
        
        resourceHandle?.close()
    }

    // Data classes
    data class ManagedAnchor(
        val id: String,
        val anchor: Anchor,
        val createdAt: Long,
        val priority: ResourceAdministrator.ResourcePriority
    )

    data class ManagedModelNode(
        val id: String,
        val modelNode: ModelNode,
        val anchorNode: AnchorNode,
        val anchorId: String,
        val createdAt: Long,
        val priority: ResourceAdministrator.ResourcePriority
    )

    data class ARCoreResourceStats(
        val activeAnchors: Int,
        val activeModelNodes: Int,
        val maxAnchors: Int,
        val maxModelNodes: Int
    ) {
        val anchorUsageRatio: Float = activeAnchors.toFloat() / maxAnchors.toFloat()
        val modelUsageRatio: Float = activeModelNodes.toFloat() / maxModelNodes.toFloat()
    }
}
