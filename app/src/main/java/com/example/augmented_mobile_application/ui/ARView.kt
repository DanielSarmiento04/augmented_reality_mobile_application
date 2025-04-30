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
import com.example.augmented_mobile_application.ai.TFLiteModelManager
import com.example.augmented_mobile_application.ai.YOLO11Detector
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException

private const val TAG = "ARView"

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
    // var frameProcessingEnabled by remember { mutableStateOf(false) } // Controlled by maintenanceStarted
    var isLoadingModel by remember { mutableStateOf(true) }
    var instructionStep by remember { mutableStateOf(0) }

    // Maintenance process state
    var maintenanceStarted by remember { mutableStateOf(false) }

    // Detection State
    val yoloDetector = rememberYoloDetector(context)
    var detectionResults by remember { mutableStateOf<List<YOLO11Detector.Detection>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) } // Throttle detection

    // Remove FPS tracking from ARView, rely on detector logs if needed
    // var fps by remember { mutableStateOf(0.0) }
    // val fpsFormat = remember { DecimalFormat("0.0") }

    // Remove OpenCV processing thread - detection runs via coroutines
    // val processingThread = remember { HandlerThread("OpenCVProcessing").apply { start() } }
    // val processingHandler = remember { Handler(processingThread.looper) }

    // Remove FPS update logic
    // val fpsUpdateIntervalMs = 500L
    // var lastFpsUpdateTime = remember { 0L }
    // var frameCount = remember { 0 }

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
                        // Consider setting CPU image resolution if needed, but defaults are usually fine
                        // session.setCameraConfig(session.cameraConfig.apply { ... })
                    }

                    planeRenderer.isEnabled = true

                    // Handle AR tracking failures
                    onTrackingFailureChanged = { reason ->
                        trackingFailureReason = reason
                    }

                    // Set up frame listener for DETECTION processing
                    onFrame = { frameTime -> // frameTime is provided, not arFrame directly
                        // Access the underlying ARCore Frame via the session
                        // Use the arSceneViewRef to get the current ARSceneView instance
                        val currentSceneView = arSceneViewRef.value
                        // Access the current ARCore Frame using the frame property (not arFrame)
                        // In SceneView 2.2.1, the property is named "frame" not "arFrame"
                        val currentFrame: ArFrame? = currentSceneView?.frame // Corrected property name

                        // Process frame only when needed and detector is ready
                        if (maintenanceStarted && modelPlaced && !isDetecting && yoloDetector != null && currentFrame != null && currentFrame.camera.trackingState == TrackingState.TRACKING) {
                            // Acquire the camera image from the ARCore Frame
                            try {
                                // Use try-with-resources on ARCore Image (android.media.Image)
                                currentFrame.acquireCameraImage().use { image: Image? -> // Explicit type
                                    if (image != null) {
                                        isDetecting = true // Set flag to prevent concurrent runs

                                        // Convert YUV Image to Bitmap (offload to background if slow)
                                        // Ensure image is android.media.Image before calling toBitmap
                                        val bitmap: Bitmap = image.toBitmap() // Call extension function

                                        // Launch detection in a background thread
                                        scope.launch(Dispatchers.IO) {
                                            var results: List<YOLO11Detector.Detection> = emptyList()
                                            try {
                                                Log.d(TAG, "Starting detection...")
                                                // Pass the bitmap to the detector
                                                results = yoloDetector.detect(bitmap)
                                                Log.d(TAG, "Detection finished: ${results.size} results")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Detection failed: ${e.message}", e)
                                            } finally {
                                                 // Recycle the bitmap only if it's mutable and you created it,
                                                 // or if you are sure it's no longer needed elsewhere.
                                                 // The bitmap from toBitmap() might be safe to recycle here.
                                                if (!bitmap.isRecycled) {
                                                    bitmap.recycle()
                                                }
                                                // Update state on the main thread
                                                withContext(Dispatchers.Main) {
                                                    detectionResults = results
                                                    isDetecting = false // Reset flag
                                                }
                                            }
                                        }
                                    } else {
                                        Log.w(TAG, "Acquired image is null")
                                        // If image is null, reset detection flag if it was set
                                        // isDetecting = false // No need to reset if it wasn't set true
                                    }
                                } // image.close() is called automatically by use {}
                            } catch (e: NotYetAvailableException) {
                                // Frame image not ready, common case, just wait for next frame
                                Log.w(TAG, "Frame image not yet available.")
                                // Do not set isDetecting = false here, as no attempt was made
                            } catch (e: ResourceExhaustedException) {
                                Log.e(TAG, "ARCore ResourceExhaustedException: Too many images acquired.")
                                isDetecting = false // Reset flag on error
                            } catch (e: IllegalStateException) {
                                // Can happen if the image is closed prematurely or session is paused
                                Log.e(TAG, "IllegalStateException during image processing: ${e.message}")
                                isDetecting = false // Reset flag on error
                            } catch (e: Exception) {
                                // Handle other exceptions during image acquisition/processing
                                Log.e(TAG, "Error acquiring/processing frame: ${e.message}", e)
                                isDetecting = false // Reset flag on error
                            }
                        } else {
                            // Clear detections if conditions are not met (e.g., tracking lost, maintenance not started)
                            if (detectionResults.isNotEmpty()) {
                                detectionResults = emptyList()
                            }
                            // If detection was somehow stuck, reset the flag
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

        // Detection Overlay - Drawn on top of the ARView
        DrawDetectionsOverlay(
            detections = detectionResults,
            detector = yoloDetector, // Pass detector for class names
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
                            // Enable frame processing by setting maintenanceStarted
                            maintenanceStarted = true
                            instructionStep = 1 // Move to first step after placement
                            // Reset detection state if needed
                            detectionResults = emptyList()
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

                    // Navigation buttons for maintenance steps - only enabled after maintenance starts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (instructionStep > 1) {
                                    instructionStep--
                                    detectionResults = emptyList() // Clear detections on step change
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
                                    detectionResults = emptyList() // Clear detections on step change
                                } else {
                                    // Complete maintenance procedure
                                    maintenanceStarted = false // Stop frame processing
                                    detectionResults = emptyList()
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
    modifier: Modifier = Modifier
) {
    // Use remember for paints to avoid recreating them on every recomposition
    val boxPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f // Adjust thickness
            color = android.graphics.Color.RED // Default color
        }
    }
    val textPaint = remember {
        android.graphics.Paint().apply {
            textSize = 40f // Adjust text size
            color = android.graphics.Color.WHITE
            // Set anti-alias for smoother text
            isAntiAlias = true
        }
    }
    val textBackgroundPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.RED // Default background color
        }
    }

    // Use Compose Canvas for drawing
    Canvas(modifier = modifier) {
        // Use drawIntoCanvas to access the underlying native canvas for Paint operations
        drawIntoCanvas { canvas ->
            detections.forEach { detection ->
                // Get class name if detector is available
                val className = detector?.getClassName(detection.classId) ?: "ID: ${detection.classId}"
                val label = "$className: ${"%.2f".format(detection.conf)}"

                // Set colors (optional: use class-specific colors if available)
                // Access classColors directly now that it's internal
                val color = detector?.classColors?.getOrNull(detection.classId % (detector.classColors.size ?: 1)) ?: intArrayOf(255, 0, 0)
                val androidColor = android.graphics.Color.rgb(color[0], color[1], color[2])
                boxPaint.color = androidColor
                textBackgroundPaint.color = androidColor

                // Draw bounding box using nativeCanvas
                canvas.nativeCanvas.drawRect(
                    detection.box.x.toFloat(),
                    detection.box.y.toFloat(),
                    (detection.box.x + detection.box.width).toFloat(),
                    (detection.box.y + detection.box.height).toFloat(),
                    boxPaint
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
                val clampedTextBgBottom = maxOf(textHeight.toFloat() + 10f, textBgBottom) // Ensure min height

                // Draw background rectangle
                canvas.nativeCanvas.drawRect(
                    textBgLeft, clampedTextBgTop, textBgRight, clampedTextBgBottom,
                    textBackgroundPaint
                )

                // Draw text (adjust Y position based on clamped background)
                val textY = clampedTextBgTop + textHeight + 5f // Position text inside the background box
                canvas.nativeCanvas.drawText(
                    label,
                    detection.box.x.toFloat() + 5f, // Padding
                    textY - 5f, // Adjust based on background position
                    textPaint
                )
            }
        }
    }
}


