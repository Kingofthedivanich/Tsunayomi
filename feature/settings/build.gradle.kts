plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.settings"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.appcompat)
}
