package com.example.augmented_mobile_application.ui

import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import androidx.navigation.NavHostController
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.augmented_mobile_application.viewmodel.ARViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.core.Frame as ArFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.example.augmented_mobile_application.ar.ModelPlacementCoordinator
import com.example.augmented_mobile_application.ar.SurfaceChecker
import com.example.augmented_mobile_application.ui.components.SurfaceQualityIndicator
import com.example.augmented_mobile_application.ui.components.SurfaceOverlay
import com.example.augmented_mobile_application.repository.RoutineRepository
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk

private const val TAG = "ARView"

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ARView(
    machine_selected: String,
    navController: NavHostController,
    glbPath: String? = null,
    routineId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize ViewModel with factory
    val arViewModel: ARViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ARViewModel(context)
            }
        }
    )
    
    // Collect ViewModel state
    val currentStep by arViewModel.currentStep.collectAsState()
    val totalSteps by arViewModel.totalSteps.collectAsState()
    val stepDescription by arViewModel.stepDescription.collectAsState()
    val isLoadingRoutine by arViewModel.isLoadingRoutine.collectAsState()
    val maintenanceStarted by arViewModel.maintenanceStarted.collectAsState()
    val modelPlaced by arViewModel.modelPlaced.collectAsState()
    
    // Determine the model path - use provided glbPath or default
    val modelPath = glbPath ?: "pump/pump.glb"
    
    // Load routine if routineId is provided
    LaunchedEffect(routineId) {
        if (routineId != null) {
            arViewModel.loadRoutine(routineId)
        }
    }
    
    // Log the model being loaded for debugging
    LaunchedEffect(modelPath) {
        Log.i(TAG, "ARView initialized with model path: $modelPath")
        routineId?.let { 
            Log.i(TAG, "ARView initialized with routine ID: $it")
        }
    }
    
    // Check ARCore availability and handle errors gracefully
    var arCoreAvailable by remember { mutableStateOf(false) }
    var arCoreError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            Log.i(TAG, "ARCore availability: $availability")
            
            arCoreAvailable = when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    arCoreError = "ARCore no está instalado o necesita actualización"
                    false
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    arCoreError = "Este dispositivo no es compatible con ARCore"
                    false
                }
                else -> {
                    arCoreError = "ARCore no está disponible"
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ARCore availability", e)
            arCoreError = "Error verificando compatibilidad AR: ${e.message}"
            arCoreAvailable = false
        }
    }
    
    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            Log.i(TAG, "Camera permission granted")
        } else {
            Log.w(TAG, "Camera permission denied")
        }
    }
    
    // Request camera permission if not granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            Log.i(TAG, "Requesting camera permission")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            // Check ARCore availability when permission is granted
            Log.i(TAG, "Camera permission already granted, checking ARCore availability")
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            Log.i(TAG, "ARCore availability: $availability")
            
            if (availability.isTransient) {
                Log.i(TAG, "ARCore availability is transient, will check again later")
            }
        }
    }
    
    // Show error screen if ARCore is not available
    if (!arCoreAvailable) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Error de Realidad Aumentada",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = arCoreError ?: "Error desconocido",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { navController.navigateUp() }
                    ) {
                        Text("Volver")
                    }
                }
            }
        }
        return
    }

    // Show permission request screen if camera permission is not granted
    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permiso de Cámara Requerido",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Esta aplicación necesita acceso a la cámara para funcionar con realidad aumentada.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Text("Conceder Permiso")
                    }
                }
            }
        }
        return
    }

    // Core AR state
    var isLoadingModel by remember { mutableStateOf(true) }
    
    // Surface detection state
    var isPlacementReady by remember { mutableStateOf(false) }
    var surfaceQuality by remember { mutableStateOf<com.example.augmented_mobile_application.ar.SurfaceQualityChecker.SurfaceQuality?>(null) }

    val arSceneViewRef = remember { mutableStateOf<ARSceneView?>(null) }
    val modelPlacementCoordinator = remember { mutableStateOf<ModelPlacementCoordinator?>(null) }
    val surfaceChecker = remember { SurfaceChecker() }

    // ARSceneView initialization state
    var arSceneViewError by remember { mutableStateOf<String?>(null) }
    var isArSceneViewInitialized by remember { mutableStateOf(false) }

    // Initialize model placement coordinator when ARSceneView is ready
    LaunchedEffect(isArSceneViewInitialized) {
        if (isArSceneViewInitialized) {
            arSceneViewRef.value?.let { sceneView ->
                try {
                    // Add a delay to ensure ARSceneView is fully initialized
                    kotlinx.coroutines.delay(1000)
                    
                    modelPlacementCoordinator.value = ModelPlacementCoordinator(sceneView).also { coordinator ->
                        // Load the 3D model with timeout and error handling
                        scope.launch {
                            try {
                                Log.i(TAG, "Starting to load 3D model: $modelPath")
                                
                                // Add timeout for model loading
                                val modelLoaded = kotlinx.coroutines.withTimeoutOrNull(10000) {
                                    coordinator.loadModel(modelPath)
                                } ?: false
                                
                                isLoadingModel = !modelLoaded
                                if (modelLoaded) {
                                    Log.i(TAG, "3D model loaded successfully")
                                } else {
                                    Log.e(TAG, "Failed to load 3D model: $modelPath (timeout or error)")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception loading 3D model: $modelPath", e)
                                isLoadingModel = false
                            }
                        }
                    }
                    Log.i(TAG, "ModelPlacementCoordinator initialized")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing ModelPlacementCoordinator", e)
                    isLoadingModel = false
                }
            }
        }
    }

    // Performance monitoring
    var performanceMonitor by remember { mutableStateOf<com.example.augmented_mobile_application.utils.ARPerformanceMonitor?>(null) }
    
    // Initialize performance monitor when ARSceneView is ready
    LaunchedEffect(isArSceneViewInitialized) {
        if (isArSceneViewInitialized) {
            arSceneViewRef.value?.let { sceneView ->
                try {
                    performanceMonitor = com.example.augmented_mobile_application.utils.ARPerformanceMonitor(sceneView)
                    Log.i(TAG, "Performance monitoring initialized")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not initialize performance monitoring: ${e.message}")
                }
            }
        }
    }
    
    // Log performance summary periodically (every 30 seconds)
    LaunchedEffect(performanceMonitor) {
        performanceMonitor?.let { monitor ->
            while (true) {
                kotlinx.coroutines.delay(30000) // 30 seconds
                try {
                    monitor.logPerformanceSummary()
                } catch (e: Exception) {
                    Log.w(TAG, "Error logging performance: ${e.message}")
                }
            }
        }
    }
    
    // ARView lifecycle management
    DisposableEffect(Unit) {
        Log.i(TAG, "ARView lifecycle started")
        
        onDispose {
            Log.i(TAG, "ARView lifecycle cleanup started")
            try {
                // Clear surface checker
                surfaceChecker.clearHistory()
                
                // Stop any running coroutines
                scope.launch {
                    // Cancel model loading
                    modelPlacementCoordinator.value?.cleanup()
                    modelPlacementCoordinator.value = null
                    
                    // Dispose ARSceneView on main thread
                    arSceneViewRef.value?.let { sceneView ->
                        sceneView.post {
                            try {
                                // Clear all models and animations
                                Log.d(TAG, "Clearing scene resources")
                                sceneView.destroy()
                                Log.d(TAG, "Scene resources cleared")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error during scene cleanup: ${e.message}", e)
                            }
                        }
                        
                        arSceneViewRef.value = null
                        Log.i(TAG, "ARSceneView disposed properly")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during ARView cleanup: ${e.message}", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Show error if ARSceneView failed to initialize
        if (arSceneViewError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Error de Renderizado AR",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = arSceneViewError ?: "Error desconocido",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                arSceneViewError = null
                                isArSceneViewInitialized = false
                            }
                        ) {
                            Text("Reintentar")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { navController.navigateUp() }
                        ) {
                            Text("Volver")
                        }
                    }
                }
            }
        } else {
            // Safe ARSceneView initialization with extensive error handling
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Log.i(TAG, "Creating ARSceneView...")
                    try {
                        // Create ARSceneView with minimal configuration to avoid crashes
                        val sceneView = ARSceneView(ctx)
                        
                        // Post initialization to avoid blocking the UI thread
                        sceneView.post {
                            try {
                                Log.i(TAG, "Post-initialization of ARSceneView...")
                                
                                // Configure session with enhanced stability settings
                                sceneView.configureSession { session, config ->
                                    try {
                                        Log.i(TAG, "Configuring ARCore session with stability settings...")
                                        
                                        // Core session configuration for stability
                                        config.focusMode = Config.FocusMode.AUTO
                                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                                        
                                        // Enable light estimation for proper PBR material rendering
                                        // Try ENVIRONMENTAL_HDR first, fallback to AMBIENT_INTENSITY if not available
                                        try {
                                            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                                            Log.i(TAG, "Using ENVIRONMENTAL_HDR light estimation for best color accuracy")
                                        } catch (e: Exception) {
                                            try {
                                                config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                                                Log.i(TAG, "Using AMBIENT_INTENSITY light estimation (fallback)")
                                            } catch (e2: Exception) {
                                                Log.w(TAG, "Light estimation not available, colors may be less accurate")
                                                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                                            }
                                        }
                                        
                                        config.depthMode = Config.DepthMode.DISABLED
                                        
                                        // Use LATEST_CAMERA_IMAGE for better sensor compatibility 
                                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                                        
                                        // Disable all advanced modes to reduce resource usage
                                        try {
                                            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                                        } catch (e: Exception) {
                                            Log.w(TAG, "InstantPlacementMode not available: ${e.message}")
                                        }
                                        
                                        // Disable geospatial mode if available
                                        try {
                                            // For newer ARCore versions
                                            val geospatialModeMethod = config.javaClass.getMethod("setGeospatialMode", Int::class.java)
                                            geospatialModeMethod.invoke(config, 0) // DISABLED = 0
                                        } catch (e: Exception) {
                                            Log.d(TAG, "Geospatial mode not available: ${e.message}")
                                        }
                                        
                                        // Set camera texture name after GL context is ready
                                        sceneView.post {
                                            try {
                                                // Use the camera texture name from SceneView if available
                                                Log.d(TAG, "Camera texture configured")
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Could not set camera texture: ${e.message}")
                                            }
                                        }
                                        
                                        Log.i(TAG, "ARCore session configured successfully")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error configuring ARCore session", e)
                                        arSceneViewError = "Error configurando sesión AR: ${e.message}"
                                    }
                                }

                                // Enable plane visualization with error handling
                                try {
                                    sceneView.planeRenderer.isEnabled = true
                                    Log.i(TAG, "Plane renderer enabled")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not enable plane renderer: ${e.message}")
                                    // Continue without plane renderer
                                }

                                // Configure scene lighting for accurate model colors
                                try {
                                    // Enable environmental lighting for PBR materials
                                    sceneView.scene?.let { scene ->
                                        // The scene should automatically use environmental lighting
                                        // when light estimation is enabled
                                        Log.i(TAG, "Scene lighting configured for PBR rendering")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not configure scene lighting: ${e.message}")
                                }

                                // Set up frame callback with surface quality monitoring
                                var lastFrameTimestamp = 0L
                                sceneView.onFrame = { frameTime ->
                                    try {
                                        val currentFrame: ArFrame? = sceneView.frame
                                        if (currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
                                            
                                            // Validate timestamp monotonicity
                                            val currentTimestamp = currentFrame.timestamp
                                            if (lastFrameTimestamp > 0 && currentTimestamp <= lastFrameTimestamp) {
                                                Log.w(TAG, "Non-monotonic timestamp detected: $currentTimestamp <= $lastFrameTimestamp")
                                                // Skip this frame to maintain monotonicity - use different approach
                                            } else {
                                                lastFrameTimestamp = currentTimestamp
                                                
                                                // Process plane detection
                                                val planes = currentFrame.getUpdatedTrackables(Plane::class.java)
                                                isPlacementReady = planes.isNotEmpty() && planes.any { it.trackingState == TrackingState.TRACKING }
                                                
                                                // Update surface quality periodically (every 30 frames to avoid performance impact)
                                                if (frameTime.toLong() % 30 == 0L) {
                                                    modelPlacementCoordinator.value?.let { coordinator ->
                                                        try {
                                                            val overallQuality = coordinator.getOverallSurfaceQuality()
                                                            surfaceQuality = overallQuality
                                                        } catch (e: Exception) {
                                                            Log.w(TAG, "Error updating surface quality: ${e.message}")
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // Clear surface quality when not tracking
                                            surfaceQuality = null
                                            isPlacementReady = false
                                        }
                                    } catch (e: Exception) {
                                        // Throttled error logging to avoid spam
                                        if (System.currentTimeMillis() % 10000 == 0L) {
                                            Log.w(TAG, "Frame processing error: ${e.message}")
                                        }
                                    }
                                }

                                // Store reference for later use
                                arSceneViewRef.value = sceneView
                                isArSceneViewInitialized = true
                                Log.i(TAG, "ARSceneView post-initialization completed successfully")
                                
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in ARSceneView post-initialization", e)
                                arSceneViewError = "Error inicializando AR: ${e.message}"
                            }
                        }
                        
                        Log.i(TAG, "ARSceneView created successfully")
                        sceneView
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Critical error creating ARSceneView", e)
                        arSceneViewError = "Error crítico creando vista AR: ${e.message}"
                        // Return a simple dummy view to prevent crashes
                        android.view.View(ctx)
                    }
                }
            )
        }

        // Simple touch handling for model placement with surface quality validation
        if (isArSceneViewInitialized && arSceneViewError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                if (maintenanceStarted && !modelPlaced) {
                                    arSceneViewRef.value?.let { arSceneView ->
                                        try {
                                            // First check surface quality at touch point
                                            val coordinator = modelPlacementCoordinator.value
                                            val surfaceQualityAtTouch = coordinator?.checkSurfaceQuality(event.x, event.y)
                                            
                                            if (surfaceQualityAtTouch != null && !surfaceQualityAtTouch.isGoodQuality) {
                                                Log.w(TAG, "Surface quality insufficient at touch point:")
                                                surfaceQualityAtTouch.issues.forEach { issue ->
                                                    Log.w(TAG, "  - $issue")
                                                }
                                                Log.i(TAG, "Consider using force placement or finding a better surface")
                                                return@pointerInteropFilter false
                                            }
                                            
                                            val frame = arSceneView.frame
                                            if (frame != null && frame.camera.trackingState == TrackingState.TRACKING) {
                                                try {
                                                    val hitResults = frame.hitTest(event.x, event.y)
                                                    val validHit = hitResults.find { hit ->
                                                        try {
                                                            val trackable = hit.trackable
                                                            trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)
                                                        } catch (e: Exception) {
                                                            Log.w(TAG, "Error checking hit validity: ${e.message}")
                                                            false
                                                        }
                                                    }
                                                    
                                                    if (validHit != null) {
                                                        // Place on validated surface
                                                        val placementSuccess = coordinator?.placeModelAtHitResult(validHit)
                                                        if (placementSuccess == true) {
                                                            arViewModel.onModelPlaced()
                                                            Log.i(TAG, "Model placed successfully on validated surface")
                                                        } else {
                                                            Log.w(TAG, "Model placement failed despite good surface quality")
                                                        }
                                                    } else {
                                                        // Fallback: Try placing at estimated position when no valid hit
                                                        Log.i(TAG, "No valid hit found, trying estimated placement")
                                                        val placementSuccess = modelPlacementCoordinator.value?.placeModelAtEstimatedPosition(
                                                            event.x, event.y, 1.5f
                                                        )
                                                        if (placementSuccess == true) {
                                                            arViewModel.onModelPlaced()
                                                            Log.i(TAG, "Model placed at estimated position")
                                                        } else {
                                                            Log.w(TAG, "Model placement failed at estimated position")
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Error during hit test: ${e.message}", e)
                                                }
                                            } else {
                                                // Fallback when tracking isn't perfect
                                                Log.i(TAG, "Camera not tracking, attempting estimated placement")
                                                val placementSuccess = modelPlacementCoordinator.value?.placeModelAtEstimatedPosition(
                                                    event.x, event.y, 1.5f
                                                )
                                                if (placementSuccess == true) {
                                                    arViewModel.onModelPlaced()
                                                    Log.i(TAG, "Model placed at estimated position (no tracking)")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error during model placement: ${e.message}", e)
                                        }
                                    }
                                }
                                false
                            }
                            else -> false
                        }
                    }
            )
        }

        // Main UI overlay with two-pane layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Top pane - Step information (expandable)
            TopStepPane(
                machineSelected = machine_selected,
                stepDescription = stepDescription,
                currentStep = currentStep,
                totalSteps = totalSteps,
                progress = arViewModel.getProgress(),
                isLoadingModel = isLoadingModel,
                maintenanceStarted = maintenanceStarted,
                modelPlaced = modelPlaced,
                surfaceQuality = surfaceQuality,
                tips = arViewModel.getCurrentStepTips(),
                mediaPath = arViewModel.getCurrentStepMedia(),
                modifier = Modifier.weight(1f)
            )
            
            // Bottom pane - Fixed navigation bar
            BottomNavigationPane(
                maintenanceStarted = maintenanceStarted,
                modelPlaced = modelPlaced,
                isLoadingModel = isLoadingModel,
                isArSceneViewInitialized = isArSceneViewInitialized,
                surfaceQuality = surfaceQuality,
                canNavigatePrevious = arViewModel.canNavigatePrevious(),
                canNavigateNext = arViewModel.canNavigateNext(),
                nextButtonText = arViewModel.getNextButtonText(),
                onStartMaintenance = { arViewModel.startMaintenance() },
                onPreviousStep = { arViewModel.navigateToPreviousStep() },
                onNextStep = { 
                    val isFinished = arViewModel.navigateToNextStep()
                    if (isFinished) {
                        arViewModel.resetRoutine()
                        modelPlacementCoordinator.value?.removeCurrentModel()
                        navController.navigateUp()
                    }
                },
                onCancel = { 
                    arViewModel.resetRoutine()
                    modelPlacementCoordinator.value?.removeCurrentModel()
                    navController.navigateUp() 
                },
                onForcePlacement = {
                    // Try to place on best detected surface first, then fallback to center
                    val bestSurface = surfaceChecker.bestSurface.value
                    val placementSuccess = if (bestSurface != null) {
                        // Use best detected surface
                        modelPlacementCoordinator.value?.placeModelAtEstimatedPosition(
                            bestSurface.centerX, bestSurface.centerY, 1.5f
                        )
                    } else {
                        // Fallback to center of screen
                        modelPlacementCoordinator.value?.placeModelAtEstimatedPosition(
                            0.5f, 0.5f, 1.5f
                        )
                    }
                    
                    if (placementSuccess == true) {
                        arViewModel.onModelPlaced()
                        Log.i(TAG, "Model force-placed ${if (bestSurface != null) "on best surface" else "at center"}")
                    }
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}

/**
 * Top pane containing step information with animation
 */
@Composable
private fun TopStepPane(
    machineSelected: String,
    stepDescription: String,
    currentStep: Int,
    totalSteps: Int,
    progress: Float,
    isLoadingModel: Boolean,
    maintenanceStarted: Boolean,
    modelPlaced: Boolean,
    surfaceQuality: com.example.augmented_mobile_application.ar.SurfaceQualityChecker.SurfaceQuality?,
    tips: List<String>,
    mediaPath: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Main instruction card with animation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header with machine name
                Text(
                    text = machineSelected,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Progress indicator (when maintenance started)
                if (maintenanceStarted && totalSteps > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paso $currentStep de ${totalSteps - 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = DarkGreen
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkGreen,
                        trackColor = DarkGreen.copy(alpha = 0.2f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Step description with crossfade animation
                Crossfade(
                    targetState = stepDescription,
                    label = "step_description"
                ) { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black,
                        lineHeight = 24.sp
                    )
                }
                
                // Loading indicator
                if (isLoadingModel) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = DarkGreen
                    )
                }
                
                // Tips section (if available)
                if (tips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = "Tips",
                                    tint = DarkGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Consejos:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            tips.forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Black,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                
                // Media section (if available)
                mediaPath?.let { path ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = "Media",
                                tint = DarkGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Material de apoyo disponible",
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkGreen
                            )
                        }
                    }
                }
                
                // Surface quality indicator
                if (maintenanceStarted && !modelPlaced && surfaceQuality != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SurfaceQualityCard(surfaceQuality = surfaceQuality)
                }
            }
        }
    }
}

