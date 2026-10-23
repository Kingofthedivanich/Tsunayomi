plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.core.image"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(projects.core.network)

    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.commons.compress)
}
