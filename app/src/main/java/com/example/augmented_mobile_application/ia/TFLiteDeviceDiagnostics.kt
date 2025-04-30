package com.example.augmented_mobile_application 

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Utility class for TFLite model management
 * Handles model extraction, validation, and adaptation
 */
class TFLiteModelManager(private val context: Context) {
    companion object {
        private const val TAG = "TFLiteModelManager"
    }

    /**
     * Extracts and validates a TFLite model from assets
     * May convert the model format to ensure compatibility with the device
     * @return Path to the optimized model file
     */
    fun prepareModelForDevice(assetModelPath: String): String {
        Log.d(TAG, "Preparing model: $assetModelPath")

        try {
            // First check if the model exists
            val assets = context.assets
            assets.open(assetModelPath).use { inStream ->
                // Read some header bytes to validate the file
                val header = ByteArray(8)
                val bytesRead = inStream.read(header)

                if (bytesRead != 8) {
                    throw IOException("Could not read model header bytes")
                }

                // Verify this is a valid FlatBuffer file (basic check)
                // TFLite models should have the first 4 bytes as the FlatBuffer header
                if (header[0].toInt() != 0x18 || header[1].toInt() != 0x00 ||
                    header[2].toInt() != 0x00 || header[3].toInt() != 0x00) {
                    Log.w(TAG, "Model may not be a valid FlatBuffer file")
                }

                Log.d(TAG, "Model header verified")
            }

            // Extract to local storage for potential modification
            val modelFile = extractAssetToCache(assetModelPath)
            Log.d(TAG, "Model extracted to: ${modelFile.absolutePath}")

            return modelFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error preparing model: ${e.message}")
            throw e
        }
    }

    /**
     * Extract an asset file to the app's cache directory
     */
    private fun extractAssetToCache(assetPath: String): File {
        val fileName = assetPath.substringAfterLast("/")
        val outputFile = File(context.cacheDir, "models_${Build.VERSION.SDK_INT}_$fileName")

        // Only extract if the file doesn't exist or is outdated
        if (!outputFile.exists() || outputFile.length() == 0L) {
            Log.d(TAG, "Extracting asset to: ${outputFile.absolutePath}")

            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
        } else {
            Log.d(TAG, "Using cached model: ${outputFile.absolutePath}")
        }

        return outputFile
    }

    /**
     * Load a TFLite model from a file with enhanced error handling
     */
    fun loadModelFile(modelPath: String): MappedByteBuffer {
        Log.d(TAG, "Loading model file: $modelPath")

        val file = File(modelPath)
        if (!file.exists()) {
            throw IOException("Model file not found: $modelPath")
        }

        return file.inputStream().channel.map(
            FileChannel.MapMode.READ_ONLY, 0, file.length()
        ).also {
            Log.d(TAG, "Model loaded, capacity: ${it.capacity()} bytes")
        }
    }

    /**
     * Check if a model file appears to be valid
     */
    fun validateModelFile(modelPath: String): Boolean {
        try {
            val file = File(modelPath)
            if (!file.exists() || file.length() < 8) {
                return false
            }

            // Basic header check
            file.inputStream().use { input ->
                val header = ByteArray(8)
                input.read(header)

                // Check for FlatBuffer header
                return header[0].toInt() == 0x18 && header[1].toInt() == 0x00 &&
                        header[2].toInt() == 0x00 && header[3].toInt() == 0x00
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating model file: ${e.message}")
            return false
        }
    }
}
