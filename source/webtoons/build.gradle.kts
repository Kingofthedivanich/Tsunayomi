plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.source.webtoons"
}

dependencies {
    api(projects.source.api)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.jsoup)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
