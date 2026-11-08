import com.diffplug.gradle.spotless.BaseKotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension
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
        kotlin {
            target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
            ktlint().currentProjectStyle {
                val customRules = listOf(
                    "io.nlopez.compose.rules:ktlint:0.5.6",
                )
                val composeProject = buildList<String> {
                    // Put compose project name here.
                }
                if (this@allprojects.name in composeProject) {
                    logger.info("project: ${this@allprojects.name}, add compose rule.")
                    customRuleSets(customRules)
                }
            }
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint().currentProjectStyle()
        }
    }
}

fun BaseKotlinExtension.KtlintConfig.currentProjectStyle(block: BaseKotlinExtension.KtlintConfig.() -> Unit = {}) {
    val overrideEditConfig = mapOf(
        "ktlint_standard_package-name" to "disabled",
        "ktlint_standard_function-naming" to "disabled",
        "ktlint_standard_no-unused-imports" to "enabled",
        "ktlint_compose_modifier-missing-check" to "disabled",
        "ktlint_compose_compositionlocal-allowlist" to "disabled",
    )

    editorConfigOverride(overrideEditConfig)
    block()
}
