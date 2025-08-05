package com.example.augmented_mobile_application.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.augmented_mobile_application.ai.YOLO11Detector
import kotlinx.coroutines.flow.StateFlow
import android.graphics.Paint
import android.graphics.Rect

/**
 * Overlay component that draws YOLO detection bounding boxes over the camera feed
 */
@Composable
fun YOLODetectionOverlay(
    detections: StateFlow<List<YOLO11Detector.Detection>>,
    yoloEnabled: StateFlow<Boolean>,
    detectionCount: StateFlow<Int>,
    screenWidth: Float,
    screenHeight: Float,
    modifier: Modifier = Modifier
) {
    val detectionsState by detections.collectAsState()
    val enabled by yoloEnabled.collectAsState()
    val count by detectionCount.collectAsState()

    if (enabled && detectionsState.isNotEmpty()) {
        Canvas(
            modifier = modifier.fillMaxSize()
        ) {
            detectionsState.forEach { detection ->
                drawDetectionBox(detection, screenWidth, screenHeight)
            }
        }
    }
}

/**
 * Component showing YOLO detection status and controls
 */
@Composable
fun YOLODetectionStatus(
    yoloEnabled: StateFlow<Boolean>,
    isDetecting: StateFlow<Boolean>,
    detectionCount: StateFlow<Int>,
    onToggleYolo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled by yoloEnabled.collectAsState()
    val detecting by isDetecting.collectAsState()
    val count by detectionCount.collectAsState()

    Card(
        modifier = modifier
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "YOLO Detection",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggleYolo() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Green,
                        uncheckedThumbColor = Color.Gray
                    )
                )
            }
            
            if (enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Status:",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    
                    Text(
                        text = if (detecting) "Detecting..." else "Ready",
                        color = if (detecting) Color.Yellow else Color.Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Objects:",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    
                    Text(
                        text = count.toString(),
                        color = if (count > 0) Color.Green else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Draw a detection bounding box with label
 */
private fun DrawScope.drawDetectionBox(
    detection: YOLO11Detector.Detection,
    screenWidth: Float,
    screenHeight: Float
) {
    val box = detection.box
    
    // Calculate screen coordinates
    val left = box.x1
    val top = box.y1
    val right = box.x2
    val bottom = box.y2
    
    // Ensure coordinates are within screen bounds
    val clampedLeft = left.coerceAtLeast(0f)
    val clampedTop = top.coerceAtLeast(0f)
    val clampedRight = right.coerceAtMost(screenWidth)
    val clampedBottom = bottom.coerceAtMost(screenHeight)
    
    // Get color based on class ID
    val color = getClassColor(detection.classId)
    
    // Draw bounding box
    drawRect(
        color = color,
        topLeft = Offset(clampedLeft, clampedTop),
        size = Size(
            clampedRight - clampedLeft,
            clampedBottom - clampedTop
        ),
        style = Stroke(width = 3.dp.toPx())
    )
    
    // Draw confidence background
    val confidence = (detection.conf * 100).toInt()
    val label = "${getClassName(detection.classId)}: ${confidence}%"
    
    val textPaint = Paint().apply {
        this.color = Color.White.toArgb()
        textSize = 14.sp.toPx()
        isAntiAlias = true
    }
    
    val textBounds = Rect()
    textPaint.getTextBounds(label, 0, label.length, textBounds)
    
    val labelBackgroundTop = clampedTop - textBounds.height() - 8.dp.toPx()
    val labelBackgroundBottom = clampedTop - 4.dp.toPx()
    
    // Draw label background
    drawRect(
        color = color.copy(alpha = 0.8f),
        topLeft = Offset(clampedLeft, labelBackgroundTop),
        size = Size(
            textBounds.width() + 8.dp.toPx(),
            textBounds.height() + 4.dp.toPx()
        )
    )
    
    // Draw label text
    drawContext.canvas.nativeCanvas.drawText(
        label,
        clampedLeft + 4.dp.toPx(),
        clampedTop - 8.dp.toPx(),
        textPaint
    )
}

/**
 * Get color for different object classes
 */
private fun getClassColor(classId: Int): Color {
    return when (classId) {
        42 -> Color.Green  // Cup
        81 -> Color.Red    // Pump (custom class)
        82 -> Color.Blue   // Pipe (custom class)
        83 -> Color.Magenta // Steel pipe (custom class)
        84 -> Color.Yellow  // Electric cable (custom class)
        else -> Color.Cyan // Other COCO objects
    }
}

/**
 * Get class name from class ID based on the classes.txt file
 */
private fun getClassName(classId: Int): String {
    return when (classId) {
        42 -> "Cup"
        81 -> "Pump"
        82 -> "Pipe"
        83 -> "Steel Pipe"
        84 -> "Electric Cable"
        0 -> "Person"
        15 -> "Cat"
        16 -> "Dog"
        // Add more COCO classes as needed
        else -> "Object $classId"
    }
}
