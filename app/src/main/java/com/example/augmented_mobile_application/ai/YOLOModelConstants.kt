package com.example.augmented_mobile_application.ai

/**
 * Constants for YOLO model configuration
 * 
 * This file contains all the constants used for YOLO model configuration,
 * including input image dimensions and other model-related parameters.
 */
object YOLOModelConstants {
    
    /**
     * Input image width for YOLO model
     * Changed from 640 to 320 for improved inference speed
     */
    const val INPUT_WIDTH = 320
    
    /**
     * Input image height for YOLO model
     * Changed from 640 to 320 for improved inference speed
     */
    const val INPUT_HEIGHT = 320
    
    /**
     * Confidence threshold for detections
     */
    const val CONFIDENCE_THRESHOLD = 0.4f
    
    /**
     * IoU threshold for Non-Maximum Suppression
     */
    const val IOU_THRESHOLD = 0.45f
    
    /**
     * Maximum number of detections to keep after NMS
     */
    const val MAX_DETECTIONS = 300
    
    /**
     * Class IDs for specific objects we're interested in
     */
    const val PUMP_CLASS_ID = 81
    const val CUP_CLASS_ID = 41
    const val PIPE_CLASS_ID = 82
    
    /**
     * Target class ID for primary detection
     */
    const val TARGET_CLASS_ID = 41  // Changed from 82 to 41 (cup) as requested
}
