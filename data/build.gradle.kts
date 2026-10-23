plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.data"
}

dependencies {
    api(projects.core.model)
    api(projects.source.api)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
}
