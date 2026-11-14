import com.diffplug.gradle.spotless.SpotlessExtension
import ncnn.tasks.DownloadNcnnTask
import ncnn.tasks.PrebuiltAsset
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
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

tasks.register<DownloadNcnnTask>("downloadNcnn") {
    group = "ncnn"
    description = "Downloads the pinned ncnn prebuilt SDKs into libs/<platform>."
    ncnnVersion.set("20261020")
    assets.set(
        mapOf(
            "windows-x64" to PrebuiltAsset(
                assetName = "ncnn-20261020-windows-vs2022-shared.zip",
                sha256 = "7f39107af5ee4c1e657b8fd01e8b3cad6c0ab7fdbb0713532f01937134285b3d",
                subDir = "x64",
            ),
            "linux-x64" to PrebuiltAsset(
                assetName = "ncnn-20261020-ubuntu-2404-shared.zip",
                sha256 = "996e07a787550b782dd5891a2a60d7fa5d8f84452bb2f244ce0f3f9c01417842",
                subDir = "",
            ),
            "android-arm64-v8a" to PrebuiltAsset(
                assetName = "ncnn-20261020-android-shared.zip",
                sha256 = "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
                subDir = "arm64-v8a",
            ),
            "android-armeabi-v7a" to PrebuiltAsset(
                assetName = "ncnn-20261020-android-shared.zip",
                sha256 = "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
                subDir = "armeabi-v7a",
            ),
            "android-x86_64" to PrebuiltAsset(
                assetName = "ncnn-20261020-android-shared.zip",
                sha256 = "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
                subDir = "x86_64",
            ),
        ),
    )
    libsDir.set(layout.projectDirectory.dir("libs"))
    downloadDir.set(layout.buildDirectory.dir("ncnn-download"))
}
