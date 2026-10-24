plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.translation.mangaocr"
}

dependencies {
    api(projects.translation.api)
    api(projects.core.model)
    implementation(projects.core.network)
    implementation(libs.onnxruntime.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
