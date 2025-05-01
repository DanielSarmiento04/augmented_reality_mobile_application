package com.example.augmented_mobile_application.ai // Correct package declaration if needed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.ceil
import com.example.augmented_mobile_application.BuildConfig // Ensure BuildConfig is imported

/**
 * YOLOv11Detector for Android using TFLite and OpenCV
 *
 * This class handles object detection using the YOLOv11 model with TensorFlow Lite
 * for inference and OpenCV for image processing.
 *
 * Migration Notes (TFLite 2.9 -> 2.16.1):
 * - Update dependencies in build.gradle:
 *   - `implementation("org.tensorflow:tensorflow-lite:2.16.1")`
 *   - `implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")` (if using manual delegate setup)
 *   - OR `implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")` (for plugin)
 *   - Ensure NDK version compatibility (check TFLite 2.16 release notes - v27 is compatible).
 * - Core Interpreter API (`Interpreter`, `run`) is stable. No changes needed for basic inference.
 * - Delegate APIs (GPU, NNAPI) are generally stable.
 *   - `NnApiDelegate.Options` available since TFLite 2.10 for fine-tuning (e.g., execution preference). Current default constructor `NnApiDelegate()` remains valid.
 *   - `GpuDelegate.Options` might have new experimental options in 2.16; review documentation if advanced tuning is needed. Current `GpuDelegate(GpuDelegate.Options())` remains valid.
 * - Thorough testing is required after migration, especially with delegates, as underlying implementations and performance characteristics might change.
 *
 * Optimization Notes:
 * - Consider using a quantized model (INT8 or FP16) for significant speedup. Convert using the TFLiteConverter.
 * - Experiment with smaller input sizes (e.g., 416x416, 320x320) via model retraining/conversion.
 * - Implement asynchronous processing: Run detection in a background thread pool. Use a queue (e.g., ArrayBlockingQueue(1))
 *   between the camera frame producer and the detector thread, dropping frames if detection falls behind.
 */
