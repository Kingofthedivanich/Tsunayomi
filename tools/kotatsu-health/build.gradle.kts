plugins {
    alias(libs.plugins.tsunayomi.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("app.tsunayomi.tools.kotatsu.health.MainKt")
}

dependencies {
    implementation("com.github.Gekkoushi:plugin-source:b81de06968")
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)
}

// Paths in the tool (allowlist, report, resume file) are relative to the repository root.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
