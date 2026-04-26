package com.maestro.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.local.PdfMerger
import com.maestro.app.data.local.ProfileLocalDataSource
import com.maestro.app.data.local.QuizResponseLocalDataSource
import com.maestro.app.data.local.StudyEventLocalDataSource
import com.maestro.app.data.remote.ClaudeClient
import com.maestro.app.data.remote.LlmClient
import com.maestro.app.data.remote.MaestroServerApi
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.OpenAiClient
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.data.repository.DocumentRepositoryImpl
import com.maestro.app.data.repository.KnowledgeRepositoryImpl
import com.maestro.app.data.repository.SettingsRepositoryImpl
import com.maestro.app.data.service.HeuristicKnowledgeTracer
import com.maestro.app.data.service.LlmServiceImpl
import com.maestro.app.data.service.OnnxRektKnowledgeTracer
import com.maestro.app.data.service.QuizServiceImpl
import com.maestro.app.data.work.ExtractionWorkScheduler
import com.maestro.app.data.work.WorkManagerExtractionWorkScheduler
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.KnowledgeRepository
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService
import com.maestro.app.domain.service.RektKnowledgeTracer
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
    single { LlmClient(get()) }
    single { OpenAiClient(get()) }
    single { ClaudeClient(get()) }
    single<LlmService> {
        LlmServiceImpl(
            get<LlmClient>(),
            get<OpenAiClient>(),
            get<ClaudeClient>(),
            get()
        )
    }
    single { ConversationLocalDataSource(get()) }
    single { ExtractionProgressStore() }
    single {
        QuizResponseLocalDataSource(
            get<android.content.Context>()
        )
    }
    single<ExtractionWorkScheduler> {
        WorkManagerExtractionWorkScheduler(get())
    }
    single { ProfileLocalDataSource(get()) }
    single {
        StudyEventLocalDataSource(
            get<android.content.Context>()
        )
    }
    single { PdfMerger(get()) }
    single<QuizService> { QuizServiceImpl(get()) }
    single<RektKnowledgeTracer> {
        OnnxRektKnowledgeTracer(
            context = get<android.content.Context>(),
            fallback = HeuristicKnowledgeTracer()
        )
    }
    single<KnowledgeRepository> {
        KnowledgeRepositoryImpl(
            context = get<android.content.Context>(),
            documentRepository = get(),
            studyEvents = get(),
            tracer = get()
        )
    }

    // Maestro Server Retrofit + API
    single<MaestroServerApi> {
        val settings = get<SettingsRepository>()
        val baseUrl = runBlocking {
            settings.getServerUrl().firstOrNull()
        } ?: "https://maestro.jwchae.com/"
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
