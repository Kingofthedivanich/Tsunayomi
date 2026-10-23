plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.history"
}

dependencies {
    implementation(projects.data)
}
