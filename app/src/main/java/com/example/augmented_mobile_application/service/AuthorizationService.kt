package com.example.augmented_mobile_application.service

import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Field

data class AuthorizationResponse(
    val access_token: String,
    val token_type: String
)

interface AuthorizationService {

    @FormUrlEncoded
    @POST("api/v1/Authorization/")
    suspend fun authorize(
        @Field("grant_type") grantType: String = "password",
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("scope") scope: String = "",
        @Field("client_id") clientId: String = "string",  // Static client_id
        @Field("client_secret") clientSecret: String = "string"  // Static client_secret
    ): AuthorizationResponse
}
