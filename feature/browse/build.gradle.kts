plugins {
    alias(libs.plugins.tsunayomi.android.feature)
}

android {
    namespace = "app.tsunayomi.feature.browse"
}

dependencies {
    implementation(projects.data)
}