/**
 * Surface quality indicator card
 */
@Composable
private fun SurfaceQualityCard(
    surfaceQuality: com.example.augmented_mobile_application.ar.SurfaceQualityChecker.SurfaceQuality
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (surfaceQuality.isGoodQuality) 
                Color.Green.copy(alpha = 0.1f) else Color(0xFFFFA500).copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calidad de Superficie:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(surfaceQuality.score * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (surfaceQuality.isGoodQuality) Color.Green else Color(0xFFFFA500)
                )
            }
            
            if (surfaceQuality.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                surfaceQuality.issues.take(2).forEach { issue ->
                    Text(
                        text = "• $issue",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * Bottom navigation pane with persistent controls
 */
@Composable
private fun BottomNavigationPane(
    maintenanceStarted: Boolean,
    modelPlaced: Boolean,
    isLoadingModel: Boolean,
    isArSceneViewInitialized: Boolean,
    surfaceQuality: com.example.augmented_mobile_application.ar.SurfaceQualityChecker.SurfaceQuality?,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    nextButtonText: String,
    onStartMaintenance: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextStep: () -> Unit,
    onCancel: () -> Unit,
    onForcePlacement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (modelPlaced) {
                // Navigation controls when model is placed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Previous button
                    OutlinedButton(
                        onClick = onPreviousStep,
                        enabled = canNavigatePrevious,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DarkGreen
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous step",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Anterior")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Next/Finish button
                    Button(
                        onClick = onNextStep,
                        enabled = canNavigateNext,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGreen,
                            disabledContainerColor = DarkGreen.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(nextButtonText)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            if (nextButtonText == "Finalizar") Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = nextButtonText,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Cancel button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(0.8f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                if (!maintenanceStarted) {
                    // Start maintenance button
                    Button(
                        onClick = onStartMaintenance,
                        enabled = !isLoadingModel && isArSceneViewInitialized,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGreen,
                            disabledContainerColor = DarkGreen.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Iniciar Mantenimiento",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    // Force placement and cancel buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Force placement button
                        if (!modelPlaced && !isLoadingModel) {
                            Button(
                                onClick = onForcePlacement,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = "Force place",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Colocar Modelo", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Cancel button
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(if (modelPlaced || isLoadingModel) 1f else 0.7f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancelar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}