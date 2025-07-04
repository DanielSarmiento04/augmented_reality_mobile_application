package com.example.augmented_mobile_application.core

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * Wrapper for pooled resources with metadata and lifecycle management
 */
class PooledResource<T : Any>(
    val resource: T,
    val createdAt: Long = System.currentTimeMillis(),
    val ttl: Duration,
    private val maxRefCount: Int = 100
) {
    private val _refCount = AtomicInteger(0)
    private val mutex = Mutex()
    
    val refCount: Int get() = _refCount.get()
    
    suspend fun incrementRefCount(): Int = mutex.withLock {
        val newCount = _refCount.incrementAndGet()
        if (newCount > maxRefCount) {
            Log.w("PooledResource", "Reference count exceeded maximum: $newCount > $maxRefCount")
        }
        newCount
    }
    
    suspend fun decrementRefCount(): Int = mutex.withLock {
        val newCount = _refCount.decrementAndGet()
        if (newCount < 0) {
            Log.w("PooledResource", "Reference count went negative: $newCount")
            _refCount.set(0)
            return 0
        }
        newCount
    }
    
    fun isValid(): Boolean {
        val now = System.currentTimeMillis()
        val isExpired = (now - createdAt) > ttl.inWholeMilliseconds
        return !isExpired
    }
    
    fun isInUse(): Boolean = _refCount.get() > 0
}

/**
 * Handle for managing resource lifecycle
 */
class ResourceHandle<T : Any>(
    val key: String,
    val resource: T,
    private val pool: ResourcePool
) : AutoCloseable {
    
    private var isClosed = false
    
    /**
     * Release the resource back to the pool
     */
    override fun close() {
        if (!isClosed) {
            isClosed = true
            // Note: We can't use suspend function in close(), so we'll need to handle this differently
            // In a real implementation, you might want to use a different pattern for resource release
            Log.d("ResourceHandle", "Resource handle closed for key: $key")
        }
    }
    
    /**
     * Check if the handle is still valid
     */
    fun isValid(): Boolean = !isClosed
}

/**
 * Factory interface for creating resources
 */
interface ResourceFactory<T : Any> {
    /**
     * Create a new instance of the resource
     */
    suspend fun create(): T
    
    /**
     * Optional cleanup method for resources (called before disposal)
     */
    suspend fun cleanup(resource: T) {
        // Default implementation does nothing
    }
}

/**
 * Abstract base class for resource factories with common functionality
 */
abstract class BaseResourceFactory<T : Any> : ResourceFactory<T> {
    
    protected fun logCreation(resourceType: String) {
        Log.d("ResourceFactory", "Creating resource of type: $resourceType")
    }
    
    protected fun logCleanup(resourceType: String) {
        Log.d("ResourceFactory", "Cleaning up resource of type: $resourceType")
    }
}

/**
 * Concrete implementation for bitmap resources
 */
class BitmapResourceFactory : BaseResourceFactory<android.graphics.Bitmap>() {
    override suspend fun create(): android.graphics.Bitmap {
        logCreation("Bitmap")
        // Create a simple bitmap - in real implementation this would be more sophisticated
        return android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
    }
    
    override suspend fun cleanup(resource: android.graphics.Bitmap) {
        logCleanup("Bitmap")
        if (!resource.isRecycled) {
            resource.recycle()
        }
    }
}

/**
 * Factory for creating drawable resources
 */
class DrawableResourceFactory(
    private val resourceId: Int,
    private val context: android.content.Context
) : BaseResourceFactory<android.graphics.drawable.Drawable>() {
    
    override suspend fun create(): android.graphics.drawable.Drawable {
        logCreation("Drawable")
        return androidx.core.content.ContextCompat.getDrawable(context, resourceId)
            ?: throw IllegalArgumentException("Invalid resource ID: $resourceId")
    }
}

/**
 * Factory for creating texture resources for AR/3D models
 */
class TextureResourceFactory(
    private val texturePath: String
) : BaseResourceFactory<ByteArray>() {
    
    override suspend fun create(): ByteArray {
        logCreation("Texture")
        // In a real implementation, this would load texture data from file/assets
        return ByteArray(1024) // Placeholder
    }
}

/**
 * Factory for creating 3D model resources
 */
class ModelResourceFactory(
    private val modelPath: String,
    private val context: android.content.Context
) : BaseResourceFactory<ByteArray>() {
    
    override suspend fun create(): ByteArray {
        logCreation("3DModel")
        try {
            return context.assets.open(modelPath).use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            Log.e("ModelResourceFactory", "Failed to load model: $modelPath", e)
            throw e
        }
    }
}
