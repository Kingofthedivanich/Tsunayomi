plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tsunayomi.translation.gemini"
}

dependencies {
    api(projects.translation.api)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
