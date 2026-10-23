plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.datastore)
}
