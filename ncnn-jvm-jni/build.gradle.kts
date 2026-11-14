import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

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

repositories {
    mavenCentral()
}

kotlin {
    mingwX64()
    linuxX64()

    sourceSets {
        nativeMain.dependencies {
            implementation(projects.ncnnBinding)
        }
    }
}

kotlin.targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
    target.binaries.sharedLib {
        baseName = "ncnn_jni"
    }

    target.binaries.all {
        linkTaskProvider.configure { dependsOn(rootProject.tasks.named("downloadNcnn")) }
    }

    target.binaries.all {
        linkerOpts += when (target.name) {
            "linuxX64" -> listOf(
                "-L${ncnnLibsDir.dir("linux-x64/lib").asFile.absolutePath}",
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
