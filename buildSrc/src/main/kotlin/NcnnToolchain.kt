package ncnn.build

import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Resolved native toolchain used to build ncnn and the JNI shared library.
 *
 * Resolution order for every path is: Gradle property, `local.properties`, environment
 * variable, `PATH`, then a platform specific fallback (for example the MinGW toolchain
 * bundled with Kotlin/Native).
 */
data class NcnnToolchain(
    val platformId: String,
    val os: String,
    val arch: String,
    val cmake: File,
    val ninja: File,
    val cCompiler: File,
    val cxxCompiler: File,
    val mingwLibDir: File?,
    val gccLibDirs: List<File>,
    val cinteropLinkerOpts: List<String>,
    val jniLinkerOpts: List<String>,
) {
    val isWindows: Boolean get() = os == "windows"
    val isLinux: Boolean get() = os == "linux"
    val isMacos: Boolean get() = os == "macos"

    /** Path separator used by CMake lists on this host. */
    val listSeparator: String get() = if (isWindows) ";" else ":"
}

fun resolveNcnnToolchain(project: Project): NcnnToolchain {
    val props = readLocalProperties(project.rootDir)

    fun config(key: String, env: String): String? {
        val fromProject = project.findProperty(key) as String?
        if (!fromProject.isNullOrBlank()) return fromProject
        val fromLocal = props.getProperty(key)
        if (!fromLocal.isNullOrBlank()) return fromLocal
        val fromEnv = System.getenv(env)
        if (!fromEnv.isNullOrBlank()) return fromEnv
        return null
    }

    val osName = System.getProperty("os.name").lowercase()
    val os = when {
        "win" in osName -> "windows"
        "mac" in osName -> "macos"
        else -> "linux"
    }
    val archName = System.getProperty("os.arch").lowercase()
    val arch = when (archName) {
        "amd64", "x86_64", "x64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> archName
    }
    val platformId = "$os-$arch"

    val cmake = resolveExecutable(
        candidates = listOfNotNull(
            config("ncnn.cmake", "NCNN_CMAKE")?.let(::File),
            findOnPath("cmake.exe") ?: findOnPath("cmake"),
        ),
        what = "CMake",
        key = "ncnn.cmake / NCNN_CMAKE",
    )

    val ninjaCandidates = listOfNotNull(
        config("ncnn.ninja", "NCNN_NINJA")?.let(::File),
        findOnPath("ninja.exe") ?: findOnPath("ninja"),
    )
    val ninja = resolveExecutable(ninjaCandidates, "Ninja", "ncnn.ninja / NCNN_NINJA")

    val mingwBin = config("ncnn.mingw.bin", "NCNN_MINGW_BIN")?.let(::File)

    val (cc, cxx, mingwRoot) = when (os) {
        "windows" -> {
            val binDir = when {
                mingwBin != null -> mingwBin
                findOnPath("g++.exe") != null -> findOnPath("g++.exe")!!.parentFile
                else -> konanMingwBin() ?: error(
                    "No MinGW-w64 toolchain found. Set ncnn.mingw.bin in local.properties " +
                        "or the NCNN_MINGW_BIN environment variable to the directory containing gcc.exe/g++.exe.",
                )
            }
            Triple(
                requireFile(File(binDir, "gcc.exe"), "MinGW gcc"),
                requireFile(File(binDir, "g++.exe"), "MinGW g++"),
                binDir.parentFile,
            )
        }
        "macos" -> {
            val ccCandidate = config("ncnn.cc", "NCNN_CC")?.let(::File)
                ?: findOnPath("clang")
                ?: error("clang not found; set ncnn.cc / NCNN_CC")
            val cxxCandidate = config("ncnn.cxx", "NCNN_CXX")?.let(::File)
                ?: findOnPath("clang++")
                ?: File(ccCandidate.parentFile, "clang++").takeIf { it.isFile }
                ?: error("clang++ not found; set ncnn.cxx / NCNN_CXX")
            Triple(ccCandidate, cxxCandidate, null)
        }
        else -> {
            val ccCandidate = config("ncnn.cc", "NCNN_CC")?.let(::File)
                ?: findOnPath("gcc")
                ?: findOnPath("cc")
                ?: error("gcc not found; set ncnn.cc / NCNN_CC")
            val cxxName = ccCandidate.name.replace("gcc", "g++").replace(Regex("(^|-)cc$"), "$1c++")
            val cxxCandidate = config("ncnn.cxx", "NCNN_CXX")?.let(::File)
                ?: File(ccCandidate.parentFile, cxxName).takeIf { it.exists() }
                ?: findOnPath("g++")
                ?: findOnPath("c++")
                ?: error("g++ not found; set ncnn.cxx / NCNN_CXX")
            Triple(ccCandidate, cxxCandidate, null)
        }
    }

    val mingwLibDir = mingwRoot?.let { File(it, "x86_64-w64-mingw32/lib") }
    val gccLibDirs = when (os) {
        "windows" -> {
            val root = requireNotNull(mingwRoot) { "MinGW root not resolved" }
            val gccRoot = File(root, "lib/gcc/x86_64-w64-mingw32")
            gccRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.reversed().orEmpty() +
                listOfNotNull(mingwLibDir, File(root, "lib"))
        }
        "linux" -> {
            val triple = if (arch == "arm64") "aarch64-linux-gnu" else "x86_64-linux-gnu"
            val dirs = mutableListOf<File>()
            val gccRoot = File("/usr/lib/gcc/$triple")
            gccRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.reversed()?.let { dirs += it }
            listOfNotNull("/usr/lib/$triple", "/usr/lib64", "/usr/lib").forEach { dirs += File(it) }
            dirs.filter { it.isDirectory }.distinct()
        }
        else -> emptyList()
    }

    val cinteropLinkerOpts = when (os) {
        "windows" -> gccLibDirs.map { "-L${it.absolutePath}" } + listOf("-lstdc++")
        "linux" -> gccLibDirs.map { "-L${it.absolutePath}" } + listOf("-lstdc++", "-lpthread", "-ldl", "-lm")
        else -> listOf("-lc++")
    }

    val jniLinkerOpts = when (os) {
        "windows" -> listOf("-static", "-static-libgcc", "-static-libstdc++")
        "linux" -> listOf("-static-libgcc", "-static-libstdc++")
        else -> listOf("-lc++")
    }

    return NcnnToolchain(
        platformId = platformId,
        os = os,
        arch = arch,
        cmake = cmake,
        ninja = ninja,
        cCompiler = cc,
        cxxCompiler = cxx,
        mingwLibDir = mingwLibDir,
        gccLibDirs = gccLibDirs,
        cinteropLinkerOpts = cinteropLinkerOpts,
        jniLinkerOpts = jniLinkerOpts,
    )
}

