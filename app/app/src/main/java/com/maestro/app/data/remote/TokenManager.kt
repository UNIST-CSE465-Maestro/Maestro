package com.maestro.app.data.remote

import com.maestro.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that attaches JWT access tokens
 * and handles transparent token refresh on 401.
 */
class TokenManager(
    private val settingsRepository: SettingsRepository,
    private val apiProvider: () -> MaestroServerApi
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = runBlocking {
            settingsRepository.getAccessToken()
                .firstOrNull()
        }

        val authed = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        val response = chain.proceed(authed)

        if (response.code != 401 || token.isNullOrBlank()) {
            return response
        }

        // Attempt token refresh
        val refreshToken = runBlocking {
            settingsRepository.getRefreshToken()
                .firstOrNull()
        } ?: run {
            runBlocking { settingsRepository.clearTokens() }
            return response
        }

        val newTokens = runBlocking {
            try {
                val resp = apiProvider().refresh(
                    RefreshRequest(refreshToken)
                )
                if (resp.isSuccessful) resp.body() else null
            } catch (_: Exception) {
                null
            }
        }

        if (newTokens == null) {
            runBlocking { settingsRepository.clearTokens() }
            return response
        }

        runBlocking {
            settingsRepository.setTokens(
                newTokens.access,
                newTokens.refresh
            )
        }

        response.close()
        val retry = original.newBuilder()
            .header(
                "Authorization",
                "Bearer ${newTokens.access}"
            )
            .build()
        return chain.proceed(retry)
    }
}
