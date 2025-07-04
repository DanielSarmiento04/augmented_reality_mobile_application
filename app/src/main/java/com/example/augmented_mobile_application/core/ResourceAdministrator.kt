package com.example.augmented_mobile_application.core

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * Advanced Resource Administrator for Android AR Applications
 * 
 * Provides centralized resource management with:
 * - Automatic lifecycle-aware cleanup
 * - Memory monitoring and pressure handling
 * - Resource pooling and reuse
 * - Leak detection and prevention
 * - Performance metrics and logging
 * 
 * @author Senior Android Developer
 */
class ResourceAdministrator private constructor(
    private val context: Context
) : DefaultLifecycleObserver, CoroutineScope {

    companion object {
        private const val TAG = "ResourceAdministrator"
        private const val MEMORY_THRESHOLD_WARNING = 0.75f // 75% memory usage warning
        private const val MEMORY_THRESHOLD_CRITICAL = 0.90f // 90% memory usage critical
        private const val CLEANUP_INTERVAL_MS = 30_000L // 30 seconds
        private const val RESOURCE_TIMEOUT_MS = 300_000L // 5 minutes default timeout
        
        @Volatile
        private var INSTANCE: ResourceAdministrator? = null
        
        fun getInstance(context: Context): ResourceAdministrator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceAdministrator(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        fun destroy() {
            INSTANCE?.shutdown()
            INSTANCE = null
        }
    }

    // Coroutine scope for background operations
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + supervisorJob

    // Resource tracking
    private val activeResources = ConcurrentHashMap<String, ManagedResource>()
    private val resourcePools = ConcurrentHashMap<String, ResourcePool<*>>()
    private val memoryWatchers = ConcurrentHashMap<String, MemoryWatcher>()
    
    // Performance metrics
    private val resourceCount = AtomicLong(0)
    private val totalResourcesCreated = AtomicLong(0)
    private val totalResourcesDestroyed = AtomicLong(0)
    
    // State flows for monitoring
    private val _memoryPressure = MutableStateFlow(MemoryPressure.NORMAL)
    val memoryPressure: StateFlow<MemoryPressure> = _memoryPressure.asStateFlow()
    
    private val _resourceMetrics = MutableStateFlow(ResourceMetrics())
    val resourceMetrics: StateFlow<ResourceMetrics> = _resourceMetrics.asStateFlow()

    // Background cleanup job
    private var cleanupJob: Job? = null

    init {
        startMemoryMonitoring()
        startPeriodicCleanup()
        Log.i(TAG, "ResourceAdministrator initialized")
    }

    /**
     * Register a resource for automatic management
     */
    fun <T : Closeable> registerResource(
        resourceId: String,
        resource: T,
        priority: ResourcePriority = ResourcePriority.NORMAL,
        timeoutMs: Long = RESOURCE_TIMEOUT_MS,
        onCleanup: ((T) -> Unit)? = null
    ): ManagedResourceHandle<T> {
        val managedResource = ManagedResource(
            id = resourceId,
            resource = WeakReference(resource),
            priority = priority,
            createdAt = System.currentTimeMillis(),
            timeoutMs = timeoutMs,
            onCleanup = onCleanup as? ((Any) -> Unit)
        )
        
        activeResources[resourceId] = managedResource
        resourceCount.incrementAndGet()
        totalResourcesCreated.incrementAndGet()
        
        Log.d(TAG, "Registered resource: $resourceId (Priority: $priority)")
        updateMetrics()
        
        return ManagedResourceHandle(resourceId, this)
    }

    /**
     * Create or get a resource pool for specific type
     */
    fun <T> getResourcePool(
        poolName: String,
        maxSize: Int = 10,
        factory: () -> T,
        reset: (T) -> Unit = {},
        dispose: (T) -> Unit = {}
    ): ResourcePool<T> {
        @Suppress("UNCHECKED_CAST")
        return resourcePools.getOrPut(poolName) {
            ResourcePool(poolName, maxSize, factory, reset, dispose)
        } as ResourcePool<T>
    }

    /**
     * Register a memory watcher for specific component
     */
    fun registerMemoryWatcher(
        watcherName: String,
        thresholdMB: Long,
        onThresholdExceeded: () -> Unit
    ) {
        memoryWatchers[watcherName] = MemoryWatcher(
            name = watcherName,
            thresholdBytes = thresholdMB * 1024 * 1024,
            callback = onThresholdExceeded
        )
        Log.d(TAG, "Registered memory watcher: $watcherName (Threshold: ${thresholdMB}MB)")
    }

    /**
     * Force cleanup of specific resource
     */
    fun cleanupResource(resourceId: String): Boolean {
        val resource = activeResources.remove(resourceId)
        return if (resource != null) {
            cleanupManagedResource(resource)
            resourceCount.decrementAndGet()
            totalResourcesDestroyed.incrementAndGet()
            updateMetrics()
            Log.d(TAG, "Manually cleaned up resource: $resourceId")
            true
        } else {
            Log.w(TAG, "Resource not found for cleanup: $resourceId")
            false
        }
    }

    /**
     * Force cleanup by priority level
     */
    fun cleanupByPriority(priority: ResourcePriority) {
        val resourcesToCleanup = activeResources.filter { it.value.priority == priority }
        resourcesToCleanup.forEach { (id, _) ->
            cleanupResource(id)
        }
        Log.i(TAG, "Cleaned up ${resourcesToCleanup.size} resources with priority: $priority")
    }

    /**
     * Emergency memory cleanup
     */
    fun emergencyCleanup() {
        Log.w(TAG, "Performing emergency cleanup due to memory pressure")
        
        // Cleanup low priority resources first
        cleanupByPriority(ResourcePriority.LOW)
        
        // Clear all resource pools
        resourcePools.values.forEach { it.clear() }
        
        // Force garbage collection
        System.gc()
        
        // Update memory pressure after cleanup
        checkMemoryPressure()
    }

    /**
     * Get current resource statistics
     */
    fun getResourceStats(): ResourceStats {
        return ResourceStats(
            activeResourceCount = activeResources.size,
            totalCreated = totalResourcesCreated.get(),
            totalDestroyed = totalResourcesDestroyed.get(),
            resourcePoolCount = resourcePools.size,
            memoryWatcherCount = memoryWatchers.size,
            currentMemoryPressure = _memoryPressure.value
        )
    }

    // Lifecycle Observer Methods
    override fun onDestroy(owner: LifecycleOwner) {
        shutdown()
    }

    /**
     * Shutdown the resource administrator
     */
    private fun shutdown() {
        Log.i(TAG, "Shutting down ResourceAdministrator")
        
        cleanupJob?.cancel()
        
        // Cleanup all active resources
        activeResources.values.forEach { cleanupManagedResource(it) }
        activeResources.clear()
        
        // Clear all pools
        resourcePools.values.forEach { it.clear() }
        resourcePools.clear()
        
        memoryWatchers.clear()
        
        supervisorJob.cancel()
        
        Log.i(TAG, "ResourceAdministrator shutdown complete")
    }

    // Private helper methods
    private fun startMemoryMonitoring() {
        launch {
            while (isActive) {
                checkMemoryPressure()
                checkMemoryWatchers()
                delay(5000) // Check every 5 seconds
            }
        }
    }

    private fun startPeriodicCleanup() {
        cleanupJob = launch {
            while (isActive) {
                performPeriodicCleanup()
                delay(CLEANUP_INTERVAL_MS)
            }
        }
    }

    private fun checkMemoryPressure() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()

        val newPressure = when {
            memoryUsageRatio >= MEMORY_THRESHOLD_CRITICAL -> MemoryPressure.CRITICAL
            memoryUsageRatio >= MEMORY_THRESHOLD_WARNING -> MemoryPressure.HIGH
            else -> MemoryPressure.NORMAL
        }

        if (_memoryPressure.value != newPressure) {
            _memoryPressure.value = newPressure
            Log.i(TAG, "Memory pressure changed to: $newPressure (Usage: ${(memoryUsageRatio * 100).toInt()}%)")
            
            when (newPressure) {
                MemoryPressure.HIGH -> cleanupByPriority(ResourcePriority.LOW)
                MemoryPressure.CRITICAL -> emergencyCleanup()
                else -> { /* Normal operation */ }
            }
        }
    }

    private fun checkMemoryWatchers() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        
        memoryWatchers.values.forEach { watcher ->
            if (usedMemory > watcher.thresholdBytes) {
                Log.w(TAG, "Memory threshold exceeded for watcher: ${watcher.name}")
                try {
                    watcher.callback()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in memory watcher callback: ${watcher.name}", e)
                }
            }
        }
    }

    private fun performPeriodicCleanup() {
        val currentTime = System.currentTimeMillis()
        val expiredResources = activeResources.filter { (_, resource) ->
            currentTime - resource.createdAt > resource.timeoutMs
        }

        expiredResources.forEach { (id, _) ->
            cleanupResource(id)
        }

        if (expiredResources.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${expiredResources.size} expired resources")
        }

        // Clean up resource pools
        resourcePools.values.forEach { it.cleanup() }
    }

    private fun cleanupManagedResource(managedResource: ManagedResource) {
        try {
            val resource = managedResource.resource.get()
            if (resource != null) {
                managedResource.onCleanup?.invoke(resource)
                if (resource is Closeable) {
                    resource.close()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resource: ${managedResource.id}", e)
        }
    }

    private fun updateMetrics() {
        _resourceMetrics.value = ResourceMetrics(
            activeResources = activeResources.size,
            totalCreated = totalResourcesCreated.get(),
            totalDestroyed = totalResourcesDestroyed.get(),
            resourcePools = resourcePools.size
        )
    }

    // Data classes and enums
    enum class ResourcePriority { LOW, NORMAL, HIGH, CRITICAL }
    enum class MemoryPressure { NORMAL, HIGH, CRITICAL }

    data class ManagedResource(
        val id: String,
        val resource: WeakReference<*>,
        val priority: ResourcePriority,
        val createdAt: Long,
        val timeoutMs: Long,
        val onCleanup: ((Any) -> Unit)?
    )

    data class MemoryWatcher(
        val name: String,
        val thresholdBytes: Long,
        val callback: () -> Unit
    )

    data class ResourceMetrics(
        val activeResources: Int = 0,
        val totalCreated: Long = 0,
        val totalDestroyed: Long = 0,
        val resourcePools: Int = 0
    )

    data class ResourceStats(
        val activeResourceCount: Int,
        val totalCreated: Long,
        val totalDestroyed: Long,
        val resourcePoolCount: Int,
        val memoryWatcherCount: Int,
        val currentMemoryPressure: MemoryPressure
    )
}

/**
 * Handle for managed resources that provides automatic cleanup
 */
class ManagedResourceHandle<T>(
    private val resourceId: String,
    private val administrator: ResourceAdministrator
) : AutoCloseable {
    
    override fun close() {
        administrator.cleanupResource(resourceId)
    }
}
