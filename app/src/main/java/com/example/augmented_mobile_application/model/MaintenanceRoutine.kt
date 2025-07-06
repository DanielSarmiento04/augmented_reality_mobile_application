package com.example.augmented_mobile_application.model

/**
 * Data class representing a maintenance routine
 */
data class MaintenanceRoutine(
    val id: String,
    val name: String,
    val displayName: String,
    val description: String = "",
    val glbFileName: String,
    val glbAssetPath: String,
    val steps: List<MaintenanceStep>
)

/**
 * Data class representing a single step in a maintenance routine
 */
data class MaintenanceStep(
    val id: String,
    val title: String,
    val instruction: String,
    val description: String,
    val stepNumber: Int
)

/**
 * UI state for the routine execution
 */
data class RoutineExecutionState(
    val currentRoutine: MaintenanceRoutine? = null,
    val currentStepIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRoutineCompleted: Boolean = false
) {
    val currentStep: MaintenanceStep?
        get() = currentRoutine?.steps?.getOrNull(currentStepIndex)
    
    val canGoToNextStep: Boolean
        get() = currentRoutine?.let { currentStepIndex < it.steps.size - 1 } ?: false
    
    val canGoToPreviousStep: Boolean
        get() = currentStepIndex > 0
    
    val progressPercentage: Float
        get() = currentRoutine?.let { 
            if (it.steps.isEmpty()) 0f 
            else (currentStepIndex + 1).toFloat() / it.steps.size.toFloat() 
        } ?: 0f
}
