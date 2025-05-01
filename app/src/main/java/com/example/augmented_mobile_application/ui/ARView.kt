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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import androidx.navigation.NavHostController
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF // Import RectF
import android.graphics.YuvImage
import android.media.Image // Import Android's Image class
import android.os.Handler
import android.os.HandlerThread
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
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import java.util.concurrent.TimeUnit // Import TimeUnit if needed for interruption logic later

private const val TAG = "ARView"
// Define the target class ID you are interested in (e.g., 82 for 'person' in COCO, adjust as needed)
private const val TARGET_CLASS_ID = 82 // Example: Person ID in COCO dataset

// Helper composable to manage YOLO detector lifecycle
@Composable
fun rememberYoloDetector(context: Context): YOLO11Detector? {
    // Define model and label paths within the 'pump' assets subfolder
    val modelPath = "pump/pump.tflite" // Updated path
    val labelsPath = "pump/classes.txt" // Updated path

    val detector = remember {
        try {
            Log.i(TAG, "Initializing YOLO11Detector...")
            YOLO11Detector(
                context = context,
                modelPath = modelPath,
                labelsPath = labelsPath,
                useNNAPI = true, // Prefer NNAPI
                useGPU = true    // Use GPU as fallback or if NNAPI fails
            ).also {
                Log.i(TAG, "YOLO11Detector initialized successfully.")
                Log.i(TAG, "Model Input Details: ${it.getInputDetails()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YOLODetector: ${e.message}", e)
            null // Return null if initialization fails
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


@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ARView(
    machine_selected: String,
    navController: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // Coroutine scope for launching detection

    // AR Scene state management
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var modelPlaced by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(true) }
    var instructionStep by remember { mutableStateOf(0) }

    // Maintenance process state
    var maintenanceStarted by remember { mutableStateOf(false) }

    // Detection State
    val yoloDetector = rememberYoloDetector(context)
    var detectionResults by remember { mutableStateOf<List<YOLO11Detector.Detection>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) } // Throttle detection
    var inferenceTimeMs by remember { mutableStateOf(0L) } // State for inference time
    var isTargetDetected by remember { mutableStateOf(false) } // State for target class detection

    // Instructions for maintenance steps
    val instructions = listOf(
        "Escanee una superficie plana y coloque el modelo 3D",
        "Verificar que la bomba esté apagada",
        "Inspeccionar el estado general de la bomba (Detección activa)", // Indicate detection
        "Comprobar conexiones eléctricas (Detección activa)",
        "Verificar el estado de las válvulas (Detección activa)",
        "Mantenimiento completado con éxito"
    )

    // References to ARSceneView and nodes
    val arSceneViewRef = remember { mutableStateOf<ARSceneView?>(null) }
    val modelNodeRef = remember { mutableStateOf<ModelNode?>(null) }
    val anchorNodeRef = remember { mutableStateOf<AnchorNode?>(null) }

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
                // Create ARSceneView and assign to a variable
                val sceneView = ARSceneView(ctx).apply {
                    arSceneViewRef.value = this // Store the reference

                    // Configure AR session settings
                    configureSession { session, config ->
                        config.focusMode = Config.FocusMode.AUTO
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE // Crucial for getting frames
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    }

                    planeRenderer.isEnabled = true

                    // Handle AR tracking failures
                    onTrackingFailureChanged = { reason ->
                        trackingFailureReason = reason
                    }

                    // Set up frame listener for DETECTION processing
                    onFrame = { frameTime -> // frameTime is provided
                        val currentSceneView = arSceneViewRef.value
                        val currentFrame: ArFrame? = currentSceneView?.frame

                        // Process frame only when needed and detector is ready
                        if (maintenanceStarted && modelPlaced && !isDetecting && yoloDetector != null && currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
                            try {
                                currentFrame.acquireCameraImage().use { image: Image? ->
                                    if (image != null) {
                                        isDetecting = true // Set flag

                                        val bitmap: Bitmap = image.toBitmap() // Convert YUV Image to Bitmap

                                        // Launch detection in a background thread
                                        scope.launch(Dispatchers.IO) {
                                            var results: List<YOLO11Detector.Detection> = emptyList()
                                            var timeTaken: Long = 0
                                            try {
                                                Log.d(TAG, "Starting detection...")
                                                // Call detector and get results + time
                                                val detectionPair = yoloDetector.detect(bitmap)
                                                results = detectionPair.first
                                                timeTaken = detectionPair.second
                                                Log.d(TAG, "Detection finished: ${results.size} results in ${timeTaken}ms")

                                                // Check if the target class is detected
                                                val targetFound = results.any { it.classId == TARGET_CLASS_ID }

                                                // Update state on the main thread
                                                withContext(Dispatchers.Main) {
                                                    detectionResults = results
                                                    inferenceTimeMs = timeTaken // Update inference time state
                                                    isTargetDetected = targetFound // Update target detected state
                                                }

                                            } catch (e: Exception) {
                                                Log.e(TAG, "Detection failed: ${e.message}", e)
                                                // Reset state on error
                                                withContext(Dispatchers.Main) {
                                                    detectionResults = emptyList()
                                                    inferenceTimeMs = 0L
                                                    isTargetDetected = false
                                                }
                                            } finally {
                                                if (!bitmap.isRecycled) {
                                                    bitmap.recycle()
                                                }
                                                // Reset detection flag on main thread after processing
                                                withContext(Dispatchers.Main) {
                                                    isDetecting = false
                                                }
                                            }
                                        }
                                    } else {
                                        Log.w(TAG, "Acquired image is null")
                                    }
                                } // image.close() is called automatically by use {}
                            } catch (e: NotYetAvailableException) {
                                Log.w(TAG, "Frame image not yet available.")
                            } catch (e: ResourceExhaustedException) {
                                Log.e(TAG, "ARCore ResourceExhaustedException: Too many images acquired.")
                                isDetecting = false // Reset flag on error
                            } catch (e: IllegalStateException) {
                                Log.e(TAG, "IllegalStateException during image processing: ${e.message}")
                                isDetecting = false // Reset flag on error
                            } catch (e: Exception) {
                                Log.e(TAG, "Error acquiring/processing frame: ${e.message}", e)
                                isDetecting = false // Reset flag on error
                            }
                        } else {
                            // Conditions not met for detection
                            // Optionally clear results/time if detection stops actively
                            if (detectionResults.isNotEmpty() || inferenceTimeMs != 0L || isTargetDetected) {
                                detectionResults = emptyList()
                                inferenceTimeMs = 0L
                                isTargetDetected = false
                            }
                            // Reset flag if stuck
                            if (isDetecting && (!maintenanceStarted || !modelPlaced || currentFrame?.camera?.trackingState != TrackingState.TRACKING)) {
                                isDetecting = false
                            }
                        }
                    }

                    // Load the pump model
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
                sceneView // Return the created sceneView instance
            }
        )

        // Detection Overlay - Pass target info if needed for highlighting
        DrawDetectionsOverlay(
            detections = detectionResults,
            detector = yoloDetector,
            targetClassId = TARGET_CLASS_ID, // Pass target ID
            isTargetDetected = isTargetDetected, // Pass detection status
            modifier = Modifier.fillMaxSize()
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
                    Spacer(modifier = Modifier.height(4.dp)) // Add spacer

                    // Display Inference Time and Target Status
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

            // Bottom control buttons and maintenance navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (modelPlaced) {
                    // "Iniciar Mantenimiento" button
                    Button(
                        onClick = {
                            maintenanceStarted = true
                            instructionStep = 1
                            // Reset detection state when starting
                            detectionResults = emptyList()
                            inferenceTimeMs = 0L
                            isTargetDetected = false
                            isDetecting = false
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

                    // Navigation buttons for maintenance steps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (instructionStep > 1) {
                                    instructionStep--
                                    // Clear state on step change
                                    detectionResults = emptyList()
                                    inferenceTimeMs = 0L
                                    isTargetDetected = false
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
                                    // Clear state on step change
                                    detectionResults = emptyList()
                                    inferenceTimeMs = 0L
                                    isTargetDetected = false
                                } else {
                                    // Complete maintenance procedure
                                    maintenanceStarted = false // Stop frame processing
                                    detectionResults = emptyList()
                                    inferenceTimeMs = 0L
                                    isTargetDetected = false
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

// Helper composable to draw detections on a Canvas overlay
@Composable
fun DrawDetectionsOverlay(
    detections: List<YOLO11Detector.Detection>,
    detector: YOLO11Detector?, // Needed for class names
    targetClassId: Int,        // ID of the target class to highlight
    isTargetDetected: Boolean, // Whether the target is currently detected
    modifier: Modifier = Modifier
) {
    // Use remember for paints to avoid recreating them on every recomposition
    val boxPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f // Adjust thickness
            // Default color set per detection below
        }
    }
    val targetBoxPaint = remember { // Specific paint for the target
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 8f // Thicker border for target
            color = android.graphics.Color.GREEN // Highlight color for target
        }
    }
    val textPaint = remember {
        android.graphics.Paint().apply {
            textSize = 40f // Adjust text size
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
    }
    val textBackgroundPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
            // Default color set per detection below
        }
    }
    val targetTextBackgroundPaint = remember { // Specific background for target label
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.GREEN // Highlight color for target label bg
        }
    }

    // Use Compose Canvas for drawing
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            detections.forEach { detection ->
                val className = detector?.getClassName(detection.classId) ?: "ID: ${detection.classId}"
                val label = "$className: ${"%.2f".format(detection.conf)}"

                // Determine if this is the target class
                val isTarget = detection.classId == targetClassId

                // Select appropriate paints based on whether it's the target
                val currentBoxPaint = if (isTarget) targetBoxPaint else boxPaint
                val currentTextBgPaint = if (isTarget) targetTextBackgroundPaint else textBackgroundPaint

                // Set colors for non-target detections
                if (!isTarget) {
                    val color = detector?.classColors?.getOrNull(detection.classId % (detector.classColors.size ?: 1)) ?: intArrayOf(255, 0, 0)
                    val androidColor = android.graphics.Color.rgb(color[0], color[1], color[2])
                    currentBoxPaint.color = androidColor
                    currentTextBgPaint.color = androidColor
                } else {
                    // Ensure target paints have the correct highlight color (already set, but safe)
                    targetBoxPaint.color = android.graphics.Color.GREEN
                    targetTextBackgroundPaint.color = android.graphics.Color.GREEN
                }

                // Draw bounding box using nativeCanvas
                canvas.nativeCanvas.drawRect(
                    detection.box.x.toFloat(),
                    detection.box.y.toFloat(),
                    (detection.box.x + detection.box.width).toFloat(),
                    (detection.box.y + detection.box.height).toFloat(),
                    currentBoxPaint // Use selected paint
                )

                // Draw label text with background using nativeCanvas
                val textBounds = android.graphics.Rect()
                textPaint.getTextBounds(label, 0, label.length, textBounds)
                val textWidth = textBounds.width()
                val textHeight = textBounds.height()

                // Calculate background position (slightly above the box)
                val textBgLeft = detection.box.x.toFloat()
                val textBgTop = detection.box.y.toFloat() - textHeight - 10f // Position above box
                val textBgRight = detection.box.x.toFloat() + textWidth + 10f
                val textBgBottom = detection.box.y.toFloat()

                // Ensure background doesn't go off-screen top
                val clampedTextBgTop = maxOf(0f, textBgTop)
                // Ensure min height, adjust based on actual text height
                val clampedTextBgBottom = clampedTextBgTop + textHeight + 10f // Relative to clamped top

                // Draw background rectangle
                canvas.nativeCanvas.drawRect(
                    textBgLeft, clampedTextBgTop, textBgRight, clampedTextBgBottom,
                    currentTextBgPaint // Use selected paint
                )

                // Draw text (adjust Y position based on clamped background)
                // Position text inside the background box, vertically centered roughly
                val textY = clampedTextBgTop + textHeight + 5f - (textPaint.descent() / 2) // Adjust for text baseline
                canvas.nativeCanvas.drawText(
                    label,
                    detection.box.x.toFloat() + 5f, // Padding
                    textY, // Use calculated Y
                    textPaint
                )
            }
        }
    }
}

