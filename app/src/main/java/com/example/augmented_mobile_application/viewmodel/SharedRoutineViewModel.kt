package com.example.augmented_mobile_application.viewmodel

import androidx.lifecycle.ViewModel
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import com.example.augmented_mobile_application.model.MaintenanceStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared ViewModel for maintaining routine state across screens
 * Used to pass routine data from UserContentView to ARView
 */
class SharedRoutineViewModel : ViewModel() {
    
    private val _currentRoutine = MutableStateFlow<MaintenanceRoutine?>(null)
    val currentRoutine: StateFlow<MaintenanceRoutine?> = _currentRoutine.asStateFlow()
    
    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()
    
    private val _isMaintenanceActive = MutableStateFlow(false)
    val isMaintenanceActive: StateFlow<Boolean> = _isMaintenanceActive.asStateFlow()
    
    // Derived state for current step
    val currentStep: MaintenanceStep?
        get() = _currentRoutine.value?.steps?.getOrNull(_currentStepIndex.value)
    
    val currentStepDisplay: String
        get() {
            val routine = _currentRoutine.value ?: return "Sin rutina activa"
            val stepIndex = _currentStepIndex.value
            return "Paso ${stepIndex + 1} de ${routine.steps.size}"
        }
    
    val currentStepInstruction: String
        get() = currentStep?.instruction ?: "Sin instrucciones"
    
    val routineTitle: String
        get() = _currentRoutine.value?.displayName ?: "Mantenimiento AR"
    
    /**
     * Set the current routine (called from UserContentView)
     */
    fun setCurrentRoutine(routine: MaintenanceRoutine) {
        _currentRoutine.value = routine
        _currentStepIndex.value = 0
        _isMaintenanceActive.value = true
    }
    
    /**
     * Navigate to next step
     */
    fun nextStep(): Boolean {
        val routine = _currentRoutine.value ?: return false
        val currentIndex = _currentStepIndex.value
        
        return if (currentIndex < routine.steps.size - 1) {
            _currentStepIndex.value = currentIndex + 1
            true
        } else {
            false // Last step reached
        }
    }
    
    /**
     * Navigate to previous step
     */
    fun previousStep(): Boolean {
        val currentIndex = _currentStepIndex.value
        return if (currentIndex > 0) {
            _currentStepIndex.value = currentIndex - 1
            true
        } else {
            false // First step reached
        }
    }
    
    /**
     * Go to specific step
     */
    fun goToStep(stepIndex: Int): Boolean {
        val routine = _currentRoutine.value ?: return false
        return if (stepIndex in 0 until routine.steps.size) {
            _currentStepIndex.value = stepIndex
            true
        } else {
            false
        }
    }
    
    /**
     * Complete maintenance session
     */
    fun completeMaintenance() {
        _isMaintenanceActive.value = false
        _currentStepIndex.value = 0
    }
    
    /**
     * Clear routine data (when exiting AR)
     */
    fun clearRoutine() {
        _currentRoutine.value = null
        _currentStepIndex.value = 0
        _isMaintenanceActive.value = false
    }
    
    /**
     * Check if there's a next step
     */
    fun hasNextStep(): Boolean {
        val routine = _currentRoutine.value ?: return false
        return _currentStepIndex.value < routine.steps.size - 1
    }
    
    /**
     * Check if there's a previous step
     */
    fun hasPreviousStep(): Boolean {
        return _currentStepIndex.value > 0
    }
    
    /**
     * Get progress percentage
     */
    fun getProgressPercentage(): Float {
        val routine = _currentRoutine.value ?: return 0f
        return if (routine.steps.isEmpty()) {
            0f
        } else {
            (_currentStepIndex.value + 1).toFloat() / routine.steps.size.toFloat()
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: SharedRoutineViewModel? = null
        
        fun getInstance(): SharedRoutineViewModel {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedRoutineViewModel().also { INSTANCE = it }
            }
        }
    }
}
