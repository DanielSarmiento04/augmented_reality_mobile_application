package com.example.augmented_mobile_application.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.augmented_mobile_application.ar.SurfaceChecker
import kotlinx.coroutines.flow.StateFlow

/**
 * UI component to display surface quality information and visual indicators
 */
@Composable
fun SurfaceQualityIndicator(
    surfaceQuality: StateFlow<SurfaceChecker.SurfaceQuality>,
    detectedSurfaces: StateFlow<List<SurfaceChecker.DetectedSurface>>,
    modifier: Modifier = Modifier
) {
    val quality by surfaceQuality.collectAsState()
    val surfaces by detectedSurfaces.collectAsState()
    
    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = when {
                    quality.isGoodSurface -> Icons.Default.CheckCircle
                    quality.qualityScore > 0.5f -> Icons.Default.Warning
                    else -> Icons.Default.Error
                },
                contentDescription = "Surface Status",
                tint = when {
                    quality.isGoodSurface -> Color.Green
                    quality.qualityScore > 0.5f -> Color.Yellow
                    else -> Color.Red
                },
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = "Calidad de Superficie",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Quality score bar
        SurfaceQualityBar(
            score = quality.qualityScore,
            isGood = quality.isGoodSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Surface info
        if (surfaces.isNotEmpty()) {
            Text(
                text = "Superficies detectadas: ${surfaces.size}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            
            val goodSurfaces = surfaces.count { it.quality.isGoodSurface }
            if (goodSurfaces > 0) {
                Text(
                    text = "Aptas para colocación: $goodSurfaces",
                    color = Color.Green.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
            }
        }
        
        // Quality details
        if (quality.area > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Área: ${String.format("%.2f", quality.area)}m² | " +
                       "Estabilidad: ${String.format("%.0f", quality.stability * 100)}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
        
        // Issues
        if (quality.issues.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = quality.issues.firstOrNull() ?: "",
                color = Color.Red.copy(alpha = 0.9f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SurfaceQualityBar(
    score: Float,
    isGood: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = when {
        isGood -> Color.Green
        score > 0.5f -> Color.Yellow
        else -> Color.Red
    }
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calidad",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
            Text(
                text = "${(score * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(3.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(score)
                    .fillMaxHeight()
                    .background(
                        barColor,
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

/**
 * Canvas overlay to draw surface indicators on the camera view
 */
@Composable
fun SurfaceOverlay(
    detectedSurfaces: StateFlow<List<SurfaceChecker.DetectedSurface>>,
    bestSurface: StateFlow<SurfaceChecker.DetectedSurface?>,
    modifier: Modifier = Modifier
) {
    val surfaces by detectedSurfaces.collectAsState()
    val best by bestSurface.collectAsState()
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        // Draw all detected surfaces
        surfaces.forEach { surface ->
            drawSurfaceIndicator(
                centerX = surface.centerX,
                centerY = surface.centerY,
                quality = surface.quality,
                isBest = surface == best
            )
        }
        
        // Draw placement target for best surface
        best?.let { bestSurface ->
            if (bestSurface.quality.isGoodSurface) {
                drawPlacementTarget(
                    centerX = bestSurface.centerX,
                    centerY = bestSurface.centerY
                )
            }
        }
    }
}

private fun DrawScope.drawSurfaceIndicator(
    centerX: Float,
    centerY: Float,
    quality: SurfaceChecker.SurfaceQuality,
    isBest: Boolean
) {
    val color = when {
        quality.isGoodSurface -> Color.Green
        quality.qualityScore > 0.5f -> Color.Yellow
        else -> Color.Red
    }
    
    val radius = if (isBest) 40f else 25f
    val strokeWidth = if (isBest) 4f else 2f
    
    // Draw circle indicator
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
    
    drawCircle(
        color = color,
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY),
        style = Stroke(width = strokeWidth)
    )
    
    // Draw inner dot
    drawCircle(
        color = color,
        radius = 4f,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
}

private fun DrawScope.drawPlacementTarget(
    centerX: Float,
    centerY: Float
) {
    val targetColor = Color.Cyan
    val center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    
    // Draw animated crosshair
    val crosshairSize = 20f
    val strokeWidth = 3f
    
    // Horizontal line
    drawLine(
        color = targetColor,
        start = androidx.compose.ui.geometry.Offset(centerX - crosshairSize, centerY),
        end = androidx.compose.ui.geometry.Offset(centerX + crosshairSize, centerY),
        strokeWidth = strokeWidth
    )
    
    // Vertical line
    drawLine(
        color = targetColor,
        start = androidx.compose.ui.geometry.Offset(centerX, centerY - crosshairSize),
        end = androidx.compose.ui.geometry.Offset(centerX, centerY + crosshairSize),
        strokeWidth = strokeWidth
    )
    
    // Outer ring
    drawCircle(
        color = targetColor,
        radius = 30f,
        center = center,
        style = Stroke(width = 2f)
    )
}

/**
 * Surface guidance text component
 */
@Composable
fun SurfaceGuidanceText(
    surfaceChecker: SurfaceChecker,
    modifier: Modifier = Modifier
) {
    val guidanceText = surfaceChecker.getSurfaceQualityDescription()
    
    Text(
        text = guidanceText,
        color = Color.White,
        fontSize = 14.sp,
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    )
}
