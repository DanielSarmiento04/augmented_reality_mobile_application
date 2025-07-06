package com.example.augmented_mobile_application.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import com.example.augmented_mobile_application.model.RoutineExecutionState
import com.example.augmented_mobile_application.service.RoutineAssetLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing maintenance routine execution
 */
class MaintenanceRoutineViewModel(context: Context) : ViewModel() {
    
    private val routineLoader = RoutineAssetLoader(context)
    
    // State flows
    private val _availableRoutines = MutableStateFlow<List<MaintenanceRoutine>>(emptyList())
    val availableRoutines: StateFlow<List<MaintenanceRoutine>> = _availableRoutines.asStateFlow()
    
    private val _executionState = MutableStateFlow(RoutineExecutionState())
    val executionState: StateFlow<RoutineExecutionState> = _executionState.asStateFlow()
    
    private val _isRoutineListLoading = MutableStateFlow(false)
    val isRoutineListLoading: StateFlow<Boolean> = _isRoutineListLoading.asStateFlow()
    
    init {
        loadAvailableRoutines()
    }
    
    /**
     * Loads all available maintenance routines
     */
    private fun loadAvailableRoutines() {
        viewModelScope.launch {
            _isRoutineListLoading.value = true
            try {
                val routines = routineLoader.loadAllRoutines()
                _availableRoutines.value = routines
            } catch (e: Exception) {
                _executionState.value = _executionState.value.copy(
                    error = "Error cargando rutinas: ${e.message}"
                )
            } finally {
                _isRoutineListLoading.value = false
            }
        }
    }
    
    /**
     * Starts execution of a specific routine
     * @param routineId The routine identifier
     */
    fun startRoutine(routineId: String) {
        viewModelScope.launch {
            _executionState.value = _executionState.value.copy(isLoading = true, error = null)
            
            try {
                val routine = routineLoader.loadRoutine(routineId)
                if (routine != null) {
                    _executionState.value = RoutineExecutionState(
                        currentRoutine = routine,
                        currentStepIndex = 0,
                        isLoading = false,
                        error = null,
                        isRoutineCompleted = false
                    )
                } else {
                    _executionState.value = _executionState.value.copy(
                        isLoading = false,
                        error = "No se pudo cargar la rutina: $routineId"
                    )
                }
            } catch (e: Exception) {
                _executionState.value = _executionState.value.copy(
                    isLoading = false,
                    error = "Error iniciando rutina: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Advances to the next step in the current routine
     */
    fun nextStep() {
        val currentState = _executionState.value
        val routine = currentState.currentRoutine ?: return
        
        val nextIndex = currentState.currentStepIndex + 1
        if (nextIndex < routine.steps.size) {
            _executionState.value = currentState.copy(
                currentStepIndex = nextIndex,
                isRoutineCompleted = false
            )
        } else {
            // Routine completed
            _executionState.value = currentState.copy(
                isRoutineCompleted = true
            )
        }
    }
    
    /**
     * Goes back to the previous step in the current routine
     */
    fun previousStep() {
        val currentState = _executionState.value
        val previousIndex = currentState.currentStepIndex - 1
        
        if (previousIndex >= 0) {
            _executionState.value = currentState.copy(
                currentStepIndex = previousIndex,
                isRoutineCompleted = false
            )
        }
    }
    
    /**
     * Restarts the current routine from the beginning
     */
    fun restartRoutine() {
        val currentState = _executionState.value
        if (currentState.currentRoutine != null) {
            _executionState.value = currentState.copy(
                currentStepIndex = 0,
                isRoutineCompleted = false,
                error = null
            )
        }
    }
    
    /**
     * Stops the current routine execution
     */
    fun stopRoutine() {
        _executionState.value = RoutineExecutionState()
    }
    
    /**
     * Clears any error state
     */
    fun clearError() {
        _executionState.value = _executionState.value.copy(error = null)
    }
    
    /**
     * Refreshes the list of available routines
     */
    fun refreshRoutines() {
        loadAvailableRoutines()
    }
    
    /**
     * Gets the current step instruction
     */
    fun getCurrentStepInstruction(): String? {
        return _executionState.value.currentStep?.instruction
    }
    
    /**
     * Gets the current routine's GLB asset path
     */
    fun getCurrentRoutineGlbPath(): String? {
        return _executionState.value.currentRoutine?.glbAssetPath
    }
}
