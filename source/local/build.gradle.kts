plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.source.local"
}

dependencies {
    api(projects.source.api)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(projects.core.datastore)
    implementation(projects.core.image)
}
