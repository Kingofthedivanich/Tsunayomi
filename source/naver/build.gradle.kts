plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tsunayomi.source.naver"
}

dependencies {
    api(projects.source.api)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
