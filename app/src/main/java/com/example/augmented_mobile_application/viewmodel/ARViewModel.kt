package com.example.augmented_mobile_application.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import com.example.augmented_mobile_application.repository.RoutineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing AR state and step navigation
 */
class ARViewModel(private val context: Context) : ViewModel() {
    
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()
    
    private val _totalSteps = MutableStateFlow(1)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()
    
    private val _stepDescription = MutableStateFlow("")
    val stepDescription: StateFlow<String> = _stepDescription.asStateFlow()
    
    private val _currentRoutine = MutableStateFlow<MaintenanceRoutine?>(null)
    val currentRoutine: StateFlow<MaintenanceRoutine?> = _currentRoutine.asStateFlow()
    
    private val _isLoadingRoutine = MutableStateFlow(false)
    val isLoadingRoutine: StateFlow<Boolean> = _isLoadingRoutine.asStateFlow()
    
    private val _maintenanceStarted = MutableStateFlow(false)
    val maintenanceStarted: StateFlow<Boolean> = _maintenanceStarted.asStateFlow()
    
    private val _modelPlaced = MutableStateFlow(false)
    val modelPlaced: StateFlow<Boolean> = _modelPlaced.asStateFlow()
    
    private val defaultInstructions = listOf(
        "Mueva lentamente el dispositivo para detectar superficies planas",
        "Verificar que la bomba esté apagada",
        "Inspeccionar el estado general de la bomba",
        "Mantenimiento completado con éxito"
    )
    
    init {
        updateStepDescription()
    }
    
    fun loadRoutine(routineId: String) {
        viewModelScope.launch {
            _isLoadingRoutine.value = true
            try {
                val repository = RoutineRepository.getInstance(context)
                val result = repository.getRoutine(routineId)
                result.onSuccess { routine ->
                    _currentRoutine.value = routine
                    _totalSteps.value = routine.steps.size
                    updateStepDescription()
                }.onFailure { error ->
                    // Handle error - keep default instructions
                    _totalSteps.value = defaultInstructions.size
                    updateStepDescription()
                }
            } catch (e: Exception) {
                // Handle error - keep default instructions
                _totalSteps.value = defaultInstructions.size
                updateStepDescription()
            } finally {
                _isLoadingRoutine.value = false
            }
        }
    }
    
    fun startMaintenance() {
        _maintenanceStarted.value = true
        _currentStep.value = 1
        updateStepDescription()
    }
    
    fun onModelPlaced() {
        _modelPlaced.value = true
        if (_currentStep.value == 0) {
            _currentStep.value = 1
        }
        updateStepDescription()
    }
    
    fun navigateToPreviousStep() {
        val current = _currentStep.value
        if (current > 1) {
            _currentStep.value = current - 1
            updateStepDescription()
        }
    }
    
    fun navigateToNextStep(): Boolean {
        val current = _currentStep.value
        val total = _totalSteps.value
        
        return if (current < total - 1) {
            _currentStep.value = current + 1
            updateStepDescription()
            false // Not finished
        } else {
            true // Finished
        }
    }
    
    fun resetRoutine() {
        _maintenanceStarted.value = false
        _modelPlaced.value = false
        _currentStep.value = 0
        updateStepDescription()
    }
    
    fun canNavigatePrevious(): Boolean {
        return _maintenanceStarted.value && _currentStep.value > 1
    }
    
    fun canNavigateNext(): Boolean {
        return _maintenanceStarted.value && _modelPlaced.value
    }
    
    fun getNextButtonText(): String {
        val current = _currentStep.value
        val total = _totalSteps.value
        return if (current < total - 1) "Siguiente" else "Finalizar"
    }
    
    fun getProgress(): Float {
        val current = _currentStep.value
        val total = _totalSteps.value
        return if (total > 0) current.toFloat() / (total - 1).toFloat() else 0f
    }
    
    private fun updateStepDescription() {
        val instructions = _currentRoutine.value?.steps?.map { it.instruction } ?: defaultInstructions
        val stepIndex = _currentStep.value.coerceIn(0, instructions.size - 1)
        _stepDescription.value = instructions[stepIndex]
    }
    
    fun getCurrentStepInstruction(): String {
        val instructions = _currentRoutine.value?.steps?.map { it.instruction } ?: defaultInstructions
        val stepIndex = _currentStep.value.coerceIn(0, instructions.size - 1)
        return instructions[stepIndex]
    }
    
    fun getCurrentStepTips(): List<String> {
        // Return empty list since MaintenanceStep doesn't have tips property yet
        // This can be extended when the model is updated
        return emptyList()
    }
    
    fun getCurrentStepMedia(): String? {
        // Return null since MaintenanceStep doesn't have mediaPath property yet  
        // This can be extended when the model is updated
        return null
    }
}
