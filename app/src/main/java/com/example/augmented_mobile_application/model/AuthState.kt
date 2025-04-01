package com.example.augmented_mobile_application.model

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    sealed class Error : AuthState() {
        data class NetworkError(val message: String) : Error()
        data class AuthenticationError(val message: String) : Error()
        data class UnknownError(val message: String) : Error()
    }
}

sealed class LoginEvent {
    data class OnLoginAttempt(val username: String, val password: String) : LoginEvent()
    object OnLogout : LoginEvent()
    object ClearError : LoginEvent()
}
