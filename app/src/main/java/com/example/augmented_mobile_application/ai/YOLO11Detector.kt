package com.example.augmented_mobile_application.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import com.example.augmented_mobile_application.BuildConfig

/**
 * YOLOv11 Object Detector for Android
 * Clean, optimized implementation for real-time performance
 */
class YOLO11Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelsPath: String,
    private val useNNAPI: Boolean = false,
    private val useGPU: Boolean = true
) {
    companion object {
        const val CONFIDENCE_THRESHOLD = 0.4f
        const val IOU_THRESHOLD = 0.45f
        private const val TAG = "YOLO11Detector"
        private const val MAX_DETECTIONS = 300
        const val PUMP_CLASS_ID = 81
        const val CUP_CLASS_ID = 41
        const val PIPE_CLASS_ID = 82
    }

    // Core components
    private var interpreter: Interpreter
    private val classNames: List<String>
    internal val classColors: List<IntArray>
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null

    // Model properties
    private var inputWidth: Int = 640
    private var inputHeight: Int = 640
    private var isQuantized: Boolean = false
    private var numClasses: Int = 0

    // Reusable buffers
    private val resizedImageMat = Mat()
    private val rgbMat = Mat()
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private val outputResults = HashMap<Int, Any>()

    // Thread safety and performance
    private val interpreterLock = Any()
    private var isDisposed: Boolean = false
    private var lastInferenceTime = 0L
    private val minInferenceInterval = 50L

    init {
        try {
            debug("Initializing YOLO11Detector: $modelPath")
            
            // Load and configure model
            val modelBuffer = loadModelFile(modelPath)
            val performanceConfig = TensorFlowLiteOptimizer.getOptimalConfig(context)
            val tfliteOptions = TensorFlowLiteOptimizer.configureInterpreter(performanceConfig)
            
            tfliteOptions.setAllowFp16PrecisionForFp32(true)
            interpreter = Interpreter(modelBuffer, tfliteOptions)

            // Extract model properties
            val inputTensor = interpreter.getInputTensor(0)
            val inputShape = inputTensor.shape()
            val outputShape = interpreter.getOutputTensor(0).shape()

            inputHeight = inputShape[1]
            inputWidth = inputShape[2]
            isQuantized = inputTensor.dataType() == org.tensorflow.lite.DataType.UINT8
            numClasses = outputShape[1] - 4

            debug("Model: ${inputWidth}x${inputHeight}, quantized=$isQuantized, classes=$numClasses")

            // Initialize buffers
            initializeBuffers()

            // Load class names and colors
            classNames = loadClassNames(labelsPath)
            classColors = generateColors(classNames.size)

            // Warmup inference
            warmupInference()

            debug("YOLO11Detector initialized successfully")
        } catch (e: Exception) {
            debug("Initialization failed: ${e.message}")
            throw e
        }
    }

    /**
     * Main detection function
     */
    fun detect(
        bitmap: Bitmap, 
        confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
        iouThreshold: Float = IOU_THRESHOLD
    ): Pair<List<Detection>, Long> {
        if (isDisposed) return Pair(emptyList(), 0L)
        
        // Rate limiting
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastInferenceTime < minInferenceInterval) {
            return Pair(emptyList(), 0L)
        }
        lastInferenceTime = currentTime
        
        val startTime = SystemClock.elapsedRealtime()
        
        try {
            // Preprocessing
            val inputMat = Mat()
            Utils.bitmapToMat(bitmap, inputMat)
            Imgproc.cvtColor(inputMat, inputMat, Imgproc.COLOR_RGBA2BGR)
            
            val originalSize = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
            val modelInputShape = Size(inputWidth.toDouble(), inputHeight.toDouble())
            
            val inputTensor = preprocessImage(inputMat, modelInputShape)
            
            // Inference
            runInference(inputTensor)
            
            // Postprocessing
            val detections = postprocess(
                outputResults,
                originalSize,
                modelInputShape,
                confidenceThreshold,
                iouThreshold
            )
            
            inputMat.release()
            
            val totalTime = SystemClock.elapsedRealtime() - startTime
            debug("Detection: ${totalTime}ms, found ${detections.size} objects")
            
            return Pair(detections, totalTime)
            
        } catch (e: Exception) {
            debug("Detection error: ${e.message}")
            return Pair(emptyList(), SystemClock.elapsedRealtime() - startTime)
        }
    }

    /**
     * Draw detections with masks on bitmap
     */
    fun drawDetectionsMask(bitmap: Bitmap, detections: List<Detection>, maskAlpha: Float = 0.4f): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val filteredDetections = detections.filter {
            it.conf > CONFIDENCE_THRESHOLD && it.classId in 0 until classNames.size
        }
        
        // Draw masks
        for (detection in filteredDetections) {
            val color = classColors[detection.classId % classColors.size]
            val paint = Paint().apply {
                this.color = Color.argb((255 * maskAlpha).toInt(), color[0], color[1], color[2])
                style = Paint.Style.FILL
            }
            
            canvas.drawRect(detection.box.x1, detection.box.y1, detection.box.x2, detection.box.y2, paint)
        }
        
        // Draw bounding boxes and labels
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = Math.max(bitmap.width, bitmap.height) * 0.004f
        }
        
        val textPaint = Paint().apply {
            textSize = Math.max(bitmap.width, bitmap.height) * 0.02f
            color = Color.WHITE
        }
        
        for (detection in filteredDetections) {
            val color = classColors[detection.classId % classColors.size]
            boxPaint.color = Color.rgb(color[0], color[1], color[2])
            
            // Draw box
            canvas.drawRect(detection.box.x1, detection.box.y1, detection.box.x2, detection.box.y2, boxPaint)
            
            // Draw label
            val label = "${classNames[detection.classId]}: ${(detection.conf * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize
            val labelY = Math.max(detection.box.y1, textHeight + 5f)
            
            val bgPaint = Paint().apply {
                this.color = Color.rgb(color[0], color[1], color[2])
                style = Paint.Style.FILL
            }
            
            canvas.drawRect(
                detection.box.x1,
                labelY - textHeight - 5f,
                detection.box.x1 + textWidth + 10f,
                labelY + 5f,
                bgPaint
            )
            
            canvas.drawText(label, detection.box.x1 + 5f, labelY - 5f, textPaint)
        }
        
        return mutableBitmap
    }

    /**
     * Cleanup resources
     */
    fun close() {
        if (isDisposed) return
        
        synchronized(interpreterLock) {
            isDisposed = true
            
            try {
                interpreter.close()
                gpuDelegate?.close()
                nnApiDelegate?.close()
                resizedImageMat.release()
                rgbMat.release()
                debug("YOLO11Detector closed")
            } catch (e: Exception) {
                debug("Error closing detector: ${e.message}")
            }
        }
    }

    // Utility functions
    fun getClassName(classId: Int): String? = 
        if (classId in 0 until classNames.size) classNames[classId] else null
    
    fun validateClassId(classId: Int): Boolean = classId in 0 until classNames.size
    
    fun findClassId(className: String): Int? = 
        classNames.indexOfFirst { it.equals(className, ignoreCase = true) }.takeIf { it >= 0 }

    fun getInputDetails(): String {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        return """
            Model Input: ${inputTensor.shape().joinToString("x")}
            Model Output: ${outputTensor.shape().joinToString("x")}
            Input Type: ${inputTensor.dataType()}
            Is Quantized: $isQuantized
            Classes: $numClasses
            Target Class 41: ${getClassName(41)}
            Pump Class 81: ${getClassName(81)}
            Pipe Class 82: ${getClassName(82)}
        """.trimIndent()
    }
    
    fun logModelValidation() {
        debug("=== Model Validation ===")
        debug("Total classes: ${classNames.size}")
        debug("Model expects: $numClasses classes")
        debug("Input size: ${inputWidth}x${inputHeight}")
        debug("Quantized: $isQuantized")
        
        // Log specific classes we're interested in
        val targetClasses = listOf(41, 81, 82)
        targetClasses.forEach { classId ->
            val className = getClassName(classId)
            debug("Class $classId: ${className ?: "INVALID"}")
        }
        
        // Verify target class exists
        if (!validateClassId(41)) {
            debug("WARNING: Target class 41 is out of range!")
        }
        
        debug("=========================")
    }

    // Private helper functions
    private fun initializeBuffers() {
        val bytesPerChannel = if (isQuantized) 1 else 4
        inputBuffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * 3 * bytesPerChannel)
            .apply { order(ByteOrder.nativeOrder()) }
        
        val outputSize = interpreter.getOutputTensor(0).shape().reduce { acc, i -> acc * i }
        outputBuffer = ByteBuffer.allocateDirect(4 * outputSize)
            .apply { order(ByteOrder.nativeOrder()) }
        
        outputResults[0] = outputBuffer as Any
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    private fun warmupInference() {
        try {
            val warmupMat = Mat(inputHeight, inputWidth, CvType.CV_8UC3, Scalar(0.0, 0.0, 0.0))
            inputBuffer?.clear()
            
            if (isQuantized) {
                val pixels = ByteArray(inputWidth * inputHeight * 3)
                warmupMat.get(0, 0, pixels)
                inputBuffer?.put(pixels)
            } else {
                val normalizedMat = Mat()
                warmupMat.convertTo(normalizedMat, CvType.CV_32FC3, 1.0/255.0)
                val floatValues = FloatArray(inputWidth * inputHeight * 3)
                normalizedMat.get(0, 0, floatValues)
                inputBuffer?.asFloatBuffer()?.put(floatValues)
                normalizedMat.release()
            }
            
            inputBuffer?.rewind()
            outputBuffer?.rewind()
            interpreter.run(inputBuffer, outputBuffer)
            warmupMat.release()
        } catch (e: Exception) {
            debug("Warmup failed: ${e.message}")
        }
    }

    private fun preprocessImage(image: Mat, newShape: Size): ByteBuffer {
        letterbox(image, resizedImageMat, newShape)
        Imgproc.cvtColor(resizedImageMat, resizedImageMat, Imgproc.COLOR_BGR2RGB)
        
        if (resizedImageMat.type() != CvType.CV_8UC3) {
            resizedImageMat.convertTo(resizedImageMat, CvType.CV_8UC3)
        }
        
        inputBuffer?.clear()
        
        if (isQuantized) {
            val pixels = ByteArray((resizedImageMat.total() * resizedImageMat.channels()).toInt())
            resizedImageMat.get(0, 0, pixels)
            inputBuffer?.put(pixels)
        } else {
            val pixelValues = ByteArray(resizedImageMat.channels())
            val floatBuffer = inputBuffer?.asFloatBuffer()
            
            for (y in 0 until resizedImageMat.rows()) {
                for (x in 0 until resizedImageMat.cols()) {
                    resizedImageMat.get(y, x, pixelValues)
                    for (c in pixelValues.indices) {
                        floatBuffer?.put((pixelValues[c].toInt() and 0xFF) / 255.0f)
                    }
                }
            }
        }
        
        inputBuffer?.rewind()
        return inputBuffer!!
    }

    private fun runInference(inputTensor: ByteBuffer) {
        synchronized(interpreterLock) {
            if (isDisposed) return
            
            try {
                inputTensor.rewind()
                outputBuffer?.rewind()
                interpreter.run(inputTensor, outputBuffer)
            } catch (e: Exception) {
                debug("Inference error: ${e.message}")
            }
        }
    }

    private fun letterbox(src: Mat, dst: Mat, targetSize: Size, color: Scalar = Scalar(114.0, 114.0, 114.0)) {
        val ratio = Math.min(targetSize.width / src.width(), targetSize.height / src.height())
        val newWidth = (src.width() * ratio).toInt()
        val newHeight = (src.height() * ratio).toInt()
        
        val dw = (targetSize.width - newWidth).toInt()
        val dh = (targetSize.height - newHeight).toInt()
        
        val top = dh / 2
        val bottom = dh - top
        val left = dw / 2
        val right = dw - left
        
        val resized = Mat()
        Imgproc.resize(src, resized, Size(newWidth.toDouble(), newHeight.toDouble()))
        
        if (dw > 0 || dh > 0) {
            Core.copyMakeBorder(resized, dst, top, bottom, left, right, Core.BORDER_CONSTANT, color)
        } else {
            resized.copyTo(dst)
        }
        
        resized.release()
    }

    private fun postprocess(
        outputMap: Map<Int, Any>,
        originalImageSize: Size,
        resizedImageShape: Size,
        confThreshold: Float,
        iouThreshold: Float
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val boxes = mutableListOf<RectF>()
        val confidences = mutableListOf<Float>()
        val classIds = mutableListOf<Int>()

        try {
            val outputBuffer = outputMap[0] as ByteBuffer
            outputBuffer.rewind()
            
            val outputShapes = interpreter.getOutputTensor(0).shape()
            val numPredictions = outputShapes[2]
            val outputFloats = FloatArray(outputShapes[1] * numPredictions)
            outputBuffer.asFloatBuffer().get(outputFloats)

            // Extract detections
            for (i in 0 until numPredictions) {
                var maxScore = 0f
                var maxClass = -1
                
                // Find max class score
                var classOffset = 4 * numPredictions + i
                for (c in 0 until numClasses) {
                    val score = outputFloats[classOffset]
                    if (score > maxScore) {
                        maxScore = score
                        maxClass = c
                    }
                    classOffset += numPredictions
                }
                
                if (maxScore >= confThreshold) {
                    val x = outputFloats[i]
                    val y = outputFloats[numPredictions + i]
                    val w = outputFloats[2 * numPredictions + i]
                    val h = outputFloats[3 * numPredictions + i]
                    
                    val box = RectF(x - w/2, y - h/2, x + w/2, y + h/2)
                    val scaledBox = scaleCoords(resizedImageShape, box, originalImageSize)
                    
                    if (scaledBox.width() > 1 && scaledBox.height() > 1) {
                        boxes.add(scaledBox)
                        confidences.add(maxScore)
                        classIds.add(maxClass)
                    }
                }
            }

            // Apply NMS
            val selectedIndices = nonMaxSuppression(boxes, confidences, confThreshold, iouThreshold)
            
            for (idx in selectedIndices) {
                val box = boxes[idx]
                detections.add(Detection(
                    BoundingBox(box.left, box.top, box.right, box.bottom),
                    confidences[idx],
                    classIds[idx]
                ))
            }
        } catch (e: Exception) {
            debug("Postprocessing error: ${e.message}")
        }

        return detections
    }

    private fun scaleCoords(imageShape: Size, coords: RectF, originalShape: Size): RectF {
        val gain = Math.min(imageShape.width / originalShape.width, imageShape.height / originalShape.height).toFloat()
        val padX = ((imageShape.width - originalShape.width * gain) / 2.0).toFloat()
        val padY = ((imageShape.height - originalShape.height * gain) / 2.0).toFloat()
        
        val x1 = (coords.left * imageShape.width.toFloat() - padX) / gain
        val y1 = (coords.top * imageShape.height.toFloat() - padY) / gain
        val x2 = (coords.right * imageShape.width.toFloat() - padX) / gain
        val y2 = (coords.bottom * imageShape.height.toFloat() - padY) / gain
        
        return RectF(
            Math.max(0f, x1),
            Math.max(0f, y1),
            Math.min(x2, originalShape.width.toFloat()),
            Math.min(y2, originalShape.height.toFloat())
        )
    }

    private fun nonMaxSuppression(
        boxes: List<RectF>,
        scores: List<Float>,
        scoreThreshold: Float,
        iouThreshold: Float
    ): List<Int> {
        val indices = mutableListOf<Int>()
        if (boxes.isEmpty()) return indices
        
        val sortedIndices = scores.indices
            .filter { scores[it] >= scoreThreshold }
            .sortedByDescending { scores[it] }
        
        val suppressed = BooleanArray(boxes.size)
        
        for (i in sortedIndices) {
            if (suppressed[i]) continue
            
            indices.add(i)
            if (indices.size >= MAX_DETECTIONS) break
            
            for (j in sortedIndices) {
                if (i == j || suppressed[j]) continue
                
                val iou = calculateIoU(boxes[i], boxes[j])
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        
        return indices
    }

    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val intersectionLeft = Math.max(box1.left, box2.left)
        val intersectionTop = Math.max(box1.top, box2.top)
        val intersectionRight = Math.min(box1.right, box2.right)
        val intersectionBottom = Math.min(box1.bottom, box2.bottom)
        
        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return 0f
        }
        
        val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
        val box1Area = box1.width() * box1.height()
        val box2Area = box2.width() * box2.height()
        val unionArea = box1Area + box2Area - intersectionArea
        
        return intersectionArea / (unionArea + 1e-5f)
    }

    private fun loadClassNames(labelsPath: String): List<String> {
        return context.assets.open(labelsPath).bufferedReader().useLines {
            it.map { line -> line.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    private fun generateColors(numClasses: Int): List<IntArray> {
        val colors = mutableListOf<IntArray>()
        val random = Random(42)
        
        repeat(numClasses) {
            colors.add(intArrayOf(
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256)
            ))
        }
        
        return colors
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    // Data classes
    data class BoundingBox(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    ) {
        val width: Float get() = x2 - x1
        val height: Float get() = y2 - y1
        val centerX: Float get() = (x1 + x2) / 2f
        val centerY: Float get() = (y1 + y2) / 2f
    }

    data class Detection(
        val box: BoundingBox,
        val conf: Float,
        val classId: Int
    )
}
