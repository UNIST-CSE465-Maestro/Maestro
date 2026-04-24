package com.maestro.app.di

import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.data.remote.TokenManager
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module

val networkModule = module {
    // LLM API OkHttpClient
    single(named("llmApi")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor
                        .Level.HEADERS
                }
            )
            .build()
    }

    // Default
    single {
        get<OkHttpClient>(named("llmApi"))
    }

    // TokenManager
    single {
        TokenManager(
            settingsRepository = get(),
            apiProvider = { get<MaestroServerApi>() }
        )
    }

    // Maestro server OkHttpClient
    single(named("maestroServer")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(get<TokenManager>())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor
                        .Level.HEADERS
                }
            )
            .build()
    }
}
