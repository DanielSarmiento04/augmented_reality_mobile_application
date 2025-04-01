package com.example.augmented_mobile_application.repository

import com.example.augmented_mobile_application.model.User
import com.example.augmented_mobile_application.model.UserResponse
import com.example.augmented_mobile_application.service.AuthenticationRequest
import com.example.augmented_mobile_application.service.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class AuthRepository {
    // Client credentials should ideally be in a secure storage or environment variables
    private val clientUsername = "UIS"
    private val clientPassword = "1298contra"
    
    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Authenticate the client to get the access token
            val authResponse = RetrofitInstance.authorizationService.authorize(
                username = clientUsername,
                password = clientPassword
            )
            
            // Step 2: Authorize the user using the access token
            val userResponse: UserResponse = RetrofitInstance.authenticationService.authenticate(
                token = "Bearer ${authResponse.access_token}",
                request = AuthenticationRequest(username = username, password = password)
            )
            
            // Map UserResponse to User domain model (without storing password)
            val user = User(
                username = userResponse.username,
                password = "", // Don't store password in memory
                role = userResponse.role,
                isAuthenticated = true
            )
            
            Result.success(user)
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> Result.failure(Exception("Credenciales inválidas"))
                403 -> Result.failure(Exception("Acceso denegado"))
                else -> Result.failure(Exception("Error de red: ${e.message()}"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Error de conexión: Verifique su conexión a internet"))
        } catch (e: Exception) {
            Result.failure(Exception("Error inesperado: ${e.message}"))
        }
    }
}
