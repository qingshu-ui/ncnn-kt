package ncnn.build

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

/** A native target and the prebuilt ncnn platform it consumes. */
data class NativeSpec(val kmpTarget: String, val platformId: String, val extraArgs: List<String>)

/** An official ncnn release asset, pinned by SHA-256. */
data class PrebuiltAsset(val assetName: String, val sha256: String)

private const val WINDOWS_SHA = "7f39107af5ee4c1e657b8fd01e8b3cad6c0ab7fdbb0713532f01937134285b3d"
private const val LINUX_SHA = "996e07a787550b782dd5891a2a60d7fa5d8f84452bb2f244ce0f3f9c01417842"
private const val ANDROID_SHA = "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87"
private const val MACOS_SHA = "e33de27e608da9f71c7c47b61cc2c443f08371f1df47a8222ea493ccd93e3f2d"
private const val IOS_SHA = "a9f64778720a6932cd03ae224d5a8fab5ceb58294b692a1b8c9f97eaa05481be"
private const val IOS_SIMULATOR_SHA = "dd842be6f9b6688652c916be74e6e40ba431f3a009d1360d6ff34fdb8701d6e4"

/**
 * Resolves the official ncnn release packages and wires them into JNI builds, cinterop
 * bindings, JVM resources and Android ABI packaging. No ncnn sources are compiled here.
 */
