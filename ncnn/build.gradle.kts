import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import ncnn.build.CMakeBuildTask
import ncnn.build.DownloadFileTask
import ncnn.build.ExtractZipTask
import ncnn.build.readLocalProperties
import ncnn.build.resolveAndroidNdk
import ncnn.build.resolveNcnnToolchain
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

val ncnnVersion = libs.versions.ncnnVersion.get()
val ncnnSourceSha256 = libs.versions.ncnnSourceSha256.get()
val toolchain = resolveNcnnToolchain(project)
val enableAndroid = (project.findProperty("ncnn.android") as String?)?.toBoolean()
    ?: System.getenv("NCNN_ANDROID")?.toBoolean()
    ?: true

if (enableAndroid) {
    apply(plugin = "com.android.kotlin.multiplatform.library")
}

val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val isWindowsHost = "win" in hostOs
val isLinuxHost = "linux" in hostOs
val isMacosHost = "mac" in hostOs
val isArmHost = hostArch == "aarch64" || hostArch == "arm64"

/** Native target descriptions usable on the current host. */
data class NativeSpec(val kmpTarget: String, val platformId: String, val extraArgs: List<String>)

val nativeSpecs: List<NativeSpec> = buildList {
    when {
        isWindowsHost -> add(NativeSpec("mingwX64", "windows-x64", emptyList()))

        isLinuxHost -> {
            if (isArmHost) {
                add(NativeSpec("linuxArm64", "linux-arm64", emptyList()))
            } else {
                add(NativeSpec("linuxX64", "linux-x64", emptyList()))
            }
        }

        isMacosHost -> {
            add(
                NativeSpec(
                    "macosArm64",
                    "macos-arm64",
                    listOf("-DCMAKE_OSX_ARCHITECTURES=arm64"),
                ),
            )
            add(
                NativeSpec(
                    "iosArm64",
                    "ios-arm64",
                    listOf(
                        "-DCMAKE_SYSTEM_NAME=iOS",
                        "-DCMAKE_OSX_SYSROOT=iphoneos",
                        "-DCMAKE_OSX_ARCHITECTURES=arm64",
                        "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
                    ),
                ),
            )
            add(
                NativeSpec(
                    "iosSimulatorArm64",
                    "ios-simulator-arm64",
                    listOf(
                        "-DCMAKE_SYSTEM_NAME=iOS",
                        "-DCMAKE_OSX_SYSROOT=iphonesimulator",
                        "-DCMAKE_OSX_ARCHITECTURES=arm64",
                        "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
                    ),
                ),
            )
        }
    }
}

/** Native platform used by the JVM target on this host. */
val jvmPlatformId: String = when {
    isWindowsHost -> "windows-x64"
    isLinuxHost -> if (isArmHost) "linux-arm64" else "linux-x64"
    else -> if (isArmHost) "macos-arm64" else "macos-x64"
}

val jvmExtraArgs: List<String> =
    if (isMacosHost && !isArmHost) listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64") else emptyList()

/** Every ncnn build needed on this host, including JVM-only desktop architectures. */
val buildSpecs: List<NativeSpec> =
    (nativeSpecs + NativeSpec("", jvmPlatformId, jvmExtraArgs)).distinctBy { it.platformId }

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

    when {
        isWindowsHost -> mingwX64()

        isLinuxHost -> {
            if (isArmHost) linuxArm64() else linuxX64()
        }

        isMacosHost -> {
            macosArm64()
            iosArm64()
            iosSimulatorArm64()
        }
    }

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

val nativeMainSourceSet = kotlin.sourceSets.findByName("nativeMain")
val hasUnixNativeMain = kotlin.sourceSets.findByName("linuxMain") != null ||
    kotlin.sourceSets.findByName("appleMain") != null
