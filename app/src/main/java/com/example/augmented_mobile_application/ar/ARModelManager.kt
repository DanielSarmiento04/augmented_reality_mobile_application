package com.example.augmented_mobile_application.ar

import android.content.Context
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Performance-optimized GLB model manager for AR routines
 * Handles caching, memory management, and background loading
 */
class ARModelManager(
    context: Context,
    private val arSceneView: ARSceneView
) : ARViewBridge {
    
    private val appContext = context.applicationContext
    private val modelCache = ConcurrentHashMap<String, WeakReference<ModelInstance>>()
    private val loadingJobs = ConcurrentHashMap<String, Job>()
    
    // State flows
    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    
    // Current model tracking
    private var currentModelNode: ModelNode? = null
    private var currentGlbPath: String? = null
    
    companion object {
        private const val TAG = "ARModelManager"
        private val CACHE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10) // 10 minute cache
        private const val MAX_CONCURRENT_LOADS = 1 // Limit concurrent GLB loads
    }
    
    /**
     * Load and display a GLB model with performance optimizations
     */
    override fun loadModel(glbPath: String) {
        Log.d(TAG, "Loading model: $glbPath")
        
        // Cancel any existing load for the same path
        loadingJobs[glbPath]?.cancel()
        
        // If same model is already loaded, skip
        if (currentGlbPath == glbPath && _isModelLoaded.value) {
            Log.d(TAG, "Model already loaded: $glbPath")
            return
        }
        
        // Clear current model first
        clearModel()
        
        val loadJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // Check cache first
                val cachedModel = getCachedModel(glbPath)
                val modelInstance = cachedModel ?: loadModelFromAssets(glbPath)
                
                if (modelInstance != null) {
                    // Switch to main thread for AR operations
                    withContext(Dispatchers.Main) {
                        displayModel(modelInstance, glbPath)
                    }
                } else {
                    _error.value = "Error loading 3D model"
                }
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Model loading cancelled: $glbPath")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model: $glbPath", e)
                _error.value = "Error loading 3D model: ${e.message}"
            } finally {
                _isLoading.value = false
                loadingJobs.remove(glbPath)
            }
        }
        
        loadingJobs[glbPath] = loadJob
    }
    
    /**
     * Load model from assets with proper resource management
     */
    private suspend fun loadModelFromAssets(glbPath: String): ModelInstance? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading from assets: $glbPath")
            
            // Remove file:///android_asset/ prefix if present
            val assetPath = glbPath.removePrefix("file:///android_asset/")
            
            // Load model using asset path directly - SceneView handles the loading
            val modelInstance = withContext(Dispatchers.Main) {
                arSceneView.modelLoader.loadModelInstance(assetPath)
            }
            
            // Cache the model (with weak reference to allow GC)
            if (modelInstance != null) {
                modelCache[glbPath] = WeakReference(modelInstance)
                Log.d(TAG, "Model cached: $glbPath")
            }
            
            modelInstance
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model from assets: $glbPath", e)
            null
        }
    }
    
    /**
     * Get cached model if available and not garbage collected
     */
    private fun getCachedModel(glbPath: String): ModelInstance? {
        val weakRef = modelCache[glbPath]
        val modelInstance = weakRef?.get()
        
        if (modelInstance != null) {
            Log.d(TAG, "Using cached model: $glbPath")
            return modelInstance
        } else if (weakRef != null) {
            // Weak reference exists but object was GC'd
            modelCache.remove(glbPath)
            Log.d(TAG, "Cached model was garbage collected: $glbPath")
        }
        
        return null
    }
    
    /**
     * Display model in AR scene (must be called on main thread)
     */
    private fun displayModel(modelInstance: ModelInstance, glbPath: String) {
        try {
            // Create model node
            val modelNode = ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 1.0f // Adjust scale as needed
            ).apply {
                // Set default position (will be updated on tap)
                position = io.github.sceneview.math.Position(0.0f, 0.0f, -1.0f)
            }
            
            // Add to scene
            arSceneView.addChildNode(modelNode)
            
            // Update state
            currentModelNode = modelNode
            currentGlbPath = glbPath
            _isModelLoaded.value = true
            
            Log.d(TAG, "Model displayed successfully: $glbPath")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying model: $glbPath", e)
            _error.value = "Error displaying 3D model"
        }
    }
    
    /**
     * Clear current model and reset state
     */
    override fun clearModel() {
        currentModelNode?.let { node ->
            arSceneView.removeChildNode(node)
            node.destroy()
        }
        
        currentModelNode = null
        currentGlbPath = null
        _isModelLoaded.value = false
        _error.value = null
        
        Log.d(TAG, "Model cleared")
    }
    
    /**
     * Restart YOLO detection (when routine changes)
     */
    override fun restartDetection() {
        // This would integrate with your existing YOLO detection system
        // For now, just log the action
        Log.d(TAG, "Restarting YOLO detection")
    }
    
    /**
     * Clean up resources and cancel ongoing operations
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up ARModelManager")
        
        // Cancel all loading jobs
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        
        // Clear current model
        clearModel()
        
        // Clear cache
        modelCache.clear()
        
        // Reset state
        _isLoading.value = false
        _error.value = null
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        val totalEntries = modelCache.size
        val validEntries = modelCache.values.count { it.get() != null }
        return "Cache: $validEntries/$totalEntries valid entries"
    }
    
    /**
     * Force garbage collection of cache (for memory pressure)
     */
    fun clearCache() {
        modelCache.clear()
        Log.d(TAG, "Model cache cleared")
    }
}
