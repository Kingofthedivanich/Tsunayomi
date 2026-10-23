plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.tsunayomi.hilt)
}

android {
    namespace = "app.tsunayomi.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
