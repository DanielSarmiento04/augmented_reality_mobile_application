package com.example.augmented_mobile_application.ui

import android.content.Context
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import androidx.navigation.NavHostController
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.os.Handler
import android.os.HandlerThread
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Scale
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.Frame as ArFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.DecimalFormat
import com.example.augmented_mobile_application.ai.YOLO11Detector
import com.example.augmented_mobile_application.ai.DetectionPipeline
import com.example.augmented_mobile_application.ai.DetectionValidator
import com.example.augmented_mobile_application.ar.ModelPositioningManager
import com.example.augmented_mobile_application.ar.ARCoreStateManager
import com.example.augmented_mobile_application.ar.SurfaceDetectionManager
import com.example.augmented_mobile_application.ar.ModelPlacementCoordinator
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.augmented_mobile_application.BuildConfig
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import androidx.compose.runtime.derivedStateOf

private const val TAG = "ARView"
private const val TARGET_CLASS_ID = 41  // Changed from 82 to 41 (cup) as requested

@Composable
fun rememberYoloDetector(context: Context): YOLO11Detector? {
    val modelPath = "pump/pump.tflite"
    val labelsPath = "pump/classes.txt"

    val detector = remember {
        try {
            Log.i(TAG, "Initializing YOLO11Detector...")
            YOLO11Detector(
                context = context,
                modelPath = modelPath,
                labelsPath = labelsPath,
                useNNAPI = false,  // Disable NNAPI for more predictable results
                useGPU = true      // Keep GPU enabled for performance
            ).also {
                Log.i(TAG, "YOLO11Detector initialized successfully.")
                Log.i(TAG, "Model Input Details: ${it.getInputDetails()}")
                it.logModelValidation()  // Add validation logging
                
                // Validate target class
                if (!it.validateClassId(TARGET_CLASS_ID)) {
                    Log.e(TAG, "ERROR: Target class $TARGET_CLASS_ID is invalid!")
                } else {
                    Log.i(TAG, "Target class $TARGET_CLASS_ID (${it.getClassName(TARGET_CLASS_ID)}) is valid")
                }
                
                // Run comprehensive validation with a test image
                try {
                    val testBitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
                    val validationReport = DetectionValidator.validatePipeline(it, testBitmap)
                    if (!validationReport.isValid) {
                        Log.e(TAG, "Validation failed! Check logs for details.")
                    }
                    testBitmap.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not run validation: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YOLODetector: ${e.message}", e)
            null
        }
    }

    DisposableEffect(detector) {
        onDispose {
            Log.i(TAG, "Disposing YOLODetector...")
            try {
                detector?.close()
                Log.i(TAG, "YOLODetector disposed.")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing YOLODetector: ${e.message}", e)
            }
        }
    }
    return detector
}

@Composable
fun rememberDetectionPipeline(detector: YOLO11Detector?): DetectionPipeline? {
    return remember(detector) {
        detector?.let { 
            DetectionPipeline(it, TARGET_CLASS_ID).also {
                Log.i(TAG, "DetectionPipeline initialized")
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ARView(
    machine_selected: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ARCore availability state
    var arCoreAvailability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    
    // Memory pressure monitoring
    var isMemoryPressureHigh by remember { mutableStateOf(false) }
    
    // Check ARCore availability
    LaunchedEffect(Unit) {
        arCoreAvailability = ArCoreApk.getInstance().checkAvailability(context)
        Log.i(TAG, "ARCore availability: $arCoreAvailability")
    }
    
    // Monitor memory pressure - less frequent to avoid recomposition issues
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat()) * 100
                
                // Use higher threshold and only change state when crossing boundaries
                val wasHighPressure = isMemoryPressureHigh
                isMemoryPressureHigh = memoryUsagePercent > 85 // Higher threshold
                
                // Only log when state changes to reduce log spam
                if (isMemoryPressureHigh != wasHighPressure) {
                    if (isMemoryPressureHigh) {
                        Log.w(TAG, "High memory pressure detected: ${memoryUsagePercent.toInt()}%")
                        // Trigger garbage collection to free memory
                        System.gc()
                    } else {
                        Log.i(TAG, "Memory pressure normalized: ${memoryUsagePercent.toInt()}%")
                    }
                }
                
                kotlinx.coroutines.delay(10000) // Check every 10 seconds to reduce state changes
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring memory pressure: ${e.message}")
                kotlinx.coroutines.delay(15000) // Wait longer on error
            }
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

    // Enhanced state management for better surface detection feedback
    var isLoadingModel by remember { mutableStateOf(true) }
    var instructionStep by remember { mutableStateOf(0) }
    var maintenanceStarted by remember { mutableStateOf(false) }
    
    // Surface detection state
    var surfacesDetected by remember { mutableStateOf(0) }
    var isPlacementReady by remember { mutableStateOf(false) }
    var lastTouchPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showPlacementIndicator by remember { mutableStateOf(false) }

    val yoloDetector = rememberYoloDetector(context)
    val detectionPipeline = rememberDetectionPipeline(yoloDetector)
    val arStateManager = remember { ARCoreStateManager() }
    val surfaceDetectionManager = remember { SurfaceDetectionManager() }
    val modelPlacementCoordinator = remember { mutableStateOf<ModelPlacementCoordinator?>(null) }
    
    // Use StateFlow from DetectionPipeline instead of local state
    val detectionResults by detectionPipeline?.detectionResults?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isTargetDetected by detectionPipeline?.isTargetDetected?.collectAsState() ?: remember { mutableStateOf(false) }
    val inferenceTimeMs by detectionPipeline?.inferenceTimeMs?.collectAsState() ?: remember { mutableStateOf(0L) }
    val isDetecting by detectionPipeline?.isProcessing?.collectAsState() ?: remember { mutableStateOf(false) }
    
    // ARCore state monitoring
    val trackingState by arStateManager.trackingState.collectAsState()
    val trackingFailureReason by arStateManager.trackingFailureReason.collectAsState()
    var modelPlaced by remember { mutableStateOf(false) }

    val instructions = listOf(
        "Mueva lentamente el dispositivo para detectar superficies planas",
        "Verificar que la bomba esté apagada",
        "Inspeccionar el estado general de la bomba (Detección activa)",
        "Comprobar conexiones eléctricas (Detección activa)",
        "Verificar el estado de las válvulas (Detección activa)",
        "Mantenimiento completado con éxito"
    )

    val arSceneViewRef = remember { mutableStateOf<ARSceneView?>(null) }
    val modelNodeRef = remember { mutableStateOf<ModelNode?>(null) }
    val anchorNodeRef = remember { mutableStateOf<AnchorNode?>(null) }
    val modelPositioningManager = remember { mutableStateOf<ModelPositioningManager?>(null) }

    // Initialize model placement coordinator when ARSceneView is ready
    LaunchedEffect(arSceneViewRef.value) {
        arSceneViewRef.value?.let { sceneView ->
            modelPlacementCoordinator.value = ModelPlacementCoordinator(sceneView).also { coordinator ->
                // Load the 3D model
                scope.launch {
                    val modelLoaded = coordinator.loadModel("pump/pump.glb")
                    if (modelLoaded) {
                        isLoadingModel = false
                        Log.i(TAG, "3D model loaded successfully via ModelPlacementCoordinator")
                    } else {
                        isLoadingModel = false
                        Log.e(TAG, "Failed to load 3D model")
                    }
                }
            }
            
            // Initialize legacy model positioning manager for detection-based placement
            modelPositioningManager.value = ModelPositioningManager(sceneView)
            Log.i(TAG, "ModelPositioningManager initialized")
        }
    }

    // Enhanced surface detection monitoring
    LaunchedEffect(trackingState) {
        if (trackingState == TrackingState.TRACKING) {
            arSceneViewRef.value?.let { sceneView ->
                val frame = sceneView.frame
                if (frame != null) {
                    surfaceDetectionManager.updatePlanes(frame)
                }
            }
        }
    }
    
    // Monitor surface detection state
    val detectedPlanes by surfaceDetectionManager.detectedPlanes.collectAsState()
    val isSurfaceReady by surfaceDetectionManager.isSurfaceReady.collectAsState()
    
    // Update local surface state
    LaunchedEffect(detectedPlanes, isSurfaceReady) {
        surfacesDetected = detectedPlanes.size
        isPlacementReady = isSurfaceReady
    }

    // Update model positions when detections change
    LaunchedEffect(detectionResults, isTargetDetected) {
        if (maintenanceStarted && modelPlaced && isTargetDetected) {
            modelPositioningManager.value?.updateModelPositions(
                detections = detectionResults,
                targetClassId = TARGET_CLASS_ID,
                modelNode = modelNodeRef.value
            )
        } else {
            // Clear models when target not detected
            modelPositioningManager.value?.clearAllModels()
        }
    }

    // Add stability by using derivedStateOf for computed values
    val isArReadyForOperations by remember {
        derivedStateOf {
            arCoreAvailability == ArCoreApk.Availability.SUPPORTED_INSTALLED &&
            hasCameraPermission &&
            trackingState == TrackingState.TRACKING
        }
    }

    // Add a flag to prevent initialization races
    var isInitializationComplete by remember { mutableStateOf(false) }
    
    // Delayed initialization to prevent ANR during startup
    LaunchedEffect(isArReadyForOperations) {
        if (isArReadyForOperations && !isInitializationComplete) {
            // Add a small delay to let the UI settle
            kotlinx.coroutines.delay(500)
            isInitializationComplete = true
            Log.i(TAG, "AR initialization complete")
        }
    }

    // Optimize detection pipeline start to prevent ANR
    LaunchedEffect(isArReadyForOperations, maintenanceStarted, modelPlaced, isInitializationComplete) {
        if (isArReadyForOperations && maintenanceStarted && modelPlaced && isInitializationComplete) {
            Log.i(TAG, "Starting detection pipeline")
            detectionPipeline?.start()
        } else {
            Log.i(TAG, "Stopping detection pipeline")
            detectionPipeline?.stop()
        }
    }

    // Cleanup detection pipeline on dispose
    DisposableEffect(detectionPipeline) {
        onDispose {
            Log.i(TAG, "Disposing detection pipeline")
            detectionPipeline?.close()
        }
    }

    // Cleanup ARSceneView only when the composable is disposed (use Unit as key)
    DisposableEffect(Unit) {
        onDispose {
            Log.i(TAG, "Disposing ARSceneView on composable disposal")
            try {
                arSceneViewRef.value?.destroy()
                arSceneViewRef.value = null
                Log.i(TAG, "ARSceneView disposed")
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing ARSceneView: ${e.message}", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Stable AndroidView with minimal recomposition triggers
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Log.i(TAG, "Creating ARSceneView...")
                val sceneView = ARSceneView(ctx).apply {
                    Log.i(TAG, "ARSceneView created successfully")

                    configureSession { session, config ->
                        Log.i(TAG, "Configuring ARCore session...")
                        // Enhanced ARCore configuration for optimal surface detection
                        config.focusMode = Config.FocusMode.AUTO
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        
                        // Enable depth for better occlusion and tracking
                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            config.depthMode = Config.DepthMode.AUTOMATIC
                            Log.i(TAG, "Depth mode enabled for better tracking")
                        } else {
                            Log.w(TAG, "Depth mode not supported on this device")
                        }
                        
                        // Enable instant placement for faster model placement
                        try {
                            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                            Log.i(TAG, "Instant placement mode enabled")
                        } catch (e: Exception) {
                            Log.w(TAG, "Instant placement not supported: ${e.message}")
                        }
                        
                        Log.i(TAG, "ARCore session configured with optimized settings")
                        arStateManager.logSessionCapabilities(session)
                    }

                    // Enhanced plane visualization for better user feedback
                    planeRenderer.isEnabled = true
                    try {
                        // Some plane renderer properties may not be available in all versions
                        Log.i(TAG, "Plane renderer enabled with enhanced settings")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not configure plane renderer material: ${e.message}")
                    }

                    onTrackingFailureChanged = { reason ->
                        // This is handled by arStateManager now
                        Log.d(TAG, "Tracking failure changed: $reason")
                    }

                    var frameCounter = 0L
                    var lastProcessedFrame = 0L
                    onFrame = { frameTime ->
                        frameCounter++
                        val currentFrame: ArFrame? = this.frame

                        // Update ARCore state manager with current frame
                        arStateManager.updateTrackingState(currentFrame)

                        // Update surface detection with current frame
                        if (currentFrame != null && trackingState == TrackingState.TRACKING) {
                            surfaceDetectionManager.updatePlanes(currentFrame)
                        }

                        // More aggressive frame limiting to prevent ANR
                        // Only process every 10th frame (~3 FPS) and add minimum time gap
                        val currentTime = System.currentTimeMillis()
                        if (maintenanceStarted && modelPlaced && detectionPipeline != null && 
                            arStateManager.isReadyForOperations() && arStateManager.isCameraImageAvailable() &&
                            isInitializationComplete && // Wait for initialization to complete
                            frameCounter % 10 == 0L && !isMemoryPressureHigh &&
                            (currentTime - lastProcessedFrame) > 300) { // At least 300ms between frames
                            
                            lastProcessedFrame = currentTime
                            
                            try {
                                currentFrame?.acquireCameraImage()?.use { image: Image? ->
                                    if (image != null && !isDetecting) {
                                        // Submit to detection pipeline asynchronously with timeout
                                        scope.launch(Dispatchers.Default) {
                                            try {
                                                // Add timeout to prevent blocking
                                                withTimeout(2000) { // 2 second timeout
                                                    val bitmap: Bitmap = image.toBitmap()
                                                    detectionPipeline.submitFrame(bitmap)
                                                }
                                            } catch (e: TimeoutCancellationException) {
                                                Log.w(TAG, "Frame processing timed out")
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Error converting image to bitmap: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Log only critical errors to avoid spam
                                if (frameCounter % 100 == 0L) {
                                    Log.w(TAG, "Error processing frame: ${e.message}")
                                }
                            }
                        }
                    }

                    // Store reference for later use
                    arSceneViewRef.value = this
                }
                Log.i(TAG, "Returning ARSceneView from factory")
                sceneView
            },
            update = { sceneView ->
                // Minimal update logic - avoid heavy operations here
                // Only update when absolutely necessary
            }
        )

        // Touch handling overlay - separate from AndroidView to prevent recomposition
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (maintenanceStarted && !modelPlaced && isPlacementReady) {
                                // Store touch position for visual feedback
                                lastTouchPosition = Pair(event.x, event.y)
                                showPlacementIndicator = true
                                
                                // Enhanced hit test with plane validation
                                arSceneViewRef.value?.let { arSceneView ->
                                    val frame = arSceneView.frame
                                    if (frame != null && frame.camera.trackingState == TrackingState.TRACKING) {
                                        try {
                                            val hitResults = frame.hitTest(event.x, event.y)
                                            
                                            // Enhanced hit test with plane validation using SurfaceDetectionManager
                                            val bestHit = surfaceDetectionManager.findBestPlaneForPlacement(
                                                frame, event.x, event.y
                                            )
                                            
                                            if (bestHit != null) {
                                                // Use ModelPlacementCoordinator for enhanced placement
                                                val placementSuccess = modelPlacementCoordinator.value?.placeModelAtHitResult(bestHit)
                                                
                                                if (placementSuccess == true) {
                                                    modelPlaced = true
                                                    instructionStep = maxOf(1, instructionStep)
                                                    showPlacementIndicator = false
                                                    
                                                    // Store reference for legacy compatibility
                                                    anchorNodeRef.value = modelPlacementCoordinator.value?.getCurrentAnchorNode()
                                                    modelNodeRef.value = modelPlacementCoordinator.value?.getCurrentModelNode()
                                                    
                                                    Log.i(TAG, "Model placed successfully using enhanced placement at distance: ${bestHit.distance}m")
                                                } else {
                                                    Log.e(TAG, "Failed to place model using ModelPlacementCoordinator")
                                                    showPlacementIndicator = false
                                                }
                                            } else {
                                                Log.d(TAG, "No valid plane surface found at touch point")
                                                // Fallback to estimated placement
                                                val fallbackSuccess = modelPlacementCoordinator.value?.placeModelAtEstimatedPosition(
                                                    event.x, event.y, 1.5f
                                                )
                                                
                                                if (fallbackSuccess == true) {
                                                    modelPlaced = true
                                                    instructionStep = maxOf(1, instructionStep)
                                                    showPlacementIndicator = false
                                                    
                                                    anchorNodeRef.value = modelPlacementCoordinator.value?.getCurrentAnchorNode()
                                                    modelNodeRef.value = modelPlacementCoordinator.value?.getCurrentModelNode()
                                                    
                                                    Log.i(TAG, "Model placed using estimated position fallback")
                                                } else {
                                                    // Auto-hide indicator after failed placement
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(1000)
                                                        showPlacementIndicator = false
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error during enhanced hit test: ${e.message}", e)
                                            showPlacementIndicator = false
                                        }
                                    } else {
                                        val currentTrackingState = frame?.camera?.trackingState
                                        Log.w(TAG, "Cannot place model - ARCore not tracking. Current state: $currentTrackingState")
                                        showPlacementIndicator = false
                                    }
                                }
                            }
                            false
                        }
                        else -> false
                    }
                }
        )

        // Enhanced visual feedback overlay for surface detection
        SurfaceDetectionOverlay(
            isPlacementReady = isPlacementReady,
            surfacesDetected = surfacesDetected,
            trackingState = trackingState,
            lastTouchPosition = lastTouchPosition,
            showPlacementIndicator = showPlacementIndicator,
            modifier = Modifier.fillMaxSize()
        )

        DrawDetectionsOverlay(
            detections = detectionResults,
            detector = yoloDetector,
            targetClassId = TARGET_CLASS_ID,
            isTargetDetected = isTargetDetected,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
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
                        text = instructions[instructionStep],
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    
                    // Enhanced surface detection feedback
                    if (maintenanceStarted && !modelPlaced) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SurfaceDetectionStatus(
                            trackingState = trackingState,
                            surfacesDetected = surfacesDetected,
                            isPlacementReady = isPlacementReady,
                            detectionQuality = surfaceDetectionManager.getDetectionQuality()
                        )
                    }
                    
                    // Debug status card
                    if (BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.7f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = "Debug Info:",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tracking: ${trackingState.name}",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Camera Permission: $hasCameraPermission",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "ARSceneView: ${if (arSceneViewRef.value != null) "Created" else "Not Created"}",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "ARCore: ${arCoreAvailability?.name ?: "Checking..."}",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Memory Pressure: ${if (isMemoryPressureHigh) "HIGH" else "Normal"}",
                                    fontSize = 10.sp,
                                    color = if (isMemoryPressureHigh) Color.Red else Color.White
                                )
                                Text(
                                    text = "Detection Active: ${if (isDetecting) "Yes" else "No"}",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                                trackingFailureReason?.let {
                                    Text(
                                        text = "Failure: ${it.name}",
                                        fontSize = 10.sp,
                                        color = Color.Red
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    if (maintenanceStarted && modelPlaced) {
                        Text(
                            text = "Inference Time: ${inferenceTimeMs}ms",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            text = "Target Class (${yoloDetector?.getClassName(TARGET_CLASS_ID) ?: TARGET_CLASS_ID}) Detected: ${if (isTargetDetected) "Yes" else "No"}",
                            fontSize = 14.sp,
                            color = if (isTargetDetected) DarkGreen else Color.Red
                        )
                    }

                    if (isLoadingModel) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = DarkGreen
                        )
                    }
                    trackingFailureReason?.let { reason ->
                        Text(
                            text = when (reason) {
                                TrackingFailureReason.NONE -> "Sin problemas de seguimiento"
                                TrackingFailureReason.BAD_STATE -> "Sistema AR en mal estado"
                                TrackingFailureReason.INSUFFICIENT_LIGHT -> "Iluminación insuficiente"
                                TrackingFailureReason.EXCESSIVE_MOTION -> "Movimiento excesivo"
                                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Insuficientes características visuales"
                                else -> "Problema de seguimiento desconocido"
                            },
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

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
                                if (instructionStep > 1) {
                                    instructionStep--
                                    // Detection state is now managed by DetectionPipeline
                                }
                            },
                            enabled = maintenanceStarted && instructionStep > 1,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGreen,
                                disabledContainerColor = DarkGreen.copy(alpha = 0.5f)
                            )
                        ) {
                            Text("Anterior")
                        }

                        Button(
                            onClick = {
                                if (instructionStep < instructions.size - 1) {
                                    instructionStep++
                                    // Detection state is now managed by DetectionPipeline
                                } else {
                                    maintenanceStarted = false
                                    modelPlaced = false
                                    instructionStep = 0
                                    // Enhanced cleanup using ModelPlacementCoordinator
                                    modelPlacementCoordinator.value?.removeCurrentModel()
                                    surfaceDetectionManager.reset()
                                    anchorNodeRef.value = null
                                    modelNodeRef.value = null
                                    navController.navigateUp()
                                }
                            },
                            enabled = maintenanceStarted,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGreen,
                                disabledContainerColor = DarkGreen.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(text = if (instructionStep < instructions.size - 1) "Siguiente" else "Finalizar")
                        }
                    }
                } else {
                    Text(
                        text = when {
                            isLoadingModel -> "Cargando modelo..."
                            !maintenanceStarted -> "Presione 'Iniciar Mantenimiento' para comenzar"
                            !isPlacementReady -> "Mueva el dispositivo lentamente para detectar superficies"
                            else -> "Toque en una superficie plana para colocar el modelo 3D"
                        },
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!maintenanceStarted) {
                                maintenanceStarted = true
                                instructionStep = 1
                                // Detection state is managed by DetectionPipeline lifecycle
                            }
                        },
                        enabled = !isLoadingModel && !maintenanceStarted,
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
                }
            }
        }
    }
}

