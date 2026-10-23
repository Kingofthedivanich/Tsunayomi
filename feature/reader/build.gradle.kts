plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.reader"
}

dependencies {
    implementation(projects.data)
    implementation(projects.downloads)
    implementation(projects.translation.api)
    implementation(projects.translation.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)
}
