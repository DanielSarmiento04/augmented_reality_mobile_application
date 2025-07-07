package com.example.augmented_mobile_application.ar

import android.util.Log
import android.util.LruCache
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Cache manager for 3D models to optimize memory usage and loading performance
 * Handles large GLB files (~170MB) with memory-conscious strategies
 */
class ModelCacheManager private constructor() {
    
    companion object {
        private const val TAG = "ModelCacheManager"
        private const val MAX_CACHE_SIZE = 2 // Maximum number of models to cache
        private const val MEMORY_WARNING_THRESHOLD = 0.8f // 80% memory usage
        
        @Volatile
        private var INSTANCE: ModelCacheManager? = null
        
        fun getInstance(): ModelCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelCacheManager().also { INSTANCE = it }
            }
        }
    }
    
    // LRU cache for model instances with weak references
    private val modelCache = LruCache<String, WeakReference<ModelNode>>(MAX_CACHE_SIZE)
    
    // Track loaded models for cleanup
    private val loadedModels = mutableSetOf<String>()
    
    /**
     * Retrieves a cached model or loads it if not available
     * @param modelPath The asset path to the GLB file
     * @param loader Function to load the model if not cached
     * @return ModelNode instance or null if loading fails
     */
    suspend fun getOrLoadModel(
        modelPath: String,
        loader: suspend (String) -> ModelNode?
    ): ModelNode? = withContext(Dispatchers.IO) {
        try {
            // Check memory pressure before loading
            if (isMemoryPressureHigh()) {
                Log.w(TAG, "High memory pressure detected, clearing cache before loading model")
                clearCache()
            }
            
            // Check cache first
            modelCache.get(modelPath)?.get()?.let { cachedModel ->
                Log.d(TAG, "Returning cached model: $modelPath")
                return@withContext cachedModel
            }
            
            // Load new model
            Log.i(TAG, "Loading new model: $modelPath")
            val model = loader(modelPath)
            
            if (model != null) {
                // Cache the model with weak reference
                modelCache.put(modelPath, WeakReference(model))
                loadedModels.add(modelPath)
                Log.i(TAG, "Model cached successfully: $modelPath")
            }
            
            model
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model: $modelPath", e)
            null
        }
    }
    
    /**
     * Clears a specific model from cache and memory
     * @param modelPath The model path to clear
     */
    fun clearModel(modelPath: String) {
        try {
            modelCache.get(modelPath)?.get()?.destroy()
            modelCache.remove(modelPath)
            loadedModels.remove(modelPath)
            Log.d(TAG, "Model cleared from cache: $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing model: $modelPath", e)
        }
    }
    
    /**
     * Clears all cached models to free memory
     */
    fun clearCache() {
        try {
            // Destroy all cached models
            for (key in loadedModels.toList()) {
                modelCache.get(key)?.get()?.destroy()
            }
            
            modelCache.evictAll()
            loadedModels.clear()
            
            // Force garbage collection
            System.gc()
            
            Log.i(TAG, "Model cache cleared completely")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing model cache", e)
        }
    }
    
    /**
     * Checks if memory pressure is high
     * @return true if memory usage is above threshold
     */
    private fun isMemoryPressureHigh(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()
            
            memoryUsageRatio > MEMORY_WARNING_THRESHOLD
        } catch (e: Exception) {
            Log.e(TAG, "Error checking memory pressure", e)
            false
        }
    }
    
    /**
     * Gets current cache statistics
     * @return Map with cache statistics
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "cacheSize" to modelCache.size(),
            "maxSize" to modelCache.maxSize(),
            "loadedModels" to loadedModels.size,
            "memoryPressureHigh" to isMemoryPressureHigh()
        )
    }
    
    /**
     * Preloads a model in the background for faster access
     * @param modelPath The model path to preload
     * @param loader Function to load the model
     */
    suspend fun preloadModel(
        modelPath: String,
        loader: suspend (String) -> ModelNode?
    ) {
        withContext(Dispatchers.IO) {
            try {
                if (modelCache.get(modelPath)?.get() == null && !isMemoryPressureHigh()) {
                    Log.d(TAG, "Preloading model: $modelPath")
                    getOrLoadModel(modelPath, loader)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading model: $modelPath", e)
            }
        }
    }
}