class NcnnBuilds(
    private val project: Project,
    private val ncnnVersion: String,
    private val enableAndroid: Boolean,
) {
    val toolchain: NcnnToolchain = resolveNcnnToolchain(project)

    private val hostOs = System.getProperty("os.name").lowercase()
    private val hostArch = System.getProperty("os.arch").lowercase()
    val isWindowsHost = "win" in hostOs
    val isLinuxHost = "linux" in hostOs
    val isMacosHost = "mac" in hostOs
    private val isArmHost = hostArch == "aarch64" || hostArch == "arm64"

    val nativeSpecs: List<NativeSpec> = buildList {
        when {
            isWindowsHost -> add(NativeSpec("mingwX64", "windows-x64", emptyList()))
            isLinuxHost -> add(NativeSpec("linuxX64", "linux-x64", emptyList()))
            isMacosHost -> {
                add(NativeSpec("macosArm64", "macos-arm64", listOf("-DCMAKE_OSX_ARCHITECTURES=arm64")))
                add(NativeSpec("iosArm64", "ios-arm64", listOf("-DCMAKE_OSX_ARCHITECTURES=arm64")))
                add(
                    NativeSpec(
                        "iosSimulatorArm64",
                        "ios-simulator-arm64",
                        listOf("-DCMAKE_OSX_ARCHITECTURES=arm64"),
                    ),
                )
            }
        }
    }

    val supportedTargets: Set<String> = nativeSpecs.map { it.kmpTarget }.toSet()

    /** Linux klibs can be compiled from any host; executables still need a Linux host. */
    val crossCompilableTargets: Set<String> = setOf("linuxX64")

    val platformIdByTarget: Map<String, String> = mapOf(
        "mingwX64" to "windows-x64",
        "linuxX64" to "linux-x64",
        "macosArm64" to "macos-arm64",
        "iosArm64" to "ios-arm64",
        "iosSimulatorArm64" to "ios-simulator-arm64",
    )

    val jvmPlatformId: String = when {
        isWindowsHost -> "windows-x64"
        isLinuxHost -> "linux-x64"
        else -> if (isArmHost) "macos-arm64" else "macos-x64"
    }

    private val jvmExtraArgs: List<String> =
        if (isMacosHost && !isArmHost) listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64") else emptyList()

    val assets: Map<String, PrebuiltAsset> = mapOf(
        "windows" to PrebuiltAsset("ncnn-$ncnnVersion-windows-vs2022-shared.zip", WINDOWS_SHA),
        "linux" to PrebuiltAsset("ncnn-$ncnnVersion-ubuntu-2404-shared.zip", LINUX_SHA),
        "android" to PrebuiltAsset("ncnn-$ncnnVersion-android-shared.zip", ANDROID_SHA),
        "macos" to PrebuiltAsset("ncnn-$ncnnVersion-macos.zip", MACOS_SHA),
        "ios" to PrebuiltAsset("ncnn-$ncnnVersion-ios.zip", IOS_SHA),
        "ios-simulator" to PrebuiltAsset("ncnn-$ncnnVersion-ios-simulator.zip", IOS_SIMULATOR_SHA),
    )

    private val androidAbis: List<String> = (project.findProperty("ncnn.android.abis") as String?)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    private val baseUrl = (project.findProperty("ncnn.prebuiltBaseUrl") as String?)
        ?: readLocalProperties(project.rootDir).getProperty("ncnn.prebuiltBaseUrl")
        ?: System.getenv("NCNN_PREBUILT_BASE_URL")
        ?: "https://github.com/Tencent/ncnn/releases/download/$ncnnVersion"

    val installDirs: Map<String, Provider<Directory>>
    val prepareTasks: Map<String, TaskProvider<PreparePrebuiltInstallTask>>
    val portableHeadersDir: Provider<Directory> =
        project.layout.buildDirectory.dir("ncnn/portable/include/ncnn")
    val portableHeadersTask: TaskProvider<PortableNcnnHeadersTask>
    val jniBuildTask: TaskProvider<CMakeBuildTask>
    val jniInstallDir: Provider<Directory>
    val jniLibFileName: String

    /** Static libraries cinterop should link for a platform (empty for Linux, which links dynamically). */
    val cinteropStaticLibraries: Map<String, List<String>> = mapOf(
        "windows-x64" to listOf("ncnn.lib"),
        "linux-x64" to emptyList(),
        "macos-arm64" to listOf("libncnn.a", "libopenmp.a"),
        "macos-x64" to listOf("libncnn.a", "libopenmp.a"),
        "ios-arm64" to listOf("libncnn.a", "libopenmp.a"),
        "ios-simulator-arm64" to listOf("libncnn.a", "libopenmp.a"),
    )

    init {
        val platformIds = (platformIdByTarget.values + jvmPlatformId + androidAbis.map { "android-$it" }).distinct()
        val installs = mutableMapOf<String, Provider<Directory>>()
        val prepares = mutableMapOf<String, TaskProvider<PreparePrebuiltInstallTask>>()
        val unpacked = mutableMapOf<String, TaskProvider<ExtractZipTask>>()

        for (platformId in platformIds) {
            val (key, layout, subDir) = installSpec(platformId)
            val raw = project.layout.buildDirectory.dir("ncnn/prebuilt/$key/raw")
            val unpack = unpacked.getOrPut(key) { registerUnpack(key, raw) }
            val install = if (layout == "android") {
                project.layout.buildDirectory.dir("ncnn/prebuilt/android/$subDir/install")
            } else {
                project.layout.buildDirectory.dir("ncnn/prebuilt/$key/install")
            }
            val task = project.tasks.register<PreparePrebuiltInstallTask>("prepareNcnn${suffix(platformId)}") {
                dependsOn(unpack)
                rawDir.set(raw)
                this.layout.set(layout)
                this.subDir.set(subDir)
                outputDir.set(install)
            }
            installs[platformId] = install
            prepares[platformId] = task
        }
        installDirs = installs
        prepareTasks = prepares

        val primaryPlatform = when {
            isWindowsHost -> "windows-x64"
            isLinuxHost -> "linux-x64"
            else -> if (isArmHost) "macos-arm64" else "macos-x64"
        }
        portableHeadersTask = project.tasks.register<PortableNcnnHeadersTask>("generatePortableHeaders") {
            dependsOn(prepares.getValue(primaryPlatform))
            cApiHeader.set(installs.getValue(primaryPlatform).map { it.file("include/ncnn/c_api.h") })
            outputDir.set(portableHeadersDir)
        }

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

    fun disableUnsupportedTargets(targetNames: Collection<String>) {
        targetNames.filter { it !in supportedTargets }.forEach { targetName ->
            val crossCompile = targetName in crossCompilableTargets
            project.tasks.matching {
                val name = it.name
                name.contains(targetName, ignoreCase = true) &&
                    !name.startsWith("cinterop", ignoreCase = true) &&
                    !name.startsWith("prepareNcnn", ignoreCase = true) &&
                    !name.startsWith("downloadNcnn", ignoreCase = true) &&
                    !name.startsWith("unpackNcnn", ignoreCase = true) &&
                    !(crossCompile && (name.startsWith("compile", ignoreCase = true) || name.endsWith("Klibrary")))
            }.configureEach {
                enabled = false
            }
        }
    }

    private fun registerUnpack(key: String, raw: Provider<Directory>): TaskProvider<ExtractZipTask> {
        val asset = assets.getValue(key)
        val download = project.tasks.register<DownloadFileTask>("downloadNcnn${suffix(key)}") {
            url.set("$baseUrl/${asset.assetName}")
            sha256.set(asset.sha256)
            outputFile.set(project.layout.buildDirectory.file("downloads/${asset.assetName}"))
        }
        return project.tasks.register<ExtractZipTask>("unpackNcnn${suffix(key)}") {
            dependsOn(download)
            archive.set(download.flatMap { it.outputFile })
            outputDir.set(raw)
        }
    }

    private fun installSpec(platformId: String): Triple<String, String, String> = when (platformId) {
        "windows-x64" -> Triple("windows", "windows", "")
        "linux-x64" -> Triple("linux", "linux", "")
        "macos-arm64", "macos-x64" -> Triple("macos", "apple", "")
        "ios-arm64" -> Triple("ios", "apple", "")
        "ios-simulator-arm64" -> Triple("ios-simulator", "apple", "")
        else -> {
            require(platformId.startsWith("android-")) { "unknown platform $platformId" }
            Triple("android", "android", platformId.removePrefix("android-"))
        }
    }

    private fun registerJniBuild(): TaskProvider<CMakeBuildTask> {
        val jdkHome = project.extensions.getByType(JavaToolchainService::class.java)
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
            .get().metadata.installationPath.asFile
        val install = installDirs.getValue(jvmPlatformId)
        return project.tasks.register<CMakeBuildTask>("buildJniNcnn") {
            dependsOn(prepareTasks.getValue(jvmPlatformId))
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
                    "-DNCNN_INSTALL_DIR=${install.get().asFile.absolutePath}",
                    "-DJAVA_HOME=${jdkHome.absolutePath}",
                ) + jvmExtraArgs,
            )
            extraInputs.from(install)
            installDir.set(jniInstallDir)
            buildDir.set(project.layout.buildDirectory.dir("ncnn/jvm-$jvmPlatformId/build"))
        }
    }

    private fun wireJvmResources() {
        val resourceDir = "io/github/qingshu/ncnn/native/$jvmPlatformId"
        val install = installDirs.getValue(jvmPlatformId)
        project.tasks.named<ProcessResources>("jvmProcessResources") {
            dependsOn(jniBuildTask)
            from(jniInstallDir.map { it.dir("lib") }) {
                include(jniLibFileName)
                into(resourceDir)
            }
            if (isWindowsHost) {
                from(install.map { it.dir("bin") }) {
                    include("ncnn.dll")
                    into(resourceDir)
                }
                from(msvcRuntimeDlls()) { into(resourceDir) }
                from(toolchain.cCompiler.parentFile) {
                    include("libwinpthread-1.dll")
                    into(resourceDir)
                }
            } else if (isLinuxHost) {
                from(install.map { it.dir("lib") }) {
                    include("libncnn.so*")
                    into(resourceDir)
                }
            }
        }
    }

    private fun registerRuntimeCopies() {
        if (isWindowsHost) {
            val install = installDirs.getValue(jvmPlatformId)
            val copy = project.tasks.register<Sync>("copyMingwTestRuntime") {
                dependsOn(prepareTasks.getValue(jvmPlatformId))
                from(install.map { it.dir("bin") }) { include("ncnn.dll") }
                from(msvcRuntimeDlls())
                from(toolchain.cCompiler.parentFile) { include("libwinpthread-1.dll") }
                into(project.layout.buildDirectory.dir("bin/mingwX64/debugTest"))
            }
            project.tasks.matching { it.name == "mingwX64Test" }.configureEach { dependsOn(copy) }
        }
        if (isLinuxHost) {
            val install = installDirs.getValue("linux-x64")
            val copy = project.tasks.register<Sync>("copyLinuxTestRuntime") {
                dependsOn(prepareTasks.getValue("linux-x64"))
                from(install.map { it.dir("lib") }) { include("libncnn.so*") }
                into(project.layout.buildDirectory.dir("bin/linuxX64/debugTest"))
            }
            project.tasks.matching { it.name == "linuxX64Test" }.configureEach { dependsOn(copy) }
        }
    }

    private fun registerAndroidBuilds() {
        val ndkVersion = "27.2.12479018"
        val ndk = resolveAndroidNdk(project, ndkVersion)
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

            for (abi in androidAbis) {
                val abiId = abi.replace("-", "")
                val install = installDirs.getValue("android-$abi")
                val prepare = prepareTasks.getValue("android-$abi")
                val jniTask = project.tasks.register<CMakeBuildTask>("buildJniAndroid$abiId") {
                    dependsOn(prepare)
                    sourceDir.set(project.layout.projectDirectory.dir("src/jvmMain/cpp"))
                    cmakeExecutable.set(toolchain.cmake.absolutePath)
                    generator.set("Ninja")
                    configureArgs.set(
                        listOf("-DCMAKE_BUILD_TYPE=Release") +
                            androidArgs(abi) +
                            "-DNCNN_INSTALL_DIR=${install.get().asFile.absolutePath}",
                    )
                    extraInputs.from(install)
                    installDir.set(project.layout.buildDirectory.dir("ncnn/android-jni-$abi/install"))
                    buildDir.set(project.layout.buildDirectory.dir("ncnn/android-jni-$abi/build"))
                }

                copies += project.tasks.register<Sync>("copyAndroidJni$abiId") {
                    dependsOn(jniTask)
                    from(jniTask.flatMap { it.installDir }.map { it.file("lib/libncnn_jni.so") })
                    from(install.map { it.file("lib/libncnn.so") })
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

    private fun msvcRuntimeDlls(): List<File> {
        val root = System.getenv("SystemRoot") ?: "C:\\Windows"
        return listOf(
            "msvcp140.dll",
            "vcruntime140.dll",
            "vcruntime140_1.dll",
            "vcomp140.dll",
        ).map { File("$root/System32/$it") }.filter { it.exists() }
    }
}

private fun suffix(id: String): String = id.split('-').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
