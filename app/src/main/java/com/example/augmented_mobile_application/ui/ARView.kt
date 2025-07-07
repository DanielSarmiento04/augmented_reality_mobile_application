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
import com.example.augmented_mobile_application.ar.ModelPlacementCoordinator
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
    
    // Check ARCore availability
    LaunchedEffect(Unit) {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        Log.i(TAG, "ARCore availability: $availability")
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

    // Core AR state
    var isLoadingModel by remember { mutableStateOf(true) }
    var maintenanceStarted by remember { mutableStateOf(false) }
    var modelPlaced by remember { mutableStateOf(false) }
    
    // Surface detection state
    var isPlacementReady by remember { mutableStateOf(false) }

    // Get instructions from loaded routine or use default
    val instructions = currentRoutine?.steps?.map { it.instruction } ?: listOf(
        "Mueva lentamente el dispositivo para detectar superficies planas",
        "Verificar que la bomba esté apagada",
        "Inspeccionar el estado general de la bomba",
        "Mantenimiento completado con éxito"
    )

    val arSceneViewRef = remember { mutableStateOf<ARSceneView?>(null) }
    val modelPlacementCoordinator = remember { mutableStateOf<ModelPlacementCoordinator?>(null) }

    // Initialize model placement coordinator when ARSceneView is ready
    LaunchedEffect(arSceneViewRef.value) {
        arSceneViewRef.value?.let { sceneView ->
            modelPlacementCoordinator.value = ModelPlacementCoordinator(sceneView).also { coordinator ->
                // Load the 3D model
                scope.launch {
                    val modelLoaded = coordinator.loadModel(modelPath)
                    isLoadingModel = !modelLoaded
                    if (modelLoaded) {
                        Log.i(TAG, "3D model loaded successfully")
                    } else {
                        Log.e(TAG, "Failed to load 3D model")
                    }
                }
            }
            Log.i(TAG, "ModelPlacementCoordinator initialized")
        }
    }

    // Cleanup ARSceneView on dispose
    DisposableEffect(Unit) {
        onDispose {
            Log.i(TAG, "Disposing ARSceneView")
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
        // Simplified AndroidView for ARSceneView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Log.i(TAG, "Creating ARSceneView...")
                val sceneView = ARSceneView(ctx).apply {
                    Log.i(TAG, "ARSceneView created successfully")

                    configureSession { session, config ->
                        Log.i(TAG, "Configuring ARCore session...")
                        config.focusMode = Config.FocusMode.AUTO
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        
                        // Enable depth for better tracking if supported
                        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            config.depthMode = Config.DepthMode.AUTOMATIC
                            Log.i(TAG, "Depth mode enabled")
                        }
                        
                        Log.i(TAG, "ARCore session configured")
                    }

                    // Enable plane visualization
                    planeRenderer.isEnabled = true

                    onFrame = { frameTime ->
                        val currentFrame: ArFrame? = this.frame
                        // Simple frame handling for surface detection
                        if (currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
                            val planes = currentFrame.getUpdatedTrackables(Plane::class.java)
                            isPlacementReady = planes.isNotEmpty() && planes.any { it.trackingState == TrackingState.TRACKING }
                        }
                    }

                    // Store reference for later use
                    arSceneViewRef.value = this
                }
                Log.i(TAG, "Returning ARSceneView from factory")
                sceneView
            }
        )

        // Simple touch handling for model placement
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (maintenanceStarted && !modelPlaced && isPlacementReady) {
                                arSceneViewRef.value?.let { arSceneView ->
                                    val frame = arSceneView.frame
                                    if (frame != null && frame.camera.trackingState == TrackingState.TRACKING) {
                                        try {
                                            val hitResults = frame.hitTest(event.x, event.y)
                                            val validHit = hitResults.find { hit ->
                                                val trackable = hit.trackable
                                                trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)
                                            }
                                            
                                            if (validHit != null) {
                                                val placementSuccess = modelPlacementCoordinator.value?.placeModelAtHitResult(validHit)
                                                if (placementSuccess == true) {
                                                    modelPlaced = true
                                                    currentStepIndex = maxOf(1, currentStepIndex)
                                                    Log.i(TAG, "Model placed successfully")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error during model placement: ${e.message}", e)
                                        }
                                    }
                                }
                            }
                            false
                        }
                        else -> false
                    }
                }
        )

        // Main UI overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top instruction card
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
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!maintenanceStarted) {
                                maintenanceStarted = true
                                currentStepIndex = 1
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