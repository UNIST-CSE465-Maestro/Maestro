package com.maestro.app

import android.app.Application
import com.maestro.app.di.appModule
import com.maestro.app.di.dataModule
import com.maestro.app.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MaestroApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MaestroApp)
            modules(appModule, dataModule, networkModule)
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("=== MAESTRO CRASH ===")
                pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                pw.println("Thread: ${thread.name}")
                pw.println()
                throwable.printStackTrace(pw)
                pw.flush()
                File(filesDir, "crash_log.txt").writeText(sw.toString())
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
