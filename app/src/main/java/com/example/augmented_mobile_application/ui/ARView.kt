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
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import android.view.MotionEvent
import androidx.compose.ui.ExperimentalComposeUiApi

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
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Placeholder for raycast handling
                            arSceneViewRef.value?.let { arSceneView ->
                                val hitResults = arSceneView.frame?.hitTest(event.x, event.y)
                                hitResults?.forEach { hit ->
                                    // Process hit result if needed
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
                            // Optionally, place the model as soon as it is loaded
                            modelPlaced = true
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
                                    // Complete maintenance procedure
                                    navController.navigateUp()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkGreen
                            )
                        ) {
                            Text(text = if (instructionStep < instructions.size - 1) "Siguiente" else "Finalizar")
                        }
                    }
                } else {
                    // Prompt message when no model is yet placed
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

// Function to prepare frames for future OpenCV processing
fun handlePreprocessing(inputFrame: Mat): Mat {
    // Clone the input frame to prepare for future processing steps
    val processedFrame = inputFrame.clone()

    // Log frame dimensions for debugging purposes
    Log.d(TAG, "Processing frame: ${inputFrame.width()} x ${inputFrame.height()}")

    // No transformation applied at this stage per requirements
    // Future processing steps might include grayscale conversion, blurring, edge detection, etc.
    // Examples (currently commented out):
    // Imgproc.cvtColor(inputFrame, processedFrame, Imgproc.COLOR_RGB2GRAY)
    // Imgproc.GaussianBlur(processedFrame, processedFrame, Size(5.0, 5.0), 0.0)
    // Imgproc.Canny(processedFrame, processedFrame, 50.0, 150.0)

    return processedFrame
}

// Helper function to convert an OpenCV Mat to a Bitmap for display if needed
fun matToBitmap(mat: Mat): Bitmap {
    val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(mat, bitmap)
    return bitmap
}