fun resolveAndroidNdk(project: Project, requestedVersion: String): File? {
    val props = readLocalProperties(project.rootDir)
    val sdk = sequenceOf(
        project.findProperty("sdk.dir") as String?,
        props.getProperty("sdk.dir"),
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
    ).filterNotNull().map(::File).firstOrNull { it.isDirectory }
    val ndkHome = sequenceOf(
        project.findProperty("ncnn.ndk") as String?,
        props.getProperty("ncnn.ndk"),
        System.getenv("ANDROID_NDK_HOME"),
        sdk?.resolve("ndk/$requestedVersion")?.absolutePath,
    ).filterNotNull().map(::File).firstOrNull { it.isDirectory }
    return ndkHome
}

fun readLocalProperties(rootDir: File): Properties {
    val file = File(rootDir, "local.properties")
    return Properties().apply {
        if (file.isFile) file.reader().use { load(it) }
    }
}

private fun resolveExecutable(candidates: List<File>, what: String, key: String): File {
    val found = candidates.firstOrNull { it.isFile }
        ?: error("$what not found. Set $key (Gradle property or local.properties) to an executable path.")
    return found.absoluteFile
}

private fun requireFile(file: File, what: String): File {
    require(file.isFile) { "$what not found at ${file.absolutePath}" }
    return file.absoluteFile
}

private fun findOnPath(name: String): File? =
    System.getenv("PATH").orEmpty()
        .split(File.pathSeparatorChar)
        .asSequence()
        .filter { it.isNotBlank() }
        .map { File(it, name) }
        .firstOrNull { it.isFile }
        ?.absoluteFile

private fun konanMingwBin(): File? {
    val konanHome = File(System.getProperty("user.home"), ".konan/dependencies")
    if (!konanHome.isDirectory) return null
    return konanHome.listFiles()
        ?.filter { it.name.startsWith("msys2-mingw-w64-") }
        ?.sortedBy { it.name }
        ?.reversed()
        ?.asSequence()
        ?.map { File(it, "bin") }
        ?.firstOrNull { File(it, "g++.exe").isFile }
}

