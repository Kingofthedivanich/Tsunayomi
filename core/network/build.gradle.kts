plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.core.network"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp.logging)
}
