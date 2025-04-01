// LoginView.kt
package com.example.augmented_mobile_application.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_mobile_application.model.AuthState
import com.example.augmented_mobile_application.model.LoginEvent
import com.example.augmented_mobile_application.viewmodel.UserViewModel
import com.example.augmented_mobile_application.ui.theme.DarkGreen
import com.example.augmented_mobile_application.ui.theme.OffWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginView(
    navController: NavHostController,
    userViewModel: UserViewModel = viewModel()
) {
    val authState by userViewModel.authState.collectAsState()
    val scope = rememberCoroutineScope()
    
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val passwordFocusRequester = remember { FocusRequester() }
    
    val isLoading = authState is AuthState.Loading
    val errorMessage = when(authState) {
        is AuthState.Error.AuthenticationError -> (authState as AuthState.Error.AuthenticationError).message
        is AuthState.Error.NetworkError -> (authState as AuthState.Error.NetworkError).message
        is AuthState.Error.UnknownError -> (authState as AuthState.Error.UnknownError).message
        else -> null
    }
    
    // Navigate if the user is authenticated
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            navController.navigate("userContent") {
                popUpTo("login") { inclusive = true }
            }
        }
    }
    
    // Animation for the shake effect on error
    val shakeAnimation = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            repeat(3) {
                shakeAnimation.animateTo(
                    targetValue = 10f,
                    animationSpec = tween(100, easing = LinearEasing)
                )
                shakeAnimation.animateTo(
                    targetValue = -10f,
                    animationSpec = tween(100, easing = LinearEasing)
                )
            }
            shakeAnimation.animateTo(0f)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
                .offset(x = shakeAnimation.value.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Inicio de Sesión",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                    color = DarkGreen
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        if (hasAttemptedSubmit && errorMessage != null) {
                            userViewModel.handleEvent(LoginEvent.ClearError)
                        }
                    },
                    label = { Text("Usuario") },
                    isError = hasAttemptedSubmit && username.isBlank(),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() }
                    ),
                    singleLine = true,
                    enabled = !isLoading
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        if (hasAttemptedSubmit && errorMessage != null) {
                            userViewModel.handleEvent(LoginEvent.ClearError)
                        }
                    },
                    label = { Text("Contraseña") },
                    isError = hasAttemptedSubmit && password.isBlank(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Contraseña") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester),
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (username.isNotBlank() && password.isNotBlank()) {
                                hasAttemptedSubmit = true
                                userViewModel.handleEvent(LoginEvent.OnLoginAttempt(username, password))
                            } else {
                                hasAttemptedSubmit = true
                            }
                        }
                    ),
                    singleLine = true,
                    enabled = !isLoading
                )
                
                // Field validation errors
                AnimatedVisibility(
                    visible = hasAttemptedSubmit && (username.isBlank() || password.isBlank()),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = "Por favor complete todos los campos",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
                
                // API error messages
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                Button(
                    onClick = { 
                        hasAttemptedSubmit = true
                        keyboardController?.hide()
                        if (username.isNotBlank() && password.isNotBlank()) {
                            userViewModel.handleEvent(LoginEvent.OnLoginAttempt(username, password))
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Ingresar")
                    }
                }
                
                // Retry button only appears when there is an error
                AnimatedVisibility(
                    visible = errorMessage != null && !isLoading,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    TextButton(
                        onClick = { 
                            userViewModel.handleEvent(LoginEvent.ClearError)
                            scope.launch {
                                delay(100) // Short delay for better UX
                                userViewModel.handleEvent(LoginEvent.OnLoginAttempt(username, password))
                            }
                        }
                    ) {
                        Text("Reintentar", color = DarkGreen)
                    }
                }
            }
        }
    }
}
