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
import android.os.Handler
import android.os.HandlerThread
// Correct SceneView 2.2.1 imports
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.math.Scale
import com.google.ar.core.Config
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.Frame as ArFrame

// OpenCV imports
import org.opencv.core.Mat
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import org.opencv.core.Size
import org.opencv.core.Core
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import java.text.DecimalFormat

private const val TAG = "ARView"

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ARView(
    machine_selected: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // AR Scene state management
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var modelPlaced by remember { mutableStateOf(false) }
    var frameProcessingEnabled by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(true) }
    var instructionStep by remember { mutableStateOf(0) }
    
    // Maintenance process state
    var maintenanceStarted by remember { mutableStateOf(false) }
    
    // FPS tracking
    var fps by remember { mutableStateOf(0.0) }
    val fpsFormat = remember { DecimalFormat("0.0") }

    // Processing thread for OpenCV
    val processingThread = remember { HandlerThread("OpenCVProcessing").apply { start() } }
    val processingHandler = remember { Handler(processingThread.looper) }
    
    // Frequency control for FPS calculation
    val fpsUpdateIntervalMs = 500L
    var lastFpsUpdateTime = remember { 0L }
    var frameCount = remember { 0 }

    // Instructions for maintenance steps
    val instructions = listOf(
        "Escanee una superficie plana y coloque el modelo 3D",
        "Verificar que la bomba esté apagada",
        "Inspeccionar el estado general de la bomba",
        "Comprobar conexiones eléctricas",
        "Verificar el estado de las válvulas",
        "Mantenimiento completado con éxito"
    )

    // References to ARSceneView and nodes
    val arSceneViewRef = remember { mutableStateOf<ARSceneView?>(null) }
    val modelNodeRef = remember { mutableStateOf<ModelNode?>(null) }
    val anchorNodeRef = remember { mutableStateOf<AnchorNode?>(null) }

    // OpenCV setup - ensure it's loaded before any frame processing
    LaunchedEffect(Unit) {
        try {
            System.loadLibrary("opencv_java4")
            Log.d(TAG, "OpenCV library loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OpenCV library: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
    
    // Cleanup resources when component is destroyed
    DisposableEffect(Unit) {
        onDispose {
            processingThread.quitSafely()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // AR SceneView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Handle model placement on tap if not already placed
                            arSceneViewRef.value?.let { arSceneView ->
                                val hitResults = arSceneView.frame?.hitTest(event.x, event.y)
                                if (!modelPlaced && !hitResults.isNullOrEmpty()) {
                                    hitResults.firstOrNull()?.let { hit ->
                                        // Place model at hit point
                                        val anchor = hit.createAnchor()
                                        val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = anchor)
                                        // Use entity-based addition
                                        arSceneView.scene.addEntity(anchorNode.entity)
                                        
                                        modelNodeRef.value?.let { modelNode ->
                                            // Add model to scene directly
                                            arSceneView.scene.addEntity(modelNode.entity)
                                            // Position model relative to anchor
                                            modelNode.transform.position = anchorNode.transform.position

                                            modelPlaced = true
                                            anchorNodeRef.value = anchorNode
                                        }
                                    }
                                }
                            }
                            false
                        }
                        else -> false
                    }
                },
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    arSceneViewRef.value = this

                    // Configure AR session settings
                    configureSession { session, config ->
                        config.focusMode = Config.FocusMode.AUTO
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    }

                    planeRenderer.isEnabled = true

                    // Handle AR tracking failures
                    onTrackingFailureChanged = { reason ->
                        trackingFailureReason = reason
                    }

                    // Set up frame listener for OpenCV processing
                    onFrame = { arFrame ->
                        if (frameProcessingEnabled && maintenanceStarted) {
                            frameCount++
                            val currentTime = System.currentTimeMillis()
                            
                            // Update FPS calculation at specified intervals
                            if (currentTime - lastFpsUpdateTime > fpsUpdateIntervalMs) {
                                val timeSpan = (currentTime - lastFpsUpdateTime) / 1000.0
                                if (timeSpan > 0) {
                                    fps = frameCount / timeSpan
                                    frameCount = 0
                                    lastFpsUpdateTime = currentTime
                                }
                            }
                            
                            // Process frame in background thread - capture the ARSceneView reference safely
                            val sceneView = arSceneViewRef.value
                            if (sceneView != null) {
                                try {
                                    // Use frame directly instead of trying to access camera texture
                                    processingHandler.post {
                                        try {
                                            // Create bitmap from current view
                                            val bitmap = createBitmapFromTexture(sceneView)
                                            // Convert bitmap to OpenCV Mat
                                            val mat = Mat()
                                            Utils.bitmapToMat(bitmap, mat)
                                            
                                            // Process frame with OpenCV
                                            val processedMat = handlePreprocessing(mat)
                                            
                                            // Clean up
                                            mat.release()
                                            processedMat.release()
                                            bitmap.recycle()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error processing frame: ${e.message}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to process frame: ${e.message}")
                                }
                            }
                        }
                    }

                    // Load the pump model from assets/pump/pump.glb
                    scope.launch {
                        try {
                            val modelNode = ModelNode(
                                modelInstance = modelLoader.createModelInstance(
                                    assetFileLocation = "pump/pump.glb"
                                ),
                                scaleToUnits = 1.0f
                            ).apply {
                                isShadowReceiver = false
                            }
                            modelNodeRef.value = modelNode
                            isLoadingModel = false
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load model: ${e.message ?: "Unknown error"}")
                            isLoadingModel = false
                        }
                    }
                }
            }
        )

        // UI Overlay for instructions and buttons
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
                    
                    // Show FPS when maintenance has started
                    if (maintenanceStarted && frameProcessingEnabled) {
                        Text(
                            text = "FPS: ${fpsFormat.format(fps)}",
                            fontSize = 14.sp,
                            color = DarkGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
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

            // Bottom control buttons and maintenance navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (modelPlaced) {
                    // "Inicial Mantenimiento" button
                    Button(
                        onClick = {
                            // Enable frame processing and update the instruction step
                            frameProcessingEnabled = true
                            maintenanceStarted = true
                            instructionStep = 1
                            // Reset FPS tracking
                            lastFpsUpdateTime = System.currentTimeMillis()
                            frameCount = 0
                        },
                        enabled = modelPlaced && !maintenanceStarted,
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

                    // Navigation buttons for maintenance steps - only enabled after maintenance starts
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
                                } else {
                                    // Complete maintenance procedure
                                    frameProcessingEnabled = false
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
                    // Prompt message when no model is yet placed
                    Text(
                        text = "Mueva la cámara para detectar superficies planas y toque para colocar el modelo",
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

// Helper function to create a bitmap from AR scene
fun createBitmapFromTexture(arSceneView: ARSceneView): Bitmap {
    val width = arSceneView.width
    val height = arSceneView.height
    
    // Create a bitmap of the current view
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Draw the view to a canvas which renders to the bitmap
    val canvas = android.graphics.Canvas(bitmap)
    arSceneView.draw(canvas)
    
    return bitmap
}

// Function to prepare frames for future OpenCV processing
fun handlePreprocessing(inputFrame: Mat): Mat {
    val processedFrame = inputFrame.clone()
    
    // First step: Convert to grayscale for better processing
    Imgproc.cvtColor(processedFrame, processedFrame, Imgproc.COLOR_RGBA2GRAY)
    
    // Apply Gaussian blur to reduce noise
    Imgproc.GaussianBlur(processedFrame, processedFrame, Size(5.0, 5.0), 0.0)
    
    // Apply Canny edge detection to highlight edges
    Imgproc.Canny(processedFrame, processedFrame, 50.0, 150.0)
    
    // Log frame dimensions for debugging purposes
    Log.d(TAG, "Processing frame: ${inputFrame.width()} x ${inputFrame.height()}")
    
    return processedFrame
}

// Helper function to convert an OpenCV Mat to a Bitmap for display if needed
fun matToBitmap(mat: Mat): Bitmap {
    val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, bitmap)
    return bitmap
}
