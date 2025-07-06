package com.example.augmented_mobile_application.service

import android.content.Context
import android.util.Log
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import com.example.augmented_mobile_application.model.MaintenanceStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Service responsible for loading maintenance routines from assets
 */
class RoutineAssetLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "RoutineAssetLoader"
        private const val ROUTINES_BASE_PATH = "pump/routines"
        private const val ROUTINE_FILE_EXTENSION = ".txt"
        private const val GLB_FILE_EXTENSION = ".glb"
    }

    /**
     * Loads all available maintenance routines from assets
     * @return List of maintenance routines
     */
    suspend fun loadAllRoutines(): List<MaintenanceRoutine> = withContext(Dispatchers.IO) {
        try {
            val routineDirectories = context.assets.list(ROUTINES_BASE_PATH) ?: emptyArray()
            
            routineDirectories
                .filter { it.startsWith("routine_") }
                .mapNotNull { routineId ->
                    loadRoutine(routineId)
                }
                .sortedBy { it.id }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading routines from assets", e)
            emptyList()
        }
    }

    /**
     * Loads a specific routine by ID
     * @param routineId The routine identifier (e.g., "routine_1")
     * @return MaintenanceRoutine or null if not found
     */
    suspend fun loadRoutine(routineId: String): MaintenanceRoutine? = withContext(Dispatchers.IO) {
        try {
            val routinePath = "$ROUTINES_BASE_PATH/$routineId"
            val files = context.assets.list(routinePath) ?: return@withContext null
            
            // Check if required files exist
            val txtFile = "$routineId$ROUTINE_FILE_EXTENSION"
            val glbFile = "$routineId$GLB_FILE_EXTENSION"
            
            if (!files.contains(txtFile)) {
                Log.w(TAG, "Text file not found for routine: $routineId")
                return@withContext null
            }
            
            if (!files.contains(glbFile)) {
                Log.w(TAG, "GLB file not found for routine: $routineId")
                return@withContext null
            }
            
            // Load steps from text file
            val steps = loadStepsFromFile("$routinePath/$txtFile")
            
            if (steps.isEmpty()) {
                Log.w(TAG, "No steps found for routine: $routineId")
                return@withContext null
            }
            
            MaintenanceRoutine(
                id = routineId,
                name = routineId,
                displayName = generateDisplayName(routineId),
                description = "Rutina de mantenimiento para $routineId",
                glbFileName = glbFile,
                glbAssetPath = "file:///android_asset/$routinePath/$glbFile",
                steps = steps
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading routine: $routineId", e)
            null
        }
    }

    /**
     * Loads steps from a text file
     * @param filePath Path to the text file within assets
     * @return List of maintenance steps
     */
    private suspend fun loadStepsFromFile(filePath: String): List<MaintenanceStep> = withContext(Dispatchers.IO) {
        try {
            context.assets.open(filePath).use { inputStream ->
                inputStream.bufferedReader().useLines { lines ->
                    lines.mapIndexedNotNull { index, line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotEmpty()) {
                            MaintenanceStep(
                                id = "step_$index",
                                title = "Paso ${index + 1}",
                                instruction = trimmedLine,
                                description = trimmedLine,
                                stepNumber = index + 1
                            )
                        } else {
                            null
                        }
                    }.toList()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading steps from file: $filePath", e)
            emptyList()
        }
    }

    /**
     * Generates a user-friendly display name for a routine
     * @param routineId The routine identifier
     * @return Display name in Spanish
     */
    private fun generateDisplayName(routineId: String): String {
        return when (routineId) {
            "routine_1" -> "Rutina Diaria"
            "routine_2" -> "Rutina Mensual" 
            "routine_3" -> "Rutina Semestral"
            else -> {
                // Extract number from routine_X format
                val routineNumber = routineId.removePrefix("routine_")
                "Rutina $routineNumber"
            }
        }
    }

    /**
     * Checks if a GLB file exists for a routine
     * @param routineId The routine identifier
     * @return True if GLB file exists
     */
    suspend fun checkGlbFileExists(routineId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val routinePath = "$ROUTINES_BASE_PATH/$routineId"
            val files = context.assets.list(routinePath) ?: return@withContext false
            val glbFile = "$routineId$GLB_FILE_EXTENSION"
            files.contains(glbFile)
        } catch (e: IOException) {
            Log.e(TAG, "Error checking GLB file for routine: $routineId", e)
            false
        }
    }
}
