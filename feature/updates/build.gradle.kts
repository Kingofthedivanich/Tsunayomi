plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.updates"
}

dependencies {
    implementation(projects.data)
    implementation(projects.downloads)
    implementation(projects.updates)
}
