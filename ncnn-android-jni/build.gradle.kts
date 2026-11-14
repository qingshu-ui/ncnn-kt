import ncnn.tasks.SyncJniLibsTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

group = "io.github.qingshu"
version = "unspecified"

val ncnnLibsDir = rootProject.layout.projectDirectory.dir("libs")
val ncnnPlatformByTarget = mapOf(
    "androidNativeArm32" to "android-armeabi-v7a",
    "androidNativeArm64" to "android-arm64-v8a",
    "androidNativeX64" to "android-x86_64",
)

kotlin {
    android {
        namespace = "io.github.qingshu.ncnn.android"
        compileSdk {
            version = release(37)
        }
    }

    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()

    sourceSets {
        androidNativeMain.dependencies {
            implementation(projects.ncnnBinding)
        }
    }
}

val jniLibsTasks = mutableListOf<TaskProvider<SyncJniLibsTask>>()

kotlin.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
    val ncnnPlatformDir = ncnnLibsDir.dir(ncnnPlatformByTarget.getValue(target.name))

    target.binaries.sharedLib {
        baseName = "ncnn_jni"
    }

    val abiName = ncnnPlatformByTarget.getValue(target.name).removePrefix("android-")
    jniLibsTasks += tasks.register<SyncJniLibsTask>("syncJniLibs${abiName.replace("-", "")}") {
        group = "build"
        description = "Bundles libncnn_jni.so and libncnn.so for $abiName into the Android AAR."
        abi.set(abiName)
        outputDir.set(layout.buildDirectory.dir("generated/jniLibs/$abiName"))
        dependsOn("linkReleaseShared${target.name.replaceFirstChar(Char::uppercaseChar)}")
        libraries.from(
            layout.buildDirectory.dir("bin/${target.name}/releaseShared").map { dir ->
                dir.asFileTree.matching { include("libncnn_jni.so") }
            },
        )
        libraries.from(ncnnPlatformDir.dir("lib").asFileTree.matching { include("libncnn.so") })
    }

    target.binaries.all {
        linkTaskProvider.configure { dependsOn(rootProject.tasks.named("downloadNcnn")) }
        linkerOpts += listOf(
            "-L${ncnnPlatformDir.dir("lib").asFile.absolutePath}",
            "-lncnn",
        )
    }
}

androidComponents {
    onVariants { variant ->
        val jniLibs = variant.sources.jniLibs ?: return@onVariants
        jniLibsTasks.forEach { task ->
            jniLibs.addGeneratedSourceDirectory(task) { it.outputDir }
        }
    }
}
