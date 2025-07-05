package com.example.augmented_mobile_application.ai

import android.graphics.Bitmap
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.CvType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

// Import constants for consistent model configuration
import com.example.augmented_mobile_application.ai.YOLOModelConstants

/**
 * Comprehensive validation and debugging utility for YOLO11 detection pipeline.
 * Helps identify common issues that prevent successful object detection.
 */
object DetectionValidator {
    private const val TAG = "DetectionValidator"
    
    data class ValidationReport(
        val isValid: Boolean,
        val issues: List<String>,
        val warnings: List<String>,
        val suggestions: List<String>
    )
    
    /**
     * Validate model setup and configuration
     */
    fun validateModelSetup(detector: YOLO11Detector): ValidationReport {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        
        try {
            // Check class configuration
            val targetClassValid = detector.validateClassId(41)
            if (!targetClassValid) {
                issues.add("Target class ID 41 is out of range")
                suggestions.add("Check your classes.txt file - it should have at least 42 classes (0-41)")
            }
            
            val targetClassName = detector.getClassName(41)
            if (targetClassName != null) {
                Log.i(TAG, "Target class 41 maps to: '$targetClassName'")
                if (targetClassName.lowercase() != "cup") {
                    warnings.add("Class 41 is '$targetClassName', not 'cup' as expected")
                    suggestions.add("Verify your model training used the correct class mapping")
                }
            }
            
            // Check pump class as alternative
            val pumpClassId = detector.findClassId("pump")
            if (pumpClassId != null) {
                Log.i(TAG, "Found 'pump' class at ID: $pumpClassId")
                suggestions.add("Consider using pump class ID $pumpClassId instead of 41")
            }
            
            // Validate input details
            val inputDetails = detector.getInputDetails()
            Log.i(TAG, "Model details:\n$inputDetails")
            
        } catch (e: Exception) {
            issues.add("Error accessing detector: ${e.message}")
        }
        
        return ValidationReport(issues.isEmpty(), issues, warnings, suggestions)
    }
    
    /**
     * Validate input image preprocessing
     */
    fun validateImagePreprocessing(
        originalBitmap: Bitmap,
        processedMat: Mat? = null
    ): ValidationReport {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        
        // Check original bitmap
        if (originalBitmap.isRecycled) {
            issues.add("Input bitmap is recycled")
            return ValidationReport(false, issues, warnings, suggestions)
        }
        
        // Check dimensions
        val width = originalBitmap.width
        val height = originalBitmap.height
        
        if (width < 100 || height < 100) {
            warnings.add("Input image is very small: ${width}x${height}")
            suggestions.add("Ensure camera provides adequate resolution")
        }
        
        if (width > 2000 || height > 2000) {
            warnings.add("Input image is very large: ${width}x${height}")
            suggestions.add("Consider resizing before processing to improve performance")
        }
        
        // Check processed Mat if available
        processedMat?.let { mat ->
            if (mat.empty()) {
                issues.add("Processed Mat is empty")
            }
            
            if (mat.type() != CvType.CV_8UC3) {
                warnings.add("Processed Mat type is ${mat.type()}, expected CV_8UC3")
                suggestions.add("Ensure proper color conversion (BGR/RGB)")
            }
            
            if (mat.cols() != YOLOModelConstants.INPUT_WIDTH || mat.rows() != YOLOModelConstants.INPUT_HEIGHT) {
                warnings.add("Processed Mat size is ${mat.cols()}x${mat.rows()}, expected ${YOLOModelConstants.INPUT_WIDTH}x${YOLOModelConstants.INPUT_HEIGHT}")
                suggestions.add("Verify letterbox resizing is working correctly")
            }
        }
        
        Log.d(TAG, "Image validation - Original: ${width}x${height}, Format: ${originalBitmap.config}")
        
        return ValidationReport(issues.isEmpty(), issues, warnings, suggestions)
    }
    
    /**
     * Validate model inference results
     */
    fun validateInferenceResults(
        detections: List<YOLO11Detector.Detection>,
        inferenceTime: Long,
        targetClassId: Int
    ): ValidationReport {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        
        // Check inference performance
        when {
            inferenceTime > 200 -> {
                warnings.add("Inference time is high: ${inferenceTime}ms")
                suggestions.add("Consider using GPU acceleration or quantized model")
            }
            inferenceTime > 100 -> {
                warnings.add("Inference time is moderate: ${inferenceTime}ms")
            }
            inferenceTime < 10 -> {
                warnings.add("Inference time suspiciously low: ${inferenceTime}ms")
                suggestions.add("Verify inference is actually running")
            }
        }
        
        // Check detection results
        if (detections.isEmpty()) {
            warnings.add("No detections found")
            suggestions.addAll(listOf(
                "Check if objects are clearly visible in camera",
                "Verify lighting conditions are adequate",
                "Ensure target object is large enough in frame",
                "Consider lowering confidence threshold temporarily for testing"
            ))
        } else {
            Log.d(TAG, "Found ${detections.size} detections:")
            detections.forEachIndexed { index, detection ->
                Log.d(TAG, "  $index: Class ${detection.classId}, Confidence ${"%.3f".format(detection.conf)}, Box ${detection.box}")
                
                // Validate individual detections
                if (detection.conf < 0.1f) {
                    warnings.add("Very low confidence detection: ${"%.3f".format(detection.conf)}")
                }
                
                if (detection.box.width < 20 || detection.box.height < 20) {
                    warnings.add("Very small detection box: ${detection.box.width}x${detection.box.height}")
                }
                
                if (detection.classId < 0 || detection.classId > 84) {
                    issues.add("Invalid class ID: ${detection.classId}")
                }
            }
            
            // Check for target class
            val targetDetections = detections.filter { it.classId == targetClassId }
            if (targetDetections.isNotEmpty()) {
                Log.i(TAG, "Target class $targetClassId detected ${targetDetections.size} times!")
                targetDetections.forEach { detection ->
                    Log.i(TAG, "  Target detection: confidence=${"%.3f".format(detection.conf)}, box=${detection.box}")
                }
            } else {
                val uniqueClasses = detections.map { it.classId }.distinct().sorted()
                warnings.add("Target class $targetClassId not detected. Found classes: $uniqueClasses")
                suggestions.add("Verify the target object is the correct type for class $targetClassId")
            }
        }
        
        return ValidationReport(issues.isEmpty(), issues, warnings, suggestions)
    }
    