if (nativeMainSourceSet != null && hasUnixNativeMain) {
    val unixNativeMain = kotlin.sourceSets.create("unixNativeMain") {
        dependsOn(nativeMainSourceSet)
    }
    listOf("linuxMain", "appleMain").forEach { name ->
        kotlin.sourceSets.findByName(name)?.dependsOn(unixNativeMain)
    }
}

// ---------------------------------------------------------------------------
// ncnn source
// ---------------------------------------------------------------------------

val ncnnSourceOverride = (project.findProperty("ncnn.srcDir") as String?)?.let(::file)
val ncnnSourceUrl = (project.findProperty("ncnn.sourceUrl") as String?)
    ?: readLocalProperties(project.rootDir).getProperty("ncnn.sourceUrl")
    ?: System.getenv("NCNN_SOURCE_URL")
    ?: "https://github.com/Tencent/ncnn/releases/download/$ncnnVersion/ncnn-$ncnnVersion-full-source.zip"

val downloadNcnnSource = tasks.register<DownloadFileTask>("downloadNcnnSource") {
    url.set(ncnnSourceUrl)
    sha256.set(ncnnSourceSha256)
    outputFile.set(layout.buildDirectory.file("downloads/ncnn-$ncnnVersion-full-source.zip"))
}

val unpackedNcnnSourceDir = layout.buildDirectory.dir("ncnn/src/$ncnnVersion")

val unpackNcnnSource = tasks.register<ExtractZipTask>("unpackNcnnSource") {
    dependsOn(downloadNcnnSource)
    archive.set(downloadNcnnSource.flatMap { it.outputFile })
    outputDir.set(unpackedNcnnSourceDir)
}

val ncnnSourceDir: File = ncnnSourceOverride ?: unpackedNcnnSourceDir.get().asFile

// ---------------------------------------------------------------------------
// ncnn static library builds
// ---------------------------------------------------------------------------

fun ncnnFeatureArgs(): List<String> = listOf(
    "-DCMAKE_BUILD_TYPE=Release",
    "-DCMAKE_POLICY_VERSION_MINIMUM=3.5",
    "-DNCNN_OPENMP=OFF",
    "-DNCNN_SIMPLEOMP=ON",
    "-DNCNN_BUILD_TOOLS=OFF",
    "-DNCNN_BUILD_EXAMPLES=OFF",
    "-DNCNN_BUILD_BENCHMARK=OFF",
    "-DNCNN_BUILD_TESTS=OFF",
    "-DNCNN_VULKAN=OFF",
    "-DNCNN_PIXEL=ON",
    "-DNCNN_PIXEL_DRAWING=OFF",
    "-DNCNN_INSTALL_SDK=ON",
)

/**
 * Windows and Linux build ncnn as a shared library: Kotlin/Native links only an import
 * library / shared object, avoiding C++ runtime and glibc-version mixing with its own
 * sysroot. macOS/iOS use a static library.
 */
fun platformNcnnArgs(): List<String> = when {
    isWindowsHost -> listOf(
        "-DNCNN_SHARED_LIB=ON",
        "-DCMAKE_SHARED_LINKER_FLAGS=-static-libgcc -static-libstdc++",
    )

    isLinuxHost -> listOf(
        "-DNCNN_SHARED_LIB=ON",
        "-DCMAKE_SHARED_LINKER_FLAGS=-static-libgcc -static-libstdc++ -Wl,-soname,libncnn.so",
    )

    else -> listOf("-DNCNN_SHARED_LIB=OFF")
}

fun commonNcnnConfigureArgs(): List<String> = listOf(
    "-DCMAKE_C_COMPILER=${toolchain.cCompiler.absolutePath}",
    "-DCMAKE_CXX_COMPILER=${toolchain.cxxCompiler.absolutePath}",
    "-DCMAKE_MAKE_PROGRAM=${toolchain.ninja.absolutePath}",
) + hostNcnnArgs() + platformNcnnArgs() + ncnnFeatureArgs()

