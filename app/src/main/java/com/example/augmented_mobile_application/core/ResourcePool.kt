package com.example.augmented_mobile_application.core

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe Resource Pool implementation for efficient resource reuse
 * 
 * @param T The type of resource to pool
 * @param poolName Unique identifier for this pool
 * @param maxSize Maximum number of resources to keep in pool
 * @param factory Function to create new resources
 * @param reset Function to reset resource state before reuse
 * @param dispose Function to properly dispose resources
 */
class ResourcePool<T>(
    private val poolName: String,
    private val maxSize: Int,
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
    private val dispose: (T) -> Unit = {}
) {
    companion object {
        private const val TAG = "ResourcePool"
    }

    private val availableResources = ConcurrentLinkedQueue<T>()
    private val currentSize = AtomicInteger(0)
    private val totalCreated = AtomicInteger(0)
    private val totalReused = AtomicInteger(0)
    private val totalDisposed = AtomicInteger(0)

    /**
     * Acquire a resource from the pool or create a new one
     */
    fun acquire(): T {
        val resource = availableResources.poll()
        
        return if (resource != null) {
            try {
                reset(resource)
                totalReused.incrementAndGet()
                Log.d(TAG, "Reused resource from pool: $poolName")
                resource
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reset pooled resource, creating new one", e)
                createNewResource()
            }
        } else {
            createNewResource()
        }
    }

    /**
     * Return a resource to the pool for reuse
     */
    fun release(resource: T) {
        if (currentSize.get() < maxSize) {
            try {
                reset(resource)
                availableResources.offer(resource)
                Log.d(TAG, "Returned resource to pool: $poolName")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reset resource for pool return, disposing", e)
                disposeResource(resource)
            }
        } else {
            disposeResource(resource)
        }
    }

    /**
     * Clear all resources in the pool
     */
    fun clear() {
        val resourcesCleared = availableResources.size
        
        while (availableResources.isNotEmpty()) {
            val resource = availableResources.poll()
            if (resource != null) {
                disposeResource(resource)
            }
        }
        
        currentSize.set(0)
        Log.i(TAG, "Cleared $resourcesCleared resources from pool: $poolName")
    }

    /**
     * Cleanup expired or unused resources
     */
    fun cleanup() {
        // Remove excess resources if pool is over optimal size
        val optimalSize = maxSize / 2
        while (availableResources.size > optimalSize) {
            val resource = availableResources.poll()
            if (resource != null) {
                disposeResource(resource)
            }
        }
    }

    /**
     * Get pool statistics
     */
    fun getStats(): PoolStats {
        return PoolStats(
            poolName = poolName,
            availableResources = availableResources.size,
            maxSize = maxSize,
            totalCreated = totalCreated.get(),
            totalReused = totalReused.get(),
            totalDisposed = totalDisposed.get()
        )
    }

    private fun createNewResource(): T {
        val resource = factory()
        currentSize.incrementAndGet()
        totalCreated.incrementAndGet()
        Log.d(TAG, "Created new resource for pool: $poolName")
        return resource
    }

    private fun disposeResource(resource: T) {
        try {
            dispose(resource)
            currentSize.decrementAndGet()
            totalDisposed.incrementAndGet()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing resource from pool: $poolName", e)
        }
    }

    data class PoolStats(
        val poolName: String,
        val availableResources: Int,
        val maxSize: Int,
        val totalCreated: Int,
        val totalReused: Int,
        val totalDisposed: Int
    ) {
        val reuseRatio: Float = if (totalCreated > 0) totalReused.toFloat() / totalCreated.toFloat() else 0f
    }
}