// Helper function to convert ARCore Image (YUV_420_888) to Bitmap
fun Image.toBitmap(): Bitmap {
    if (format != ImageFormat.YUV_420_888) {
        throw IllegalArgumentException("Invalid image format, expected YUV_420_888, got $format")
    }

    val yBuffer = planes[0].buffer // Y plane
    val uBuffer = planes[1].buffer // U plane (Cb)
    val vBuffer = planes[2].buffer // V plane (Cr)

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    // Copy Y plane
    yBuffer.get(nv21, 0, ySize)

    // Copy VU planes (interleaved)
    val vPixelStride = planes[2].pixelStride
    val uPixelStride = planes[1].pixelStride
    val vRowStride = planes[2].rowStride
    val uRowStride = planes[1].rowStride

    val vuOrder = ByteArray(uSize + vSize)
    var vIndex = 0
    var uIndex = 0

    if (vPixelStride == 2 && uPixelStride == 2 && vRowStride == uRowStride) {
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val vuBuffer = ByteArray(vSize + uSize)
        vBuffer.get(vuBuffer, 0, vSize)
        uBuffer.get(vuBuffer, vSize, uSize)

        var outputIndex = ySize
        var vInputIndex = 0
        var uInputIndex = vSize
        val uvWidth = width / 2
        val uvHeight = height / 2
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vuBaseIndex = row * vRowStride + col * vPixelStride
                if (vuBaseIndex < vSize && vuBaseIndex < uSize) {
                    nv21[outputIndex++] = vuBuffer[vInputIndex]
                    nv21[outputIndex++] = vuBuffer[uInputIndex]
                    vInputIndex += vPixelStride
                    uInputIndex += uPixelStride
                } else {
                    if (outputIndex < nv21.size) nv21[outputIndex++] = 128.toByte()
                    if (outputIndex < nv21.size) nv21[outputIndex++] = 128.toByte()
                }
            }
            vInputIndex = (row + 1) * vRowStride
            uInputIndex = vSize + (row + 1) * uRowStride
        }
    } else {
        Log.w(TAG, "Using simplified YUV->NV21 conversion due to unexpected strides.")
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
    }

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)

    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