/**
 * ncnn's simpleomp.cpp uses pthread_once/pthread_once_t but ncnn's platform.h only
 * includes <pthread.h> on non-Windows hosts (Windows uses win32 threads). Force the
 * include on MinGW so NCNN_SIMPLEOMP compiles.
 */
fun hostNcnnArgs(): List<String> = if (isWindowsHost) listOf("-DCMAKE_CXX_FLAGS=-include pthread.h") else emptyList()

fun taskSuffix(id: String): String = id.split('-').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

val ncnnBuildTasks = mutableMapOf<String, TaskProvider<CMakeBuildTask>>()

for (spec in buildSpecs) {
    val buildTask = tasks.register<CMakeBuildTask>("buildNcnn${taskSuffix(spec.platformId)}") {
        if (ncnnSourceOverride == null) {
            dependsOn(unpackNcnnSource)
        }
        sourceDir.set(ncnnSourceDir)
        cmakeExecutable.set(toolchain.cmake.absolutePath)
        generator.set("Ninja")
        configureArgs.set(commonNcnnConfigureArgs() + spec.extraArgs)
        environment.set(emptyMap<String, String>())
        installDir.set(layout.buildDirectory.dir("ncnn/${spec.platformId}/install"))
        buildDir.set(layout.buildDirectory.dir("ncnn/${spec.platformId}/build"))
    }
    ncnnBuildTasks[spec.platformId] = buildTask
}

// ---------------------------------------------------------------------------
// cinterop bindings
// ---------------------------------------------------------------------------

for (spec in nativeSpecs) {
    val target = kotlin.targets.findByName(spec.kmpTarget) as? KotlinNativeTarget ?: continue
    val installDir = layout.buildDirectory.dir("ncnn/${spec.platformId}/install")
    val buildTask = ncnnBuildTasks.getValue(spec.platformId)

    target.compilations.getByName("main").cinterops.create("ncnn") {
        packageName("ncnn")
        headers(installDir.get().dir("include/ncnn/c_api.h").asFile.absolutePath.replace('\\', '/'))
        compilerOpts("-I${installDir.get().dir("include/ncnn").asFile.absolutePath}")
        if (!isLinuxHost) {
            val libraryName = if (isWindowsHost) "libncnn.dll.a" else "libncnn.a"
            extraOpts(
                "-libraryPath",
                installDir.get().dir("lib").asFile.absolutePath,
                "-staticLibrary",
                libraryName,
            )
        }
    }

    target.binaries.configureEach {
        val linkerOptions = when {
            // Windows links the import library through cinterop; its runtime lives in the DLL.
            isWindowsHost -> emptyList()

            // Linux links the shared object directly and finds it next to the test binary.
            isLinuxHost -> listOf(
                "-L${installDir.get().dir("lib").asFile.absolutePath}",
                "-lncnn",
                "-Wl,-rpath,\$ORIGIN",
            )

            else -> toolchain.cinteropLinkerOpts
        }
        linkerOpts(*linkerOptions.toTypedArray())
    }

    val interopTaskName = "cinteropNcnn" + spec.kmpTarget.replaceFirstChar(Char::uppercaseChar)
    tasks.matching { it.name == interopTaskName }.configureEach {
        dependsOn(buildTask)
    }
}

// ---------------------------------------------------------------------------
// JVM JNI library
// ---------------------------------------------------------------------------

/** Native platform used by the JVM target on this host. */
val jvmNcnnBuild = ncnnBuildTasks.getValue(jvmPlatformId)
val jvmNcnnInstallDir = layout.buildDirectory.dir("ncnn/$jvmPlatformId/install")

val javaToolchainService = extensions.getByType(JavaToolchainService::class.java)
val jdkHome = javaToolchainService.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}.get().metadata.installationPath.asFile

val jniLibFileName = when {
    isWindowsHost -> "ncnn_jni.dll"
    isMacosHost -> "libncnn_jni.dylib"
    else -> "libncnn_jni.so"
}

