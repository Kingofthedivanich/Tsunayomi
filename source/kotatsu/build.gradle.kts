plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.tsunayomi.source.kotatsu"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(projects.source.api)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.datastore)
    implementation(projects.extension.lib)

    implementation("com.github.Gekkoushi:plugin-source:b81de06968")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
}
