package com.example.augmented_mobile_application.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import com.example.augmented_mobile_application.viewmodel.SharedRoutineViewModel

/**
 * AR TopAppBar that shows current routine step and navigation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ARRoutineTopBar(
    onBack: () -> Unit,
    sharedViewModel: SharedRoutineViewModel = SharedRoutineViewModel.getInstance()
) {
    val currentRoutine by sharedViewModel.currentRoutine.collectAsState()
    val currentStepIndex by sharedViewModel.currentStepIndex.collectAsState()
    val isMaintenanceActive by sharedViewModel.isMaintenanceActive.collectAsState()
    
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkGreen,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isMaintenanceActive) sharedViewModel.routineTitle else "Mantenimiento AR",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                
                if (isMaintenanceActive && currentRoutine != null) {
                    Text(
                        text = sharedViewModel.currentStepDisplay,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    
                    // Progress indicator
                    val progress = sharedViewModel.getProgressPercentage()
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(top = 2.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
        },
        actions = {
            if (isMaintenanceActive && currentRoutine != null) {
                // Previous step button
                IconButton(
                    onClick = { sharedViewModel.previousStep() },
                    enabled = sharedViewModel.hasPreviousStep()
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Paso anterior",
                        tint = if (sharedViewModel.hasPreviousStep()) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Next step button
                IconButton(
                    onClick = { sharedViewModel.nextStep() },
                    enabled = sharedViewModel.hasNextStep()
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Siguiente paso",
                        tint = if (sharedViewModel.hasNextStep()) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    )
}

/**
 * Current step instruction card for AR overlay
 */
@Composable
fun ARStepInstructionCard(
    modifier: Modifier = Modifier,
    sharedViewModel: SharedRoutineViewModel = SharedRoutineViewModel.getInstance()
) {
    val currentRoutine by sharedViewModel.currentRoutine.collectAsState()
    val isMaintenanceActive by sharedViewModel.isMaintenanceActive.collectAsState()
    
    if (isMaintenanceActive && currentRoutine != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Instrucción Actual",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = sharedViewModel.currentStepInstruction,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Start
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Step navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { sharedViewModel.previousStep() },
                        enabled = sharedViewModel.hasPreviousStep(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                        modifier = Modifier.weight(0.4f)
                    ) {
                        Text("Anterior", fontSize = 12.sp)
                    }
                    
                    Spacer(modifier = Modifier.weight(0.2f))
                    
                    Button(
                        onClick = { sharedViewModel.nextStep() },
                        enabled = sharedViewModel.hasNextStep(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                        modifier = Modifier.weight(0.4f)
                    ) {
                        Text("Siguiente", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
