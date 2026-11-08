package ncnn.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

/** A native target and the ncnn build platform it consumes. */
data class NativeSpec(val kmpTarget: String, val platformId: String, val extraArgs: List<String>)

/**
 * Registers everything the ncnn module needs outside the Kotlin multiplatform DSL:
 * source download/extraction, per-platform ncnn builds, JNI builds, runtime copies,
 * Android NDK builds, and task disabling for targets the host cannot build.
 *
 * The build script keeps the KMP target/source-set/cinterop declarations (those need
 * Kotlin Gradle plugin DSL access) and delegates the plumbing to this class.
 */
class NcnnBuilds(
    private val project: Project,
    private val ncnnVersion: String,
    private val ncnnSourceSha256: String,
    private val enableAndroid: Boolean,
) {
    val toolchain: NcnnToolchain = resolveNcnnToolchain(project)

    private val hostOs = System.getProperty("os.name").lowercase()
    private val hostArch = System.getProperty("os.arch").lowercase()
    val isWindowsHost = "win" in hostOs
    val isLinuxHost = "linux" in hostOs
    val isMacosHost = "mac" in hostOs
    private val isArmHost = hostArch == "aarch64" || hostArch == "arm64"

    /** Targets that can actually be built on this host. */
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
                add(NativeSpec("macosArm64", "macos-arm64", listOf("-DCMAKE_OSX_ARCHITECTURES=arm64")))
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

    val supportedTargets: Set<String> = nativeSpecs.map { it.kmpTarget }.toSet()

    /** Linux klibs can be compiled from any host; executables still need a Linux host. */
    val crossCompilableTargets: Set<String> = setOf("linuxX64", "linuxArm64")

    /** KMP target name -> ncnn platform id, for every declared target. */
    val platformIdByTarget: Map<String, String> = mapOf(
        "mingwX64" to "windows-x64",
        "linuxX64" to "linux-x64",
        "linuxArm64" to "linux-arm64",
        "macosArm64" to "macos-arm64",
        "iosArm64" to "ios-arm64",
        "iosSimulatorArm64" to "ios-simulator-arm64",
    )

    val jvmPlatformId: String = when {
        isWindowsHost -> "windows-x64"
        isLinuxHost -> if (isArmHost) "linux-arm64" else "linux-x64"
        else -> if (isArmHost) "macos-arm64" else "macos-x64"
    }

    private val jvmExtraArgs: List<String> =
        if (isMacosHost && !isArmHost) listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64") else emptyList()

    private val buildSpecs: List<NativeSpec> =
        (nativeSpecs + NativeSpec("", jvmPlatformId, jvmExtraArgs)).distinctBy { it.platformId }

    private val ncnnSourceOverride: File? = (project.findProperty("ncnn.srcDir") as String?)?.let(project::file)
    private val ncnnSourceUrl: String = (project.findProperty("ncnn.sourceUrl") as String?)
        ?: readLocalProperties(project.rootDir).getProperty("ncnn.sourceUrl")
        ?: System.getenv("NCNN_SOURCE_URL")
        ?: "https://github.com/Tencent/ncnn/releases/download/$ncnnVersion/ncnn-$ncnnVersion-full-source.zip"

    val ncnnSourceDir: File
    val portableHeadersDir: Provider<Directory>
    val portableHeadersTask: TaskProvider<PortableNcnnHeadersTask>
    val ncnnBuildTasks: Map<String, TaskProvider<CMakeBuildTask>>
    val installDirs: Map<String, Provider<Directory>>
    val jvmNcnnInstallDir: Provider<Directory>
    val jniBuildTask: TaskProvider<CMakeBuildTask>
    val jniInstallDir: Provider<Directory>
    val jniLibFileName: String

    internal val downloadTask: TaskProvider<DownloadFileTask>
    internal val unpackTask: TaskProvider<ExtractZipTask>

    init {
        downloadTask = project.tasks.register<DownloadFileTask>("downloadNcnnSource") {
            url.set(ncnnSourceUrl)
            sha256.set(ncnnSourceSha256)
            outputFile.set(project.layout.buildDirectory.file("downloads/ncnn-$ncnnVersion-full-source.zip"))
        }
        val unpackedSource = project.layout.buildDirectory.dir("ncnn/src/$ncnnVersion")
        unpackTask = project.tasks.register<ExtractZipTask>("unpackNcnnSource") {
            dependsOn(downloadTask)
            archive.set(downloadTask.flatMap { it.outputFile })
            outputDir.set(unpackedSource)
        }
        ncnnSourceDir = ncnnSourceOverride ?: unpackedSource.get().asFile

        portableHeadersDir = project.layout.buildDirectory.dir("ncnn/portable/include/ncnn")
        portableHeadersTask = project.tasks.register<PortableNcnnHeadersTask>("generatePortableHeaders") {
            if (ncnnSourceOverride == null) dependsOn(unpackTask)
            cApiHeader.set(File(ncnnSourceDir, "src/c_api.h"))
            outputDir.set(portableHeadersDir)
        }

        val allInstallDirs = (platformIdByTarget.values + jvmPlatformId)
            .distinct()
            .associateWith { project.layout.buildDirectory.dir("ncnn/$it/install") }
        val builds = mutableMapOf<String, TaskProvider<CMakeBuildTask>>()
        for (spec in buildSpecs) {
            val install = allInstallDirs.getValue(spec.platformId)
            builds[spec.platformId] = project.tasks.register<CMakeBuildTask>(
                "buildNcnn${taskSuffix(spec.platformId)}",
            ) {
                if (ncnnSourceOverride == null) dependsOn(unpackTask)
                sourceDir.set(ncnnSourceDir)
                cmakeExecutable.set(toolchain.cmake.absolutePath)
                generator.set("Ninja")
                configureArgs.set(commonNcnnConfigureArgs() + spec.extraArgs)
                environment.set(emptyMap())
                installDir.set(install)
                buildDir.set(project.layout.buildDirectory.dir("ncnn/${spec.platformId}/build"))
            }
        }
        ncnnBuildTasks = builds
        installDirs = allInstallDirs

        jvmNcnnInstallDir = allInstallDirs.getValue(jvmPlatformId)
        jniLibFileName = when {
            isWindowsHost -> "ncnn_jni.dll"
            isMacosHost -> "libncnn_jni.dylib"
            else -> "libncnn_jni.so"
        }
        jniInstallDir = project.layout.buildDirectory.dir("ncnn/jvm-$jvmPlatformId/install")
        jniBuildTask = registerJniBuild()
        wireJvmResources()
        registerRuntimeCopies()
        if (enableAndroid) registerAndroidBuilds()
    }

    /** Disables link/test tasks for targets this host cannot run, keeping cinterop/compile visible to IDEs. */
    fun disableUnsupportedTargets(targetNames: Collection<String>) {
        targetNames.filter { it !in supportedTargets }.forEach { targetName ->
            val crossCompile = targetName in crossCompilableTargets
            project.tasks.matching {
                val name = it.name
                name.contains(targetName, ignoreCase = true) &&
                    !name.startsWith("cinterop", ignoreCase = true) &&
                    !(crossCompile && (name.startsWith("compile", ignoreCase = true) || name.endsWith("Klibrary")))
            }.configureEach {
                enabled = false
            }
        }
    }

    private fun ncnnFeatureArgs(): List<String> = listOf(
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

    private fun platformNcnnArgs(): List<String> = when {
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

    private fun hostNcnnArgs(): List<String> =
        if (isWindowsHost) listOf("-DCMAKE_CXX_FLAGS=-include pthread.h") else emptyList()

    private fun commonNcnnConfigureArgs(): List<String> = listOf(
        "-DCMAKE_C_COMPILER=${toolchain.cCompiler.absolutePath}",
        "-DCMAKE_CXX_COMPILER=${toolchain.cxxCompiler.absolutePath}",
        "-DCMAKE_MAKE_PROGRAM=${toolchain.ninja.absolutePath}",
    ) + hostNcnnArgs() + platformNcnnArgs() + ncnnFeatureArgs()

    private fun registerJniBuild(): TaskProvider<CMakeBuildTask> {
        val jdkHome = project.extensions.getByType(JavaToolchainService::class.java)
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
            .get().metadata.installationPath.asFile
        return project.tasks.register<CMakeBuildTask>("buildJniNcnn") {
            dependsOn(ncnnBuildTasks.getValue(jvmPlatformId))
            sourceDir.set(project.layout.projectDirectory.dir("src/jvmMain/cpp"))
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
            installDir.set(jniInstallDir)
            buildDir.set(project.layout.buildDirectory.dir("ncnn/jvm-$jvmPlatformId/build"))
        }
    }

    private fun wireJvmResources() {
        val resourceDir = "io/github/qingshu/ncnn/native/$jvmPlatformId"
        project.tasks.named<ProcessResources>("jvmProcessResources") {
            dependsOn(jniBuildTask)
            from(jniInstallDir.map { it.dir("lib") }) {
                include(jniLibFileName)
                into(resourceDir)
            }
            if (isWindowsHost) {
                from(jvmNcnnInstallDir.map { it.dir("bin") }) {
                    include("*.dll")
                    into(resourceDir)
                }
                from(toolchain.cCompiler.parentFile) {
                    include("libwinpthread-1.dll")
                    into(resourceDir)
                }
            } else if (isLinuxHost) {
                from(jvmNcnnInstallDir.map { it.dir("lib") }) {
                    include("libncnn.so*")
                    into(resourceDir)
                }
            }
        }
    }

    private fun registerRuntimeCopies() {
        if (isWindowsHost) {
            val copy = project.tasks.register<Sync>("copyMingwTestRuntime") {
                dependsOn(ncnnBuildTasks.getValue(jvmPlatformId))
                from(jvmNcnnInstallDir.map { it.dir("bin") }) { include("*.dll") }
                from(toolchain.cCompiler.parentFile) { include("libwinpthread-1.dll") }
                into(project.layout.buildDirectory.dir("bin/mingwX64/debugTest"))
            }
            project.tasks.matching { it.name == "mingwX64Test" }.configureEach { dependsOn(copy) }
        }
        if (isLinuxHost) {
            for (spec in nativeSpecs) {
                val copy = project.tasks.register<Sync>(
                    "copy${spec.kmpTarget.replaceFirstChar(Char::uppercaseChar)}Runtime",
                ) {
                    dependsOn(ncnnBuildTasks.getValue(spec.platformId))
                    from(installDirs.getValue(spec.platformId).map { it.dir("lib") }) { include("libncnn.so*") }
                    into(project.layout.buildDirectory.dir("bin/${spec.kmpTarget}/debugTest"))
                }
                project.tasks.matching { it.name == "${spec.kmpTarget}Test" }.configureEach { dependsOn(copy) }
            }
        }
    }

    private fun registerAndroidBuilds() {
        val ndkVersion = "27.2.12479018"
        val ndk = resolveAndroidNdk(project, ndkVersion)
        val abis = (project.findProperty("ncnn.android.abis") as String?)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        val jniLibsDir = project.rootProject.layout.buildDirectory.dir("ncnn/android/jniLibs")
        val copies = mutableListOf<TaskProvider<*>>()

        if (ndk != null) {
            fun androidArgs(abi: String): List<String> = listOf(
                "-DCMAKE_POLICY_VERSION_MINIMUM=3.5",
                "-DCMAKE_TOOLCHAIN_FILE=${ndk.absolutePath}/build/cmake/android.toolchain.cmake",
                "-DANDROID_ABI=$abi",
                "-DANDROID_PLATFORM=android-24",
                "-DANDROID_STL=c++_static",
                "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                "-DCMAKE_MAKE_PROGRAM=${toolchain.ninja.absolutePath}",
            )

            for (abi in abis) {
                val abiId = abi.replace("-", "")
                val ncnnInstall = project.layout.buildDirectory.dir("ncnn/android-$abi/install")
                val ncnnTask = project.tasks.register<CMakeBuildTask>("buildNcnnAndroid$abiId") {
                    if (ncnnSourceOverride == null) dependsOn(unpackTask)
                    sourceDir.set(ncnnSourceDir)
                    cmakeExecutable.set(toolchain.cmake.absolutePath)
                    generator.set("Ninja")
                    configureArgs.set(ncnnFeatureArgs() + androidArgs(abi))
                    environment.set(emptyMap())
                    installDir.set(ncnnInstall)
                    buildDir.set(project.layout.buildDirectory.dir("ncnn/android-$abi/build"))
                }

                val jniTask = project.tasks.register<CMakeBuildTask>("buildJniAndroid$abiId") {
                    dependsOn(ncnnTask)
                    sourceDir.set(project.layout.projectDirectory.dir("src/jvmMain/cpp"))
                    cmakeExecutable.set(toolchain.cmake.absolutePath)
                    generator.set("Ninja")
                    configureArgs.set(
                        listOf("-DCMAKE_BUILD_TYPE=Release") +
                            androidArgs(abi) +
                            "-DNCNN_INSTALL_DIR=${ncnnInstall.get().asFile.absolutePath}",
                    )
                    extraInputs.from(ncnnInstall)
                    installDir.set(project.layout.buildDirectory.dir("ncnn/android-jni-$abi/install"))
                    buildDir.set(project.layout.buildDirectory.dir("ncnn/android-jni-$abi/build"))
                }

                copies += project.tasks.register<Sync>("copyAndroidJni$abiId") {
                    dependsOn(jniTask)
                    from(jniTask.flatMap { it.installDir }.map { it.file("lib/libncnn_jni.so") })
                    into(jniLibsDir.map { it.dir(abi) })
                }
            }
        }

        project.tasks.register("buildAndroidJni") {
            group = "build"
            description = "Builds libncnn_jni.so for all configured Android ABIs."
            if (ndk != null) {
                dependsOn(copies)
            } else {
                doLast {
                    throw GradleException(
                        "Android NDK $ndkVersion not found. Install it under sdk.dir/ndk/$ndkVersion " +
                            "or set ncnn.ndk / ANDROID_NDK_HOME.",
                    )
                }
            }
        }
    }
}

private fun taskSuffix(id: String): String =
    id.split('-').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
