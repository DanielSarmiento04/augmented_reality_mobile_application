package com.example.augmented_mobile_application.ai

import android.content.Context
import android.os.Build
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate

/**
 * Optimized TensorFlow Lite configuration for maximum performance on mobile devices.
 * This class handles delegate selection, performance tuning, and fallback strategies.
 */
object TensorFlowLiteOptimizer {
    private const val TAG = "TFLiteOptimizer"
    
    data class PerformanceConfig(
        val useGPU: Boolean,
        val useNNAPI: Boolean,
        val numThreads: Int,
        val useXNNPACK: Boolean,
        val allowFp16: Boolean
    )
    
    /**
     * Get optimal configuration for the current device
     */
    fun getOptimalConfig(context: Context): PerformanceConfig {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val isLowEndDevice = isLowEndDevice()
        
        return PerformanceConfig(
            useGPU = shouldUseGPU(),
            useNNAPI = shouldUseNNAPI(),
            numThreads = getOptimalThreadCount(cpuCores, isLowEndDevice),
            useXNNPACK = true, // Generally beneficial
            allowFp16 = true   // Reduces memory usage
        )
    }
    
    /**
     * Configure TensorFlow Lite interpreter with optimal settings
     */
    fun configureInterpreter(config: PerformanceConfig): Interpreter.Options {
        val options = Interpreter.Options()
        
        Log.d(TAG, "Configuring TFLite with: GPU=${config.useGPU}, NNAPI=${config.useNNAPI}, Threads=${config.numThreads}")
        
        var delegateApplied = false
        
        // Try NNAPI first if enabled (better for sustained performance)
        if (config.useNNAPI) {
            delegateApplied = tryConfigureNNAPI(options)
        }
        
        // Try GPU if NNAPI failed or not enabled
        if (!delegateApplied && config.useGPU) {
            delegateApplied = tryConfigureGPU(options)
        }
        
        // Configure CPU fallback
        if (!delegateApplied) {
            configureCPU(options, config)
        }
        
        return options
    }
    
    private fun tryConfigureNNAPI(options: Interpreter.Options): Boolean {
        return try {
            val nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
            Log.d(TAG, "NNAPI delegate configured successfully")
            true
        } catch (e: Exception) {
            Log.w(TAG, "NNAPI configuration failed: ${e.message}")
            false
        }
    }
    
    private fun tryConfigureGPU(options: Interpreter.Options): Boolean {
        return try {
            val compatList = CompatibilityList()
            if (!compatList.isDelegateSupportedOnThisDevice) {
                Log.d(TAG, "GPU delegate not supported on this device")
                return false
            }
            
            // Use default GPU delegate configuration for best compatibility
            val gpuDelegate = GpuDelegate()
            options.addDelegate(gpuDelegate)
            Log.d(TAG, "GPU delegate configured successfully")
            true
        } catch (e: Exception) {
            Log.w(TAG, "GPU configuration failed: ${e.message}")
            false
        }
    }
    
    private fun configureCPU(options: Interpreter.Options, config: PerformanceConfig) {
        Log.d(TAG, "Configuring CPU execution")
        options.setNumThreads(config.numThreads)
        options.setUseXNNPACK(config.useXNNPACK)
        options.setAllowFp16PrecisionForFp32(config.allowFp16)
        options.setAllowBufferHandleOutput(true)
    }
    
    private fun shouldUseGPU(): Boolean {
        // GPU is generally good for sustained inference
        // but can have cold start issues
        return try {
            val compatList = CompatibilityList()
            compatList.isDelegateSupportedOnThisDevice
        } catch (e: Exception) {
            Log.w(TAG, "Error checking GPU support: ${e.message}")
            false
        }
    }
    
    private fun shouldUseNNAPI(): Boolean {
        // NNAPI can be unstable on some devices, especially older ones
        return Build.VERSION.SDK_INT >= 29 && // Android 10+
               !isKnownProblematicDevice()
    }
    
    private fun getOptimalThreadCount(cpuCores: Int, isLowEnd: Boolean): Int {
        return when {
            isLowEnd -> 1
            cpuCores <= 2 -> 1
            cpuCores <= 4 -> 2
            else -> maxOf(1, cpuCores - 2) // Leave some cores for UI
        }
    }
    
    private fun isLowEndDevice(): Boolean {
        // Simple heuristic based on available processors
        val cores = Runtime.getRuntime().availableProcessors()
        return cores <= 4 && Build.VERSION.SDK_INT < 26
    }
    
    private fun isKnownProblematicDevice(): Boolean {
        // Add known problematic devices here
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        
        // Example: Some older Samsung devices have NNAPI issues
        return when {
            manufacturer == "motorola" && model.contains("g22") -> false // Your test device
            manufacturer == "samsung" && Build.VERSION.SDK_INT < 29 -> true
            else -> false
        }
    }
    
    /**
     * Benchmark different configurations to find the best one
     */
    fun benchmarkConfigurations(context: Context, modelPath: String): PerformanceConfig {
        Log.d(TAG, "Starting configuration benchmark...")
        
        val configs = listOf(
            PerformanceConfig(useGPU = true, useNNAPI = false, numThreads = 2, useXNNPACK = true, allowFp16 = true),
            PerformanceConfig(useGPU = false, useNNAPI = true, numThreads = 2, useXNNPACK = true, allowFp16 = true),
            PerformanceConfig(useGPU = false, useNNAPI = false, numThreads = 4, useXNNPACK = true, allowFp16 = true)
        )
        
        var bestConfig = configs.first()
        var bestTime = Long.MAX_VALUE
        
        // This would require a more complex benchmarking setup
        // For now, return the optimal config based on device characteristics
        return getOptimalConfig(context)
    }
}
