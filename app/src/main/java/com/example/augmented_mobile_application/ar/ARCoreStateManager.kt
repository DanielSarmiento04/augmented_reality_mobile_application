package com.example.augmented_mobile_application.ar

import android.util.Log
import com.google.ar.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages ARCore session state and provides utilities for safe ARCore operations
 */
class ARCoreStateManager {
    companion object {
        private const val TAG = "ARCoreStateManager"
    }

    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState

    private val _trackingFailureReason = MutableStateFlow<TrackingFailureReason?>(null)
    val trackingFailureReason: StateFlow<TrackingFailureReason?> = _trackingFailureReason

    private val currentFrame = AtomicReference<Frame?>(null)

    /**
     * Update tracking state from ARCore frame
     */
    fun updateTrackingState(frame: Frame?) {
        currentFrame.set(frame)
        
        frame?.let {
            val newTrackingState = it.camera.trackingState
            val newFailureReason = it.camera.trackingFailureReason
            
            if (_trackingState.value != newTrackingState) {
                _trackingState.value = newTrackingState
                Log.d(TAG, "ARCore tracking state changed to: $newTrackingState")
            }
            
            if (_trackingFailureReason.value != newFailureReason) {
                _trackingFailureReason.value = newFailureReason
                if (newFailureReason != TrackingFailureReason.NONE) {
                    Log.w(TAG, "ARCore tracking failure: $newFailureReason")
                }
            }
        }
    }

    /**
     * Check if ARCore is ready for operations (tracking and frame available)
     */
    fun isReadyForOperations(): Boolean {
        return _trackingState.value == TrackingState.TRACKING && currentFrame.get() != null
    }

    /**
     * Safely perform hit test only when ARCore is tracking
     */
    fun safeHitTest(x: Float, y: Float): List<HitResult>? {
        return if (isReadyForOperations()) {
            try {
                currentFrame.get()?.hitTest(x, y)
            } catch (e: Exception) {
                Log.e(TAG, "Hit test failed: ${e.message}", e)
                null
            }
        } else {
            Log.w(TAG, "Cannot perform hit test - ARCore not tracking. State: ${_trackingState.value}")
            null
        }
    }

    /**
     * Check if camera image is available for processing
     */
    fun isCameraImageAvailable(): Boolean {
        return isReadyForOperations() && try {
            currentFrame.get()?.acquireCameraImage()?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get tracking state description for UI display
     */
    fun getTrackingStateDescription(): String {
        return when (_trackingState.value) {
            TrackingState.TRACKING -> "Tracking"
            TrackingState.PAUSED -> "Tracking Paused"
            TrackingState.STOPPED -> "Tracking Stopped"
        }
    }

    /**
     * Get failure reason description for UI display
     */
    fun getFailureReasonDescription(): String? {
        return when (_trackingFailureReason.value) {
            TrackingFailureReason.NONE -> null
            TrackingFailureReason.BAD_STATE -> "Sistema AR en mal estado"
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "Iluminación insuficiente"
            TrackingFailureReason.EXCESSIVE_MOTION -> "Movimiento excesivo"
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Insuficientes características visuales"
            else -> "Problema de seguimiento desconocido"
        }
    }

    /**
     * Validate session configuration for optimal performance
     */
    fun validateSessionConfig(session: Session, config: Config): List<String> {
        val warnings = mutableListOf<String>()

        // Check depth mode support
        if (config.depthMode == Config.DepthMode.DISABLED && 
            session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            warnings.add("Depth mode is disabled but supported - consider enabling for better tracking")
        }

        // Check instant placement support
        if (config.instantPlacementMode == Config.InstantPlacementMode.DISABLED &&
            session.isInstantPlacementModeSupported(Config.InstantPlacementMode.LOCAL_Y_UP)) {
            warnings.add("Instant placement is disabled but supported - consider enabling for faster placement")
        }

        // Check light estimation
        if (config.lightEstimationMode == Config.LightEstimationMode.DISABLED) {
            warnings.add("Light estimation is disabled - this may affect rendering quality")
        }

        // Check plane finding mode
        if (config.planeFindingMode == Config.PlaneFindingMode.DISABLED) {
            warnings.add("Plane finding is disabled - this may affect placement accuracy")
        }

        return warnings
    }

    /**
     * Log session capabilities for debugging
     */
    fun logSessionCapabilities(session: Session) {
        Log.i(TAG, "ARCore Session Capabilities:")
        Log.i(TAG, "- Depth mode supported: ${session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)}")
        Log.i(TAG, "- Instant placement supported: ${session.isInstantPlacementModeSupported(Config.InstantPlacementMode.LOCAL_Y_UP)}")
        Log.i(TAG, "- Shared camera supported: ${session.isSharedCameraUsed}")
        
        try {
            val cameraConfigFilter = CameraConfigFilter(session)
            val supportedConfigs = session.getSupportedCameraConfigs(cameraConfigFilter)
            Log.i(TAG, "- Camera configurations available: ${supportedConfigs.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not get camera configurations: ${e.message}")
        }
    }
}
