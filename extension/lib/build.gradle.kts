plugins {
    alias(libs.plugins.tsunayomi.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Host side of the Tachiyomi/Mihon extension API ("extensions-lib"). Extensions are compiled against
// stubs of these classes and link to the real ones at runtime, so packages, names and signatures
// must stay exactly as upstream. Most files are taken from Mihon (Apache-2.0, see LICENSE-mihon).
android {
    namespace = "app.tsunayomi.extension.lib"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.okhttp.dnsoverhttps)
    api(libs.okhttp.brotli)
    api(libs.okhttp.zstd)
    api(libs.rxjava)
    api(libs.injekt)
    api(libs.jsoup)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.json.okio)
    api(libs.kotlinx.serialization.protobuf)
    api(libs.androidx.preference)
    api(libs.quickjs)
    api(libs.logcat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.kotlinx.coroutines.android)
}
