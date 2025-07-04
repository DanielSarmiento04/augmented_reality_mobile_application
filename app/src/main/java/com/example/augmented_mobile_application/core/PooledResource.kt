package com.example.augmented_mobile_application.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * Simplified resource wrapper
 */
class PooledResource<T : Any>(
    val resource: T,
    val createdAt: Long = System.currentTimeMillis(),
    val ttl: Duration
) {
    private val _refCount = AtomicInteger(0)
    private val mutex = Mutex()
    
    val refCount: Int get() = _refCount.get()
    
    suspend fun incrementRefCount(): Int = mutex.withLock {
        _refCount.incrementAndGet()
    }
    
    suspend fun decrementRefCount(): Int = mutex.withLock {
        val newCount = _refCount.decrementAndGet()
        if (newCount < 0) {
            _refCount.set(0)
            return 0
        }
        newCount
    }
    
    fun isValid(): Boolean {
        val now = System.currentTimeMillis()
        return (now - createdAt) <= ttl.inWholeMilliseconds
    }
}

/**
 * Resource handle for lifecycle management
 */
class ResourceHandle<T : Any>(
    val key: String,
    val resource: T,
    private val pool: ResourcePool
) : AutoCloseable {
    
    private var isClosed = false
    
    override fun close() {
        if (!isClosed) {
            isClosed = true
            // Resource will be released when reference count reaches zero
        }
    }
    
    fun isValid(): Boolean = !isClosed
}

/**
 * Factory interface for creating resources
 */
interface ResourceFactory<T : Any> {
    suspend fun create(): T
}

/**
 * Base implementation for resource factories
 */
abstract class BaseResourceFactory<T : Any> : ResourceFactory<T> {
    
    protected fun logCreation(resourceType: String) {
        android.util.Log.d("ResourceFactory", "Creating resource: $resourceType")
    }
}

/**
 * Simple bitmap factory
 */
class BitmapResourceFactory : BaseResourceFactory<android.graphics.Bitmap>() {
    override suspend fun create(): android.graphics.Bitmap {
        logCreation("Bitmap")
        return android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
    }
}

/**
 * Drawable factory
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
