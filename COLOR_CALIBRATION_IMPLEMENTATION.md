# AR Color Calibration System - Implementation Guide

## Overview

This implementation provides a comprehensive solution for achieving color-accurate GLB model rendering in ARView that matches GLTF viewers like Don McCurdy's viewer. The system addresses color discrepancies through advanced material configuration, lighting calibration, and color space management.

## Key Components

### 1. ARColorCalibrationSystem
**Main coordination class that integrates all color correction systems.**

- Initializes enhanced lighting and material configuration
- Provides continuous calibration monitoring
- Offers manual calibration triggers for testing
- Validates color accuracy with test metrics

### 2. MaterialConfigurationManager
**Handles PBR material configuration for accurate color rendering.**

Features:
- Configures metallic/roughness values based on Blender settings (Metallic: 1.0, Roughness: 0.5)
- Maps specific materials: "gris", "Material.006", "negro"
- Applies environmental lighting adjustments
- Simulates IOR effects through material parameter adjustments

### 3. LightingConfigurationManager
**Provides advanced lighting calibration for realistic material behavior.**

Features:
- Environmental HDR lighting when available
- Fallback to ambient intensity lighting
- Supplementary directional and ambient lights
- Dynamic lighting adjustment based on AR conditions
- Color temperature calibration (6500K daylight)

### 4. ColorSpaceManager
**Handles color space conversions and gamma correction.**

Features:
- sRGB ↔ Linear color space conversions
- Gamma correction (2.2 gamma)
- Material-specific color adjustments
- Blender-to-Android color conversion
- GLTF viewer matching algorithms

### 5. ColorCalibrationDebugger
**Testing and validation utilities for color calibration.**

Features:
- Color accuracy testing
- Material property logging
- Performance benchmarking
- Visual debugging information

## Configuration Details

### Material Settings (Based on Your Blender Export)

```kotlin
// gris material
baseColor = Color(0.5f, 0.5f, 0.5f, 1.0f)
metallic = 1.0f    // Matches Blender: Metallic 1.000
roughness = 0.5f   // Matches Blender: Roughness 0.500

// Material.006
baseColor = Color(0.6f, 0.6f, 0.6f, 1.0f)
metallic = 1.0f
roughness = 0.5f

// negro material
baseColor = Color(0.1f, 0.1f, 0.1f, 1.0f)
metallic = 1.0f
roughness = 0.5f
```

### Lighting Configuration

```kotlin
// Environmental HDR (preferred)
config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

// Fallback: Ambient Intensity
config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY

// Supplementary lighting
Directional Light: Intensity 0.8f, Color Temperature 6500K
Ambient Light: Intensity 0.3f, Fill lighting
```

## Implementation Usage

### Basic Integration

```kotlin
// In your ARView or GLBModelLoader
val colorCalibrationSystem = ARColorCalibrationSystem()

// Initialize the system
colorCalibrationSystem.initialize(arSceneView)

// Configure a model for color accuracy
colorCalibrationSystem.configureModelForColorAccuracy(modelNode, modelInstance, arSceneView)
```

### Manual Calibration (For Testing)

```kotlin
// Trigger manual calibration
colorCalibrationSystem.triggerManualCalibration(modelNode, modelInstance, arSceneView)

// Test color accuracy
val testResult = colorCalibrationSystem.testColorAccuracy()
Log.d("ColorTest", "Accuracy: ${testResult.overallAccuracy * 100}%")
```

### Debug Testing

```kotlin
// In debug builds only
if (BuildConfig.DEBUG) {
    ColorCalibrationDebugger.testColorAccuracy(modelNode, modelInstance, arSceneView)
    ColorCalibrationDebugger.testColorSpaceConversions()
    ColorCalibrationDebugger.performanceTest(modelNode, modelInstance, arSceneView)
}
```

## Technical Approach

### 1. Color Space Management
- Converts between sRGB and Linear color spaces
- Applies proper gamma correction (2.2 gamma)
- Handles Android-specific color adjustments
- Matches GLTF viewer color pipeline

### 2. PBR Material Configuration
- Uses metallic-roughness workflow
- Preserves material properties from GLB export
- Applies environmental lighting responses
- Simulates IOR effects through parameter adjustments

### 3. Environmental Lighting
- Prioritizes Environmental HDR for maximum accuracy
- Supplements with calibrated directional/ambient lights
- Adjusts intensity based on AR conditions
- Uses realistic color temperature (6500K)

### 4. Performance Optimization
- Caches material configurations
- Throttles dynamic adjustments (2-second intervals)
- Validates configurations to prevent errors
- Provides fallback mechanisms

## Troubleshooting

### Common Issues and Solutions

**Colors still appear different:**
1. Check if Environmental HDR is available: `lightingConditions.hasEnvironmentalHdr`
2. Verify material mapping: Check logs for "Material X mapped to: Y"
3. Test with debug system: `ColorCalibrationDebugger.testColorAccuracy()`

**Performance issues:**
1. Monitor calibration intervals in logs
2. Run performance test: `ColorCalibrationDebugger.performanceTest()`
3. Check for excessive material refreshes

**Material validation failures:**
1. Check model visibility: `modelNode.isVisible`
2. Verify shadow settings: `isShadowCaster`, `isShadowReceiver`
3. Validate material instances count

## Expected Results

After implementation, you should see:

1. **Improved Color Accuracy**: Colors matching GLTF viewer output
2. **Realistic Material Behavior**: Proper metallic/roughness response
3. **Environmental Integration**: Materials responding to AR lighting
4. **Performance Stability**: No significant impact on rendering performance

## Monitoring and Validation

The system provides several metrics:

- **Color Accuracy Score**: Overall percentage match
- **Lighting Conditions**: Current environmental state
- **Material Validation**: Configuration correctness
- **Performance Metrics**: Processing times

## Next Steps

1. **Test the Implementation**: Load your GLB model and compare with GLTF viewer
2. **Fine-tune Parameters**: Adjust material configs if needed
3. **Monitor Performance**: Check frame rates and calibration overhead
4. **Validate Results**: Use debug tools to verify color accuracy

The system is designed to be robust with fallback mechanisms, so it should work even if some features aren't available on specific devices.
