package com.example.augmented_mobile_application.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_mobile_application.viewmodel.ManualViewModel
import com.example.augmented_mobile_application.viewmodel.UserViewModel
import com.example.augmented_mobile_application.model.User
import com.example.augmented_mobile_application.model.AuthState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import com.example.augmented_mobile_application.ui.theme.OffWhite
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.request.ImageRequest
import coil.size.Size

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

    // Define constant for machine type and PDF path
    val MACHINE_TYPE = "Bomba Centrifuga"
    val PDF_PATH = "pump/pump"

    // Only keep the rutina selection
    var selectedRutina by remember { mutableStateOf<String?>(null) }
    val rutinas = listOf("Diaria", "Mensual", "Semestral")

    // Navigate back to login if not authenticated
    LaunchedEffect(authState) {
        if (authState !is AuthState.Authenticated) {
            navController.navigate("login") {
                popUpTo("userContent") { inclusive = true }
            }
        }
    }

    // Create ImageLoader with GIF decoder
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(GifDecoder.Factory())
            }
            .build()
    }

    Scaffold(
        topBar = {
            user?.let {
                CenterAlignedTopAppBarExample(
                    user = it,
                    onLogout = onLogout
                )
            } ?: run {
                // Fallback TopAppBar if user is null
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.White)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Display machine name before image
                Text(
                    text = MACHINE_TYPE,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // GIF loader using Coil
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/pump/pump.gif")
                        .size(Size.ORIGINAL)
                        .build(),
                    contentDescription = "Animated pump GIF",
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Seleccione la frecuencia de mantenimiento",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                EnhancedDropdownMenuField(
                    label = "Frecuencia de mantenimiento",
                    items = rutinas,
                    selectedValue = selectedRutina,
                    onValueChange = { selectedRutina = it }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Update the documentation button to use the PDF path
                Button(
                    onClick = {
                        // Navigate to pump manual using the PDF path
                        navController.navigate("pumpManual")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
                ) {
                    Text(text = "Ver Manual de la Bomba")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add a specific button for maintenance documentation
                Button(
                    onClick = {
                        navController.navigate("manualView/$MACHINE_TYPE")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
                ) {
                    Text(text = "Ver Documentación de Mantenimiento")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        navController.navigate("arView/$MACHINE_TYPE")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen),
                    enabled = selectedRutina != null
                ) {
                    Text(text = "Iniciar Mantenimiento")
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                IconButton(onClick = { isMenuOpen = true }) {
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
                            isMenuOpen = false
                            onLogout()
                        }
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { isDialogOpen = true }) {
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
