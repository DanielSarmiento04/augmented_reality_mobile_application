package com.example.augmented_mobile_application.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_mobile_application.model.User
import com.example.augmented_mobile_application.model.UserResponse
import com.example.augmented_mobile_application.service.AuthenticationRequest
import com.example.augmented_mobile_application.service.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log


class UserViewModel : ViewModel() {

    private val _user = MutableStateFlow(User(username = "", password = "", isAuthenticated = false))
    val user: StateFlow<User> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _accessToken = MutableStateFlow<String?>(null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val client_username = "UIS"
    private val client_password = "1298contra"

    fun login(username: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                // Step 1: Authenticate the client to get the access token
                val response = RetrofitInstance.authorizationService.authorize(
                    username = client_username,
                    password = client_password
                )
                _accessToken.value = response.access_token

                // Step 2: Authorize the user using the access token
                val userResponse: UserResponse = RetrofitInstance.authenticationService.authenticate(
                    token = "Bearer ${_accessToken.value}",
                    request = AuthenticationRequest(username = username, password = password)
                )

                // Update user state based on the response
                _user.value = User(
                    username = userResponse.username,
                    password = userResponse.password,
                    role = userResponse.role,
                    isAuthenticated = true
                )

                Log.d("UserViewModel", "User authenticated successfully.")

            } catch (e: Exception) {
                _errorMessage.value = "Fallo en el ingreso, por favor verifique las credenciales."
                _user.value = User(username = "", password = "", isAuthenticated = false)
                Log.e("UserViewModel", "Exception during login", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        _user.value = User(username = "", password = "", isAuthenticated = false)
        _accessToken.value = null
    }
}
