package com.example.augmented_mobile_application.core

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Enterprise-grade Resource Pool Manager for Android applications.
 * 
 * Provides centralized resource management with:
 * - Lifecycle-aware resource cleanup
 * - Memory pressure monitoring
 * - Automatic resource pooling and reuse
 * - Thread-safe operations
 * - Performance monitoring and metrics
 * - Configurable resource limits and TTL
 * 
 * @author Senior Android Developer
 */
class ResourcePool private constructor(
    private val context: Context,
    private val config: ResourcePoolConfig = ResourcePoolConfig()
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ResourcePool"
        private const val CLEANUP_INTERVAL_MS = 30_000L // 30 seconds
        private const val MEMORY_PRESSURE_THRESHOLD = 0.85f
        
        @Volatile
        private var INSTANCE: ResourcePool? = null
        
        /**
         * Initialize the ResourcePool singleton with application context
         */
        @MainThread
        fun initialize(context: Context, config: ResourcePoolConfig = ResourcePoolConfig()) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = ResourcePool(context.applicationContext, config)
                    }
                }
            }
        }
        
        /**
         * Get the ResourcePool instance (must be initialized first)
         */
        fun getInstance(): ResourcePool {
            return INSTANCE ?: throw IllegalStateException(
                "ResourcePool not initialized. Call initialize() first."
            )
        }
    }

    // Core components
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val resourceRegistry = ConcurrentHashMap<String, PooledResource<*>>()
    private val resourceFactories = ConcurrentHashMap<String, ResourceFactory<*>>()
    private val cleanupChannel = Channel<String>(Channel.UNLIMITED)
    
    // Metrics and monitoring
    private val _metrics = MutableStateFlow(ResourceMetrics())
    val metrics: StateFlow<ResourceMetrics> = _metrics.asStateFlow()
    
    private val isInitialized = AtomicBoolean(false)
    private val lastCleanupTime = AtomicLong(System.currentTimeMillis())
    
    // Memory management
    private val memoryPressureDetector = MemoryPressureDetector(context)
    
    init {
        startBackgroundCleanup()
        startMemoryMonitoring()
        isInitialized.set(true)
        Log.i(TAG, "ResourcePool initialized with config: $config")
    }

    /**
     * Register a resource factory for a specific resource type
     */
    @MainThread
    fun <T : Any> registerFactory(key: String, factory: ResourceFactory<T>) {
        resourceFactories[key] = factory
        Log.d(TAG, "Registered factory for resource type: $key")
    }

    /**
     * Acquire a resource from the pool or create a new one
     */
    @WorkerThread
    suspend fun <T : Any> acquireResource(
        key: String,
        factory: ResourceFactory<T>? = null
    ): ResourceHandle<T> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val existingResource = resourceRegistry[key] as? PooledResource<T>
            
            if (existingResource?.isValid() == true) {
                existingResource.incrementRefCount()
                updateMetrics { copy(cacheHits = cacheHits + 1) }
                Log.d(TAG, "Resource cache hit for key: $key")
                return@withContext ResourceHandle(key, existingResource.resource, this@ResourcePool)
            }
            
            // Resource not found or invalid, create new one
            val resourceFactory = factory ?: resourceFactories[key] as? ResourceFactory<T>
                ?: throw IllegalArgumentException("No factory registered for resource type: $key")
            
            val newResource = try {
                resourceFactory.create()
            } catch (e: Exception) {
                updateMetrics { copy(creationFailures = creationFailures + 1) }
                Log.e(TAG, "Failed to create resource for key: $key", e)
                throw ResourceCreationException("Failed to create resource: $key", e)
            }
            
            val pooledResource = PooledResource(
                resource = newResource,
                createdAt = System.currentTimeMillis(),
                ttl = config.defaultTtl,
                maxRefCount = config.maxRefCount
            ).apply { incrementRefCount() }
            
            resourceRegistry[key] = pooledResource
            updateMetrics { 
                copy(
                    cacheMisses = cacheMisses + 1,
                    totalResourcesCreated = totalResourcesCreated + 1,
                    activeResources = resourceRegistry.size
                )
            }
            
            Log.d(TAG, "Created new resource for key: $key")
            ResourceHandle(key, newResource, this@ResourcePool)
        }
    }

    /**
     * Release a resource back to the pool
     */
    @WorkerThread
    suspend fun releaseResource(key: String) {
        mutex.withLock {
            val resource = resourceRegistry[key]
            if (resource != null) {
                resource.decrementRefCount()
                Log.d(TAG, "Released resource: $key, refCount: ${resource.refCount}")
                
                if (resource.refCount <= 0 && !resource.isValid()) {
                    cleanupChannel.trySend(key)
                }
            }
        }
    }

    /**
     * Manually evict a resource from the pool
     */
    @WorkerThread
    suspend fun evictResource(key: String): Boolean = mutex.withLock {
        val resource = resourceRegistry.remove(key)
        if (resource != null) {
            safeCleanupResource(resource)
            updateMetrics { copy(activeResources = resourceRegistry.size) }
            Log.d(TAG, "Evicted resource: $key")
            true
        } else {
            false
        }
    }

    /**
     * Clear all resources from the pool
     */
    @WorkerThread
    suspend fun clearAll() {
        mutex.withLock {
            val resources = resourceRegistry.values.toList()
            resourceRegistry.clear()
            
            resources.forEach { resource ->
                safeCleanupResource(resource)
            }
            
            updateMetrics { 
                copy(
                    activeResources = 0,
                    totalClearedResources = totalClearedResources + resources.size
                )
            }
            
            Log.i(TAG, "Cleared all resources from pool (${resources.size} resources)")
        }
    }

    /**
     * Get current pool statistics
     */
    fun getPoolStats(): PoolStats {
        return PoolStats(
            totalResources = resourceRegistry.size,
            registeredFactories = resourceFactories.size,
            memoryPressure = memoryPressureDetector.getCurrentPressure(),
            lastCleanupTime = lastCleanupTime.get(),
            uptime = System.currentTimeMillis() - (metrics.value.startTime ?: 0L)
        )
    }

    // Lifecycle callbacks
    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "App moved to foreground - resuming resource management")
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, "App moved to background - triggering cleanup")
        scope.launch {
            performCleanup(aggressive = true)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        shutdown()
    }

    /**
     * Shutdown the resource pool and cleanup all resources
     */
    fun shutdown() {
        if (!isInitialized.compareAndSet(true, false)) return
        
        Log.i(TAG, "Shutting down ResourcePool")
        scope.cancel()
        
        runBlocking {
            clearAll()
        }
        
        memoryPressureDetector.stop()
        cleanupChannel.close()
        
        synchronized(ResourcePool::class.java) {
            INSTANCE = null
        }
    }

    // Private implementation methods
    
    private fun startBackgroundCleanup() {
        scope.launch {
            while (isActive) {
                try {
                    // Process cleanup requests
                    val keysToCleanup = mutableSetOf<String>()
                    while (!cleanupChannel.isEmpty) {
                        cleanupChannel.tryReceive().getOrNull()?.let { key ->
                            keysToCleanup.add(key)
                        }
                    }
                    
                    if (keysToCleanup.isNotEmpty()) {
                        performCleanup(keysToCleanup)
                    }
                    
                    // Periodic cleanup
                    if (System.currentTimeMillis() - lastCleanupTime.get() > CLEANUP_INTERVAL_MS) {
                        performCleanup()
                    }
                    
                    delay(5.seconds)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in background cleanup", e)
                }
            }
        }
    }

    private fun startMemoryMonitoring() {
        scope.launch {
            memoryPressureDetector.memoryPressure.collect { pressure ->
                if (pressure > MEMORY_PRESSURE_THRESHOLD) {
                    Log.w(TAG, "High memory pressure detected: $pressure - triggering aggressive cleanup")
                    performCleanup(aggressive = true)
                }
            }
        }
    }

    private suspend fun performCleanup(
        specificKeys: Set<String>? = null,
        aggressive: Boolean = false
    ) {
        mutex.withLock {
            val keysToRemove = mutableListOf<String>()
            val now = System.currentTimeMillis()
            
            val targetKeys = specificKeys ?: resourceRegistry.keys
            
            for (key in targetKeys) {
                val resource = resourceRegistry[key] ?: continue
                
                val shouldRemove = when {
                    specificKeys != null -> true
                    !resource.isValid() -> true
                    resource.refCount <= 0 && aggressive -> true
                    resource.refCount <= 0 && (now - resource.createdAt) > resource.ttl.inWholeMilliseconds -> true
                    else -> false
                }
                
                if (shouldRemove) {
                    keysToRemove.add(key)
                }
            }
            
            keysToRemove.forEach { key ->
                resourceRegistry.remove(key)?.let { resource ->
                    safeCleanupResource(resource)
                }
            }
            
            if (keysToRemove.isNotEmpty()) {
                updateMetrics { 
                    copy(
                        activeResources = resourceRegistry.size,
                        totalClearedResources = totalClearedResources + keysToRemove.size
                    )
                }
                Log.d(TAG, "Cleaned up ${keysToRemove.size} resources")
            }
            
            lastCleanupTime.set(now)
        }
    }

    private fun safeCleanupResource(resource: PooledResource<*>) {
        try {
            if (resource.resource is AutoCloseable) {
                resource.resource.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resource", e)
        }
    }

    private fun updateMetrics(update: ResourceMetrics.() -> ResourceMetrics) {
        _metrics.value = _metrics.value.update()
    }
}

/**
 * Configuration for ResourcePool behavior
 */
data class ResourcePoolConfig(
    val defaultTtl: Duration = 10.minutes,
    val maxRefCount: Int = 100,
    val enableMemoryPressureMonitoring: Boolean = true,
    val cleanupIntervalMs: Long = 30_000L
)

/**
 * Metrics for monitoring ResourcePool performance
 */
data class ResourceMetrics(
    val startTime: Long? = System.currentTimeMillis(),
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val totalResourcesCreated: Long = 0,
    val totalClearedResources: Long = 0,
    val creationFailures: Long = 0,
    val activeResources: Int = 0
) {
    val hitRate: Double
        get() = if (cacheHits + cacheMisses > 0) {
            cacheHits.toDouble() / (cacheHits + cacheMisses)
        } else 0.0
}

/**
 * Current pool statistics
 */
data class PoolStats(
    val totalResources: Int,
    val registeredFactories: Int,
    val memoryPressure: Float,
    val lastCleanupTime: Long,
    val uptime: Long
)

/**
 * Exception thrown when resource creation fails
 */
class ResourceCreationException(message: String, cause: Throwable? = null) : Exception(message, cause)
