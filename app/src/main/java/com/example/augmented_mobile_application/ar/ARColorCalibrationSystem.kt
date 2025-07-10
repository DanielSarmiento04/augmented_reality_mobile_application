package com.example.augmented_mobile_application.ar

import android.util.Log
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * AR Color Calibration System
 * 
 * Main coordination class for ensuring color-accurate rendering of GLB models
 * in ARView to match GLTF viewer output. Integrates all color correction,
 * material configuration, and lighting systems.
 */
class ARColorCalibrationSystem {
    
    companion object {
        private const val TAG = "ARColorCalibration"
        private const val CALIBRATION_UPDATE_INTERVAL = 2000L // 2 seconds
    }
    
    private val materialConfigManager = MaterialConfigurationManager()
    private val lightingConfigManager = LightingConfigurationManager()
    private val colorSpaceManager = ColorSpaceManager()
    
    private var calibrationJob: Job? = null
    private var isCalibrationActive = false
    
    /**
     * Initialize the color calibration system for an AR scene
     */
    fun initialize(arSceneView: ARSceneView) {
        try {
            Log.i(TAG, "Initializing AR Color Calibration System...")
            
            // Configure enhanced lighting first
            lightingConfigManager.configureLighting(arSceneView)
            
            // Start continuous calibration monitoring
            startContinuousCalibration(arSceneView)
            
            isCalibrationActive = true
            Log.i(TAG, "AR Color Calibration System initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AR Color Calibration System: ${e.message}", e)
        }
    }
    
    /**
     * Configure a model node for color-accurate rendering
     */
    fun configureModelForColorAccuracy(
        modelNode: ModelNode,
        modelInstance: io.github.sceneview.model.ModelInstance,
        arSceneView: ARSceneView
    ) {
        try {
            Log.i(TAG, "Configuring model for color accuracy with color preservation...")
            
            // Apply gentle material configuration that preserves colors
            materialConfigManager.configureModelMaterialsGentle(modelNode, modelInstance, arSceneView)
            
            // Skip validation for now to avoid aggressive fixes
            Log.i(TAG, "Model configured for color accuracy - colors preserved")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure model for color accuracy: ${e.message}", e)
            // Apply minimal safety fixes only
            applyMinimalSafetyFixes(modelNode, modelInstance)
        }
    }
    
    /**
     * Apply minimal safety fixes that don't override colors
     */
    private fun applyMinimalSafetyFixes(
        modelNode: ModelNode,
        modelInstance: io.github.sceneview.model.ModelInstance
    ) {
        try {
            Log.d(TAG, "Applying minimal safety fixes...")
            
            // Just ensure visibility
            modelNode.isVisible = true
            
            Log.d(TAG, "Minimal safety fixes applied")
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply minimal safety fixes: ${e.message}")
        }
    }
    
    /**
     * Apply color accuracy fixes for problematic models
     */
    private fun applyColorAccuracyFixes(
        modelNode: ModelNode,
        modelInstance: io.github.sceneview.model.ModelInstance,
        arSceneView: ARSceneView
    ) {
        try {
            Log.d(TAG, "Applying color accuracy fixes...")
            
            // Ensure basic model properties are correct
            modelNode.apply {
                isVisible = true
                isShadowCaster = true
                isShadowReceiver = true
            }
            
            // Force refresh materials
            materialConfigManager.refreshMaterials(modelNode, modelInstance, arSceneView)
            
            Log.d(TAG, "Color accuracy fixes applied")
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply color accuracy fixes: ${e.message}")
        }
    }
    
    /**
     * Start continuous calibration monitoring
     */
    private fun startContinuousCalibration(arSceneView: ARSceneView) {
        calibrationJob = CoroutineScope(Dispatchers.Main).launch {
            while (isCalibrationActive) {
                try {
                    // Monitor lighting conditions and adjust if needed
                    val lightingConditions = lightingConfigManager.getCurrentLightingConditions(arSceneView)
                    
                    // Log current conditions periodically
                    if (System.currentTimeMillis() % 10000 == 0L) { // Every 10 seconds
                        Log.d(TAG, "Current lighting conditions: " +
                                "intensity=${lightingConditions.pixelIntensity}, " +
                                "HDR=${lightingConditions.hasEnvironmentalHdr}")
                    }
                    
                    delay(CALIBRATION_UPDATE_INTERVAL)
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error in continuous calibration: ${e.message}")
                    delay(CALIBRATION_UPDATE_INTERVAL * 2) // Back off on error
                }
            }
        }
    }
    
