package com.example.augmented_mobile_application.ar

import android.util.Log
import io.github.sceneview.math.Color
import kotlin.math.pow
import kotlin.math.ln

/**
 * Color Space Management for Accurate GLB Rendering
 * 
 * Handles color space conversions and gamma correction to ensure colors
 * match between ARView and GLTF viewers like Don McCurdy's viewer.
 */
class ColorSpaceManager {
    
    companion object {
        private const val TAG = "ColorSpaceManager"
        
        // Color space constants
        private const val SRGB_GAMMA = 2.2f
        private const val LINEAR_GAMMA = 1.0f
        
        // Color correction factors for different environments
        private const val ANDROID_COLOR_CORRECTION = 1.1f
        private const val GLTF_VIEWER_MATCH_CORRECTION = 0.95f
        
        // Material-specific color adjustments based on Blender export
        private val MATERIAL_COLOR_ADJUSTMENTS = mapOf(
            "gris" to ColorAdjustment(
                brightnessMultiplier = 1.0f,
                contrastMultiplier = 1.1f,
                saturationMultiplier = 0.9f
            ),
            "negro" to ColorAdjustment(
                brightnessMultiplier = 1.2f, // Brighten dark materials slightly
                contrastMultiplier = 1.2f,
                saturationMultiplier = 1.0f
            ),
            "Material.006" to ColorAdjustment(
                brightnessMultiplier = 0.95f,
                contrastMultiplier = 1.0f,
                saturationMultiplier = 1.05f
            )
        )
    }
    
    /**
     * Data class for color adjustment parameters
     */
    data class ColorAdjustment(
        val brightnessMultiplier: Float = 1.0f,
        val contrastMultiplier: Float = 1.0f,
        val saturationMultiplier: Float = 1.0f
    )
    
    /**
     * Convert color from sRGB to linear color space
     */
    fun sRgbToLinear(color: Color): Color {
        return Color(
            sRgbToLinearComponent(color.r),
            sRgbToLinearComponent(color.g),
            sRgbToLinearComponent(color.b),
            color.a // Alpha is not gamma corrected
        )
    }
    
    /**
     * Convert color from linear to sRGB color space
     */
    fun linearToSRgb(color: Color): Color {
        return Color(
            linearToSRgbComponent(color.r),
            linearToSRgbComponent(color.g),
            linearToSRgbComponent(color.b),
            color.a // Alpha is not gamma corrected
        )
    }
    
    /**
     * Convert a single color component from sRGB to linear
     */
    private fun sRgbToLinearComponent(component: Float): Float {
        return if (component <= 0.04045f) {
            component / 12.92f
        } else {
            component.pow(SRGB_GAMMA)
        }
    }
    
    /**
     * Convert a single color component from linear to sRGB
     */
    private fun linearToSRgbComponent(component: Float): Float {
        return if (component <= 0.0031308f) {
            component * 12.92f
        } else {
            1.055f * component.pow(1.0f / SRGB_GAMMA) - 0.055f
        }
    }
    
    /**
     * Apply gamma correction to match GLTF viewer appearance
     */
    fun applyGammaCorrection(color: Color, gamma: Float = SRGB_GAMMA): Color {
        val correctionFactor = 1.0f / gamma
        return Color(
            color.r.pow(correctionFactor).coerceIn(0.0f, 1.0f),
            color.g.pow(correctionFactor).coerceIn(0.0f, 1.0f),
            color.b.pow(correctionFactor).coerceIn(0.0f, 1.0f),
            color.a
        )
    }
    
    /**
     * Apply material-specific color adjustments
     */
    fun applyMaterialColorAdjustment(color: Color, materialName: String): Color {
        val adjustment = MATERIAL_COLOR_ADJUSTMENTS[materialName] ?: return color
        
        // Apply brightness adjustment
        val brightnessAdjusted = Color(
            (color.r * adjustment.brightnessMultiplier).coerceIn(0.0f, 1.0f),
            (color.g * adjustment.brightnessMultiplier).coerceIn(0.0f, 1.0f),
            (color.b * adjustment.brightnessMultiplier).coerceIn(0.0f, 1.0f),
            color.a
        )
        
        // Apply contrast adjustment
        val contrastAdjusted = applyContrast(brightnessAdjusted, adjustment.contrastMultiplier)
        
        // Apply saturation adjustment
        val saturationAdjusted = applySaturation(contrastAdjusted, adjustment.saturationMultiplier)
        
        return saturationAdjusted
    }
    
    /**
     * Apply contrast adjustment to color
     */
    private fun applyContrast(color: Color, contrast: Float): Color {
        val factor = (259 * (contrast + 255)) / (255 * (259 - contrast))
        
        return Color(
            ((factor * (color.r - 0.5f)) + 0.5f).coerceIn(0.0f, 1.0f),
            ((factor * (color.g - 0.5f)) + 0.5f).coerceIn(0.0f, 1.0f),
            ((factor * (color.b - 0.5f)) + 0.5f).coerceIn(0.0f, 1.0f),
            color.a
        )
    }
    
