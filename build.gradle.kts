import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("ncnn.sdk")
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
}

val spotlessPlugin = libs.plugins.spotless.get().pluginId
allprojects {
    apply(plugin = spotlessPlugin)

    configure<SpotlessExtension> {
        val overrideEditConfig = mapOf(
            "ktlint_standard_package-name" to "disabled",
            "ktlint_standard_function-naming" to "disabled",
            "ktlint_standard_no-unused-imports" to "enabled",
            "ktlint_compose_modifier-missing-check" to "disabled",
            "ktlint_compose_compositionlocal-allowlist" to "disabled",
        )
        kotlin {
            target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
            ktlint().editorConfigOverride(overrideEditConfig)
            val composeProject = buildList<String> {
                // Put compose project name here.
            }
            if (this@allprojects.name in composeProject) {
                logger.info("project: ${this@allprojects.name}, add compose rule.")
                ktlint().customRuleSets(listOf("io.nlopez.compose.rules:ktlint:0.5.6"))
            }
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint().editorConfigOverride(overrideEditConfig)
        }
    }
}

val publishedModules = setOf("ncnn-core", "ncnn-binding", "ncnn-jvm-jni", "ncnn-android-jni")
val publishGroup: String = providers.gradleProperty("GROUP_ID").get()
val publishVersion: String = providers.gradleProperty("VERSION").get()
val pomUrl: String = providers.gradleProperty("githubURL").get()
val pomLicenseName: String = providers.gradleProperty("licenseName").get()
val pomLicenseUrl: String = providers.gradleProperty("licenseURL").get()
val pomDeveloperId: String = providers.gradleProperty("developerID").get()
val pomDeveloperName: String = providers.gradleProperty("developerName").get()
val pomDeveloperEmail: String = providers.gradleProperty("developerEmail").get()
val pomScm: String = providers.gradleProperty("scmURL").get()

allprojects {
    if (name in publishedModules) {
        val moduleName = name
        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<MavenPublishBaseExtension> {
            coordinates(publishGroup, moduleName, publishVersion)
            pom {
                name.set(moduleName)
                description.set("Kotlin Multiplatform bindings for ncnn")
                url.set(pomUrl)
                licenses {
                    license {
                        name.set(pomLicenseName)
                        url.set(pomLicenseUrl)
                    }
                }
                developers {
                    developer {
                        id.set(pomDeveloperId)
                        name.set(pomDeveloperName)
                        email.set(pomDeveloperEmail)
                    }
                }
                scm {
                    connection.set(pomScm)
                    developerConnection.set(pomScm)
                    url.set(pomUrl)
                }
            }
            publishToMavenCentral()
            signAllPublications()
        }
    }
}
