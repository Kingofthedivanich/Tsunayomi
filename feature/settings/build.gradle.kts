plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.settings"
}

dependencies {
    implementation(projects.translation.mangaocr)
    implementation(projects.data)
    implementation(libs.androidx.appcompat)
}
