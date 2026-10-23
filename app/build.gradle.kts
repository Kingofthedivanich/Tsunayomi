import java.util.Properties

plugins {
    alias(libs.plugins.tsunayomi.android.application)
    alias(libs.plugins.tsunayomi.android.compose)
    alias(libs.plugins.tsunayomi.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing comes from keystore.properties (never committed) or, on CI, from environment variables.
 * Without either, the release build is simply left unsigned.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(property: String, environment: String): String? =
    keystoreProperties.getProperty(property) ?: providers.environmentVariable(environment).orNull

android {
    namespace = "app.tsunayomi"

    defaultConfig {
        applicationId = "app.tsunayomi"
        // CI passes these from the release tag (v1.2.3 → versionName 1.2.3); local builds use the defaults.
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile", "TSUNAYOMI_KEYSTORE_FILE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingValue("storePassword", "TSUNAYOMI_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TSUNAYOMI_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "TSUNAYOMI_KEY_PASSWORD")
            }
        }
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

    buildFeatures {
        buildConfig = true
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
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
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
    implementation(projects.updates)
    implementation(projects.feature.updates)
    implementation(projects.extension.manager)
    implementation(projects.feature.extensions)
    implementation(projects.source.local)
    implementation(projects.source.mangadex)
    implementation(projects.source.komga)
    implementation(projects.source.webtoons)
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
