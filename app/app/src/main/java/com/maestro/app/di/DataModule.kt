package com.maestro.app.di

import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.data.repository.DocumentRepositoryImpl
import com.maestro.app.data.repository.SettingsRepositoryImpl
import com.maestro.app.domain.repository.DocumentRepository
import com.maestro.app.domain.repository.SettingsRepository
import org.koin.dsl.module

val dataModule = module {
    single<DocumentRepository> { DocumentRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single { AnnotationRepositoryImpl(get()) }
}
