# Advanced PBR Color Calibration System for GLB Models in ARView

## Overview

This comprehensive solution addresses color discrepancies between GLB models rendered in ARView and GLTF viewers (like Don McCurdy's GLTF Viewer) by implementing advanced PBR (Physically-Based Rendering) color calibration techniques.

## System Architecture

### Core Components

1. **PBRColorCalibrationSystem** - Main calibration orchestrator
2. **ThreadSafeMaterialHandler** - Thread-safe material operations
3. **ColorSpaceManager** - Color space conversions and gamma correction
4. **LightingConfigurationManager** - Environmental lighting optimization
5. **ColorCalibrationValidator** - Testing and validation framework

### Key Features Implemented

#### 1. Base Color Preservation
- **CRITICAL**: Preserves original GLB base colors exactly as authored
- Prevents color overriding that causes red objects to appear black/yellow
- Maintains color fidelity from Blender to ARView

#### 2. Advanced PBR Material Configuration
- **Metallic Factor**: Optimized to 0.1f for natural appearance (prevents metallic color shifts)
- **Roughness Factor**: Set to 0.6f for realistic surface properties
- **IOR (Index of Refraction)**: Configured to 1.5f for standard dielectric materials
- **Emissive Factor**: Set to 0.0f to prevent unwanted glow effects

#### 3. Environmental Lighting Calibration
- **HDR Lighting**: Enabled Environmental HDR for realistic lighting
- **Ambient Light**: Balanced to preserve natural colors
- **Real-time Adjustment**: Adapts to changing lighting conditions

#### 4. Color Space Management
- **Gamma Correction**: Proper sRGB ↔ Linear conversions
- **Color Preservation**: Option to maintain original GLB colors
- **GLTF Viewer Matching**: Algorithms to match viewer output

#### 5. Performance Optimization
- **Thread Safety**: All material updates are thread-safe
- **Batch Processing**: Efficient material updates
- **Memory Management**: Optimized for real-time AR performance

## Usage Guide

### Basic Implementation

```kotlin
// 1. Load GLB model with advanced color calibration
val modelNode = GLBModelLoader.loadGLBModel(
    arSceneView = arSceneView,
    modelPath = "your_model.glb",
    scale = 0.3f
)

// The GLBModelLoader automatically applies:
// - PBR color calibration
// - Thread-safe material handling
// - Color space management
// - Advanced lighting configuration
```

### Advanced Usage

```kotlin
// 1. Initialize PBR calibration system
val pbrSystem = PBRColorCalibrationSystem()
pbrSystem.initialize(arSceneView)

// 2. Configure model for color accuracy
pbrSystem.calibrateModelForColorAccuracy(
    modelNode = modelNode,
    modelInstance = modelInstance,
    arSceneView = arSceneView
)

// 3. Use thread-safe material handler for updates
val materialHandler = ThreadSafeMaterialHandler()

// 4. Apply PBR configuration while preserving colors
val pbrConfig = ThreadSafeMaterialHandler.PBRConfiguration(
    baseColor = null, // null preserves original GLB colors
    metallic = 0.1f,
    roughness = 0.6f,
    ior = 1.5f,
    emissive = Color(0.0f, 0.0f, 0.0f, 1.0f)
)

// Apply to materials
kotlinx.coroutines.runBlocking {
    materialHandler.preserveOriginalColorsWithPBR(
        materialInstance = materialInstance,
        materialId = "material_id",
        pbrConfiguration = pbrConfig
    )
}
```

### Validation and Testing

```kotlin
// Initialize validator
val validator = ColorCalibrationValidator()

// Run validation
val report = validator.validateModelColorAccuracy(
    modelNode = modelNode,
    modelInstance = modelInstance,
    arSceneView = arSceneView
)

// Check results
Log.i("ColorValidation", "Overall Score: ${report.overallScore}")
report.recommendations.forEach { recommendation ->
    Log.d("ColorValidation", "Recommendation: $recommendation")
}

// Start continuous monitoring (debug builds)
validator.startContinuousValidation(modelNode, modelInstance, arSceneView)
```

## Technical Implementation Details

### Color Preservation Strategy

The system uses a **color-first approach**:

1. **Never Override baseColorFactor**: Original GLB colors are preserved exactly
2. **PBR Property Optimization**: Only metallic/roughness/IOR are adjusted
3. **Lighting Compensation**: Environmental lighting is tuned instead of colors
4. **Gamma Awareness**: Proper color space handling maintains fidelity

### Material Configuration Process

```kotlin
// Step 1: Base Color Preservation
// - Keep original GLB baseColorFactor untouched
// - Ensure alpha channel is properly set for solid materials

// Step 2: PBR Property Tuning
materialInstance.setParameter("metallicFactor", 0.1f)  // Low metallic
materialInstance.setParameter("roughnessFactor", 0.6f) // Medium roughness
materialInstance.setParameter("ior", 1.5f)             // Standard IOR

// Step 3: Environmental Optimization
// - Configure HDR lighting
// - Adjust ambient/directional light balance
// - Real-time lighting adaptation
```

### Performance Considerations

- **Thread Safety**: All material operations are thread-safe
- **Batch Updates**: Materials are updated in batches to avoid blocking
- **Memory Efficient**: Caching and optimization for real-time AR
- **Background Processing**: Validation runs in background threads

## Troubleshooting Common Issues

### Issue: Colors Still Don't Match GLTF Viewer

**Solutions:**
1. Verify GLB export settings from Blender
2. Check color space configuration in export
3. Ensure gamma correction is not double-applied
4. Validate environmental lighting setup

### Issue: Performance Impact

**Solutions:**
1. Disable continuous validation in production
2. Use batch material updates
3. Reduce validation frequency
4. Optimize material parameter access

### Issue: Materials Appear Too Dark/Bright

**Solutions:**
1. Adjust environmental lighting intensity
2. Check metallic factor (should be low for most materials)
3. Verify roughness settings
4. Validate IOR configuration

## Configuration Options

### For Metallic Materials
```kotlin
PBRConfiguration(
    baseColor = null,        // Preserve original
    metallic = 0.8f,        // High metallic
    roughness = 0.2f,       // Low roughness (shiny)
    ior = 1.0f,            // Metallic IOR
    emissive = Color.BLACK
)
```

### For Dielectric Materials
```kotlin
PBRConfiguration(
    baseColor = null,        // Preserve original
    metallic = 0.0f,        // Non-metallic
    roughness = 0.7f,       // Higher roughness
    ior = 1.5f,            // Standard dielectric
    emissive = Color.BLACK
)
```

### For Glass Materials
```kotlin
PBRConfiguration(
    baseColor = null,        // Preserve original
    metallic = 0.0f,        // Non-metallic
    roughness = 0.1f,       // Very smooth
    ior = 1.52f,           // Glass IOR
    emissive = Color.BLACK
)
```

## Best Practices

1. **Always Preserve Original Colors**: Never override baseColorFactor unless absolutely necessary
2. **Test with Reference Models**: Use known good models for validation
3. **Monitor Performance**: Keep validation overhead minimal in production
4. **Use Appropriate IOR Values**: Match material properties to real-world equivalents
5. **Validate Continuously**: Use the validator to catch regressions
6. **Environment Specific Testing**: Test under different lighting conditions

## Integration with Existing Code

The system is designed to integrate seamlessly with existing GLB loading code:

```kotlin
// Replace existing GLB loading
val modelNode = GLBModelLoader.loadGLBModel(arSceneView, modelPath)

// The new system automatically:
// - Preserves original GLB colors
// - Applies optimal PBR settings
// - Configures appropriate lighting
// - Validates color accuracy (debug builds)
```

## Future Enhancements

1. **Machine Learning Color Matching**: AI-based color calibration
2. **Real-time Color Adjustment**: Dynamic color tuning based on environment
3. **Multi-Model Validation**: Batch validation across multiple models
4. **Advanced Material Detection**: Automatic material type recognition
5. **Export Integration**: Direct Blender export optimization

This comprehensive solution ensures that GLB models render with accurate, natural colors in ARView that match the expected output from GLTF viewers, while maintaining optimal performance for real-time AR applications.