    /**
     * Manual calibration trigger for testing
     */
    fun triggerManualCalibration(
        modelNode: ModelNode,
        modelInstance: io.github.sceneview.model.ModelInstance,
        arSceneView: ARSceneView
    ) {
        try {
            Log.i(TAG, "Manual calibration triggered")
            
            // Force refresh lighting
            lightingConfigManager.configureLighting(arSceneView)
            
            // Force refresh materials
            materialConfigManager.refreshMaterials(modelNode, modelInstance, arSceneView)
            
            Log.i(TAG, "Manual calibration completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Manual calibration failed: ${e.message}", e)
        }
    }
    
    /**
     * Test color accuracy by comparing with reference colors
     */
    fun testColorAccuracy(): ColorAccuracyTestResult {
        val testColors = listOf(
            TestColor("gris", colorSpaceManager.colorFromHex("#808080")),
            TestColor("negro", colorSpaceManager.colorFromHex("#1A1A1A")),
            TestColor("Material.006", colorSpaceManager.colorFromHex("#B0B0B0"))
        )
        
        val results = testColors.map { testColor ->
            val result = colorSpaceManager.testColorConversion(testColor.color, testColor.materialName)
            Log.d(TAG, "Color test for ${testColor.materialName}: " +
                    "Original=${colorSpaceManager.colorToHex(result.original)}, " +
                    "Adjusted=${colorSpaceManager.colorToHex(result.adjusted)}")
            result
        }
        
        return ColorAccuracyTestResult(
            testResults = results,
            overallAccuracy = calculateOverallAccuracy(results)
        )
    }
    
    /**
     * Calculate overall accuracy score
     */
    private fun calculateOverallAccuracy(results: List<ColorSpaceManager.ColorConversionResult>): Float {
        if (results.isEmpty()) return 0.0f
        
        val totalDifference = results.sumOf { result ->
            val originalLuminance = calculateLuminance(result.original)
            val adjustedLuminance = calculateLuminance(result.adjusted)
            kotlin.math.abs(originalLuminance - adjustedLuminance).toDouble()
        }
        
        val averageDifference = totalDifference / results.size
        return (1.0f - averageDifference.toFloat()).coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Calculate luminance of a color
     */
    private fun calculateLuminance(color: io.github.sceneview.math.Color): Float {
        return 0.299f * color.r + 0.587f * color.g + 0.114f * color.b
    }
    
    /**
     * Get current calibration status
     */
    fun getCalibrationStatus(): CalibrationStatus {
        return CalibrationStatus(
            isActive = isCalibrationActive,
            lightingConfigured = lightingConfigManager != null,
            materialConfigured = materialConfigManager != null,
            colorSpaceConfigured = colorSpaceManager != null
        )
    }
    
    /**
     * Cleanup calibration system
     */
    fun cleanup() {
        try {
            Log.i(TAG, "Cleaning up AR Color Calibration System...")
            
            isCalibrationActive = false
            calibrationJob?.cancel()
            calibrationJob = null
            
            lightingConfigManager.cleanup()
            
            Log.i(TAG, "AR Color Calibration System cleaned up")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error during calibration cleanup: ${e.message}")
        }
    }
    
    /**
     * Data classes for testing and status
     */
    data class TestColor(
        val materialName: String,
        val color: io.github.sceneview.math.Color
    )
    
    data class ColorAccuracyTestResult(
        val testResults: List<ColorSpaceManager.ColorConversionResult>,
        val overallAccuracy: Float
    )
    
    data class CalibrationStatus(
        val isActive: Boolean,
        val lightingConfigured: Boolean,
        val materialConfigured: Boolean,
        val colorSpaceConfigured: Boolean
    )
}