val buildJniNcnn = tasks.register<CMakeBuildTask>("buildJniNcnn") {
    dependsOn(jvmNcnnBuild)
    sourceDir.set(layout.projectDirectory.dir("src/jvmMain/cpp"))
    cmakeExecutable.set(toolchain.cmake.absolutePath)
    generator.set("Ninja")
    configureArgs.set(
        listOf(
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_POLICY_VERSION_MINIMUM=3.5",
            "-DCMAKE_C_COMPILER=${toolchain.cCompiler.absolutePath}",
            "-DCMAKE_CXX_COMPILER=${toolchain.cxxCompiler.absolutePath}",
            "-DCMAKE_MAKE_PROGRAM=${toolchain.ninja.absolutePath}",
            "-DNCNN_INSTALL_DIR=${jvmNcnnInstallDir.get().asFile.absolutePath}",
            "-DJAVA_HOME=${jdkHome.absolutePath}",
        ),
    )
    extraInputs.from(jvmNcnnInstallDir)
    installDir.set(layout.buildDirectory.dir("ncnn/jvm-$jvmPlatformId/install"))
    buildDir.set(layout.buildDirectory.dir("ncnn/jvm-$jvmPlatformId/build"))
}

val jniInstallDir = layout.buildDirectory.dir("ncnn/jvm-$jvmPlatformId/install")

tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(buildJniNcnn)
    from(jniInstallDir.map { it.dir("lib") }) {
        include(jniLibFileName)
        into("io/github/qingshu/ncnn/native/$jvmPlatformId")
    }
    if (isWindowsHost) {
        // Windows uses a shared ncnn DLL plus the dynamically linked winpthread.
        from(jvmNcnnInstallDir.map { it.dir("bin") }) {
            include("*.dll")
            into("io/github/qingshu/ncnn/native/$jvmPlatformId")
        }
        from(toolchain.cCompiler.parentFile) {
            include("libwinpthread-1.dll")
            into("io/github/qingshu/ncnn/native/$jvmPlatformId")
        }
    } else if (isLinuxHost) {
        from(jvmNcnnInstallDir.map { it.dir("lib") }) {
            include("libncnn.so*")
            into("io/github/qingshu/ncnn/native/$jvmPlatformId")
        }
    }
}

// ---------------------------------------------------------------------------
// Android (NDK) libraries
// ---------------------------------------------------------------------------

