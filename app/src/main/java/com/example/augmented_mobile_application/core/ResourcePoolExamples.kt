package com.example.augmented_mobile_application.core

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Application class integration example
 */
class ARApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize ResourcePool with custom configuration
        val config = ResourcePoolConfig(
            defaultTtl = kotlin.time.Duration.Companion.minutes(15),
            maxRefCount = 50,
            enableMemoryPressureMonitoring = true
        )
        
        ResourcePool.initialize(this, config)
        
        // Register common resource factories
        val pool = ResourcePool.getInstance()
        pool.registerFactory("bitmap", BitmapResourceFactory())
        pool.registerFactory("model_pump", ModelResourceFactory("models/pump.obj", this))
        pool.registerFactory("texture_metal", TextureResourceFactory("textures/metal.jpg"))
        
        Log.i("ARApplication", "ResourcePool initialized successfully")
    }
}

/**
 * Example ViewModel showing how to use ResourcePool
 */
class ARResourceViewModel : ViewModel() {
    
    private val resourcePool = ResourcePool.getInstance()
    
    // Observe pool metrics
    val poolMetrics = resourcePool.metrics
    
    fun loadBitmap(key: String) {
        viewModelScope.launch {
            try {
                val handle = resourcePool.acquireResource<Bitmap>(key)
                // Use the bitmap resource
                val bitmap = handle.resource
                
                // Process bitmap...
                Log.d("ARResourceViewModel", "Using bitmap: ${bitmap.width}x${bitmap.height}")
                
                // Resource will be automatically managed by the pool
                handle.close()
                
            } catch (e: Exception) {
                Log.e("ARResourceViewModel", "Failed to load bitmap: $key", e)
            }
        }
    }
    
    fun load3DModel(modelKey: String) {
        viewModelScope.launch {
            try {
                val handle = resourcePool.acquireResource<ByteArray>(modelKey)
                val modelData = handle.resource
                
                // Process 3D model data...
                Log.d("ARResourceViewModel", "Loaded 3D model: ${modelData.size} bytes")
                
                // Use model data for AR rendering...
                
                handle.close()
                
            } catch (e: Exception) {
                Log.e("ARResourceViewModel", "Failed to load 3D model: $modelKey", e)
            }
        }
    }
    
    fun getPoolStatistics(): PoolStats {
        return resourcePool.getPoolStats()
    }
}

/**
 * Composable function for displaying resource pool metrics
 */
@Composable
fun ResourcePoolMonitor() {
    val pool = remember { ResourcePool.getInstance() }
    val metrics by pool.metrics.collectAsState()
    val stats = remember { pool.getPoolStats() }
    
    LaunchedEffect(Unit) {
        // Monitor pool health
        Log.d("ResourcePoolMonitor", "Pool Stats: $stats")
        Log.d("ResourcePoolMonitor", "Hit Rate: ${(metrics.hitRate * 100).toInt()}%")
    }
    
    // UI implementation would go here
    // For example, displaying metrics in a debug panel
}

/**
 * Extension functions for easier resource management
 */
suspend inline fun <reified T : Any> ResourcePool.withResource(
    key: String,
    factory: ResourceFactory<T>? = null,
    block: (T) -> Unit
) {
    val handle = acquireResource(key, factory)
    try {
        block(handle.resource)
    } finally {
        handle.close()
        releaseResource(key)
    }
}

/**
 * Helper class for managing AR-specific resources
 */
class ARResourceManager(private val context: Context) {
    
    private val resourcePool = ResourcePool.getInstance()
    
    suspend fun loadPumpModel(): ByteArray? {
        return try {
            val handle = resourcePool.acquireResource<ByteArray>("model_pump")
            val modelData = handle.resource
            handle.close()
            resourcePool.releaseResource("model_pump")
            modelData
        } catch (e: Exception) {
            Log.e("ARResourceManager", "Failed to load pump model", e)
            null
        }
    }
    
    suspend fun loadTexture(textureName: String): ByteArray? {
        return try {
            val handle = resourcePool.acquireResource<ByteArray>("texture_$textureName")
            val textureData = handle.resource
            handle.close()
            resourcePool.releaseResource("texture_$textureName")
            textureData
        } catch (e: Exception) {
            Log.e("ARResourceManager", "Failed to load texture: $textureName", e)
            null
        }
    }
    
    suspend fun clearCache() {
        resourcePool.clearAll()
        Log.i("ARResourceManager", "Resource cache cleared")
    }
    
    fun getMemoryPressure(): Float {
        return resourcePool.getPoolStats().memoryPressure
    }
}

/**
 * Factory for creating AR-specific resources
 */
class ARModelFactory(
    private val context: Context,
    private val modelPath: String
) : BaseResourceFactory<ARModelData>() {
    
    override suspend fun create(): ARModelData {
        logCreation("ARModel")
        
        // Load model data from assets
        val modelBytes = context.assets.open(modelPath).use { it.readBytes() }
        
        // Parse model data (simplified example)
        return ARModelData(
            vertices = FloatArray(0), // Would be parsed from model file
            indices = IntArray(0),    // Would be parsed from model file
            textureCoords = FloatArray(0),
            normals = FloatArray(0),
            rawData = modelBytes
        )
    }
    
    override suspend fun cleanup(resource: ARModelData) {
        logCleanup("ARModel")
        // Cleanup OpenGL resources if needed
    }
}

/**
 * Data class for AR model resources
 */
data class ARModelData(
    val vertices: FloatArray,
    val indices: IntArray,
    val textureCoords: FloatArray,
    val normals: FloatArray,
    val rawData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ARModelData
        
        if (!vertices.contentEquals(other.vertices)) return false
        if (!indices.contentEquals(other.indices)) return false
        if (!textureCoords.contentEquals(other.textureCoords)) return false
        if (!normals.contentEquals(other.normals)) return false
        if (!rawData.contentEquals(other.rawData)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = vertices.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + textureCoords.contentHashCode()
        result = 31 * result + normals.contentHashCode()
        result = 31 * result + rawData.contentHashCode()
        return result
    }
}
