package com.example.augmented_mobile_application.core

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.StateFlow

/**
 * Compose Extensions for Resource Administrator
 * 
 * Provides convenient Composable functions for integrating
 * resource management into Jetpack Compose UIs.
 */

/**
 * Remember a ResourceAdministrator instance tied to the lifecycle
 */
@Composable
fun rememberResourceAdministrator(): ResourceAdministrator {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val resourceAdmin = remember { 
        ResourceAdministrator.getInstance(context)
    }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Cleanup low priority resources when app is paused
                    resourceAdmin.cleanupByPriority(ResourceAdministrator.ResourcePriority.LOW)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // Perform emergency cleanup on destroy
                    resourceAdmin.emergencyCleanup()
                }
                else -> { /* No action needed */ }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    return resourceAdmin
}

/**
 * Monitor memory pressure and react accordingly
 */
@Composable
fun MemoryPressureMonitor(
    onHighPressure: () -> Unit = {},
    onCriticalPressure: () -> Unit = {}
) {
    val resourceAdmin = rememberResourceAdministrator()
    val memoryPressure by resourceAdmin.memoryPressure.collectAsState()
    
    LaunchedEffect(memoryPressure) {
        when (memoryPressure) {
            ResourceAdministrator.MemoryPressure.HIGH -> onHighPressure()
            ResourceAdministrator.MemoryPressure.CRITICAL -> onCriticalPressure()
            else -> { /* Normal pressure - no action needed */ }
        }
    }
}

/**
 * Create a managed resource that's automatically cleaned up
 */
@Composable
fun <T : AutoCloseable> rememberManagedResource(
    key: Any?,
    priority: ResourceAdministrator.ResourcePriority = ResourceAdministrator.ResourcePriority.NORMAL,
    factory: () -> T
): T? {
    val resourceAdmin = rememberResourceAdministrator()
    
    return remember(key) {
        try {
            val resource = factory()
            resourceAdmin.registerResource(
                resourceId = "compose_resource_${resource.hashCode()}",
                resource = resource,
                priority = priority
            )
            resource
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Get a resource pool for Compose components
 */
@Composable
fun <T> rememberResourcePool(
    poolName: String,
    maxSize: Int = 10,
    factory: () -> T,
    reset: (T) -> Unit = {},
    dispose: (T) -> Unit = {}
): ResourcePool<T> {
    val resourceAdmin = rememberResourceAdministrator()
    
    return remember(poolName) {
        resourceAdmin.getResourcePool(poolName, maxSize, factory, reset, dispose)
    }
}

/**
 * Display resource statistics in debug builds
 */
@Composable
fun ResourceStatistics() {
    val resourceAdmin = rememberResourceAdministrator()
    val metrics by resourceAdmin.resourceMetrics.collectAsState()
    val memoryPressure by resourceAdmin.memoryPressure.collectAsState()
    
    // Only show in debug builds
    if (com.example.augmented_mobile_application.BuildConfig.DEBUG) {
        androidx.compose.material3.Text(
            text = "Resources: ${metrics.activeResources} | " +
                   "Pools: ${metrics.resourcePools} | " +
                   "Memory: $memoryPressure",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
    }
}
