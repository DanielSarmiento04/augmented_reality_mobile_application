# Dynamic Routine Integration Guide

## Overview
This implementation refactors `UserContentView` to dynamically load maintenance routines from `assets/pump/routines/` with production-ready architecture for the Motorola G32 Android 12 device.

## Architecture Components

### 1. Data Layer
- **`RoutineRepository`**: Singleton repository with caching and error handling
- **`RoutineAssetLoader`**: Asset scanning and parsing service (existing, enhanced)
- **Performance**: Background loading, weak reference caching, proper InputStream management

### 2. ViewModel Layer
- **`RoutineSelectionViewModel`**: Manages routine selection and step navigation
- **Features**: Step-by-step navigation, AR session management, error handling
- **State**: Reactive StateFlow architecture for UI updates

### 3. UI Layer
- **`UserContentView`**: Refactored with dynamic routine loading
- **`RoutineSelectionView`**: Card-based routine picker with LazyColumn
- **`RoutineDetailsView`**: Step-by-step pager with navigation controls
- **`StepCard`**: Individual step display component

### 4. AR Integration
- **`ARViewBridge`**: Interface for AR-Compose communication
- **`ARModelManager`**: Performance-optimized GLB loading and caching
- **`RoutineARBridge`**: Simplified integration bridge

## Usage Flow

### 1. App Startup
```kotlin
// Repository automatically loads and caches all routines
val repository = RoutineRepository.getInstance(context)
repository.loadRoutines() // Loads routine_1, routine_2, routine_3
```

### 2. Routine Selection
```kotlin
// User sees list of available routines
LazyColumn {
    items(availableRoutines) { routine ->
        RoutineCard(
            routine = routine, // "Rutina Diaria", "Rutina Mensual", etc.
            onSelect = { routineViewModel.selectRoutine(routine) }
        )
    }
}
```

### 3. Step Navigation
```kotlin
// HorizontalPager for step-by-step navigation
HorizontalPager(state = pagerState) { page ->
    StepCard(
        step = routine.steps[page],
        stepNumber = page + 1,
        totalSteps = routine.steps.size
    )
}
```

### 4. AR Session Launch
```kotlin
// User taps "Iniciar Mantenimiento"
Button(onClick = { 
    routineViewModel.startMaintenanceAR() // Triggers GLB validation and loading
}) {
    Text("Iniciar Mantenimiento")
}

// Navigation with GLB path
LaunchedEffect(arSessionRequested) {
    arSessionRequested?.let { glbPath ->
        navController.navigate("arView/$MACHINE_TYPE?glbPath=${glbPath}")
    }
}
```

## Performance Optimizations

### Memory Management (170MB GLB Files)
- **Weak Reference Caching**: Allows GC when memory pressure occurs
- **Background Loading**: IO dispatcher for asset operations
- **Stream Management**: Automatic closing with `use {}` blocks
- **Single Instance**: Limit to 1 concurrent GLB load

### UI Performance (16ms Budget)
- **LazyColumn**: Efficient list rendering for routines
- **StateFlow**: Reactive state management
- **Derived States**: Computed values with `derivedStateOf`
- **Stable Keys**: `key = { routine.id }` for list performance

### Threading Safety
- **Main Thread**: All ARCore/Filament operations
- **IO Dispatcher**: Asset loading and parsing
- **Coroutine Scope**: Proper lifecycle management

## Error Handling

### Graceful Degradation
```kotlin
// Missing GLB file
if (!repository.validateRoutineGlb(routine.id)) {
    showError("Archivo 3D no encontrado para ${routine.displayName}")
    return
}

// Empty steps
if (routine.steps.isEmpty()) {
    showError("La rutina no tiene pasos definidos")
    return
}
```

### User Feedback
- Loading states with progress indicators
- Error cards with retry functionality
- Haptic feedback for user actions

## File Structure
```
assets/pump/routines/
├── routine_1/
│   ├── routine_1.glb  (170MB, loaded on demand)
│   └── routine_1.txt  (Steps in Spanish)
├── routine_2/
│   ├── routine_2.glb
│   └── routine_2.txt
└── routine_3/
    ├── routine_3.glb
    └── routine_3.txt
```

## Integration with Existing ARView

### Option 1: Minimal Integration
```kotlin
// In ARView.kt, add GLB path parameter
@Composable
fun ARView(
    machineType: String,
    glbPath: String? = null // New parameter
) {
    // Use glbPath instead of hardcoded "pump/pump.glb"
    val modelPath = glbPath ?: "pump/pump.glb"
    
    LaunchedEffect(arSceneViewRef.value) {
        arSceneViewRef.value?.let { sceneView ->
            coordinator.loadModel(modelPath) // Use dynamic path
        }
    }
}
```

### Option 2: Full Integration
```kotlin
// Use ARModelManager for performance-optimized loading
val arModelManager = remember { ARModelManager(context, arSceneView) }

LaunchedEffect(glbPath) {
    glbPath?.let { path ->
        arModelManager.loadModel(path)
    }
}
```

## Testing on Motorola G32

### Performance Monitoring
- Frame time monitoring (<16ms)
- Memory usage tracking
- Battery consumption analysis
- Thermal throttling detection

### Device-Specific Optimizations
- Reduced concurrent operations
- Aggressive cache eviction
- Background task prioritization
- Network operation delays

## Migration Steps

1. **Replace hardcoded routine selection** in `UserContentView`
2. **Add `RoutineRepository` initialization** in `MainActivity` or Application class
3. **Update navigation** to pass GLB path to `ARView`
4. **Integrate `ARModelManager`** for optimized GLB loading
5. **Test memory usage** with multiple routine switches
6. **Verify performance** on Motorola G32 device

This architecture provides a scalable, maintainable solution that dynamically handles routine management while maintaining optimal performance for your target device constraints.
