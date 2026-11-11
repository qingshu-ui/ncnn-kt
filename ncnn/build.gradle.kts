import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import ncnn.build.NcnnBuilds
import ncnn.build.readAndroidAbis
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import java.io.File

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

val androidAbis: List<String> = readAndroidAbis(project)

val androidJniTargetNames: Set<String> = if (enableAndroid) {
    androidAbis.map { abi ->
        when (abi) {
            "arm64-v8a" -> "androidNativeArm64"
            "armeabi-v7a" -> "androidNativeArm32"
            "x86_64" -> "androidNativeX64"
            "x86" -> "androidNativeX86"
            else -> error("Unsupported Android ABI '$abi'")
        }
    }.toSet()
} else {
    emptySet()
}

val hostOs: String = System.getProperty("os.name").lowercase()
val hostArch: String = System.getProperty("os.arch").lowercase()
val hostDesktopJniTargets: Set<String> = when {
    "win" in hostOs -> setOf("mingwX64")
    "mac" in hostOs -> setOf("macosArm64", "macosX64")
    else -> setOf("linuxX64")
}

// Only targets that can process the jni cinterop on this host are included. Android uses
// the platform.android JNI bindings instead of the jni cinterop.
val jniTargetNames: Set<String> = hostDesktopJniTargets

val jdkHome: File = extensions.getByType(JavaToolchainService::class.java)
    .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    .get().metadata.installationPath.asFile
