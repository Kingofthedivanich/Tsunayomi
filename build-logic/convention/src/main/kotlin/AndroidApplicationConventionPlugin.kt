import app.tsunayomi.buildlogic.configureKotlinCompilation
import app.tsunayomi.buildlogic.intVersion
import app.tsunayomi.buildlogic.libs
import app.tsunayomi.buildlogic.plugin
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.plugin("android-application"))

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.intVersion("compileSdk")
            defaultConfig {
                minSdk = libs.intVersion("minSdk")
                targetSdk = libs.intVersion("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            lint {
                abortOnError = true
                checkDependencies = true
                error += listOf("HardcodedText", "MissingTranslation", "ExtraTranslation")
            }
        }
        configureKotlinCompilation()
    }
}
