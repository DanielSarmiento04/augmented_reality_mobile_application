package com.example.augmented_mobile_application.repository

import android.content.Context
import android.util.Log
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import com.example.augmented_mobile_application.service.RoutineAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing maintenance routines with caching and performance optimization
 */
class RoutineRepository(context: Context) {
    
    private val routineLoader = RoutineAssetLoader(context)
    private val routineCache = ConcurrentHashMap<String, MaintenanceRoutine>()
    
    private val _availableRoutines = MutableStateFlow<List<MaintenanceRoutine>>(emptyList())
    val availableRoutines: StateFlow<List<MaintenanceRoutine>> = _availableRoutines.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    companion object {
        private const val TAG = "RoutineRepository"
        
        @Volatile
        private var INSTANCE: RoutineRepository? = null
        
        fun getInstance(context: Context): RoutineRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RoutineRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Loads all available routines with caching
     */
    suspend fun loadRoutines(): Result<List<MaintenanceRoutine>> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        
        try {
            // Check if we have cached routines
            if (routineCache.isNotEmpty()) {
                val cachedRoutines = routineCache.values.toList().sortedBy { it.id }
                _availableRoutines.value = cachedRoutines
                _isLoading.value = false
                return@withContext Result.success(cachedRoutines)
            }
            
            // Load from assets
            val routines = routineLoader.loadAllRoutines()
            
            // Cache the results
            routineCache.clear()
            routines.forEach { routine ->
                routineCache[routine.id] = routine
            }
            
            _availableRoutines.value = routines
            _isLoading.value = false
            
            Log.d(TAG, "Loaded ${routines.size} routines from assets")
            Result.success(routines)
            
        } catch (e: Exception) {
            val errorMessage = "Error loading routines: ${e.message}"
            Log.e(TAG, errorMessage, e)
            _error.value = errorMessage
            _isLoading.value = false
            Result.failure(e)
        }
    }
    
    /**
     * Gets a specific routine by ID with caching
     */
    suspend fun getRoutine(routineId: String): Result<MaintenanceRoutine> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            routineCache[routineId]?.let { cachedRoutine ->
                Log.d(TAG, "Returning cached routine: $routineId")
                return@withContext Result.success(cachedRoutine)
            }
            
            // Load from assets
            val routine = routineLoader.loadRoutine(routineId)
            
            if (routine != null) {
                // Cache the result
                routineCache[routineId] = routine
                Log.d(TAG, "Loaded and cached routine: $routineId")
                Result.success(routine)
            } else {
                val errorMessage = "Routine not found: $routineId"
                Log.w(TAG, errorMessage)
                Result.failure(Exception(errorMessage))
            }
            
        } catch (e: Exception) {
            val errorMessage = "Error loading routine $routineId: ${e.message}"
            Log.e(TAG, errorMessage, e)
            Result.failure(e)
        }
    }
    
    /**
     * Validates if a routine's GLB file exists
     */
    suspend fun validateRoutineGlb(routineId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            routineLoader.checkGlbFileExists(routineId)
        } catch (e: Exception) {
            Log.e(TAG, "Error validating GLB for routine: $routineId", e)
            false
        }
    }
    
    /**
     * Clears the cache (useful for testing or memory management)
     */
    fun clearCache() {
        routineCache.clear()
        _availableRoutines.value = emptyList()
        _error.value = null
        Log.d(TAG, "Cache cleared")
    }
    
    /**
     * Gets cached routine count
     */
    fun getCachedRoutineCount(): Int = routineCache.size
}
