package com.maestro.app.di

import android.net.Uri
import com.maestro.app.ui.home.HomeViewModel
import com.maestro.app.ui.settings.SettingsViewModel
import com.maestro.app.ui.viewer.ViewerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { HomeViewModel(get(), get()) }
    viewModel { params ->
        ViewerViewModel(
            annotationRepo = get(),
            analyzerClient = get(),
            settingsRepository = get(),
            quizService = get(),
            appContext = get(),
            pdfId = params.get<String>(),
            pageCount = params.get<Int>(),
            pdfUri = params.get<Uri>()
        )
    }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
