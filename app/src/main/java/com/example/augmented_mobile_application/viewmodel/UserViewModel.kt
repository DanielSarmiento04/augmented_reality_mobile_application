package com.example.augmented_mobile_application.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_mobile_application.model.AuthState
import com.example.augmented_mobile_application.model.LoginEvent
import com.example.augmented_mobile_application.model.User
import com.example.augmented_mobile_application.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import java.util.regex.Pattern

class UserViewModel : ViewModel() {

    private val repository = AuthRepository()
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Cached user data
    private var authenticatedUser: User? = null
    
    // Password validation pattern
    private val passwordPattern = Pattern.compile(
        "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$"
    )
    
    fun handleEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.OnLoginAttempt -> login(event.username, event.password)
            is LoginEvent.OnLogout -> logout()
            is LoginEvent.ClearError -> _authState.value = AuthState.Idle
        }
    }
    
    /**
     * Validates input and initiates login process
     */
    private fun login(username: String, password: String) {
        // Input validation
        if (username.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error.AuthenticationError("Usuario y contraseña son requeridos")
            return
        }
        
        // Set loading state
        _authState.value = AuthState.Loading
        
        viewModelScope.launch {
            repository.login(username, password).fold(
                onSuccess = { user ->
                    authenticatedUser = user
                    _authState.value = AuthState.Authenticated(user)
                    Log.d("UserViewModel", "User authenticated: ${user.username}, Role: ${user.role}")
                },
                onFailure = { exception ->
                    when (exception.message) {
                        "Credenciales inválidas" -> _authState.value = AuthState.Error.AuthenticationError(
                            "Fallo en el ingreso, por favor verifique las credenciales."
                        )
                        "Acceso denegado" -> _authState.value = AuthState.Error.AuthenticationError(
                            "No tiene permisos para acceder a esta aplicación."
                        )
                        else -> {
                            if (exception.message?.contains("conexión") == true) {
                                _authState.value = AuthState.Error.NetworkError(
                                    "Error de conexión. Verifique su conexión a internet e intente nuevamente."
                                )
                            } else {
                                _authState.value = AuthState.Error.UnknownError(
                                    exception.message ?: "Error desconocido"
                                )
                            }
                        }
                    }
                    Log.e("UserViewModel", "Login error", exception)
                }
            )
        }
    }
    
    /**
     * Logs out the current user
     */
    private fun logout() {
        authenticatedUser = null
        _authState.value = AuthState.Idle
    }
    
    /**
     * Validates if a password meets security requirements
     */
    fun isValidPassword(password: String): Boolean {
        return passwordPattern.matcher(password).matches()
    }
    
    /**
     * Returns the current authenticated user or null
     */
    fun getAuthenticatedUser(): User? = authenticatedUser
}
