plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.translation.mlkit"
}

dependencies {
    api(projects.translation.api)
    implementation(libs.kotlinx.coroutines.play.services)
    // Bundled models: OCR works offline and without Google Play services (+~15 MB APK).
    implementation(libs.mlkit.text.recognition.latin)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
}
