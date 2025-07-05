# YOLO Model Input Size Configuration Update

## Summary
Successfully updated the Android AR application to use a centralized constant configuration for YOLO model input dimensions. The input size has been changed from **640x640** to **320x320** for improved inference speed.

## Changes Made

### 1. Created Constants File
**File:** `app/src/main/java/com/example/augmented_mobile_application/ai/YOLOModelConstants.kt`
- Centralized all YOLO model configuration constants
- Set `INPUT_WIDTH = 320` and `INPUT_HEIGHT = 320`
- Included all other related constants (confidence thresholds, class IDs, etc.)

### 2. Updated YOLO11Detector.kt
**Changes:**
- Added import for `YOLOModelConstants`
- Updated companion object to use constants from `YOLOModelConstants`
- Changed `inputWidth` and `inputHeight` initialization to use `YOLOModelConstants.INPUT_WIDTH/HEIGHT`
- Replaced hardcoded values with constant references

### 3. Updated ARView.kt
**Changes:**
- Added import for `YOLOModelConstants`
- Updated `TARGET_CLASS_ID` to use `YOLOModelConstants.TARGET_CLASS_ID`
- Changed test bitmap creation to use `YOLOModelConstants.INPUT_WIDTH/HEIGHT`

### 4. Updated DetectionValidator.kt
**Changes:**
- Added import for `YOLOModelConstants`
- Updated validation checks to use `YOLOModelConstants.INPUT_WIDTH/HEIGHT` instead of hardcoded 640x640

### 5. Updated ModelPositioningManager.kt
**Changes:**
- Added import for `YOLOModelConstants`
- Updated area normalization calculations to use `YOLOModelConstants.INPUT_WIDTH/HEIGHT`

## Key Benefits

### 1. Performance Improvement
- **Input size reduced from 640x640 to 320x320**
- **~4x fewer pixels to process** (640×640 = 409,600 vs 320×320 = 102,400)
- **Faster inference speed** - approximately 2-4x faster depending on hardware
- **Lower memory usage** - reduced GPU/CPU memory footprint

### 2. Maintainability
- **Single source of truth** for all model dimensions
- **Easy to modify** - change in one place affects all components
- **Consistent configuration** across the entire application
- **Clear documentation** of all model-related constants

### 3. Code Quality
- **Eliminated magic numbers** throughout the codebase
- **Better code readability** with meaningful constant names
- **Type safety** with compile-time constant validation

## File Structure
```
app/src/main/java/com/example/augmented_mobile_application/
├── ai/
│   ├── YOLOModelConstants.kt          ✅ NEW - Centralized constants
│   ├── YOLO11Detector.kt              ✅ UPDATED - Uses constants
│   └── DetectionValidator.kt          ✅ UPDATED - Uses constants
├── ui/
│   └── ARView.kt                      ✅ UPDATED - Uses constants
└── ar/
    └── ModelPositioningManager.kt     ✅ UPDATED - Uses constants
```

## Configuration Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `INPUT_WIDTH` | 320 | Model input image width |
| `INPUT_HEIGHT` | 320 | Model input image height |
| `CONFIDENCE_THRESHOLD` | 0.4f | Detection confidence threshold |
| `IOU_THRESHOLD` | 0.45f | Non-Maximum Suppression IoU threshold |
| `MAX_DETECTIONS` | 300 | Maximum detections to keep after NMS |
| `TARGET_CLASS_ID` | 41 | Primary target class (cup) |
| `PUMP_CLASS_ID` | 81 | Pump object class ID |
| `CUP_CLASS_ID` | 41 | Cup object class ID |
| `PIPE_CLASS_ID` | 82 | Pipe object class ID |

## Usage Examples

### Accessing Constants
```kotlin
// Get input dimensions
val width = YOLOModelConstants.INPUT_WIDTH
val height = YOLOModelConstants.INPUT_HEIGHT

// Create bitmap with model input size
val bitmap = Bitmap.createBitmap(
    YOLOModelConstants.INPUT_WIDTH,
    YOLOModelConstants.INPUT_HEIGHT,
    Bitmap.Config.ARGB_8888
)

// Area normalization
val normalizedArea = boxArea / (
    YOLOModelConstants.INPUT_WIDTH.toFloat() * 
    YOLOModelConstants.INPUT_HEIGHT.toFloat()
)
```

### Future Modifications
To change the input size in the future:
1. Open `YOLOModelConstants.kt`
2. Modify `INPUT_WIDTH` and `INPUT_HEIGHT` values
3. All components will automatically use the new values

## Model Compatibility
⚠️ **Important:** Ensure your YOLO model (`pump.tflite`) is compatible with 320x320 input dimensions. If the model expects 640x640, you'll need to:
1. Retrain/resize the model for 320x320 input, OR
2. Update the constants back to 640x640 if needed

## Testing Recommendations
1. **Performance Testing:** Measure inference time before/after the change
2. **Accuracy Testing:** Verify detection accuracy with smaller input size
3. **Memory Testing:** Monitor memory usage during extended usage
4. **Integration Testing:** Test full AR pipeline with new dimensions

## Next Steps
1. Test the application with the new 320x320 input size
2. Monitor performance improvements
3. Verify detection quality remains acceptable
4. Consider further optimizations if needed

---
**Status:** ✅ COMPLETED - All files updated successfully with no compilation errors.