// Helper composable to draw detections on a Canvas overlay
@Composable
fun DrawDetectionsOverlay(
    detections: List<YOLO11Detector.Detection>,
    detector: YOLO11Detector?,
    targetClassId: Int,
    isTargetDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val boxPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
    }
    val targetBoxPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 8f
            color = android.graphics.Color.GREEN
        }
    }
    val textPaint = remember {
        android.graphics.Paint().apply {
            textSize = 40f
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
    }
    val textBackgroundPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
        }
    }
    val targetTextBackgroundPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.GREEN
        }
    }

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            detections.forEach { detection ->
                val className = detector?.getClassName(detection.classId) ?: "ID: ${detection.classId}"
                val label = "$className: ${"%.2f".format(detection.conf)}"

                val isTarget = detection.classId == targetClassId

                val currentBoxPaint = if (isTarget) targetBoxPaint else boxPaint
                val currentTextBgPaint = if (isTarget) targetTextBackgroundPaint else textBackgroundPaint

                if (!isTarget) {
                    val color = detector?.classColors?.getOrNull(detection.classId % (detector.classColors.size ?: 1)) ?: intArrayOf(255, 0, 0)
                    val androidColor = android.graphics.Color.rgb(color[0], color[1], color[2])
                    currentBoxPaint.color = androidColor
                    currentTextBgPaint.color = androidColor
                } else {
                    targetBoxPaint.color = android.graphics.Color.GREEN
                    targetTextBackgroundPaint.color = android.graphics.Color.GREEN
                }

                canvas.nativeCanvas.drawRect(
                    detection.box.x.toFloat(),
                    detection.box.y.toFloat(),
                    (detection.box.x + detection.box.width).toFloat(),
                    (detection.box.y + detection.box.height).toFloat(),
                    currentBoxPaint
                )

                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(label, 0, label.length, textBounds)
                val textWidth = textBounds.width()
                val textHeight = textBounds.height()

                val textBgLeft = detection.box.x.toFloat()
                val textBgTop = detection.box.y.toFloat() - textHeight - 10f
                val textBgRight = detection.box.x.toFloat() + textWidth + 10f
                val textBgBottom = detection.box.y.toFloat()

                val clampedTextBgTop = maxOf(0f, textBgTop)
                val clampedTextBgBottom = clampedTextBgTop + textHeight + 10f

                canvas.nativeCanvas.drawRect(
                    textBgLeft, clampedTextBgTop, textBgRight, clampedTextBgBottom,
                    currentTextBgPaint
                )

                val textY = clampedTextBgTop + textHeight + 5f - (textPaint.descent() / 2)
                canvas.nativeCanvas.drawText(
                    label,
                    detection.box.x.toFloat() + 5f,
                    textY,
                    textPaint
                )
            }
        }
    }
}

