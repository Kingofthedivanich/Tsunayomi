plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tsunayomi.translation.core"
}

dependencies {
    api(projects.translation.api)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