class YOLO11Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelsPath: String,
    private val useNNAPI: Boolean = false, // Add NNAPI option
    private val useGPU: Boolean = true     // Default GPU to true
) {
    // Detection parameters - matching C++ implementation
    companion object {
        // Match the C++ implementation thresholds
        const val CONFIDENCE_THRESHOLD = 0.25f  // Changed from 0.4f to match C++ code
        const val IOU_THRESHOLD = 0.45f         // Changed from 0.3f to match C++ code
        private const val TAG = "YOLO11Detector"

        // Maximum detections after NMS
        private const val MAX_DETECTIONS = 300
    }

    // Data structures for model and inference
    private var interpreter: Interpreter
    private val classNames: List<String>
    internal val classColors: List<IntArray> // Changed visibility to internal
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null // Add NNAPI delegate instance

    // Input shape info
    private var inputWidth: Int = 640
    private var inputHeight: Int = 640
    private var isQuantized: Boolean = false
    private var numClasses: Int = 0

    // Add reusable buffers for better memory management
    private val resizedImageMat = Mat()
    private val rgbMat = Mat()
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private val outputResults = HashMap<Int, Any>()

    init {
        try {
            // --- Model Optimization Suggestion ---
            // For significantly better performance, convert the model to FP16 or INT8 quantization
            // using the TensorFlow Lite Converter. Update `isQuantized` accordingly.
            // Example: tflite_convert --output_file=model_fp16.tflite --model_file=model.pb --inference_type=FLOAT --inference_input_type=FLOAT --optimizations=DEFAULT
            // Or for INT8: requires a representative dataset.

            // --- Input Size Suggestion ---
            // If possible, train or convert the model to use a smaller input size like 416x416 or 320x320.
            // This drastically reduces computation needed. Update inputWidth/inputHeight if changed.

            // Log starting initialization for debugging purposes
            debug("Initializing YOLO11Detector with model: $modelPath, useNNAPI: $useNNAPI, useGPU: $useGPU")
            debug("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.SDK_INT}")

            // Load model with proper options
            val tfliteOptions = Interpreter.Options()
            var delegateApplied = false

            // NNAPI Delegate setup (preferred for NPU/DSP acceleration)
            if (useNNAPI) {
                delegateApplied = setupNnApiDelegate(tfliteOptions)
            }

            // GPU Delegate setup with improved validation and error recovery
            // Note: Alternatively, consider using the tensorflow-lite-gpu-delegate-plugin
            // which simplifies setup but offers less manual control.
            if (!delegateApplied && useGPU) {
                try {
                    val compatList = CompatibilityList()
                    debug("GPU delegate supported on device: ${compatList.isDelegateSupportedOnThisDevice}")

                    if (compatList.isDelegateSupportedOnThisDevice) {
                        // First try to create GPU delegate without configuring options
                        // This can help detect early incompatibilities
                        try {
                            val tempDelegate = GpuDelegate()
                            tempDelegate.close() // Just testing creation
                            debug("Basic GPU delegate creation successful")
                        } catch (e: Exception) {
                            debug("Basic GPU delegate test failed: ${e.message}")
                            throw Exception("Device reports GPU compatible but fails basic delegate test")
                        }

                        debug("Configuring GPU acceleration using default options")

                        // Use the default GpuDelegate constructor.
                        // GpuDelegate.Options() is deprecated. TFLite's defaults are generally recommended.
                        // Consult TFLite 2.16.1 documentation for specific advanced options if needed,
                        // potentially via InterpreterApi.Options.addDelegateFactory().
                        gpuDelegate = GpuDelegate() // Use default constructor
                        tfliteOptions.addDelegate(gpuDelegate)
                        delegateApplied = true
                        debug("GPU delegate successfully created and added (using default options)")

                        // Always configure CPU fallback options
                        configureCpuOptions(tfliteOptions)

                    } else {
                        debug("GPU acceleration not supported on this device, using CPU only")
                        configureCpuOptions(tfliteOptions)
                    }
                } catch (e: Exception) {
                    debug("Error setting up GPU acceleration: ${e.message}, stack: ${e.stackTraceToString()}")
                    debug("Falling back to CPU execution")
                    // Clean up any GPU resources
                    try {
                        gpuDelegate?.close()
                        gpuDelegate = null
                    } catch (closeEx: Exception) {
                        debug("Error closing GPU delegate: ${closeEx.message}")
                    }
                    configureCpuOptions(tfliteOptions)
                }
            }

            // Configure CPU if no delegate was applied
            if (!delegateApplied) {
                debug("No hardware delegate applied (NNAPI disabled/failed, GPU disabled/failed). Using CPU.")
                configureCpuOptions(tfliteOptions)
            } // Removed redundant 'else' block for CPU config

            // Enhanced model loading with diagnostics
            val modelBuffer: MappedByteBuffer
            try {
                debug("Loading model from assets: $modelPath")
                modelBuffer = loadModelFile(modelPath)
                debug("Model loaded successfully, size: ${modelBuffer.capacity() / 1024} KB")

                // Simple validation - check if buffer size is reasonable
                if (modelBuffer.capacity() < 10000) {
                    throw RuntimeException("Model file appears too small (${modelBuffer.capacity()} bytes)")
                }
            } catch (e: Exception) {
                debug("Failed to load model: ${e.message}")
                throw RuntimeException("Model loading failed: ${e.message}", e)
            }

            // Initialize interpreter with more controlled error handling
            try {
                debug("Creating TFLite interpreter")

                // Add memory management options for large models
                // These options are generally safe and recommended
                tfliteOptions.setAllowFp16PrecisionForFp32(true) // Reduce memory requirements

                interpreter = Interpreter(modelBuffer, tfliteOptions)
                debug("TFLite interpreter created successfully")

                // Log interpreter details for diagnostics
                val inputTensor = interpreter.getInputTensor(0)
                val inputShape = inputTensor.shape()
                val outputTensor = interpreter.getOutputTensor(0)
                val outputShape = outputTensor.shape()

                debug("Model input shape: ${inputShape.joinToString()}")
                debug("Model output shape: ${outputShape.joinToString()}")
                debug("Input tensor type: ${inputTensor.dataType()}")

                // Capture model input properties
                inputHeight = inputShape[1]
                inputWidth = inputShape[2]
                isQuantized = inputTensor.dataType() == org.tensorflow.lite.DataType.UINT8
                numClasses = outputShape[1] - 4

                debug("Model setup: inputSize=${inputWidth}x${inputHeight}, isQuantized=$isQuantized, numClasses=$numClasses")

                // Initialize reusable buffers based on model requirements
                val bytesPerChannel = if (isQuantized) 1 else 4
                inputBuffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * 3 * bytesPerChannel)
                    .apply { order(ByteOrder.nativeOrder()) }

                // Allocate output buffer based on model output shape
                val outputSize = outputShape.reduce { acc, i -> acc * i }
                outputBuffer = ByteBuffer.allocateDirect(4 * outputSize)
                    .apply { order(ByteOrder.nativeOrder()) }

                outputResults[0] = outputBuffer as Any

                // Run a warmup inference to initialize caching
                warmupInference()

            } catch (e: Exception) {
                debug("Failed to initialize interpreter: ${e.message}, stack: ${e.stackTraceToString()}")
                // Clean up resources
                try {
                    gpuDelegate?.close()
                    nnApiDelegate?.close()
                } catch (closeEx: Exception) {
                    // Log specific delegate close error
                    debug("Error closing delegate during interpreter init cleanup: ${closeEx.message}")
                }
                throw RuntimeException("TFLite initialization failed: ${e.message}", e)
            }

            // Load class names
            try {
                classNames = loadClassNames(labelsPath)
                debug("Loaded ${classNames.size} classes from $labelsPath")
                classColors = generateColors(classNames.size)

                if (classNames.size != numClasses) {
                    debug("Warning: Number of classes in label file (${classNames.size}) differs from model output ($numClasses)")
                }
            } catch (e: Exception) {
                debug("Failed to load class names: ${e.message}")
                throw RuntimeException("Failed to load class names", e)
            }

            debug("YOLO11Detector initialization completed successfully")
        } catch (e: Exception) {
            debug("FATAL: Detector initialization failed: ${e.message}")
            debug("Stack trace: ${e.stackTraceToString()}")
            throw e  // Re-throw to ensure caller sees the failure
        }
    }

    /**
     * Sets up the NNAPI delegate if requested and supported.
     * Returns true if the delegate was successfully added, false otherwise.
     */
    private fun setupNnApiDelegate(options: Interpreter.Options): Boolean {
        try {
            nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
            debug("NNAPI delegate created and added successfully (using default options).")
            return true
        } catch (e: Exception) {
            debug("NNAPI delegate setup failed: ${e.message}. Falling back.")
            try {
                nnApiDelegate?.close()
            } catch (closeEx: Exception) {
                debug("Error closing NNAPI delegate during fallback: ${closeEx.message}")
            }
            nnApiDelegate = null
            return false
        }
    }

    /**
     * Configure CPU-specific options for the TFLite interpreter with safer defaults
     */
    private fun configureCpuOptions(options: Interpreter.Options) {
        try {
            val cpuCores = Runtime.getRuntime().availableProcessors()
            val optimalThreads = when {
                cpuCores <= 2 -> 1
                cpuCores <= 4 -> 2
                else -> max(1, cpuCores - 2)
            }

            options.setNumThreads(optimalThreads)
            options.setUseXNNPACK(true)
            options.setAllowFp16PrecisionForFp32(true)
            options.setAllowBufferHandleOutput(true)

            debug("CPU options configured with $optimalThreads threads")
        } catch (e: Exception) {
            debug("Error configuring CPU options: ${e.message}")
            options.setNumThreads(1)
        }
    }

    /**
     * Loads the TFLite model file with enhanced error checking
     */
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        try {
            debug("Loading model from assets: $modelPath")
            val assetManager = context.assets
            val assetFileDescriptor = assetManager.openFd(modelPath)
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            debug("Error loading model file: $modelPath - ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Runs a warmup inference to prime internal caches and reduce first-inference latency
     */
    private fun warmupInference() {
        try {
            debug("Running warmup inference to initialize caches")
            val warmupMat = Mat(inputHeight, inputWidth, CvType.CV_8UC3, Scalar(0.0, 0.0, 0.0))
            val warmupRgbMat = Mat()
            Imgproc.cvtColor(warmupMat, warmupRgbMat, Imgproc.COLOR_BGR2RGB)
            inputBuffer?.clear()
            if (isQuantized) {
                val pixels = ByteArray(inputWidth * inputHeight * 3)
                warmupRgbMat.get(0, 0, pixels)
                inputBuffer?.put(pixels)
            } else {
                val normalizedMat = Mat()
                warmupRgbMat.convertTo(normalizedMat, CvType.CV_32FC3, 1.0/255.0)
                val floatValues = FloatArray(inputWidth * inputHeight * 3)
                normalizedMat.get(0, 0, floatValues)
                inputBuffer?.asFloatBuffer()?.put(floatValues)
                normalizedMat.release()
            }
            inputBuffer?.rewind()
            outputBuffer?.rewind()
            interpreter.run(inputBuffer, outputBuffer)
            warmupMat.release()
            warmupRgbMat.release()
            debug("Warmup inference completed successfully")
        } catch (e: Exception) {
            debug("Warmup inference failed: ${e.message}")
        }
    }

    /**
     * Main detection function that processes an image and returns detected objects
     * along with the total processing time.
     */
    fun detect(bitmap: Bitmap, confidenceThreshold: Float = CONFIDENCE_THRESHOLD,
               iouThreshold: Float = IOU_THRESHOLD): Pair<List<Detection>, Long> { // Return Pair
        val startTime = SystemClock.elapsedRealtime()
        var detections: List<Detection> = emptyList() // Initialize detections

        try {
            val preprocessingStartTime = SystemClock.elapsedRealtime()

            // Convert Bitmap to Mat for OpenCV processing - optimize memory use
            val inputMat = Mat()
            Utils.bitmapToMat(bitmap, inputMat)
            Imgproc.cvtColor(inputMat, inputMat, Imgproc.COLOR_RGBA2BGR)

            // Original dimensions for later scaling
            val originalSize = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
            val modelInputShape = Size(inputWidth.toDouble(), inputHeight.toDouble())

            // Reset reused buffers
            inputBuffer?.clear()
            outputBuffer?.clear()

            // Optimized preprocessing - reuse existing matrices
            resizedImageMat.release() // Ensure clean state
            rgbMat.release()

            // GPU-accelerated preprocessing where possible
            val inputTensor = preprocessImageOptimized(
                inputMat,
                resizedImageMat,
                modelInputShape
            )

            val preprocessingTime = SystemClock.elapsedRealtime() - preprocessingStartTime
            val inferenceStartTime = SystemClock.elapsedRealtime()

            // Run inference with pre-allocated outputs
            runOptimizedInference(inputTensor)

            val inferenceTime = SystemClock.elapsedRealtime() - inferenceStartTime
            val postprocessingStartTime = SystemClock.elapsedRealtime()

            // Process outputs to get detections
            detections = postprocessOptimized( // Assign to detections variable
                outputResults,
                originalSize,
                Size(inputWidth.toDouble(), inputHeight.toDouble()),
                confidenceThreshold,
                iouThreshold
            )

            val postprocessingTime = SystemClock.elapsedRealtime() - postprocessingStartTime
            val totalTime = SystemClock.elapsedRealtime() - startTime

            debug("Detection stats: total=${totalTime}ms, preprocess=${preprocessingTime}ms, " +
                  "inference=${inferenceTime}ms, postprocess=${postprocessingTime}ms, " +
                  "detections=${detections.size}")

            inputMat.release() // Clean up input mat

            // Return detections and total time
            return Pair(detections, totalTime)

        } catch (e: Exception) {
            debug("Error in detection: ${e.message}")
            e.printStackTrace()
            // Return empty list and 0 time on error
            return Pair(emptyList(), SystemClock.elapsedRealtime() - startTime)
        }
    }

    /**
     * GPU-accelerated image preprocessing with buffer reuse
     * Reverted to CPU-only path using Mat to resolve type errors.
     */
    private fun preprocessImageOptimized(image: Mat, outImage: Mat, newShape: Size): ByteBuffer {
        try {
            letterBoxOptimized(image, outImage, newShape, Scalar(114.0, 114.0, 114.0)) // Use Mat directly
            Imgproc.cvtColor(outImage, outImage, Imgproc.COLOR_BGR2RGB)

            // Prepare input buffer - reuse existing if possible
            if (inputBuffer == null) {
                val bytesPerChannel = if (isQuantized) 1 else 4
                inputBuffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * 3 * bytesPerChannel)
                    .apply { order(ByteOrder.nativeOrder()) }
            } else {
                inputBuffer?.clear()
            }

            // Fill input buffer efficiently with specialized paths for quantized vs float models
            if (isQuantized) {
                // Direct byte copy for quantized models - much faster
                val pixels = ByteArray(outImage.width() * outImage.height() * outImage.channels())
                outImage.get(0, 0, pixels)
                inputBuffer?.put(pixels)
            } else {
                // For float models - normalize while copying to avoid extra matrix allocation
                val floatBuffer = inputBuffer?.asFloatBuffer()
                val pixelValues = FloatArray(outImage.channels()) // Reusable buffer for pixel values
                val rows = outImage.rows()
                val cols = outImage.cols()
                val channels = outImage.channels()

                // Direct normalization loop avoids extra matrix allocation
                for (y in 0 until rows) {
                    for (x in 0 until cols) {
                        outImage.get(y, x, pixelValues) // Read pixel into buffer
                        for (c in 0 until channels) {
                            floatBuffer?.put(pixelValues[c] / 255.0f) // Normalize and put
                        }
                    }
                }
            }

            inputBuffer?.rewind()
            return inputBuffer!!

        } catch (e: Exception) {
            debug("Error in optimized preprocessing: ${e.message}")
            throw RuntimeException("Preprocessing failed", e)
        }
    }

    /**
     * Optimized letterboxing with minimal padding and memory allocations
     * Modified to work directly with Mat.
     */
    private fun letterBoxOptimized(src: Mat, dst: Mat, targetSize: Size, color: Scalar) {
        try {
            // Calculate scaling ratios
            val wRatio = targetSize.width / src.width()
            val hRatio = targetSize.height / src.height()
            val ratio = Math.min(wRatio, hRatio)

            // Calculate new dimensions
            val newUnpadWidth = (src.width() * ratio).toInt()
            val newUnpadHeight = (src.height() * ratio).toInt()

            // Calculate padding
            val dw = (targetSize.width - newUnpadWidth).toInt()
            val dh = (targetSize.height - newUnpadHeight).toInt()

            // Calculate padding on each side
            val top = dh / 2
            val bottom = dh - top
            val left = dw / 2
            val right = dw - left

            // Optimize resize interpolation method based on scaling
            val interpolation = if (ratio > 1) Imgproc.INTER_LINEAR else Imgproc.INTER_AREA

            // Resize the image efficiently
            val resized: Mat
            val needsRelease: Boolean
            if (src.nativeObjAddr == dst.nativeObjAddr) {
                resized = Mat() // Create a temporary Mat
                needsRelease = true
            } else {
                resized = dst // Resize directly into dst if it's different
                needsRelease = false
            }

            Imgproc.resize(src, resized, Size(newUnpadWidth.toDouble(), newUnpadHeight.toDouble()), 0.0, 0.0, interpolation)

            // Apply padding only if needed
            if (dw > 0 || dh > 0) {
                Core.copyMakeBorder(resized, dst, top, bottom, left, right, Core.BORDER_CONSTANT, color)
            } else if (needsRelease) {
                // If no padding was needed BUT we used a temporary Mat, copy it to dst
                resized.copyTo(dst)
            }

            // Cleanup temp mat if created
            if (needsRelease) {
                resized.release()
            }

        } catch (e: Exception) {
            debug("Error in letterbox optimization: ${e.message}")
            throw RuntimeException("Letterboxing failed", e)
        }
    }

    /**
     * Optimized inference with pre-allocated buffers
     */
    private fun runOptimizedInference(inputTensor: ByteBuffer) {
        try {
            // Ensure output buffer is ready
            outputBuffer?.rewind()

            // Use direct interpreter run with pre-allocated buffers
            interpreter.run(inputTensor, outputBuffer)

        } catch (e: Exception) {
            debug("Error during optimized inference: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Optimized post-processing with pre-allocated arrays for NMS
     */
    private fun postprocessOptimized(
        outputMap: Map<Int, Any>,
        originalImageSize: Size,
        resizedImageShape: Size,
        confThreshold: Float,
        iouThreshold: Float
    ): List<Detection> {
        val scopedTimer = ScopedTimer("postprocessing_optimized")

        // Pre-allocate collections to avoid growth reallocations
        val detections = ArrayList<Detection>(100)
        val boxes = ArrayList<RectF>(500)
        val confidences = ArrayList<Float>(500)
        val classIds = ArrayList<Int>(500)

        try {
            // Get output buffer
            val outputBuffer = outputMap[0] as ByteBuffer
            outputBuffer.rewind()

            // Get output dimensions (same logic as before)
            val outputShapes = interpreter.getOutputTensor(0).shape()
            val num_predictions = outputShapes[2]

            // Extract features to float array once for faster access
            val outputFloats = FloatArray(outputShapes[1] * num_predictions)
            val floatBuffer = outputBuffer.asFloatBuffer()
            floatBuffer.get(outputFloats)

            // Detection extraction loop with optimized inner loop
            var detectionCount = 0
            val classScoreOffset = 4 * num_predictions // Offset to class scores

            for (i in 0 until num_predictions) {
                // Fast max score search
                var maxScore = 0f
                var maxClass = -1

                // Optimized class loop with direct array access
                var classOffset = classScoreOffset + i
                for (c in 0 until numClasses) {
                    val score = outputFloats[classOffset]
                    if (score > maxScore) {
                        maxScore = score
                        maxClass = c
                    }
                    classOffset += num_predictions // Move to next class
                }

                // Apply confidence threshold
                if (maxScore >= confThreshold) {
                    // Extract box coordinates efficiently
                    val x = outputFloats[i]
                    val y = outputFloats[num_predictions + i]
                    val w = outputFloats[2 * num_predictions + i]
                    val h = outputFloats[3 * num_predictions + i]

                    // Convert center-form to corner-form (normalized)
                    val left = x - w / 2
                    val top = y - h / 2
                    val right = x + w / 2
                    val bottom = y + h / 2

                    // Create initial box (reuse object if possible)
                    val box = RectF(left, top, right, bottom)

                    // Scale to original image size with minimal allocations
                    val scaledBox = scaleCoords(
                        resizedImageShape,
                        box,
                        originalImageSize,
                        true // clip = true
                    )

                    // Add to candidates if valid size
                    val boxWidth = scaledBox.right - scaledBox.left
                    val boxHeight = scaledBox.bottom - scaledBox.top

                    if (boxWidth > 1 && boxHeight > 1) {
                        // Create box for NMS with class offset (avoids separate per-class NMS)
                        val nmsBox = RectF(
                            scaledBox.left + maxClass * 7680f,
                            scaledBox.top + maxClass * 7680f,
                            scaledBox.right + maxClass * 7680f,
                            scaledBox.bottom + maxClass * 7680f
                        )

                        boxes.add(nmsBox)
                        confidences.add(maxScore)
                        classIds.add(maxClass)
                        detectionCount++
                    }
                }
            }

            debug("Found $detectionCount raw detections before NMS")

            // Apply Non-Maximum Suppression with preallocated arrays
            val selectedIndices = ArrayList<Int>(100)
            fastNonMaxSuppression(boxes, confidences, confThreshold, iouThreshold, selectedIndices)

            debug("After NMS: ${selectedIndices.size} detections remaining")

            // Create final Detection objects
            for (idx in selectedIndices) {
                val nmsBox = boxes[idx]
                val classId = classIds[idx]

                // Remove the class offset from the box coordinates
                val originalBox = RectF(
                    nmsBox.left - classId * 7680f,
                    nmsBox.top - classId * 7680f,
                    nmsBox.right - classId * 7680f,
                    nmsBox.bottom - classId * 7680f
                )

                // Round coordinates to integers
                val boxX = Math.round(originalBox.left)
                val boxY = Math.round(originalBox.top)
                val boxWidth = Math.round(originalBox.right - originalBox.left)
                val boxHeight = Math.round(originalBox.bottom - originalBox.top)

                detections.add(
                    Detection(
                        BoundingBox(boxX, boxY, boxWidth, boxHeight),
                        confidences[idx],
                        classId
                    )
                )
            }
        } catch (e: Exception) {
            debug("Error during optimized postprocessing: ${e.message}")
            e.printStackTrace()
        }

        scopedTimer.stop()
        return detections
    }

    /**
     * Scale coordinates from model input size to original image size
     * FIXED: Improved coordinate scaling to account for minimal padding
     */
    private fun scaleCoords(
        imageShape: Size,
        coords: RectF,
        imageOriginalShape: Size,
        clip: Boolean = true
    ): RectF {
        // Get dimensions in pixels
        val inputWidth = imageShape.width.toFloat()
        val inputHeight = imageShape.height.toFloat()
        val originalWidth = imageOriginalShape.width.toFloat()
        val originalHeight = imageOriginalShape.height.toFloat()

        // Calculate scaling factor (gain) used during letterboxing
        val gain = min(inputWidth / originalWidth, inputHeight / originalHeight)

        // Calculate padding added during letterboxing
        val padX = (inputWidth - originalWidth * gain) / 2.0f
        val padY = (inputHeight - originalHeight * gain) / 2.0f

        // Convert normalized coordinates [0-1] relative to inputShape to absolute pixel coordinates
        val absLeft = coords.left * inputWidth
        val absTop = coords.top * inputHeight
        val absRight = coords.right * inputWidth
        val absBottom = coords.bottom * inputHeight

        // Remove padding and scale back to original image dimensions
        val x1 = (absLeft - padX) / gain
        val y1 = (absTop - padY) / gain
        val x2 = (absRight - padX) / gain
        val y2 = (absBottom - padY) / gain

        // Create result rectangle in original image coordinates
        val result = RectF(x1, y1, x2, y2)

        // Clip to image boundaries if requested
        if (clip) {
            result.left = max(0f, result.left)
            result.top = max(0f, result.top)
            result.right = min(result.right, originalWidth)
            result.bottom = min(result.bottom, originalHeight)
        }

        return result
    }

    /**
     * Fast Non-Maximum Suppression implementation optimized for speed
     * Uses vectorized operations and early termination for better performance
     */
    private fun fastNonMaxSuppression(
        boxes: List<RectF>,
        scores: List<Float>,
        scoreThreshold: Float,
        iouThreshold: Float,
        indices: MutableList<Int>
    ) {
        indices.clear()

        // Return early if no boxes
        if (boxes.isEmpty()) {
            return
        }

        // Filter indices by score threshold first, then sort by score descending
        val filteredSortedIndices = scores.indices
            .filter { scores[it] >= scoreThreshold }
            .sortedByDescending { scores[it] }

        if (filteredSortedIndices.isEmpty()) return

        // Pre-compute areas for all boxes (using the offset boxes)
        val areas = FloatArray(boxes.size)
        for (i in boxes.indices) {
            areas[i] = boxes[i].width() * boxes[i].height()
        }

        // Bit set is much faster than boolean array for large numbers of boxes
        val suppressed = BitSet(boxes.size)

        // Process boxes in order of decreasing confidence
        for (i in filteredSortedIndices.indices) {
            val currentIdx = filteredSortedIndices[i]

            // Skip if this box is already suppressed
            if (suppressed.get(currentIdx)) continue

            // Add current box index to the output list
            indices.add(currentIdx)

            // Get current box data (with offset)
            val currentBox = boxes[currentIdx]
            val area1 = areas[currentIdx]

            // Early termination - if we've added enough boxes
            if (indices.size >= MAX_DETECTIONS) {
                break
            }

            // Compare with remaining boxes
            for (j in i + 1 until filteredSortedIndices.size) {
                val compareIdx = filteredSortedIndices[j]

                // Skip if already suppressed
                if (suppressed.get(compareIdx)) continue

                val compareBox = boxes[compareIdx]

                // Calculate intersection dimensions
                val overlapWidth = Math.min(currentBox.right, compareBox.right) -
                                  Math.max(currentBox.left, compareBox.left)
                val overlapHeight = Math.min(currentBox.bottom, compareBox.bottom) -
                                   Math.max(currentBox.top, compareBox.top)

                // Calculate IoU only if boxes overlap significantly (width/height > 0)
                if (overlapWidth > 0 && overlapHeight > 0) {
                    val intersection = overlapWidth * overlapHeight
                    val area2 = areas[compareIdx]
                    val iou = intersection / (area1 + area2 - intersection + 1e-5f)

                    // Suppress if IoU is above threshold
                    if (iou > iouThreshold) {
                        suppressed.set(compareIdx)
                    }
                }
            }
        }
    }

    /**
     * Draws bounding boxes and semi-transparent masks on the provided bitmap
     */
    fun drawDetectionsMask(bitmap: Bitmap, detections: List<Detection>, maskAlpha: Float = 0.4f): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = bitmap.width
        val height = bitmap.height

        // Create a mask bitmap for overlay
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)

        // Filter detections to ensure quality results
        val filteredDetections = detections.filter {
            it.conf > CONFIDENCE_THRESHOLD &&
                    it.classId >= 0 &&
                    it.classId < classNames.size
        }

        // Draw filled rectangles on mask bitmap
        for (detection in filteredDetections) {
            val color = classColors[detection.classId % classColors.size]
            val paint = Paint()
            paint.color = Color.argb(
                (255 * maskAlpha).toInt(),
                color[0],
                color[1],
                color[2]
            )
            paint.style = Paint.Style.FILL

            maskCanvas.drawRect(
                detection.box.x.toFloat(),
                detection.box.y.toFloat(),
                (detection.box.x + detection.box.width).toFloat(),
                (detection.box.y + detection.box.height).toFloat(),
                paint
            )
        }

        // Overlay mask on original image
        val canvas = Canvas(mutableBitmap)
        val paint = Paint()
        paint.alpha = (255 * maskAlpha).toInt()
        canvas.drawBitmap(maskBitmap, 0f, 0f, paint)

        // Draw bounding boxes and labels (reusing existing method but with full opacity)
        val mainCanvas = Canvas(mutableBitmap)
        val boxPaint = Paint()
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = max(width, height) * 0.004f

        val textPaint = Paint()
        textPaint.textSize = max(width, height) * 0.02f

        for (detection in filteredDetections) {
            val color = classColors[detection.classId % classColors.size]
            boxPaint.color = Color.rgb(color[0], color[1], color[2])

            // Draw bounding box
            mainCanvas.drawRect(
                detection.box.x.toFloat(),
                detection.box.y.toFloat(),
                (detection.box.x + detection.box.width).toFloat(),
                (detection.box.y + detection.box.height).toFloat(),
                boxPaint
            )

            // Create and draw label
            val label = "${classNames[detection.classId]}: ${(detection.conf * 100).toInt()}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize

            val labelY = max(detection.box.y.toFloat(), textHeight + 5f)

            val bgPaint = Paint()
            bgPaint.color = Color.rgb(color[0], color[1], color[2])
            bgPaint.style = Paint.Style.FILL

            mainCanvas.drawRect(
                detection.box.x.toFloat(),
                labelY - textHeight - 5f,
                detection.box.x.toFloat() + textWidth + 10f,
                labelY + 5f,
                bgPaint
            )

            textPaint.color = Color.WHITE
            mainCanvas.drawText(
                label,
                detection.box.x.toFloat() + 5f,
                labelY - 5f,
                textPaint
            )
        }

        // Clean up
        maskBitmap.recycle()

        return mutableBitmap
    }

    /**
     * Loads class names from a file
     */
    private fun loadClassNames(labelsPath: String): List<String> {
        return context.assets.open(labelsPath).bufferedReader().useLines {
            it.map { line -> line.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    /**
     * Generate colors for visualization
     */
    private fun generateColors(numClasses: Int): List<IntArray> {
        val colors = mutableListOf<IntArray>()
        val random = Random(42) // Fixed seed for reproducibility

        for (i in 0 until numClasses) {
            val color = intArrayOf(
                random.nextInt(256),  // R
                random.nextInt(256),  // G
                random.nextInt(256)   // B
            )
            colors.add(color)
        }

        return colors
    }

    /**
     * Get class name for a given class ID
     * @param classId The class ID to get the name for
     * @return The class name or "Unknown" if the ID is invalid
     */
    fun getClassName(classId: Int): String {
        return if (classId >= 0 && classId < classNames.size) {
            classNames[classId]
        } else {
            "Unknown"
        }
    }

    /**
     * Get details about the model's input requirements
     * @return String containing shape and data type information
     */
    fun getInputDetails(): String {
        val inputTensor = interpreter.getInputTensor(0)
        val shape = inputTensor.shape()
        val type = when(inputTensor.dataType()) {
            org.tensorflow.lite.DataType.FLOAT32 -> "FLOAT32"
            org.tensorflow.lite.DataType.UINT8 -> "UINT8"
            else -> "OTHER"
        }
        return "Shape: ${shape.joinToString()}, Type: $type"
    }

    /**
     * Cleanup resources when no longer needed
     */
    fun close() {
        try {
            interpreter.close()
            debug("TFLite interpreter closed")
        } catch (e: Exception) {
            debug("Error closing interpreter: ${e.message}")
        }

        try {
            gpuDelegate?.close()
            debug("GPU delegate resources released")
        } catch (e: Exception) {
            debug("Error closing GPU delegate: ${e.message}")
        }

        try {
            nnApiDelegate?.close()
            debug("NNAPI delegate resources released")
        } catch (e: Exception) {
            debug("Error closing NNAPI delegate: ${e.message}")
        }

        // Release OpenCV resources
        try {
            resizedImageMat.release()
            rgbMat.release()
        } catch (e: Exception) {
            debug("Error releasing OpenCV resources: ${e.message}")
        }

        // Clear references
        inputBuffer = null
        outputBuffer = null
        gpuDelegate = null
        nnApiDelegate = null
    }

    /**
     * Data classes for detections and bounding boxes
     */
    data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int)

    data class Detection(val box: BoundingBox, val conf: Float, val classId: Int)

    /**
     * Helper functions
     */

    /**
     * Clamp a value between min and max
     */
    private fun clamp(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    /**
     * Debug print function with enhanced logging
     */
    private fun debug(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    // Add ScopedTimer implementation (if missing)
    private class ScopedTimer(private val name: String) {
        private val startTime = SystemClock.elapsedRealtime()

        fun stop() {
            val endTime = SystemClock.elapsedRealtime()
        }
    }
}