fun Image.toBitmap(): Bitmap {
    if (format != ImageFormat.YUV_420_888) {
        throw IllegalArgumentException("Invalid image format, expected YUV_420_888, got $format")
    }

    val width = this.width
    val height = this.height

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer.apply { rewind() }
    val uBuffer = uPlane.buffer.apply { rewind() }
    val vBuffer = vPlane.buffer.apply { rewind() }

    val yRowStride = yPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vRowStride = vPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vPixelStride = vPlane.pixelStride
    val yPixelStride = yPlane.pixelStride

    val nv21Size = width * height + ceil(height / 2.0).toInt() * ceil(width / 2.0).toInt() * 2
    val nv21 = ByteArray(nv21Size)

    var yOffset = 0
    if (yPixelStride == 1 && yRowStride == width) {
        yBuffer.get(nv21, 0, width * height)
        yOffset = width * height
    } else {
        val yRowData = ByteArray(yRowStride)
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            if (yBuffer.remaining() < width * yPixelStride) {
                nv21.fill(128.toByte(), yOffset, nv21Size)
                yOffset = nv21Size
                break
            }
            if (yPixelStride == 1) {
                yBuffer.get(nv21, yOffset, width)
            } else {
                yBuffer.get(yRowData, 0, width * yPixelStride)
                for (col in 0 until width) {
                    nv21[yOffset + col] = yRowData[col * yPixelStride]
                }
            }
            yOffset += width
        }
    }

    val uvWidth = ceil(width / 2.0).toInt()
    val uvHeight = ceil(height / 2.0).toInt()

    val uRowBytes = ByteArray(uRowStride)
    val vRowBytes = ByteArray(vRowStride)

    for (row in 0 until uvHeight) {
        val vRowPos = row * vRowStride
        val uRowPos = row * uRowStride

        if (vRowPos >= vBuffer.capacity() || uRowPos >= uBuffer.capacity()) {
            break
        }

        vBuffer.position(vRowPos)
        uBuffer.position(uRowPos)

        val vBytesToRead = min(vRowStride, vBuffer.remaining())
        val uBytesToRead = min(uRowStride, uBuffer.remaining())

        if (vBytesToRead > 0) {
            vBuffer.get(vRowBytes, 0, vBytesToRead)
            if (vBytesToRead < vRowStride) vRowBytes.fill(0, vBytesToRead, vRowStride)
        } else {
            vRowBytes.fill(0)
        }

        if (uBytesToRead > 0) {
            uBuffer.get(uRowBytes, 0, uBytesToRead)
            if (uBytesToRead < uRowStride) uRowBytes.fill(0, uBytesToRead, uRowStride)
        } else {
            uRowBytes.fill(0)
        }

        for (col in 0 until uvWidth) {
            val vIndex = col * vPixelStride
            val uIndex = col * uPixelStride

            if (vIndex >= vRowStride || uIndex >= uRowStride) {
                if (yOffset < nv21Size) nv21[yOffset++] = 128.toByte()
                if (yOffset < nv21Size) nv21[yOffset++] = 128.toByte()
                continue
            }

            if (yOffset + 1 < nv21Size) {
                nv21[yOffset++] = vRowBytes[vIndex]
                nv21[yOffset++] = uRowBytes[uIndex]
            } else {
                break
            }
        }
        if (yOffset >= nv21Size) break
    }

    if (yOffset < nv21Size) {
        nv21.fill(128.toByte(), yOffset, nv21Size)
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)

    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)

    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        ?: throw RuntimeException("BitmapFactory.decodeByteArray returned null")
}

