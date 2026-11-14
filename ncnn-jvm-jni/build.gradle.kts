import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.github.qingshu"
version = "unspecified"

val jdkHome: File = extensions.getByType(JavaToolchainService::class.java)
    .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    .get().metadata.installationPath.asFile
val jniIncludeDir: File = layout.projectDirectory.dir("src/nativeInterop/cinterop/jni").asFile
val ncnnLibsDir = rootProject.layout.projectDirectory.dir("libs")

kotlin {
    jvmToolchain(21)
    jvm()

    mingwX64()
    linuxX64()

    sourceSets {
        nativeMain.dependencies {
            implementation(projects.ncnnBinding)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

kotlin.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
    val ncnnPlatformDir = when (target.name) {
        "mingwX64" -> ncnnLibsDir.dir("windows-x64")
        "linuxX64" -> ncnnLibsDir.dir("linux-x64")
        else -> error("No ncnn prebuilt SDK for target ${target.name}")
    }

    target.binaries.sharedLib {
        baseName = "ncnn_jni"
    }

    target.binaries.all {
        linkTaskProvider.configure { dependsOn(rootProject.tasks.named("downloadNcnn")) }
        linkerOpts += when (target.name) {
            "linuxX64" -> listOf(
                "-L${ncnnPlatformDir.dir("lib").asFile.absolutePath}",
                "-lncnn",
                $$"-Wl,-rpath,$ORIGIN",
            )

            else -> emptyList()
        }
    }

    target.compilations.getByName("main") {
        cinterops.create("jni") {
            defFile(project.file("src/nativeInterop/cinterop/jni.def"))
            includeDirs(jniIncludeDir, jdkHome.resolve("include"))
        }
    }
}

// Bundle the host JNI bridge and its ncnn runtime into the JVM jar under /natives/<platform>/.
val hostPlatformId = when {
    HostManager.hostIsMingw -> "windows-x64"
    HostManager.hostIsLinux -> "linux-x64"
    else -> null
}
if (hostPlatformId != null) {
    val hostTarget = if (hostPlatformId == "windows-x64") "mingwX64" else "linuxX64"
    val hostJniLibrary = if (hostPlatformId == "windows-x64") "ncnn_jni.dll" else "libncnn_jni.so"

    val jvmNatives = tasks.register<Sync>("jvmNatives") {
        group = "build"
        description = "Bundles the host ncnn JNI bridge and ncnn runtime for the JVM jar."
        dependsOn("linkReleaseShared${hostTarget.replaceFirstChar(Char::uppercaseChar)}")
        into(layout.buildDirectory.dir("generated/ncnnJvmResources"))

        from(layout.buildDirectory.dir("bin/$hostTarget/releaseShared")) {
            include(hostJniLibrary)
            into("natives/$hostPlatformId")
        }
        if (hostPlatformId == "windows-x64") {
            from(ncnnLibsDir.dir("windows-x64/bin")) {
                include("ncnn.dll")
                into("natives/$hostPlatformId")
            }
        } else {
            from(ncnnLibsDir.dir("linux-x64/lib")) {
                include("libncnn.so*")
                into("natives/$hostPlatformId")
            }
        }
    }

    kotlin.sourceSets.named("jvmMain") {
        resources.srcDir(jvmNatives)
    }
}
