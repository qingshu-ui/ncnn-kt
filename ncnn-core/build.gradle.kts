import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.android.kmp.library)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    android {
        namespace = "io.github.qingshu.ncnn_core"
        compileSdk {
            version = release(37)
        }
    }
    mingwX64()
    linuxX64()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }

    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(projects.ncnnJvmJni)
        }
        androidMain.dependencies {
            implementation(projects.ncnnAndroidJni)
        }
        nativeMain.dependencies {
            implementation(projects.ncnnBinding)
        }
    }
}

tasks.register<Jar>("fatJar") {
    group = "build"
    archiveClassifier.set("all")

    from(kotlin.targets["jvm"].compilations["main"].output)
    dependsOn(configurations["jvmRuntimeClasspath"])

    from({
        configurations["jvmRuntimeClasspath"]
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to "io.github.qingshu.kmpt.MainKt",
        )
    }
}

tasks.withType<KotlinNativeLink>().configureEach {
    val taskName = name.lowercase()
    enabled = when {
        taskName.contains("linux") -> HostManager.hostIsLinux
        taskName.contains("mingw") -> HostManager.hostIsMingw
        taskName.contains("macos") -> HostManager.hostIsMac
        else -> true
    }
}

val ncnnLibsDir = rootProject.layout.projectDirectory.dir("libs")

kotlin.targets.filterIsInstance<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().forEach { target ->
    target.binaries.all {
        linkTaskProvider.configure { dependsOn(rootProject.tasks.named("downloadNcnn")) }
        linkerOpts += when (target.name) {
            "linuxX64" -> listOf(
                "-L${ncnnLibsDir.dir("linux-x64/lib").asFile.absolutePath}",
                "-lncnn",
                $$"-Wl,-rpath,$ORIGIN",
            )

            else -> emptyList()
        }
    }
}
// Native tests load the shared ncnn runtime from next to the test executable.
val copyMingwTestNatives = tasks.register<Copy>("copyMingwTestNatives") {
    dependsOn(rootProject.tasks.named("downloadNcnn"))
    from(ncnnLibsDir.dir("windows-x64/bin")) { include("ncnn.dll") }
    into(layout.buildDirectory.dir("bin/mingwX64/debugTest"))
}
tasks.matching { it.name == "mingwX64Test" }.configureEach { dependsOn(copyMingwTestNatives) }

val copyLinuxTestNatives = tasks.register<Copy>("copyLinuxTestNatives") {
    dependsOn(rootProject.tasks.named("downloadNcnn"))
    from(ncnnLibsDir.dir("linux-x64/lib")) { include("libncnn.so.1") }
    into(layout.buildDirectory.dir("bin/linuxX64/debugTest"))
}
tasks.matching { it.name == "linuxX64Test" }.configureEach { dependsOn(copyLinuxTestNatives) }