val jniIncludeDir: File = layout.projectDirectory.dir("src/nativeInterop/cinterop/jni").asFile

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
    if ("mac" in hostOs && hostArch !in setOf("aarch64", "arm64")) {
        macosX64()
    }
    iosArm64()
    iosSimulatorArm64()

    if (enableAndroid) {
        for (targetName in androidJniTargetNames) {
            when (targetName) {
                "androidNativeArm32" -> androidNativeArm32()
                "androidNativeArm64" -> androidNativeArm64()
                "androidNativeX86" -> androidNativeX86()
                "androidNativeX64" -> androidNativeX64()
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM and Android share the JNI declarations, NativeLoader and Mat/Net wrappers;
        // both targets run on a JVM, but the code uses java.* APIs so it cannot live in
        // commonMain.
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

// The JNI export sources are shared by directory rather than by source set: sharing a
// source set across the desktop and Android Native target groups prevents cinterop
// declarations from being visible to the Android compilation. The small JniTypes.kt
// aliases map the same names to `jni` on desktop and `platform.android` on Android.
val jniSourceDir = layout.projectDirectory.dir("src/jniMain/kotlin")
val jniDesktopTypesDir = layout.projectDirectory.dir("src/jniDesktop/kotlin")
val jniAndroidTypesDir = layout.projectDirectory.dir("src/jniAndroid/kotlin")
val hostJniSourceSet = when {
    "win" in hostOs -> "mingwMain"
    "mac" in hostOs -> "macosMain"
    else -> "linuxMain"
}
kotlin.sourceSets.findByName(hostJniSourceSet)?.kotlin?.apply {
    srcDir(jniSourceDir)
    srcDir(jniDesktopTypesDir)
}
androidJniTargetNames.forEach { targetName ->
    kotlin.sourceSets.findByName("${targetName}Main")?.kotlin?.apply {
        srcDir(jniSourceDir)
        srcDir(jniAndroidTypesDir)
    }
}

val nativeMain = kotlin.sourceSets.getByName("nativeMain")
val unixNativeMain = kotlin.sourceSets.create("unixNativeMain") {
    dependsOn(nativeMain)
}
listOf("linuxMain", "appleMain").forEach { name ->
    kotlin.sourceSets.getByName(name).dependsOn(unixNativeMain)
}
kotlin.sourceSets.findByName("androidNativeMain")?.dependsOn(unixNativeMain)

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
    val isAndroidTarget = platformId.startsWith("android")
    // The Android release headers reference FILE without including <stdio.h>; use the
    // normalized portable headers for cinterop and keep the ABI install only for linking.
    val includeDir = if (isSupported && !isAndroidTarget) {
        installDir.get().dir("include/ncnn")
    } else {
        ncnn.portableHeadersDir.get()
    }

    if (target.name in jniTargetNames) {
        target.compilations.getByName("main").cinterops.create("jni") {
            packageName("jni")
            includeDirs(jniIncludeDir, jdkHome.resolve("include"))
        }
    }
    if (target.name in jniTargetNames || target.name in androidJniTargetNames) {
        target.binaries.sharedLib {
            baseName = "ncnn_jni"
        }
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

            isAndroidTarget -> listOf(
                "-L${installDir.get().dir("lib").asFile.absolutePath}",
                "-lncnn",
            )

            else -> ncnn.macosLinkerOpts
        }
        linkerOpts(*linkerOptions.toTypedArray())
    }

    val interopTaskName = "cinteropNcnn" + target.name.replaceFirstChar(Char::uppercaseChar)
    tasks.matching { it.name == interopTaskName }.configureEach {
        if (isSupported && !isAndroidTarget) {
            dependsOn(ncnn.prepareTasks.getValue(platformId))
        } else {
            dependsOn(ncnn.portableHeadersTask)
        }
        if (isAndroidTarget) {
            dependsOn(ncnn.prepareTasks.getValue(platformId))
        }
    }

    // KGP cross-compiles cinterop as an unpacked klib (so the compiler can consume it),
    // while the IDE model points at the packed klib under build/libs. Emit both.
    if (!isSupported && target.name in ncnn.crossCompilableTargets) {
        val interop = tasks.named<CInteropProcess>(interopTaskName)
        val capitalized = target.name.replaceFirstChar(Char::uppercaseChar)
        val packTask = tasks.register<Zip>("packCinteropKlib$capitalized") {
            dependsOn(interop)
            from(interop.flatMap { it.klibDirectory })
            archiveFileName.set("${project.name}-${target.name}Cinterop-ncnnMain-${project.version}.klib")
            destinationDirectory.set(layout.buildDirectory.dir("libs"))
        }
        interop.configure { finalizedBy(packTask) }
        tasks.matching { it.name == "compileKotlin$capitalized" }.configureEach {
            dependsOn(packTask)
        }
    }
}

val hostNativeTarget = kotlin.targets.getByName(ncnn.jvmTargetName) as KotlinNativeTarget
val hostJniBinary = hostNativeTarget.binaries.getSharedLib(NativeBuildType.RELEASE)
tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(hostJniBinary.linkTaskProvider)
    from(hostJniBinary.outputDirectoryProperty) {
        include(hostJniBinary.outputFile.name)
        into("io/github/qingshu/ncnn/native/${ncnn.jvmPlatformId}")
    }
}

if (enableAndroid) {
    val androidJniCopies = mutableListOf<Any>()
    for (abi in ncnn.androidAbis) {
        val targetName = ncnn.androidTargetNameByAbi.getValue(abi)
        val target = kotlin.targets.getByName(targetName) as KotlinNativeTarget
        val binary = target.binaries.getSharedLib(NativeBuildType.RELEASE)
        val prepare = ncnn.prepareTasks.getValue("android-$abi")
        val abiId = abi.replace("-", "")
        androidJniCopies += tasks.register<Sync>("copyAndroidJni$abiId") {
            dependsOn(binary.linkTaskProvider, prepare)
            from(binary.outputDirectoryProperty) { include(binary.outputFile.name) }
            from(ncnn.installDirs.getValue("android-$abi").map { it.file("lib/libncnn.so") })
            into(rootProject.layout.buildDirectory.dir("ncnn/android/jniLibs/$abi"))
        }
    }
    tasks.register("buildAndroidJni") {
        group = "build"
        description = "Builds libncnn_jni.so for all configured Android ABIs."
        dependsOn(androidJniCopies)
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
