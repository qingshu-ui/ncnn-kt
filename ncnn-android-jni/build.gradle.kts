import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.github.qingshu"
version = "unspecified"

val ncnnLibsDir = rootProject.layout.projectDirectory.dir("libs")
val ncnnPlatformByTarget = mapOf(
    "androidNativeArm32" to "android-armeabi-v7a",
    "androidNativeArm64" to "android-arm64-v8a",
    "androidNativeX64" to "android-x86_64",
)

repositories {
    mavenCentral()
}

kotlin {
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()

    sourceSets {
        androidNativeMain.dependencies {
            implementation(projects.ncnnBinding)
        }
    }
}

kotlin.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
    val ncnnPlatformDir = ncnnLibsDir.dir(ncnnPlatformByTarget.getValue(target.name))

    target.binaries.sharedLib {
        baseName = "ncnn_jni"
    }

    target.binaries.all {
        linkTaskProvider.configure { dependsOn(rootProject.tasks.named("downloadNcnn")) }
    }

    target.binaries.all {
        linkerOpts += listOf(
            "-L${ncnnPlatformDir.dir("lib").asFile.absolutePath}",
            "-lncnn",
        )
    }
}
