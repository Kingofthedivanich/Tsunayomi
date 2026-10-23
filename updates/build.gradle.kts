plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.updates"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.data)
    implementation(projects.downloads)
    implementation(projects.source.api)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
