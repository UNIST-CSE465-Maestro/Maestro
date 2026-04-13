package com.maestro.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.data.local.PdfMerger
import com.maestro.app.data.remote.AnthropicSseClient
import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.data.repository.DocumentRepositoryImpl
import com.maestro.app.data.repository.SettingsRepositoryImpl
import com.maestro.app.data.service.LlmServiceImpl
import com.maestro.app.data.service.QuizServiceImpl
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

private val json = Json { ignoreUnknownKeys = true }

val dataModule = module {
    single<DocumentRepository> {
        DocumentRepositoryImpl(get())
    }
    single<SettingsRepository> {
        SettingsRepositoryImpl(get())
    }
    single { AnnotationRepositoryImpl(get()) }
    single { AnthropicSseClient(get()) }
    single<LlmService> {
        LlmServiceImpl(get(), get())
    }
    single { ConversationLocalDataSource(get()) }
    single { PdfMerger(get()) }
    single<QuizService> { QuizServiceImpl(get()) }

    // Maestro Server Retrofit + API
    single<MaestroServerApi> {
        val settings = get<SettingsRepository>()
        val baseUrl = runBlocking {
            settings.getServerUrl().firstOrNull()
        } ?: "http://localhost:8000/"
        val normalizedUrl = if (
            baseUrl.endsWith("/")
        ) {
            baseUrl
        } else {
            "$baseUrl/"
        }
        Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(
                get<OkHttpClient>(
                    named("maestroServer")
                )
            )
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
            .create(MaestroServerApi::class.java)
    }

    single {
        MaterialAnalyzerClient(
            api = get(),
            context = get()
        )
    }
}