    /**
     * Validate TensorFlow Lite buffer format
     */
    fun validateInputBuffer(
        inputBuffer: ByteBuffer,
        expectedSize: Int,
        isQuantized: Boolean
    ): ValidationReport {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        
        val actualSize = inputBuffer.remaining()
        if (actualSize != expectedSize) {
            issues.add("Input buffer size mismatch: expected $expectedSize, got $actualSize")
            suggestions.add("Check image preprocessing and buffer allocation")
        }
        
        // Sample first few values for validation
        val position = inputBuffer.position()
        val sampleSize = minOf(12, actualSize)
        
        if (isQuantized) {
            val samples = ByteArray(sampleSize)
            inputBuffer.get(samples)
            Log.d(TAG, "Input buffer samples (uint8): ${samples.joinToString { (it.toInt() and 0xFF).toString() }}")
            
            val allZero = samples.all { it == 0.toByte() }
            val allSame = samples.all { it == samples[0] }
            
            if (allZero) {
                warnings.add("Input buffer contains all zeros")
                suggestions.add("Check image preprocessing - image might be completely black")
            } else if (allSame) {
                warnings.add("Input buffer contains all identical values")
                suggestions.add("Check image preprocessing - image might be uniform color")
            }
        } else {
            val floatBuffer = inputBuffer.asFloatBuffer()
            val samples = FloatArray(sampleSize / 4)
            floatBuffer.get(samples)
            Log.d(TAG, "Input buffer samples (float32): ${samples.joinToString { "%.3f".format(it) }}")
            
            val allZero = samples.all { it == 0f }
            val outOfRange = samples.any { it < -1f || it > 1f }
            
            if (allZero) {
                warnings.add("Input buffer contains all zeros")
                suggestions.add("Check image preprocessing and normalization")
            }
            
            if (outOfRange) {
                warnings.add("Input values outside expected range [-1, 1] or [0, 1]")
                suggestions.add("Verify normalization: divide by 255.0 for [0,1] range")
            }
        }
        
        // Restore buffer position
        inputBuffer.position(position)
        
        return ValidationReport(issues.isEmpty(), issues, warnings, suggestions)
    }
    
    /**
     * Print comprehensive validation report
     */
    fun printReport(report: ValidationReport, title: String) {
        Log.i(TAG, "=== $title ===")
        Log.i(TAG, "Valid: ${report.isValid}")
        
        if (report.issues.isNotEmpty()) {
            Log.e(TAG, "Issues:")
            report.issues.forEach { Log.e(TAG, "  ❌ $it") }
        }
        
        if (report.warnings.isNotEmpty()) {
            Log.w(TAG, "Warnings:")
            report.warnings.forEach { Log.w(TAG, "  ⚠️ $it") }
        }
        
        if (report.suggestions.isNotEmpty()) {
            Log.i(TAG, "Suggestions:")
            report.suggestions.forEach { Log.i(TAG, "  💡 $it") }
        }
        
        Log.i(TAG, "================")
    }
    
    /**
     * Run comprehensive validation of the entire detection pipeline
     */
    fun validatePipeline(
        detector: YOLO11Detector,
        testBitmap: Bitmap
    ): ValidationReport {
        Log.i(TAG, "Running comprehensive pipeline validation...")
        
        val allIssues = mutableListOf<String>()
        val allWarnings = mutableListOf<String>()
        val allSuggestions = mutableListOf<String>()
        
        // Validate model setup
        val modelReport = validateModelSetup(detector)
        printReport(modelReport, "Model Setup")
        allIssues.addAll(modelReport.issues)
        allWarnings.addAll(modelReport.warnings)
        allSuggestions.addAll(modelReport.suggestions)
        
        // Validate image preprocessing
        val imageReport = validateImagePreprocessing(testBitmap)
        printReport(imageReport, "Image Preprocessing")
        allIssues.addAll(imageReport.issues)
        allWarnings.addAll(imageReport.warnings)
        allSuggestions.addAll(imageReport.suggestions)
        
        // Run test detection
        try {
            val (detections, inferenceTime) = detector.detect(testBitmap)
            val resultsReport = validateInferenceResults(detections, inferenceTime, 41)
            printReport(resultsReport, "Inference Results")
            allIssues.addAll(resultsReport.issues)
            allWarnings.addAll(resultsReport.warnings)
            allSuggestions.addAll(resultsReport.suggestions)
        } catch (e: Exception) {
            allIssues.add("Test detection failed: ${e.message}")
            Log.e(TAG, "Test detection failed", e)
        }
        
        val finalReport = ValidationReport(allIssues.isEmpty(), allIssues, allWarnings, allSuggestions)
        printReport(finalReport, "Pipeline Validation Summary")
        
        return finalReport
    }
}
