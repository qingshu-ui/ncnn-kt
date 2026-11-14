import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

val ncnnLibsDir = rootProject.layout.projectDirectory.dir("libs")
val ncnnPlatformByTarget = mapOf(
    "mingwX64" to "windows-x64",
    "linuxX64" to "linux-x64",
    "androidNativeArm64" to "android-arm64-v8a",
    "androidNativeArm32" to "android-armeabi-v7a",
    "androidNativeX64" to "android-x86_64",
)

kotlin {
    mingwX64()
    linuxX64()
    androidNativeArm64()
    androidNativeArm32()
    androidNativeX64()

    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
        }
        nativeMain.dependencies {
        }
    }
}

kotlin.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
    val ncnnPlatformDir = ncnnLibsDir.dir(
        ncnnPlatformByTarget[target.name]
            ?: error("No ncnn prebuilt SDK for target ${target.name}"),
    )
    target.compilations.getByName("main") {
        cinterops {
            create("ncnn") {
                defFile(project.file("src/nativeInterop/cinterop/ncnn.def"))
                includeDirs(
                    ncnnPlatformDir.dir("include"),
                    layout.projectDirectory.dir("src/nativeInterop/cinterop/ncnn"),
                )
                if (target.name == "mingwX64") {
                    extraOpts(
                        "-libraryPath",
                        ncnnPlatformDir.dir("lib").asFile.absolutePath,
                        "-staticLibrary",
                        "ncnn.lib",
                    )
                }
            }
        }
    }

    target.binaries.all {
        linkerOpts += when (target.name) {
            "linuxX64" -> listOf(
                "-L${ncnnPlatformDir.dir("lib").asFile.absolutePath}",
                "-lncnn",
                $$"-Wl,-rpath,$ORIGIN",
            )

            else -> emptyList()
        }
    }
}

tasks.matching { it.name.startsWith("cinteropNcnn") }.configureEach {
    dependsOn(rootProject.tasks.named("downloadNcnn"))
}
