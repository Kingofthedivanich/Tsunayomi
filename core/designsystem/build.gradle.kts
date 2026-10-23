plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.android.compose)
}

android {
    namespace = "app.tsunayomi.core.designsystem"
}

dependencies {
    api(projects.core.model)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.coil.compose)
    implementation(libs.androidx.activity.compose)
}
