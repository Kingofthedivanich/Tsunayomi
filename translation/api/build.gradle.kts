plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tsunayomi.translation.api"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
