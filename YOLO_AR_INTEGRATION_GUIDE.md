# YOLO Object Detection Integration in AR View

This document describes the implementation of YOLO object detection with bounding box overlay in the AR application.

## Overview

The YOLO integration enables real-time object detection in the AR camera feed with visual bounding box overlays. This provides users with enhanced object recognition capabilities during maintenance procedures.

## Architecture

### Core Components

1. **YOLODetectionManager** (`/ar/YOLODetectionManager.kt`)
   - Manages YOLO detector lifecycle
   - Handles camera frame processing
   - Provides detection state management
   - Currently uses demonstration detections (can be enhanced with real camera frame processing)

2. **YOLODetectionOverlay** (`/ui/components/YOLODetectionOverlay.kt`)
   - Renders bounding boxes over camera feed
   - Provides color-coded detection visualization
   - Shows detection status and controls

3. **ARViewModel Extensions** (`/viewmodel/ARViewModel.kt`)
   - Added YOLO detection state management
   - Detection toggle functionality
   - Real-time detection count tracking

### Integration Points

4. **ARView Integration** (`/ui/ARView.kt`)
   - Integrated YOLO manager with AR lifecycle
   - Added detection processing in frame callback
   - Overlaid detection UI components

## Features Implemented

### ✅ Core Features
- YOLO detector initialization with custom pump/pipe model
- Real-time detection status indicator
- Toggle detection on/off functionality
- Bounding box overlay with class labels
- Color-coded detection boxes by object type
- Performance-optimized detection rate (2 FPS)

### 🎯 Object Classes Supported
- **Pump** (Class 81) - Red boxes
- **Pipe** (Class 82) - Blue boxes 
- **Steel Pipe** (Class 83) - Magenta boxes
- **Electric Cable** (Class 84) - Yellow boxes
- **Cup** (Class 42) - Green boxes
- **Other COCO objects** - Cyan boxes

### 🔧 Configuration
- Model: `pump/pump.tflite`
- Labels: `pump/classes.txt` (Extended COCO + custom classes)
- Detection interval: 500ms (2 FPS for performance)
- GPU acceleration enabled
- Confidence threshold: 0.4 (from YOLOModelConstants)

## User Interface

### Detection Status Panel (Top Right)
- Toggle switch to enable/disable detection
- Real-time status indicator (Detecting.../Ready)
- Object count display
- Semi-transparent dark background

### Bounding Box Overlay
- Colored rectangles around detected objects
- Class name and confidence percentage labels
- Screen-coordinate mapped positioning
- Automatic coordinate clamping to screen bounds

## Performance Optimizations

1. **Rate Limiting**: Detection runs at 2 FPS to maintain smooth AR experience
2. **Job Management**: Maximum 1 concurrent detection job
3. **Memory Management**: Proper cleanup of detector resources
4. **Background Processing**: Detection runs on IO dispatcher
5. **Frame Skipping**: Detection processes every 15th frame

## Current Implementation Status

### ✅ Working Features
- YOLO detector initialization
- Detection state management
- UI overlay and controls
- Demonstration bounding boxes
- Performance optimization
- Resource cleanup

### 🚧 Future Enhancements
- Real camera frame capture and processing
- YUV to RGB conversion for actual frames
- Enhanced detection accuracy tuning
- Custom model training for specific equipment
- 3D object tracking integration

## Usage

### Enabling Detection
1. Open AR maintenance view
2. Toggle the YOLO Detection switch (top right)
3. Green "Ready" status indicates detection is active
4. Detected objects will show colored bounding boxes

### Detection Controls
- **Toggle Switch**: Enable/disable detection
- **Status Indicator**: Shows detection state
- **Object Counter**: Real-time count of detected objects

## Technical Notes

### Model Integration
- Uses existing `YOLO11Detector` class
- Leverages TensorFlow Lite for inference
- Supports both GPU and CPU execution
- Custom class labels include maintenance equipment

### Coordinate System
- Detection coordinates mapped to screen space
- Automatic clamping to prevent off-screen rendering
- Scalable bounding box sizes
- Dynamic text label positioning

### Error Handling
- Graceful degradation if YOLO initialization fails
- Exception handling in frame processing
- Proper resource cleanup on view disposal
- Logging for debugging and monitoring

## Testing

### Demo Mode
Current implementation includes demonstration detections that simulate:
- Pump detection (85% confidence)
- Pipe detection (72% confidence)
- Random appearance to simulate real detection

### Integration Testing
- AR view lifecycle tested
- Detection toggle functionality verified
- UI overlay positioning confirmed
- Performance impact assessed

## Future Development

### Camera Frame Processing
1. Implement proper YUV420 to RGB conversion
2. Optimize frame capture for ARCore
3. Add frame preprocessing pipeline
4. Integrate with existing AR surface detection

### Enhanced Detection
1. Custom model training for specific equipment
2. Multi-class detection refinement
3. Detection confidence tuning
4. Tracking and persistence across frames

### User Experience
1. Detection history and analytics
2. Guided detection tutorials
3. Maintenance procedure integration
4. Equipment identification assistance

## Code Structure

```
app/src/main/java/com/example/augmented_mobile_application/
├── ar/
│   └── YOLODetectionManager.kt          # Detection management
├── ui/
│   ├── ARView.kt                        # Main AR view integration
│   └── components/
│       └── YOLODetectionOverlay.kt      # UI overlays
├── viewmodel/
│   └── ARViewModel.kt                   # State management
└── ai/
    └── YOLO11Detector.kt               # Existing detector (used)
```

## Dependencies

- Existing YOLO11Detector implementation
- TensorFlow Lite Android
- ARCore/SceneView integration
- Jetpack Compose for UI
- Coroutines for async processing

This implementation provides a solid foundation for YOLO object detection in the AR maintenance application, with room for enhancement as requirements evolve.
