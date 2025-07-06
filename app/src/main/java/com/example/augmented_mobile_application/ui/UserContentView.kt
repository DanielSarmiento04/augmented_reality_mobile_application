package com.example.augmented_mobile_application.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_mobile_application.viewmodel.ManualViewModel
import com.example.augmented_mobile_application.viewmodel.UserViewModel
import com.example.augmented_mobile_application.viewmodel.RoutineSelectionViewModel
import com.example.augmented_mobile_application.model.User
import com.example.augmented_mobile_application.model.AuthState
import com.example.augmented_mobile_application.model.MaintenanceRoutine
import com.example.augmented_mobile_application.model.MaintenanceStep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import com.example.augmented_mobile_application.ui.theme.OffWhite
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserContentView(
    navController: NavHostController,
    userViewModel: UserViewModel,
    manualViewModel: ManualViewModel = viewModel(),
    onLogout: () -> Unit
) {
    // Get AuthState from ViewModel
    val authState by userViewModel.authState.collectAsState()
    
    // Extract user from AuthState if authenticated
    val user = when (authState) {
        is AuthState.Authenticated -> (authState as AuthState.Authenticated).user
        else -> null
    }

    // Initialize RoutineSelectionViewModel
    val context = LocalContext.current
    val routineViewModel: RoutineSelectionViewModel = remember { RoutineSelectionViewModel(context) }
    
    // Collect states
    val availableRoutines by routineViewModel.availableRoutines.collectAsState()
    val selectedRoutine by routineViewModel.selectedRoutine.collectAsState()
    val showStepDetails by routineViewModel.showStepDetails.collectAsState()
    val isLoading by routineViewModel.isLoading.collectAsState()
    val repositoryError by routineViewModel.error.collectAsState()
    val localError by routineViewModel.localError.collectAsState()
    val arSessionRequested by routineViewModel.arSessionRequested.collectAsState()
    
    // Combine errors
    val error = localError ?: repositoryError

    // Define constant for machine type
    val MACHINE_TYPE = "Bomba Centrifuga"

    // Navigate back to login if not authenticated
    LaunchedEffect(authState) {
        if (authState !is AuthState.Authenticated) {
            navController.navigate("login") {
                popUpTo("userContent") { inclusive = true }
            }
        }
    }
    
    // Handle AR session requests - go directly to AR when routine is selected
    LaunchedEffect(arSessionRequested) {
        arSessionRequested?.let { glbPath ->
            // Pass both the machine type and the selected routine data
            val selectedRoutineData = selectedRoutine
            if (selectedRoutineData != null) {
                navController.navigate("arView/${MACHINE_TYPE}?glbPath=${glbPath}&routineId=${selectedRoutineData.id}")
            } else {
                navController.navigate("arView/$MACHINE_TYPE?glbPath=${glbPath}")
            }
            routineViewModel.clearArSessionRequest()
        }
    }

    // Create ImageLoader with GIF decoder
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(GifDecoder.Factory())
            }
            .build()
    }

    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            user?.let {
                CenterAlignedTopAppBarExample(
                    user = it,
                    onLogout = onLogout
                )
            } ?: run {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkGreen,
                        titleContentColor = Color.White
                    ),
                    title = { Text("Mantenimiento AR") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 16.dp)
                .background(OffWhite),
            contentAlignment = Alignment.Center
        ) {
            // Always show routine selection screen - no intermediate step view
            RoutineSelectionView(
                machineType = MACHINE_TYPE,
                imageLoader = imageLoader,
                availableRoutines = availableRoutines,
                isLoading = isLoading,
                error = error,
                onRoutineSelected = { routine ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Select routine and immediately start AR session
                    routineViewModel.selectRoutine(routine)
                    routineViewModel.startMaintenanceAR()
                },
                onNavigateToManuals = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.navigate("pumpManuals")
                },
                onLogout = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLogout()
                },
                onRetry = { routineViewModel.loadRoutines() },
                onClearError = { 
                    routineViewModel.clearError()
                    routineViewModel.clearLocalError()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenterAlignedTopAppBarExample(
    user: User,
    onLogout: () -> Unit
) {
    var isDialogOpen by remember { mutableStateOf(false) }
    var isMenuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current // Get haptic feedback instance

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkGreen,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        title = {
            Text(
                text = "Mantenimiento AR",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        },
        navigationIcon = {
            Box {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Add haptic feedback
                    isMenuOpen = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Menú",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                DropdownMenu(
                    expanded = isMenuOpen,
                    onDismissRequest = { isMenuOpen = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    DropdownMenuItem(
                        text = { Text("Inicio", color = DarkGreen) },
                        onClick = { 
                            isMenuOpen = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Historial de mantenimiento", color = DarkGreen) },
                        onClick = { 
                            isMenuOpen = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Configuración", color = DarkGreen) },
                        onClick = { isMenuOpen = false }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Cerrar sesión", color = Color.Red) },
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Add haptic feedback
                            isMenuOpen = false
                            onLogout()
                        }
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress) // Add haptic feedback
                isDialogOpen = true
            }) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.take(2).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    )

    if (isDialogOpen) {
        AlertDialog(
            onDismissRequest = { isDialogOpen = false },
            title = {
                Text(
                    text = "Información del Usuario",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(text = "Nombre de usuario: ${user.username}", fontSize = 16.sp)
                    Text(text = "Rol: ${user.role}", fontSize = 16.sp)
                }
            },
            confirmButton = {
                Button(onClick = { isDialogOpen = false }) {
                    Text(text = "Cerrar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedDropdownMenuField(
    label: String,
    items: List<String>,
    selectedValue: String?,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = selectedValue ?: label

    Column(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = displayText,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkGreen,
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = item,
                                color = if (selectedValue == item) DarkGreen else Color.Black,
                                fontWeight = if (selectedValue == item) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        onClick = {
                            onValueChange(item)
                            expanded = false
                        },
                        modifier = Modifier.background(
                            if (selectedValue == item) Color(0xFFEAF5EA) else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

/**
 * Routine details view with step-by-step pager
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoutineDetailsView(
    routine: MaintenanceRoutine,
    onBack: () -> Unit,
    onStartMaintenance: () -> Unit,
    routineViewModel: RoutineSelectionViewModel
) {
    val currentStepIndex by routineViewModel.currentStepIndex.collectAsState()
    val routineProgress by routineViewModel.routineProgress.collectAsState(initial = null)
    val pagerState = rememberPagerState(pageCount = { routine.steps.size })
    val coroutineScope = rememberCoroutineScope()
    
    // Sync pager with ViewModel
    LaunchedEffect(currentStepIndex) {
        if (pagerState.currentPage != currentStepIndex) {
            pagerState.animateScrollToPage(currentStepIndex)
        }
    }
    
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentStepIndex) {
            routineViewModel.goToStep(pagerState.currentPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = DarkGreen
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
                routineProgress?.let { progress ->
                    Text(
                        text = "Paso ${progress.currentStep} de ${progress.totalSteps}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
            
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.Gray
                )
            }
        }

        // Progress indicator
        routineProgress?.let { progress ->
            LinearProgressIndicator(
                progress = progress.progressPercentage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                color = DarkGreen,
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
        }

        // Step content pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            StepCard(
                step = routine.steps[page],
                stepNumber = page + 1,
                totalSteps = routine.steps.size
            )
        }

        // Navigation controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (routineViewModel.hasPreviousStep()) {
                            routineViewModel.previousStep()
                        }
                    }
                },
                enabled = routineViewModel.hasPreviousStep(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen.copy(alpha = 0.7f))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Anterior",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Anterior")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (routineViewModel.hasNextStep()) {
                            routineViewModel.nextStep()
                        }
                    }
                },
                enabled = routineViewModel.hasNextStep(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen.copy(alpha = 0.7f))
            ) {
                Text("Siguiente")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Siguiente",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Start maintenance FAB
        FloatingActionButton(
            onClick = onStartMaintenance,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            containerColor = DarkGreen,
            contentColor = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Iniciar",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Iniciar Mantenimiento",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Individual step card
 */
@Composable
fun StepCard(
    step: MaintenanceStep,
    stepNumber: Int,
    totalSteps: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Step header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paso $stepNumber",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )
                
                Text(
                    text = "$stepNumber/$totalSteps",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .background(
                            Color.Gray.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step instruction
            Text(
                text = step.instruction,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = Color.Black,
                textAlign = TextAlign.Start
            )
        }
    }
}

/**
 * Routine selection screen - displays available routines as cards
 */
@Composable
fun RoutineSelectionView(
    machineType: String,
    imageLoader: ImageLoader,
    availableRoutines: List<MaintenanceRoutine>,
    isLoading: Boolean,
    error: String?,
    onRoutineSelected: (MaintenanceRoutine) -> Unit,
    onNavigateToManuals: () -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with machine info
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = machineType,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/pump/pump.gif")
                        .size(Size.ORIGINAL)
                        .build(),
                    contentDescription = "Animated pump GIF",
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Error handling
        error?.let { errorMessage ->
            item {
                ErrorCard(
                    error = errorMessage,
                    onRetry = onRetry,
                    onDismiss = onClearError
                )
            }
        }

        // Loading state
        if (isLoading) {
            item {
                LoadingCard()
            }
        }

        // Routine selection header
        if (!isLoading && availableRoutines.isNotEmpty()) {
            item {
                Text(
                    text = "Seleccione una rutina de mantenimiento",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Routine cards
        items(availableRoutines) { routine ->
            RoutineCard(
                routine = routine,
                onSelect = { onRoutineSelected(routine) }
            )
        }

        // Action buttons
        item {
            ActionButtonsSection(
                onNavigateToManuals = onNavigateToManuals,
                onLogout = onLogout
            )
        }
    }
}

/**
 * Individual routine card with direct start button
 */
@Composable
fun RoutineCard(
    routine: MaintenanceRoutine,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = routine.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkGreen
                    )
                    Text(
                        text = "${routine.steps.size} pasos",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // Preview of first few steps
            if (routine.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Primer paso: ${routine.steps.first().instruction.take(60)}...",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
            
            // Direct start button
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Iniciar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Iniciar Mantenimiento",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Error display card
 */
@Composable
fun ErrorCard(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Error",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
                TextButton(onClick = onRetry) {
                    Text("Reintentar")
                }
            }
        }
    }
}

/**
 * Loading state card
 */
@Composable
fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = DarkGreen
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Cargando rutinas de mantenimiento...",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * Action buttons section
 */
@Composable
fun ActionButtonsSection(
    onNavigateToManuals: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onNavigateToManuals,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
        ) {
            Text(text = "Ver Manuales de la Bomba")
        }

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(text = "Cerrar Sesión", color = Color.White)
        }
    }
}
