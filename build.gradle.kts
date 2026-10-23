// Plugins are resolved here once so every module shares the same classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.android.lint) apply false
}

// The project lives in an iCloud-synced folder on the main dev machine. iCloud duplicates churned build files
// ("values-ru 3.xml") and breaks resource merging, but it skips anything named *.nosync.
allprojects {
    layout.buildDirectory = layout.projectDirectory.dir("build.nosync")
}
