plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.details"
}

dependencies {
    implementation(projects.data)
    implementation(projects.extension.lib)
    implementation(projects.translation.core)
    implementation(projects.downloads)
    implementation(libs.androidx.activity.compose)
}
