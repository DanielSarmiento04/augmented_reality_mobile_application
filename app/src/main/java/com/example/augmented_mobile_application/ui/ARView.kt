package com.example.augmented_mobile_application.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    
    // Determine the model path - use provided glbPath or default
    val modelPath = glbPath ?: "pump/pump.glb"
    
    // Routine loading state
    var currentRoutine by remember { mutableStateOf<MaintenanceRoutine?>(null) }
    var currentStepIndex by remember { mutableStateOf(0) }
    var isLoadingRoutine by remember { mutableStateOf(false) }
    
    // Load routine if routineId is provided
    LaunchedEffect(routineId) {
        if (routineId != null) {
            isLoadingRoutine = true
            try {
                val repository = RoutineRepository.getInstance(context)
                val result = repository.getRoutine(routineId)
                result.onSuccess { routine ->
                    currentRoutine = routine
                    Log.i(TAG, "Loaded routine: ${routine.displayName} with ${routine.steps.size} steps")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to load routine: $routineId", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading routine: $routineId", e)
            } finally {
                isLoadingRoutine = false
            }
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
    var maintenanceStarted by remember { mutableStateOf(false) }
    var modelPlaced by remember { mutableStateOf(false) }
    
    // Surface detection state
    var isPlacementReady by remember { mutableStateOf(false) }
    var surfaceQuality by remember { mutableStateOf<com.example.augmented_mobile_application.ar.SurfaceQualityChecker.SurfaceQuality?>(null) }

    // Get instructions from loaded routine or use default
    val instructions = currentRoutine?.steps?.map { it.instruction } ?: listOf(
        "Mueva lentamente el dispositivo para detectar superficies planas",
        "Verificar que la bomba esté apagada",
        "Inspeccionar el estado general de la bomba",
        "Mantenimiento completado con éxito"
    )

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
                                        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
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
                                                            modelPlaced = true
                                                            currentStepIndex = maxOf(1, currentStepIndex)
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
                                                            modelPlaced = true
                                                            currentStepIndex = maxOf(1, currentStepIndex)
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
                                                    modelPlaced = true
                                                    currentStepIndex = maxOf(1, currentStepIndex)
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

        // Main UI overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section with instruction card and surface quality
            Column {
                // Instruction card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = machine_selected,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkGreen
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = instructions[currentStepIndex],
                            fontSize = 16.sp,
                            color = Color.Black
                        )
                        
                        // Loading indicator
                        if (isLoadingModel) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = DarkGreen
                            )
                        }
                        
                        // Surface quality indicator
                        if (maintenanceStarted && !modelPlaced && surfaceQuality != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (surfaceQuality!!.isGoodQuality) 
                                        Color.Green.copy(alpha = 0.1f) else Color(0xFFFFA500).copy(alpha = 0.1f) // Orange
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Calidad de Superficie:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${(surfaceQuality!!.score * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (surfaceQuality!!.isGoodQuality) Color.Green else Color(0xFFFFA500) // Orange
                                        )
                                    }
                                    
                                    if (surfaceQuality!!.issues.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        surfaceQuality!!.issues.take(2).forEach { issue ->
                                            Text(
                                                text = "• $issue",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (modelPlaced) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (currentStepIndex > 1) {
                                    currentStepIndex--
                                }
                            },
                            enabled = maintenanceStarted && currentStepIndex > 1,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGreen,
                                disabledContainerColor = DarkGreen.copy(alpha = 0.5f)
                            )
                        ) {
                            Text("Anterior")
                        }

                        Button(
                            onClick = {
                                if (currentStepIndex < instructions.size - 1) {
                                    currentStepIndex++
                                } else {
                                    // Finish maintenance
                                    maintenanceStarted = false
                                    modelPlaced = false
                                    currentStepIndex = 0
                                    modelPlacementCoordinator.value?.removeCurrentModel()
                                    navController.navigateUp()
                                }
                            },
                            enabled = maintenanceStarted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGreen,
                                disabledContainerColor = DarkGreen.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(text = if (currentStepIndex < instructions.size - 1) "Siguiente" else "Finalizar")
                        }
                    }
                } else {
                    Text(
                        text = when {
                            !isArSceneViewInitialized -> "Iniciando vista AR..."
                            isLoadingModel -> "Cargando modelo 3D..."
                            !maintenanceStarted -> "Presione 'Iniciar Mantenimiento' para comenzar"
                            surfaceQuality?.isGoodQuality == true -> "Superficie detectada - Toque para colocar el modelo"
                            surfaceQuality != null -> "Calidad de superficie: ${(surfaceQuality!!.score * 100).toInt()}% - Mejore la superficie o use colocación forzada"
                            else -> "Toque en la pantalla para colocar el modelo 3D o use el botón 'Colocar Modelo'"
                        },
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!maintenanceStarted) {
                                maintenanceStarted = true
                                currentStepIndex = 1
                            }
                        },
                        enabled = !isLoadingModel && !maintenanceStarted && isArSceneViewInitialized,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGreen,
                            disabledContainerColor = DarkGreen.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Iniciar Mantenimiento",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                    
                    // Add a force placement button for debugging/fallback
                    if (maintenanceStarted && !modelPlaced && !isLoadingModel) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
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
                                    modelPlaced = true
                                    currentStepIndex = maxOf(1, currentStepIndex)
                                    Log.i(TAG, "Model force-placed ${if (bestSurface != null) "on best surface" else "at center"}")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Colocar Modelo (Forzar)",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
}