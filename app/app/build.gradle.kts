import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

fun secretProperty(vararg names: String): String {
    return names.firstNotNullOfOrNull { name ->
        providers.environmentVariable(name).orNull
            ?: localProperties.getProperty(name)
    }.orEmpty()
}

fun buildConfigString(value: String): String {
    val escaped =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.maestro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maestro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"
        buildConfigField(
            "String",
            "MAESTRO_SERVER_BEARER_TOKEN",
            buildConfigString(
                secretProperty(
                    "MAESTRO_SERVER_BEARER_TOKEN",
                    "MINERU_SERVER_BEARER_TOKEN",
                    "MINERU_BEARER_TOKEN",
                    "MAESTRO_BEARER_TOKEN"
                )
            )
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Image
    implementation(libs.coil.compose)

    // Stylus
    implementation(libs.androidx.input.motionprediction)

    // Local ML inference
    implementation(libs.onnxruntime.android)

    // PDF text layer indexing
    implementation(libs.pdfbox.android)

    // Local OCR experiment
    implementation(libs.mlkit.text.recognition)

    // Markdown + LaTeX rendering for LLM chat
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.latex)
    implementation(libs.markwon.inline.parser)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.linkify)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp)
}
