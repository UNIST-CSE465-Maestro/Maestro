package com.maestro.app.di

import com.maestro.app.data.local.ConversationLocalDataSource
import com.maestro.app.data.local.PdfMerger
import com.maestro.app.data.local.PdfTextExtractor
import com.maestro.app.data.remote.AnthropicSseClient
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.data.repository.DocumentRepositoryImpl
import com.maestro.app.data.repository.SettingsRepositoryImpl
import com.maestro.app.data.service.LlmServiceImpl
import com.maestro.app.data.service.QuizServiceImpl
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.domain.service.LlmService
import com.maestro.app.domain.service.QuizService
import org.koin.dsl.module

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
    single { PdfTextExtractor(get(), get()) }
    single<QuizService> { QuizServiceImpl(get()) }
}
