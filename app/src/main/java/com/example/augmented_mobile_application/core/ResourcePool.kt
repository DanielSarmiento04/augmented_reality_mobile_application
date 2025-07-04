package com.example.augmented_mobile_application.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Simplified Resource Pool for Android applications
 */
class ResourcePool private constructor(
    private val context: Context,
    private val config: ResourcePoolConfig = ResourcePoolConfig()
) {
    companion object {
        private const val TAG = "ResourcePool"
        
        @Volatile
        private var INSTANCE: ResourcePool? = null
        
        fun initialize(context: Context, config: ResourcePoolConfig = ResourcePoolConfig()) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = ResourcePool(context.applicationContext, config)
                    }
                }
            }
        }
        
        fun getInstance(): ResourcePool {
            return INSTANCE ?: throw IllegalStateException("ResourcePool not initialized")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val resourceRegistry = ConcurrentHashMap<String, PooledResource<*>>()
    private val resourceFactories = ConcurrentHashMap<String, ResourceFactory<*>>()
    private val isInitialized = AtomicBoolean(false)

    init {
        isInitialized.set(true)
        Log.i(TAG, "ResourcePool initialized")
    }

    fun <T : Any> registerFactory(key: String, factory: ResourceFactory<T>) {
        resourceFactories[key] = factory
    }

    suspend fun <T : Any> acquireResource(
        key: String,
        factory: ResourceFactory<T>? = null
    ): ResourceHandle<T> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val existingResource = resourceRegistry[key] as? PooledResource<T>
            
            if (existingResource?.isValid() == true) {
                existingResource.incrementRefCount()
                Log.d(TAG, "Resource cache hit for key: $key")
                return@withContext ResourceHandle(key, existingResource.resource, this@ResourcePool)
            }
            
            val resourceFactory = factory ?: resourceFactories[key] as? ResourceFactory<T>
                ?: throw IllegalArgumentException("No factory registered for resource type: $key")
            
            val newResource = try {
                resourceFactory.create()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create resource for key: $key", e)
                throw e
            }
            
            val pooledResource = PooledResource(
                resource = newResource,
                createdAt = System.currentTimeMillis(),
                ttl = config.defaultTtl
            ).apply { incrementRefCount() }
            
            resourceRegistry[key] = pooledResource
            Log.d(TAG, "Created new resource for key: $key")
            ResourceHandle(key, newResource, this@ResourcePool)
        }
    }

    suspend fun releaseResource(key: String) {
        mutex.withLock {
            val resource = resourceRegistry[key]
            if (resource != null) {
                resource.decrementRefCount()
                Log.d(TAG, "Released resource: $key")
                
                if (resource.refCount <= 0 && !resource.isValid()) {
                    resourceRegistry.remove(key)
                    cleanupResource(resource)
                }
            }
        }
    }

    suspend fun clearAll() {
        mutex.withLock {
            val resources = resourceRegistry.values.toList()
            resourceRegistry.clear()
            
            resources.forEach { resource ->
                cleanupResource(resource)
            }
            
            Log.i(TAG, "Cleared all resources from pool")
        }
    }

    fun shutdown() {
        if (!isInitialized.compareAndSet(true, false)) return
        
        Log.i(TAG, "Shutting down ResourcePool")
        scope.cancel()
        
        runBlocking {
            clearAll()
        }
        
        synchronized(ResourcePool::class.java) {
            INSTANCE = null
        }
    }

    private fun cleanupResource(resource: PooledResource<*>) {
        try {
            if (resource.resource is AutoCloseable) {
                resource.resource.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resource", e)
        }
    }
}

/**
 * Configuration for ResourcePool
 */
data class ResourcePoolConfig(
    val defaultTtl: Duration = 10.minutes
)

/**
 * Exception for resource creation failures
 */
class ResourceCreationException(message: String, cause: Throwable? = null) : Exception(message, cause)
