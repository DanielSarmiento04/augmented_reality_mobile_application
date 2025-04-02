package com.example.augmented_mobile_application.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.augmented_mobile_application.viewmodel.ManualState
import com.example.augmented_mobile_application.viewmodel.ManualViewModel
import com.example.augmented_mobile_application.viewmodel.ZoomPanState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.example.augmented_mobile_application.R
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import com.example.augmented_mobile_application.ui.theme.OffWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualView(
    navController: NavHostController,
    manualViewModel: ManualViewModel,
    pdfName: String
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    // State from ViewModel
    val manualState by manualViewModel.manualState.collectAsState()
    val pageCount by manualViewModel.pageCount.collectAsState()
    
    // Local UI state
    var zoomPanState by remember { mutableStateOf(ZoomPanState()) }
    var currentPage by remember { mutableStateOf(0) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
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
    
    // Load the current page when it changes
    LaunchedEffect(currentPage, manualState) {
        if (manualState is ManualState.Initialized || manualState is ManualState.PageReady) {
            coroutineScope.launch {
                currentPageBitmap = manualViewModel.getPageAtIndex(currentPage)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Documentación",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.popBackStack() 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Add search icon
                    IconButton(onClick = { /* TODO: Implement search functionality */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search in document",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkGreen,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(OffWhite),
            contentAlignment = Alignment.Center
        ) {
            when (val state = manualState) {
                is ManualState.Loading -> {
                    LoadingView()
                }
                
                is ManualState.Error -> {
                    ErrorView(
                        errorMessage = state.message,
                        onRetry = {
                            resetViewState(
                                zoomPanState = { zoomPanState = ZoomPanState() }, 
                                currentPage = { currentPage = 0 }
                            )
                            manualViewModel.displayPdf(context, pdfName)
                        }
                    )
                }
                
                is ManualState.Initialized, is ManualState.PageReady -> {
                    if (pageCount <= 0) {
                        EmptyPdfView()
                    } else {
                        EnhancedPdfViewContent(
                            currentPage = currentPage,
                            pageCount = pageCount,
                            pageBitmap = currentPageBitmap,
                            zoomPanState = zoomPanState,
                            onZoomPanStateChange = { zoomPanState = it },
                            onPageChange = { newPage -> 
                                if (newPage in 0 until pageCount) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentPage = newPage
                                    zoomPanState = ZoomPanState() // Reset zoom/pan when changing pages
                                }
                            },
                            animatedScale = animatedScale
                        )
                    }
                }
                
                is ManualState.Success -> {
                    // Legacy support for the old implementation
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
fun EnhancedPdfViewContent(
    currentPage: Int,
    pageCount: Int,
    pageBitmap: Bitmap?,
    zoomPanState: ZoomPanState,
    onZoomPanStateChange: (ZoomPanState) -> Unit,
    onPageChange: (Int) -> Unit,
    animatedScale: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Document title
        Text(
            text = "Manual de Operación",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkGreen,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Zoomable PDF view with additional controls
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        ) {
            // PDF Page rendering
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
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
                if (pageBitmap != null) {
                    Image(
                        bitmap = pageBitmap.asImageBitmap(),
                        contentDescription = "Page ${currentPage + 1}",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = DarkGreen)
                }
            }
            
            // Zoom controls overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { 
                        val newScale = (zoomPanState.scale - 0.5f).coerceIn(1f, 5f)
                        onZoomPanStateChange(zoomPanState.copy(scale = newScale))
                    },
                    containerColor = DarkGreen.copy(alpha = 0.8f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = Color.White
                    )
                }
                
                FloatingActionButton(
                    onClick = { 
                        val newScale = (zoomPanState.scale + 0.5f).coerceIn(1f, 5f)
                        onZoomPanStateChange(zoomPanState.copy(scale = newScale))
                    },
                    containerColor = DarkGreen.copy(alpha = 0.8f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enhanced page navigation controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onPageChange(currentPage - 1) },
                enabled = currentPage > 0,
                modifier = Modifier.alpha(if (currentPage > 0) 1f else 0.5f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
            ) {
                Text(text = "Anterior", color = Color.White)
            }
            
            Text(
                text = "Página ${currentPage + 1} / $pageCount",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = DarkGreen
            )
            
            Button(
                onClick = { onPageChange(currentPage + 1) },
                enabled = currentPage < pageCount - 1,
                modifier = Modifier.alpha(if (currentPage < pageCount - 1) 1f else 0.5f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
            ) {
                Text(text = "Siguiente", color = Color.White)
            }
        }
    }
}

@Composable
fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White)
                .padding(32.dp)
        ) {
            CircularProgressIndicator(color = DarkGreen)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Cargando documento...",
                style = MaterialTheme.typography.bodyLarge,
                color = DarkGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ErrorView(errorMessage: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White)
                .padding(24.dp)
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
                color = Color.DarkGray
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DarkGreen
                )
            ) {
                Text("Reintentar", color = Color.White)
            }
        }
    }
}

@Composable
fun EmptyPdfView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White)
                .padding(24.dp)
        ) {
            Text(
                text = "No se encontraron páginas en el documento",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = DarkGreen,
                fontWeight = FontWeight.Medium
            )
        }
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
            .padding(16.dp)
    ) {
        // Document title
        Text(
            text = "Manual de Operación",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkGreen,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Zoomable PDF view with white background
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(8.dp)
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
            if (pages.isNotEmpty() && currentPage in pages.indices) {
                Image(
                    bitmap = pages[currentPage].asImageBitmap(),
                    contentDescription = "Page ${currentPage + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Page navigation controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onPageChange(currentPage - 1) },
                enabled = currentPage > 0,
                modifier = Modifier.alpha(if (currentPage > 0) 1f else 0.5f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
            ) {
                Text(text = "Anterior", color = Color.White)
            }
            
            Text(
                text = "Página ${currentPage + 1} / ${pages.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = DarkGreen
            )
            
            Button(
                onClick = { onPageChange(currentPage + 1) },
                enabled = currentPage < pages.size - 1,
                modifier = Modifier.alpha(if (currentPage < pages.size - 1) 1f else 0.5f),
                colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
            ) {
                Text(text = "Siguiente", color = Color.White)
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
