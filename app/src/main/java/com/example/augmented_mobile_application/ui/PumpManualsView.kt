package com.example.augmented_mobile_application.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import com.example.augmented_mobile_application.ui.theme.OffWhite
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpManualsView(navController: NavHostController) {
    val haptic = LocalHapticFeedback.current

    // Function to navigate to the ManualView with a specific PDF path
    fun navigateToManual(pdfPath: String) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // Encode the pdfPath to handle slashes correctly in the route
        val encodedPdfPath = URLEncoder.encode(pdfPath, StandardCharsets.UTF_8.toString())
        navController.navigate("manualView/$encodedPdfPath")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manuales Bomba Centrífuga",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
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
        },
        containerColor = OffWhite
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            ManualButton(
                text = "Componentes",
                onClick = { navigateToManual("pump/componentes") }
            )
            ManualButton(
                text = "Manual de Mantenimiento",
                onClick = { navigateToManual("pump/manual_mantenimiento") }
            )
            ManualButton(
                text = "Manual Técnico",
                onClick = { navigateToManual("pump/manual_tecnico") }
            )
        }
    }
}

@Composable
private fun ManualButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null, // Decorative icon
            tint = Color.White,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
