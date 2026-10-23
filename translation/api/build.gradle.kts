plugins {
    alias(libs.plugins.tsunayomi.android.library)
}

android {
    namespace = "app.tsunayomi.translation.api"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