// Helper function to convert ARCore Image (YUV_420_888) to Bitmap
// IMPORTANT: This is a basic implementation. For production, consider RenderScript or a native library for performance.
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
    // NV21 format requires V before U
    val vPixelStride = planes[2].pixelStride
    val uPixelStride = planes[1].pixelStride
    val vRowStride = planes[2].rowStride
    val uRowStride = planes[1].rowStride

    val vuOrder = ByteArray(uSize + vSize)
    var vIndex = 0
    var uIndex = 0

    // Assume V and U planes have the same row stride and pixel stride for simplicity here.
    // A robust implementation needs to handle different strides carefully.
    if (vPixelStride == 2 && uPixelStride == 2 && vRowStride == uRowStride) {
         // Common case: U and V are interleaved in buffers already (e.g., VUVUVU...)
         // We need to de-interleave and re-interleave as VUVU... -> VVVV... UUUU... -> VUVUVU... (NV21 order)
         // This simplified version assumes they might be separate or need careful interleaving.

        // Copy V then U for NV21 format (V goes first for NV21)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // NV21 interleaves V and U like VUVUVU... starting from ySize
        // This part requires careful byte manipulation based on strides
        val vuBuffer = ByteArray(vSize + uSize)
        vBuffer.get(vuBuffer, 0, vSize)
        uBuffer.get(vuBuffer, vSize, uSize) // Now vuBuffer has VVV...UUU...

        var outputIndex = ySize
        var vInputIndex = 0
        var uInputIndex = vSize
        // Interleave V and U into nv21 array
        // This loop assumes pixelStride is 2 for U and V planes
        // And that the number of V and U samples is half the number of Y samples horizontally and vertically
        val uvWidth = width / 2
        val uvHeight = height / 2
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                 val vuBaseIndex = row * vRowStride + col * vPixelStride // Calculate base index in original V/U buffer
                 // Check bounds before accessing vBuffer and uBuffer directly
                 if (vuBaseIndex < vSize && vuBaseIndex < uSize) {
                    nv21[outputIndex++] = vuBuffer[vInputIndex] // V
                    nv21[outputIndex++] = vuBuffer[uInputIndex] // U
                    vInputIndex += vPixelStride
                    uInputIndex += uPixelStride
                 } else {
                     // Handle potential stride issues or incomplete data
                     // Fill with default value or break
                     if (outputIndex < nv21.size) nv21[outputIndex++] = 128.toByte() // Gray
                     if (outputIndex < nv21.size) nv21[outputIndex++] = 128.toByte() // Gray
                 }
            }
             // Adjust indices to the start of the next row based on row strides
             // This part is complex if row strides differ significantly from width * pixelStride
             vInputIndex = (row + 1) * vRowStride // Simplified, assumes direct mapping
             uInputIndex = vSize + (row + 1) * uRowStride // Simplified
        }


    } else {
         // Fallback or error for unexpected strides - this part is complex
         // A common approach is to copy row by row, respecting strides.
         Log.w(TAG, "Using simplified YUV->NV21 conversion due to unexpected strides.")
         vBuffer.get(nv21, ySize, vSize) // V plane
         uBuffer.get(nv21, ySize + vSize, uSize) // U plane
    }


    // Convert NV21 byte array to YuvImage
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)

    // Convert YuvImage to JPEG stream
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out) // Use 100 for quality

    // Decode JPEG stream to Bitmap
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