// Enhanced surface detection status component
@Composable
fun SurfaceDetectionStatus(
    trackingState: TrackingState,
    surfacesDetected: Int,
    isPlacementReady: Boolean,
    detectionQuality: SurfaceDetectionManager.PlaneDetectionQuality
) {
    val statusColor by animateColorAsState(
        targetValue = when {
            trackingState != TrackingState.TRACKING -> Color.Red
            detectionQuality == SurfaceDetectionManager.PlaneDetectionQuality.NONE -> Color.Red
            detectionQuality in listOf(
                SurfaceDetectionManager.PlaneDetectionQuality.POOR, 
                SurfaceDetectionManager.PlaneDetectionQuality.FAIR
            ) -> Color.Yellow
            else -> DarkGreen
        },
        animationSpec = tween(300)
    )
    
    val statusText = when {
        trackingState != TrackingState.TRACKING -> "Iniciando seguimiento..."
        detectionQuality == SurfaceDetectionManager.PlaneDetectionQuality.NONE -> "Buscando superficies..."
        detectionQuality == SurfaceDetectionManager.PlaneDetectionQuality.POOR -> "Mejorar iluminación y superficies"
        detectionQuality == SurfaceDetectionManager.PlaneDetectionQuality.FAIR -> "Continúe moviendo el dispositivo"
        detectionQuality == SurfaceDetectionManager.PlaneDetectionQuality.GOOD -> "Superficies detectadas: $surfacesDetected"
        else -> "Excelente detección - Listo para colocar"
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                statusColor.copy(alpha = 0.1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Icon(
            imageVector = when (detectionQuality) {
                SurfaceDetectionManager.PlaneDetectionQuality.NONE -> Icons.Default.Search
                SurfaceDetectionManager.PlaneDetectionQuality.POOR -> Icons.Default.Warning
                SurfaceDetectionManager.PlaneDetectionQuality.FAIR -> Icons.Default.Refresh
                SurfaceDetectionManager.PlaneDetectionQuality.GOOD -> Icons.Default.Check
                SurfaceDetectionManager.PlaneDetectionQuality.EXCELLENT -> Icons.Default.CheckCircle
            },
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Enhanced surface detection overlay with visual feedback
@Composable
fun SurfaceDetectionOverlay(
    isPlacementReady: Boolean,
    surfacesDetected: Int,
    trackingState: TrackingState,
    lastTouchPosition: Pair<Float, Float>?,
    showPlacementIndicator: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // Draw crosshair when ready for placement
        if (isPlacementReady && trackingState == TrackingState.TRACKING) {
            val centerX = canvasWidth / 2
            val centerY = canvasHeight / 2
            val crosshairSize = 30.dp.toPx()
            
            drawLine(
                color = androidx.compose.ui.graphics.Color.White,
                start = androidx.compose.ui.geometry.Offset(centerX - crosshairSize, centerY),
                end = androidx.compose.ui.geometry.Offset(centerX + crosshairSize, centerY),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = androidx.compose.ui.graphics.Color.White,
                start = androidx.compose.ui.geometry.Offset(centerX, centerY - crosshairSize),
                end = androidx.compose.ui.geometry.Offset(centerX, centerY + crosshairSize),
                strokeWidth = 3.dp.toPx()
            )
            
            // Draw circle around crosshair
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White,
                radius = crosshairSize * 1.5f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // Draw placement indicator at touch position
        if (showPlacementIndicator && lastTouchPosition != null) {
            val (touchX, touchY) = lastTouchPosition
            drawCircle(
                color = DarkGreen.copy(alpha = 0.7f),
                radius = 40.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(touchX, touchY),
                style = Stroke(width = 4.dp.toPx())
            )
            
            // Animated ripple effect
            drawCircle(
                color = DarkGreen.copy(alpha = 0.3f),
                radius = 60.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(touchX, touchY),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
