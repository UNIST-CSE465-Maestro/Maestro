package com.maestro.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface MaestroServerApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<Map<String, String>>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenResponse>

    @Multipart
    @POST("api/v1/material-analyzer/")
    suspend fun uploadPdf(
        @Part file: MultipartBody.Part,
        @Part("sha256") sha256: RequestBody,
        @Part("mode") mode: RequestBody
    ): Response<AnalysisTaskResponse>

    @GET("api/v1/material-analyzer/{id}")
    suspend fun getTaskStatus(@Path("id") taskId: String): Response<AnalysisTaskResponse>

    @GET("api/v1/material-analyzer/{id}.md")
    suspend fun getResultMd(@Path("id") taskId: String): Response<ResponseBody>

    @GET("api/v1/material-analyzer/{id}.json")
    suspend fun getResultJson(@Path("id") taskId: String): Response<ResponseBody>

    @GET("api/v1/health")
    suspend fun health(): Response<Map<String, Any>>
}

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refresh: String
)

@Serializable
data class TokenResponse(
    val access: String,
    val refresh: String
)

@Serializable
data class AnalysisTaskResponse(
    val id: String,
    val sha256: String = "",
    @SerialName("original_filename")
    val originalFilename: String = "",
    val mode: String = "",
    val status: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("expires_at")
    val expiresAt: String = ""
)
