package com.example.augmented_mobile_application.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import com.example.augmented_mobile_application.model.RoutineExecutionState
import com.example.augmented_mobile_application.repository.RoutineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for managing routine selection and execution state
 */
class RoutineSelectionViewModel(context: Context) : ViewModel() {
    
    private val repository = RoutineRepository.getInstance(context)
    
    // UI State
    private val _selectedRoutine = MutableStateFlow<MaintenanceRoutine?>(null)
    val selectedRoutine: StateFlow<MaintenanceRoutine?> = _selectedRoutine.asStateFlow()
    
    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()
    
    private val _showStepDetails = MutableStateFlow(false)
    val showStepDetails: StateFlow<Boolean> = _showStepDetails.asStateFlow()
    
    private val _arSessionRequested = MutableStateFlow<String?>(null)
    val arSessionRequested: StateFlow<String?> = _arSessionRequested.asStateFlow()
    
    // Derived states
    val availableRoutines: StateFlow<List<MaintenanceRoutine>> = repository.availableRoutines
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val error: StateFlow<String?> = repository.error
    
    // Combined state for current step
    val currentStep = combine(
        selectedRoutine,
        currentStepIndex
    ) { routine, stepIndex ->
        routine?.steps?.getOrNull(stepIndex)
    }
    
    // Progress information
    val routineProgress = combine(
        selectedRoutine,
        currentStepIndex
    ) { routine, stepIndex ->
        routine?.let { r ->
            RoutineProgress(
                currentStep = stepIndex + 1,
                totalSteps = r.steps.size,
                progressPercentage = (stepIndex + 1).toFloat() / r.steps.size.toFloat()
            )
        }
    }
    
    init {
        loadRoutines()
    }
    
    /**
     * Loads all available routines
     */
    fun loadRoutines() {
        viewModelScope.launch {
            repository.loadRoutines()
        }
    }
    
    /**
     * Selects a routine and shows its details
     */
    fun selectRoutine(routine: MaintenanceRoutine) {
        _selectedRoutine.value = routine
        _currentStepIndex.value = 0
        
        // Also set in shared ViewModel for AR access
        val sharedViewModel = SharedRoutineViewModel.getInstance()
        sharedViewModel.setCurrentRoutine(routine)
    }
    
    /**
     * Navigates to the next step
     */
    fun nextStep() {
        val routine = _selectedRoutine.value ?: return
        val currentIndex = _currentStepIndex.value
        
        if (currentIndex < routine.steps.size - 1) {
            _currentStepIndex.value = currentIndex + 1
        }
    }
    
    /**
     * Navigates to the previous step
     */
    fun previousStep() {
        val currentIndex = _currentStepIndex.value
        if (currentIndex > 0) {
            _currentStepIndex.value = currentIndex - 1
        }
    }
    
    /**
     * Jumps to a specific step
     */
    fun goToStep(stepIndex: Int) {
        val routine = _selectedRoutine.value ?: return
        if (stepIndex in 0 until routine.steps.size) {
            _currentStepIndex.value = stepIndex
        }
    }
    
    /**
     * Shows the step details view for the selected routine
     */
    fun showStepDetails() {
        _showStepDetails.value = true
    }
    
    /**
     * Shows the routine selection view
     */
    fun showRoutineSelection() {
        _showStepDetails.value = false
        _selectedRoutine.value = null
        _currentStepIndex.value = 0
    }
    
    /**
     * Starts the AR maintenance session with the selected routine's GLB
     */
    fun startMaintenanceAR() {
        val routine = _selectedRoutine.value
        if (routine != null) {
            _arSessionRequested.value = routine.glbAssetPath
        }
    }
    
    /**
     * Clears the AR session request
     */
    fun clearArSessionRequest() {
        _arSessionRequested.value = null
    }
    
    /**
     * Closes routine details view
     */
    fun closeRoutineDetails() {
        _showStepDetails.value = false
        _selectedRoutine.value = null
        _currentStepIndex.value = 0
    }
    
    /**
     * Checks if there's a next step available
     */
    fun hasNextStep(): Boolean {
        val routine = _selectedRoutine.value ?: return false
        return _currentStepIndex.value < routine.steps.size - 1
    }
    
    /**
     * Checks if there's a previous step available
     */
    fun hasPreviousStep(): Boolean {
        return _currentStepIndex.value > 0
    }
    
    /**
     * Clears any errors
     */
    fun clearError() {
        _localError.value = null
    }
    
    /**
     * Sets an error message
     */
    private fun setError(message: String) {
        // Since we can't access repository's private _error, we'll need to handle this differently
        // For now, we'll add a local error state
        _localError.value = message
    }
    
    // Add local error state
    private val _localError = MutableStateFlow<String?>(null)
    val localError: StateFlow<String?> = _localError.asStateFlow()
    
    /**
     * Clears local errors
     */
    fun clearLocalError() {
        _localError.value = null
    }
}

/**
 * Data class for routine progress information
 */
data class RoutineProgress(
    val currentStep: Int,
    val totalSteps: Int,
    val progressPercentage: Float
)
