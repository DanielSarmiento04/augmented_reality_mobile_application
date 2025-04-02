package com.example.augmented_mobile_application.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.augmented_mobile_application.viewmodel.ManualState
import com.example.augmented_mobile_application.viewmodel.ManualViewModel
import com.example.augmented_mobile_application.viewmodel.ZoomPanState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import com.example.augmented_mobile_application.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualView(
    navController: NavHostController,
    manualViewModel: ManualViewModel,
    pdfName: String
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    // State from ViewModel
    val manualState by manualViewModel.manualState.collectAsState()
    
    // Local UI state
    var zoomPanState by remember { mutableStateOf(ZoomPanState()) }
    var currentPage by remember { mutableStateOf(0) }
    
    // Animated values
    val animatedScale by animateFloatAsState(
        targetValue = zoomPanState.scale,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    
    // Load PDF on launch
    LaunchedEffect(pdfName) {
        manualViewModel.displayPdf(context, pdfName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Documentación") },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.popBackStack() 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = manualState) {
                is ManualState.Loading -> {
                    LoadingView()
                }
                
                is ManualState.Error -> {
                    ErrorView(
                        errorMessage = state.message,
                        onRetry = {
                            resetViewState(zoomPanState = { zoomPanState = ZoomPanState() }, currentPage = { currentPage = 0 })
                            manualViewModel.displayPdf(context, pdfName)
                        }
                    )
                }
                
                is ManualState.Success -> {
                    if (state.pages.isEmpty()) {
                        EmptyPdfView()
                    } else {
                        PdfViewContent(
                            pages = state.pages,
                            currentPage = currentPage,
                            zoomPanState = zoomPanState,
                            onZoomPanStateChange = { zoomPanState = it },
                            onPageChange = { newPage -> 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentPage = newPage
                                zoomPanState = ZoomPanState() // Reset zoom/pan when changing pages
                            },
                            animatedScale = animatedScale
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cargando documento...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ErrorView(errorMessage: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_error),
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
fun EmptyPdfView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No se encontraron páginas en el documento",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun PdfViewContent(
    pages: List<Bitmap>,
    currentPage: Int,
    zoomPanState: ZoomPanState,
    onZoomPanStateChange: (ZoomPanState) -> Unit,
    onPageChange: (Int) -> Unit,
    animatedScale: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Zoomable PDF view
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (zoomPanState.scale * zoom).coerceIn(1f, 5f)
                        val maxX = (newScale - 1) * size.width / 2
                        val maxY = (newScale - 1) * size.height / 2

                        onZoomPanStateChange(
                            ZoomPanState(
                                scale = newScale,
                                offsetX = (zoomPanState.offsetX + pan.x).coerceIn(-maxX, maxX),
                                offsetY = (zoomPanState.offsetY + pan.y).coerceIn(-maxY, maxY)
                            )
                        )
                    }
                }
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = zoomPanState.offsetX,
                    translationY = zoomPanState.offsetY
                ),
            contentAlignment = Alignment.Center
        ) {
            // Fixed AnimatedVisibility usage by removing it and showing image directly
            if (pages.isNotEmpty() && currentPage in pages.indices) {
                Image(
                    bitmap = pages[currentPage].asImageBitmap(),
                    contentDescription = "Page ${currentPage + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Page navigation controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onPageChange(currentPage - 1) },
                enabled = currentPage > 0,
                modifier = Modifier.alpha(if (currentPage > 0) 1f else 0.5f)
            ) {
                Text(text = "Anterior")
            }
            
            Text(
                text = "Página ${currentPage + 1} / ${pages.size}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Button(
                onClick = { onPageChange(currentPage + 1) },
                enabled = currentPage < pages.size - 1,
                modifier = Modifier.alpha(if (currentPage < pages.size - 1) 1f else 0.5f)
            ) {
                Text(text = "Siguiente")
            }
        }
    }
}

private fun resetViewState(
    zoomPanState: () -> Unit,
    currentPage: () -> Unit
) {
    zoomPanState()
    currentPage()
}