    /**
     * Apply saturation adjustment to color
     */
    private fun applySaturation(color: Color, saturation: Float): Color {
        // Calculate luminance using standard weights
        val luminance = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b
        
        return Color(
            (luminance + (color.r - luminance) * saturation).coerceIn(0.0f, 1.0f),
            (luminance + (color.g - luminance) * saturation).coerceIn(0.0f, 1.0f),
            (luminance + (color.b - luminance) * saturation).coerceIn(0.0f, 1.0f),
            color.a
        )
    }
    
    /**
     * Convert Blender color to Android-compatible color
     */
    fun blenderToAndroidColor(blenderColor: Color): Color {
        // Blender typically uses linear color space, so we need to convert
        val linearColor = sRgbToLinear(blenderColor)
        
        // Apply Android-specific correction
        val androidAdjusted = Color(
            (linearColor.r * ANDROID_COLOR_CORRECTION).coerceIn(0.0f, 1.0f),
            (linearColor.g * ANDROID_COLOR_CORRECTION).coerceIn(0.0f, 1.0f),
            (linearColor.b * ANDROID_COLOR_CORRECTION).coerceIn(0.0f, 1.0f),
            linearColor.a
        )
        
        // Convert back to sRGB for display
        return linearToSRgb(androidAdjusted)
    }
    
    /**
     * Adjust color to match GLTF viewer appearance
     */
    fun adjustForGltfViewer(color: Color, materialName: String = ""): Color {
        try {
            // Apply material-specific adjustment first
            val materialAdjusted = if (materialName.isNotEmpty()) {
                applyMaterialColorAdjustment(color, materialName)
            } else {
                color
            }
            
            // Apply general GLTF viewer matching correction
            val viewerMatched = Color(
                (materialAdjusted.r * GLTF_VIEWER_MATCH_CORRECTION).coerceIn(0.0f, 1.0f),
                (materialAdjusted.g * GLTF_VIEWER_MATCH_CORRECTION).coerceIn(0.0f, 1.0f),
                (materialAdjusted.b * GLTF_VIEWER_MATCH_CORRECTION).coerceIn(0.0f, 1.0f),
                materialAdjusted.a
            )
            
            // Apply gamma correction for proper display
            val gammaCorr = applyGammaCorrection(viewerMatched)
            
            Log.d(TAG, "Color adjusted for GLTF viewer: $materialName")
            Log.d(TAG, "Original: ${color.r}, ${color.g}, ${color.b}")
            Log.d(TAG, "Adjusted: ${gammaCorr.r}, ${gammaCorr.g}, ${gammaCorr.b}")
            
            return gammaCorr
            
        } catch (e: Exception) {
            Log.w(TAG, "Error adjusting color for GLTF viewer: ${e.message}")
            return color
        }
    }
    
    /**
     * Create color from hex string (for debugging/testing)
     */
    fun colorFromHex(hex: String): Color {
        val cleanHex = hex.removePrefix("#")
        val r = cleanHex.substring(0, 2).toInt(16) / 255.0f
        val g = cleanHex.substring(2, 4).toInt(16) / 255.0f
        val b = cleanHex.substring(4, 6).toInt(16) / 255.0f
        val a = if (cleanHex.length == 8) {
            cleanHex.substring(6, 8).toInt(16) / 255.0f
        } else {
            1.0f
        }
        
        return Color(r, g, b, a)
    }
    
    /**
     * Convert color to hex string (for debugging)
     */
    fun colorToHex(color: Color): String {
        val r = (color.r * 255).toInt().coerceIn(0, 255)
        val g = (color.g * 255).toInt().coerceIn(0, 255)
        val b = (color.b * 255).toInt().coerceIn(0, 255)
        val a = (color.a * 255).toInt().coerceIn(0, 255)
        
        return "#%02X%02X%02X%02X".format(r, g, b, a)
    }
    
    /**
     * Test color conversion accuracy
     */
    fun testColorConversion(testColor: Color, materialName: String = ""): ColorConversionResult {
        val original = testColor
        val adjusted = adjustForGltfViewer(testColor, materialName)
        val linearOriginal = sRgbToLinear(original)
        val linearAdjusted = sRgbToLinear(adjusted)
        
        return ColorConversionResult(
            original = original,
            adjusted = adjusted,
            linearOriginal = linearOriginal,
            linearAdjusted = linearAdjusted,
            materialName = materialName
        )
    }
    
    /**
     * Result of color conversion testing
     */
    data class ColorConversionResult(
        val original: Color,
        val adjusted: Color,
        val linearOriginal: Color,
        val linearAdjusted: Color,
        val materialName: String
    )
}
