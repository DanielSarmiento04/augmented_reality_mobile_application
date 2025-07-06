# Routine Management Performance Guide

## Memory Management for 170MB GLB Files

### 1. Asset Loading Strategy
```kotlin
// ✅ DO: Use InputStreams with explicit closing
context.assets.open(assetPath).use { inputStream ->
    val modelInstance = arSceneView.modelLoader.loadModelInstance(inputStream)
    // Stream automatically closed by 'use'
}

// ❌ DON'T: Load without proper resource management
val inputStream = context.assets.open(assetPath) // Resource leak risk
```

### 2. Model Caching
```kotlin
// ✅ DO: Use WeakReference for cache to allow GC
private val modelCache = ConcurrentHashMap<String, WeakReference<ModelInstance>>()

// ✅ DO: Check cache before loading
val cachedModel = modelCache[glbPath]?.get()
if (cachedModel != null) {
    // Use cached model
    return cachedModel
}
```

### 3. Background Loading
```kotlin
// ✅ DO: Load on IO dispatcher to avoid blocking UI
val loadJob = CoroutineScope(Dispatchers.IO).launch {
    val modelInstance = loadModelFromAssets(glbPath)
    
    // Switch to Main for AR operations
    withContext(Dispatchers.Main) {
        displayModel(modelInstance)
    }
}
```

### 4. Memory Pressure Handling
```kotlin
// ✅ DO: Implement cleanup on memory pressure
override fun onTrimMemory(level: Int) {
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
            arModelManager.clearCache()
        }
    }
}
```

## ARCore Threading Rules

### 1. GL Thread Safety
```kotlin
// ✅ DO: Only touch ARCore objects on main/GL thread
withContext(Dispatchers.Main) {
    arSceneView.addChildNode(modelNode)
    anchorNode.addChildNode(modelNode)
}

// ❌ DON'T: Access from background threads
CoroutineScope(Dispatchers.IO).launch {
    arSceneView.addChildNode(modelNode) // CRASH!
}
```

### 2. Model Instance Management
```kotlin
// ✅ DO: Reuse ModelInstance, create new ModelNode
val modelNode = ModelNode(
    modelInstance = cachedModelInstance, // Reuse
    scaleToUnits = 1.0f
)

// ✅ DO: Destroy nodes when done
modelNode.destroy()
anchorNode.destroy()
```

## Performance Optimizations

### 1. Routine Loading
```kotlin
// ✅ DO: Load routines on app startup
class RoutineRepository {
    suspend fun preloadRoutines() = withContext(Dispatchers.IO) {
        val routines = routineLoader.loadAllRoutines()
        routines.forEach { routine ->
            routineCache[routine.id] = routine
        }
    }
}
```

### 2. Step Navigation
```kotlin
// ✅ DO: Use derivedStateOf for computed values
val currentStep = derivedStateOf {
    selectedRoutine.value?.steps?.getOrNull(currentStepIndex.value)
}

// ✅ DO: Use LazyColumn for step lists
LazyColumn {
    items(routine.steps) { step ->
        StepCard(step = step)
    }
}
```

### 3. String Processing
```kotlin
// ✅ DO: Use bufferedReader for large text files
context.assets.open(filePath).bufferedReader().useLines { lines ->
    lines.mapIndexedNotNull { index, line ->
        if (line.trim().isNotEmpty()) {
            MaintenanceStep(id = index, instruction = line.trim())
        } else null
    }.toList()
}
```

## UI Performance (16ms Budget)

### 1. Lazy Loading
```kotlin
// ✅ DO: Load routine details on demand
fun selectRoutine(routine: MaintenanceRoutine) {
    _selectedRoutine.value = routine
    _showStepDetails.value = true
    // Steps already loaded in routine object
}
```

### 2. State Management
```kotlin
// ✅ DO: Use StateFlow for reactive UI
class RoutineSelectionViewModel {
    private val _availableRoutines = MutableStateFlow<List<MaintenanceRoutine>>(emptyList())
    val availableRoutines: StateFlow<List<MaintenanceRoutine>> = _availableRoutines.asStateFlow()
}
```

### 3. Compose Optimizations
```kotlin
// ✅ DO: Use keys in LazyColumn
LazyColumn {
    items(
        items = availableRoutines,
        key = { routine -> routine.id } // Stable key
    ) { routine ->
        RoutineCard(routine = routine)
    }
}

// ✅ DO: Use remember for expensive calculations
val imageLoader = remember {
    ImageLoader.Builder(context)
        .components { add(GifDecoder.Factory()) }
        .build()
}
```

## Error Handling

### 1. Resource Loading
```kotlin
// ✅ DO: Graceful degradation
try {
    val routine = routineLoader.loadRoutine(routineId)
    if (routine?.steps?.isEmpty() == true) {
        return Result.failure(Exception("Routine has no steps"))
    }
    Result.success(routine)
} catch (e: Exception) {
    Log.e(TAG, "Error loading routine: $routineId", e)
    Result.failure(e)
}
```

### 2. AR Model Loading
```kotlin
// ✅ DO: Validate before loading
suspend fun validateRoutineGlb(routineId: String): Boolean {
    return try {
        context.assets.open("pump/routines/$routineId/$routineId.glb").use {
            true // File exists and readable
        }
    } catch (e: IOException) {
        false
    }
}
```

## Motorola G32 Specific Optimizations

### 1. Memory Constraints
- Limit to 1 concurrent GLB load
- Use image compression for GIFs
- Implement model cache eviction

### 2. Performance Monitoring
```kotlin
// ✅ DO: Monitor frame rates
val frameTimeWatcher = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
        val frameTime = (frameTimeNanos - lastFrameTime) / 1_000_000f
        if (frameTime > 16.67f) { // Dropped frame
            Log.w(TAG, "Frame drop detected: ${frameTime}ms")
        }
    }
}
```

### 3. Battery Optimization
- Pause YOLO detection when not needed
- Use lower quality models for preview
- Implement smart AR session management
