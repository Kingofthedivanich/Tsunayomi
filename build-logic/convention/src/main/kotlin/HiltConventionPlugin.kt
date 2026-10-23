import app.tsunayomi.buildlogic.lib
import app.tsunayomi.buildlogic.libs
import app.tsunayomi.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Applied after an Android application/library plugin. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.plugin("ksp"))
        pluginManager.apply(libs.plugin("hilt"))

        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
    }
}
