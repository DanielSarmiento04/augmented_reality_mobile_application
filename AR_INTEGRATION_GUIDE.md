# ARView Integration Guide

## How to integrate the new routine flow in ARView

### 1. Update ARView.kt to use the new TopBar

```kotlin
// Add these imports to ARView.kt
import com.example.augmented_mobile_application.ui.components.ARRoutineTopBar
import com.example.augmented_mobile_application.ui.components.ARStepInstructionCard
import com.example.augmented_mobile_application.viewmodel.SharedRoutineViewModel

@Composable
fun ARView(
    machineType: String,
    glbPath: String? = null,
    routineId: String? = null,
    navController: NavHostController
) {
    // Get shared routine data
    val sharedRoutineViewModel = SharedRoutineViewModel.getInstance()
    val currentRoutine by sharedRoutineViewModel.currentRoutine.collectAsState()
    val isMaintenanceActive by sharedRoutineViewModel.isMaintenanceActive.collectAsState()
    
    // Use the GLB path from routine if available
    val modelPath = glbPath ?: currentRoutine?.glbAssetPath?.removePrefix("file:///android_asset/") ?: "pump/pump.glb"
    
    // ... existing ARView code ...
    
    Scaffold(
        topBar = {
            // Use the new routine-aware TopBar
            ARRoutineTopBar(
                onBack = {
                    // Clear routine when going back
                    sharedRoutineViewModel.clearRoutine()
                    navController.popBackStack()
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Your existing AR content
            AndroidView(
                factory = { context -> /* ARSceneView setup */ },
                modifier = Modifier.fillMaxSize()
            )
            
            // Show step instruction card when maintenance is active
            if (isMaintenanceActive) {
                ARStepInstructionCard(
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
```

### 2. Update Navigation Route

In your navigation setup, make sure to handle the new parameters:

```kotlin
// In your NavHost
composable(
    route = "arView/{machineType}?glbPath={glbPath}&routineId={routineId}",
    arguments = listOf(
        navArgument("machineType") { type = NavType.StringType },
        navArgument("glbPath") { 
            type = NavType.StringType
            nullable = true
        },
        navArgument("routineId") { 
            type = NavType.StringType
            nullable = true
        }
    )
) { backStackEntry ->
    val machineType = backStackEntry.arguments?.getString("machineType") ?: ""
    val glbPath = backStackEntry.arguments?.getString("glbPath")
    val routineId = backStackEntry.arguments?.getString("routineId")
    
    ARView(
        machineType = machineType,
        glbPath = glbPath,
        routineId = routineId,
        navController = navController
    )
}
```

### 3. Model Loading Integration

Update your model loading logic to use the routine's GLB:

```kotlin
// In ARView, when initializing the model coordinator
LaunchedEffect(currentRoutine, glbPath) {
    val modelPathToLoad = glbPath ?: currentRoutine?.glbAssetPath?.removePrefix("file:///android_asset/") ?: "pump/pump.glb"
    
    modelPlacementCoordinator.value?.let { coordinator ->
        scope.launch {
            val modelLoaded = coordinator.loadModel(modelPathToLoad)
            if (modelLoaded) {
                Log.i(TAG, "Routine 3D model loaded: $modelPathToLoad")
            } else {
                Log.e(TAG, "Failed to load routine model: $modelPathToLoad")
            }
        }
    }
}
```

## Summary of Changes

### Direct Flow Implementation:
1. **Removed step-by-step intermediate view** 
2. **Added direct "Iniciar Mantenimiento" buttons** on routine cards
3. **Created SharedRoutineViewModel** for cross-screen state
4. **Built ARRoutineTopBar** showing current step and progress
5. **Added ARStepInstructionCard** for step navigation in AR

### User Experience:
1. User sees routine list with direct start buttons
2. Tapping "Iniciar Mantenimiento" → immediately loads routine data and goes to AR
3. AR view shows current step in top banner with navigation controls
4. Step instruction overlay shows current step details with next/previous buttons

### Benefits:
- **Faster workflow**: One less screen to navigate
- **Better AR integration**: Step information directly in AR view
- **Cleaner UX**: Direct action buttons instead of intermediate views
- **Persistent state**: Routine data available across screens
