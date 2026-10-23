plugins {
    alias(libs.plugins.tsunayomi.android.application)
    alias(libs.plugins.tsunayomi.android.compose)
    alias(libs.plugins.tsunayomi.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tsunayomi"

    defaultConfig {
        applicationId = "app.tsunayomi"
        versionCode = 1
        versionName = "0.1.0"
    }

    splits {
        abi {
            // ML Kit ships native code per ABI; one APK per ABI keeps each download small.
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    androidResources {
        // Only ship the languages we translate; drops unused locales pulled in by libraries.
        localeFilters += listOf("en", "ru")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.designsystem)
    implementation(projects.core.image)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.data)
    implementation(projects.downloads)
    implementation(projects.source.local)
    implementation(projects.source.mangadex)
    implementation(projects.source.komga)
    implementation(projects.translation.core)
    implementation(projects.translation.mlkit)
    implementation(projects.translation.gemini)

    implementation(projects.feature.library)
    implementation(projects.feature.browse)
    implementation(projects.feature.details)
    implementation(projects.feature.reader)
    implementation(projects.feature.history)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
}
