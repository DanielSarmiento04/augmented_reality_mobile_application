package com.example.augmented_mobile_application.service

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.Response
import com.example.augmented_mobile_application.model.UserResponse

data class AuthenticationRequest(
    val username: String,
    val password: String
)


interface AuthenticationService {

    @POST("user/verify")
    suspend fun authenticate(
        @Header("Authorization") token: String,
        @Body request: AuthenticationRequest
    ): UserResponse
}