if (enableAndroid) {
    val androidNdkVersion = "27.2.12479018"
    val androidNdk = resolveAndroidNdk(project, androidNdkVersion)
    val androidAbis: List<String> = (project.findProperty("ncnn.android.abis") as String?)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    val androidJniLibsDir = rootProject.layout.buildDirectory.dir("ncnn/android/jniLibs")
    val androidJniTasks = mutableListOf<TaskProvider<*>>()

    if (androidNdk != null) {
        fun androidCommonArgs(abi: String): List<String> = listOf(
            "-DCMAKE_POLICY_VERSION_MINIMUM=3.5",
            "-DCMAKE_TOOLCHAIN_FILE=${androidNdk.absolutePath}/build/cmake/android.toolchain.cmake",
            "-DANDROID_ABI=$abi",
            "-DANDROID_PLATFORM=android-24",
            "-DANDROID_STL=c++_static",
            "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
            "-DCMAKE_MAKE_PROGRAM=${toolchain.ninja.absolutePath}",
        )

        for (abi in androidAbis) {
            val abiId = abi.replace("-", "")
            val ncnnInstall = layout.buildDirectory.dir("ncnn/android-$abi/install")
            val ncnnTask = tasks.register<CMakeBuildTask>("buildNcnnAndroid$abiId") {
                if (ncnnSourceOverride == null) {
                    dependsOn(unpackNcnnSource)
                }
                sourceDir.set(ncnnSourceDir)
                cmakeExecutable.set(toolchain.cmake.absolutePath)
                generator.set("Ninja")
                configureArgs.set(ncnnFeatureArgs() + androidCommonArgs(abi))
                environment.set(emptyMap<String, String>())
                installDir.set(ncnnInstall)
                buildDir.set(layout.buildDirectory.dir("ncnn/android-$abi/build"))
            }

            val jniTask = tasks.register<CMakeBuildTask>("buildJniAndroid$abiId") {
                dependsOn(ncnnTask)
                sourceDir.set(layout.projectDirectory.dir("src/jvmMain/cpp"))
                cmakeExecutable.set(toolchain.cmake.absolutePath)
                generator.set("Ninja")
                configureArgs.set(
                    listOf("-DCMAKE_BUILD_TYPE=Release") +
                        androidCommonArgs(abi) +
                        "-DNCNN_INSTALL_DIR=${ncnnInstall.get().asFile.absolutePath}",
                )
                extraInputs.from(ncnnInstall)
                installDir.set(layout.buildDirectory.dir("ncnn/android-jni-$abi/install"))
                buildDir.set(layout.buildDirectory.dir("ncnn/android-jni-$abi/build"))
            }

            val copyTask = tasks.register<Sync>("copyAndroidJni$abiId") {
                dependsOn(jniTask)
                from(jniTask.flatMap { it.installDir }.map { it.file("lib/libncnn_jni.so") })
                into(androidJniLibsDir.map { it.dir(abi) })
            }
            androidJniTasks += copyTask
        }
    }

    tasks.register("buildAndroidJni") {
        group = "build"
        description = "Builds libncnn_jni.so for all configured Android ABIs."
        if (androidNdk != null) {
            dependsOn(androidJniTasks)
        } else {
            doLast {
                throw GradleException(
                    "Android NDK $androidNdkVersion not found. Install it under sdk.dir/ndk/$androidNdkVersion " +
                        "or set ncnn.ndk / ANDROID_NDK_HOME.",
                )
            }
        }
    }
}

if (isWindowsHost) {
    // The Windows native test binary loads ncnn.dll and winpthread next to the executable.
    val copyMingwTestRuntime = tasks.register<Sync>("copyMingwTestRuntime") {
        dependsOn(jvmNcnnBuild)
        from(jvmNcnnInstallDir.map { it.dir("bin") }) {
            include("*.dll")
        }
        from(toolchain.cCompiler.parentFile) {
            include("libwinpthread-1.dll")
        }
        into(layout.buildDirectory.dir("bin/mingwX64/debugTest"))
    }
    tasks.matching { it.name == "mingwX64Test" }.configureEach {
        dependsOn(copyMingwTestRuntime)
    }
}

if (isLinuxHost) {
    // Linux links libncnn.so from the test binary directory (see -rpath,$ORIGIN).
    for (spec in nativeSpecs) {
        val copyTask = tasks.register<Sync>("copy${spec.kmpTarget.replaceFirstChar(Char::uppercaseChar)}Runtime") {
            dependsOn(ncnnBuildTasks.getValue(spec.platformId))
            from(layout.buildDirectory.dir("ncnn/${spec.platformId}/install/lib")) {
                include("libncnn.so*")
            }
            into(layout.buildDirectory.dir("bin/${spec.kmpTarget}/debugTest"))
        }
        tasks.matching { it.name == "${spec.kmpTarget}Test" }.configureEach {
            dependsOn(copyTask)
        }
    }
}

// On unsupported hosts only the JVM artifacts are expected to build.
tasks.withType<KotlinNativeLink>().configureEach {
    enabled = when {
        name.contains("Linux", ignoreCase = true) -> isLinuxHost
        name.contains("Macos", ignoreCase = true) -> isMacosHost
        name.contains("Ios", ignoreCase = true) -> isMacosHost
        name.contains("Mingw", ignoreCase = true) -> isWindowsHost
        else -> true
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}
