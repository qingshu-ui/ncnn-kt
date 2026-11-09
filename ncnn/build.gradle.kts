import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import ncnn.build.NcnnBuilds
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

val enableAndroid = (project.findProperty("ncnn.android") as String?)?.toBoolean()
    ?: System.getenv("NCNN_ANDROID")?.toBoolean()
    ?: true

if (enableAndroid) {
    apply(plugin = "com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    if (enableAndroid) {
        extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("android") {
            namespace = "io.github.qingshu.ncnn"
            compileSdk {
                version = release(37)
            }
            minSdk = 24
        }
    }

    // Declared on every host so IDEs index all platform source sets. Non-host targets have
    // their Kotlin/Native tasks disabled below.
    mingwX64()
    linuxX64()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmSharedMain)
        findByName("androidMain")?.let { androidMain ->
            androidMain.dependsOn(jvmSharedMain)
            if (findProject(":ncnn-android-native") != null) {
                androidMain.dependencies {
                    runtimeOnly(project(":ncnn-android-native"))
                }
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val nativeMain = kotlin.sourceSets.getByName("nativeMain")
val unixNativeMain = kotlin.sourceSets.create("unixNativeMain") {
    dependsOn(nativeMain)
}
listOf("linuxMain", "appleMain").forEach { name ->
    kotlin.sourceSets.getByName(name).dependsOn(unixNativeMain)
}

val ncnn = NcnnBuilds(
    project = project,
    ncnnVersion = libs.versions.ncnnVersion.get(),
    enableAndroid = enableAndroid,
)

for (target in kotlin.targets.filterIsInstance<KotlinNativeTarget>()) {
    val platformId = ncnn.platformIdByTarget.getValue(target.name)
    val installDir = ncnn.installDirs.getValue(platformId)
    val isSupported = target.name in ncnn.supportedTargets
    val isWindowsTarget = platformId.startsWith("windows")
    val isLinuxTarget = platformId.startsWith("linux")
    val includeDir = if (isSupported) {
        installDir.get().dir("include/ncnn")
    } else {
        ncnn.portableHeadersDir.get()
    }

    target.compilations.getByName("main").cinterops.create("ncnn") {
        packageName("ncnn")
        headers(includeDir.dir("c_api.h").asFile.absolutePath.replace('\\', '/'))
        compilerOpts("-I${includeDir.asFile.absolutePath}")
        val staticLibraries = ncnn.cinteropStaticLibraries[platformId].orEmpty()
        if (isSupported && staticLibraries.isNotEmpty()) {
            val options = buildList {
                add("-libraryPath")
                add(installDir.get().dir("lib").asFile.absolutePath)
                for (library in staticLibraries) {
                    add("-staticLibrary")
                    add(library)
                }
            }
            extraOpts(*options.toTypedArray())
        }
    }

    target.binaries.configureEach {
        val linkerOptions = when {
            !isSupported -> emptyList()

            // Windows links the import library through cinterop; its runtime lives in the DLL.
            isWindowsTarget -> emptyList()

            // Linux links the shared object directly and finds it next to the test binary.
            isLinuxTarget -> listOf(
                "-L${installDir.get().dir("lib").asFile.absolutePath}",
                "-lncnn",
                "-Wl,-rpath,\$ORIGIN",
            )

            else -> ncnn.toolchain.cinteropLinkerOpts
        }
        linkerOpts(*linkerOptions.toTypedArray())
    }

    val interopTaskName = "cinteropNcnn" + target.name.replaceFirstChar(Char::uppercaseChar)
    tasks.matching { it.name == interopTaskName }.configureEach {
        if (isSupported) {
            dependsOn(ncnn.prepareTasks.getValue(platformId))
        } else {
            dependsOn(ncnn.portableHeadersTask)
        }
    }
}

ncnn.disableUnsupportedTargets(
    kotlin.targets.filterIsInstance<KotlinNativeTarget>().map { it.name },
)

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}
