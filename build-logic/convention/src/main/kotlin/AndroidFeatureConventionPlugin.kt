import app.tsunayomi.buildlogic.lib
import app.tsunayomi.buildlogic.libs
import app.tsunayomi.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A screen module: Android library + Compose + Hilt + type-safe navigation. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.plugin("tsunayomi-android-library"))
        pluginManager.apply(libs.plugin("tsunayomi-android-compose"))
        pluginManager.apply(libs.plugin("tsunayomi-hilt"))
        pluginManager.apply(libs.plugin("kotlin-serialization"))

        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:model"))

            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))
        }
    }
}
