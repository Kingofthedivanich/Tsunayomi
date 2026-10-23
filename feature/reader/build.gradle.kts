plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.reader"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)
}
