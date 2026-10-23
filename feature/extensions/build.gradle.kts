plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.extensions"
}

dependencies {
    implementation(projects.extension.manager)
    implementation(libs.androidx.preference)
}
