package com.example.augmented_mobile_application.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.ResourceExhaustedException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.min

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

    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var modelPlaced by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(true) }
    var instructionStep by remember { mutableStateOf(0) }
    var maintenanceStarted by remember { mutableStateOf(false) }

    val yoloDetector = rememberYoloDetector(context)
    val detectionPipeline = rememberDetectionPipeline(yoloDetector)
    val arStateManager = remember { ARCoreStateManager() }
    
    // Use StateFlow from DetectionPipeline instead of local state
    val detectionResults by detectionPipeline?.detectionResults?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isTargetDetected by detectionPipeline?.isTargetDetected?.collectAsState() ?: remember { mutableStateOf(false) }
    val inferenceTimeMs by detectionPipeline?.inferenceTimeMs?.collectAsState() ?: remember { mutableStateOf(0L) }
    val isDetecting by detectionPipeline?.isProcessing?.collectAsState() ?: remember { mutableStateOf(false) }
    
    // ARCore state monitoring
    val trackingState by arStateManager.trackingState.collectAsState()
    val trackingFailureReason by arStateManager.trackingFailureReason.collectAsState()

    val instructions = listOf(
        "Escanee una superficie plana y coloque el modelo 3D",
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

    // Initialize model positioning manager when ARSceneView is ready
    LaunchedEffect(arSceneViewRef.value) {
        arSceneViewRef.value?.let { sceneView ->
            modelPositioningManager.value = ModelPositioningManager(sceneView)
            Log.i(TAG, "ModelPositioningManager initialized")
        }
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

    // Manage detection pipeline lifecycle
    LaunchedEffect(maintenanceStarted, modelPlaced) {
        if (maintenanceStarted && modelPlaced && detectionPipeline != null) {
            Log.i(TAG, "Starting detection pipeline")
            detectionPipeline.start()
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (maintenanceStarted && !modelPlaced) {
                                arSceneViewRef.value?.let { arSceneView ->
                                    val frame = arSceneView.frame
                                    // Critical: Check ARCore tracking state before hitTest
                                    if (frame != null && frame.camera.trackingState == TrackingState.TRACKING) {
                                        try {
                                            val hitResults = frame.hitTest(event.x, event.y)
                                            if (!hitResults.isNullOrEmpty()) {
                                                hitResults.firstOrNull()?.let { hit ->
                                                    val anchor = hit.createAnchor()
                                                    val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = anchor)
                                                    arSceneView.scene.addEntity(anchorNode.entity)

                                                    modelNodeRef.value?.let { modelNode ->
                                                        arSceneView.scene.addEntity(modelNode.entity)
                                                        modelNode.transform.position = anchorNode.transform.position

                                                        modelPlaced = true
                                                        anchorNodeRef.value = anchorNode
                                                        instructionStep = maxOf(1, instructionStep)
                                                        
                                                        Log.i(TAG, "Model placed successfully at anchor position")
                                                    }
                                                }
                                            } else {
                                                Log.d(TAG, "No hit results found for touch event")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error during hit test: ${e.message}", e)
                                        }
                                    } else {
                                        val trackingState = frame?.camera?.trackingState
                                        Log.w(TAG, "Cannot place model - ARCore not tracking. Current state: $trackingState")
                                    }
                                }
                            }
                            false
                        }
                        else -> false
                    }
                },
            factory = { ctx ->
                val sceneView = ARSceneView(ctx).apply {
                    arSceneViewRef.value = this

                    configureSession { session, config ->
                        // Optimized ARCore configuration for better performance and tracking
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
                        if (session.isInstantPlacementModeSupported(Config.InstantPlacementMode.LOCAL_Y_UP)) {
                            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                            Log.i(TAG, "Instant placement mode enabled")
                        }
                        
                        Log.i(TAG, "ARCore session configured with optimized settings")
                    }

                    planeRenderer.isEnabled = true

                    onTrackingFailureChanged = { reason ->
                        trackingFailureReason = reason
                    }

                    onFrame = { frameTime ->
                        val currentSceneView = arSceneViewRef.value
                        val currentFrame: ArFrame? = currentSceneView?.frame

                        // Update ARCore state manager with current frame
                        arStateManager.updateTrackingState(currentFrame)

                        // Only process frames when in optimal conditions
                        if (maintenanceStarted && modelPlaced && detectionPipeline != null && 
                            arStateManager.isReadyForOperations() && arStateManager.isCameraImageAvailable()) {
                            
                            try {
                                currentFrame?.acquireCameraImage()?.use { image: Image? ->
                                    if (image != null) {
                                        // Use background thread for bitmap conversion to avoid UI blocking
                                        scope.launch(Dispatchers.Default) {
                                            try {
                                                val bitmap: Bitmap = image.toBitmap()
                                                // Submit to pipeline (non-blocking)
                                                detectionPipeline.submitFrame(bitmap)
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Error converting image to bitmap: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            } catch (e: NotYetAvailableException) {
                                // Frame not ready, skip silently
                            } catch (e: ResourceExhaustedException) {
                                // Camera resources exhausted, skip and log
                                Log.w(TAG, "Camera resources exhausted, skipping frame")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error processing camera frame: ${e.message}")
                            }
                        }
                    }

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
                            isLoadingModel = false
                        }
                    }
                }
                sceneView
            }
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
                                    // Detection state is managed by DetectionPipeline lifecycle
                                    anchorNodeRef.value?.let { arSceneViewRef.value?.scene?.removeEntity(it.entity) }
                                    modelNodeRef.value?.let { arSceneViewRef.value?.scene?.removeEntity(it.entity) }
                                    anchorNodeRef.value = null
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
                            !maintenanceStarted -> "Presione 'Iniciar Mantenimiento' para habilitar la colocación del modelo"
                            else -> "Mueva la cámara para detectar superficies planas y toque para colocar el modelo"
                        },
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(8.dp)
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
