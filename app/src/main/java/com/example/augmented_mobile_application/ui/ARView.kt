package com.example.augmented_mobile_application.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import androidx.navigation.NavHostController
import android.graphics.Bitmap

// Correct SceneView 2.2.1 imports
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.ArFrame
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.PlacementMode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import com.google.ar.core.Config
import com.google.ar.core.TrackingFailureReason

// OpenCV imports
import org.opencv.core.Mat
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import kotlinx.coroutines.launch

private const val TAG = "ARView"

@Composable
fun ARView(
    machine_selected: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // AR Scene setup and state
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var modelPlaced by remember { mutableStateOf(false) }
    var frameProcessingEnabled by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(true) }
    var instructionStep by remember { mutableStateOf(0) }
    
    // Instructions for maintenance steps
    val instructions = listOf(
        "Escanee una superficie plana y coloque el modelo 3D",
        "Verificar que la bomba esté apagada",
        "Inspeccionar el estado general de la bomba",
        "Comprobar conexiones eléctricas",
        "Verificar el estado de las válvulas",
        "Mantenimiento completado con éxito"
    )

    // Reference to the ARSceneView and model node
    val arSceneViewRef = remember { mutableStateOf<ARSceneView?>(null) }
    val modelNodeRef = remember { mutableStateOf<ArModelNode?>(null) }
    
    // OpenCV setup
    LaunchedEffect(Unit) {
        try {
            System.loadLibrary("opencv_java4")
            Log.d(TAG, "OpenCV library loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OpenCV library: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // AR SceneView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    arSceneViewRef.value = this
                    
                    // Configure AR session
                    configureSession { arSession, config ->
                        config.focusMode = Config.FocusMode.AUTO
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    }
                    
                    // Create and configure the model node
                    val modelNode = ArModelNode(placementMode = PlacementMode.BEST_AVAILABLE).apply {
                        // Set initial scale and rotation
                        scale = Position(1.0f, 1.0f, 1.0f)
                        rotation = Rotation(0.0f, 0.0f, 0.0f)
                        
                        // Load the 3D model asynchronously with explicit type parameters
                        loadModelGlbAsync(
                            context = context,
                            glbFileLocation = "pump/pump_model.glb",
                            autoAnimate = true,
                            scaleToUnits = 0.5f, // Adjust scale as needed
                            centerOrigin = Position(0.0f, 0.0f, 0.0f),
                            onError = { exception ->
                                Log.e(TAG, "Error loading model: ${exception.message}")
                                isLoadingModel = false
                            },
                            onLoaded = { _, _ ->
                                Log.d(TAG, "Model loaded successfully")
                                isLoadingModel = false
                            }
                        )
                        
                        // Set placement listener to know when the model is placed
                        onAnchorChanged = { anchor ->
                            if (anchor != null && !modelPlaced) {
                                modelPlaced = true
                                instructionStep = 1
                            }
                        }
                    }
                    
                    // Add the model node to the scene
                    addChild(modelNode)
                    modelNodeRef.value = modelNode
                    
                    // Set scene frame update listener for OpenCV processing
                    onArFrame = { arFrame ->
                        // Update tracking state info
                        val camera = arFrame.camera
                        if (camera.trackingState == com.google.ar.core.TrackingState.TRACKING) {
                            trackingFailureReason = null
                        } else if (camera.trackingState == com.google.ar.core.TrackingState.PAUSED) {
                            trackingFailureReason = camera.trackingFailureReason
                        }
                        
                        // Process frame with OpenCV if enabled
                        if (frameProcessingEnabled && camera.trackingState == com.google.ar.core.TrackingState.TRACKING) {
                            try {
                                // Get the camera image
                                arFrame.acquireCameraImage()?.use { image ->
                                    // Convert camera image to OpenCV Mat for processing
                                    // This would be implemented in a real app
                                    // Currently, we're just preparing the infrastructure
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing frame: ${e.message}")
                            }
                        }
                    }
                }
            }
        )
        
        // UI Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top information card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
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
                    
                    if (isLoadingModel) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = DarkGreen
                        )
                    }
                    
                    if (trackingFailureReason != null) {
                        Text(
                            text = when (trackingFailureReason) {
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
            
            // Bottom control buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (modelPlaced) {
                    // Show the "Inicial Mantenimiento" button when model is placed
                    Button(
                        onClick = {
                            // Start maintenance procedure
                            frameProcessingEnabled = true
                            instructionStep = 1
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkGreen
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Inicial Mantenimiento",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                    
                    // Navigation buttons for maintenance steps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (instructionStep > 1) {
                                    instructionStep--
                                }
                            },
                            enabled = instructionStep > 1,
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
                                } else {
                                    // Complete maintenance
                                    navController.navigateUp()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGreen
                            )
                        ) {
                            Text(
                                if (instructionStep < instructions.size - 1) "Siguiente" else "Finalizar"
                            )
                        }
                    }
                } else {
                    // Initial help message
                    Text(
                        text = "Mueva la cámara para detectar superficies planas",
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

// Function to handle preprocessing of frames with OpenCV
fun handlePreprocessing(inputFrame: Mat): Mat {
    // This function prepares frames for future processing with OpenCV
    // Currently, we just return a copy of the original frame without modifications
    // In future iterations, this could include:
    // - Color space conversion
    // - Noise reduction
    // - Edge detection
    // - Feature extraction
    
    val processedFrame = inputFrame.clone()
    
    // Log the frame dimensions for debugging
    Log.d(TAG, "Processing frame: ${inputFrame.width()} x ${inputFrame.height()}")
    
    // You could apply basic transformations here
    // Example (commented out):
    // Imgproc.cvtColor(inputFrame, processedFrame, Imgproc.COLOR_RGB2GRAY)
    // Imgproc.GaussianBlur(processedFrame, processedFrame, Size(5.0, 5.0), 0.0)
    
    return processedFrame
}


