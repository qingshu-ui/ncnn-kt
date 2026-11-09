package io.github.qingshu.ncnn

import java.io.File
import java.nio.file.Files

/**
 * Loads the JNI bridge once per process.
 *
 * Resolution order: `ncnn.jni.path` system property, `NCNN_JNI_PATH` environment
 * variable, `System.loadLibrary("ncnn_jni")`, then the bundled resource for the
 * current OS/architecture (JVM desktop artifacts). On Windows the bundled
 * `libwinpthread-1.dll` is preloaded before the JNI library.
 */
internal object NativeLoader {
    private val loaded: Unit by lazy {
        val explicit = System.getProperty("ncnn.jni.path")
            ?: System.getenv("NCNN_JNI_PATH")
        if (!explicit.isNullOrBlank()) {
            System.load(File(explicit).absolutePath)
            return@lazy
        }

        try {
            System.loadLibrary("ncnn_jni")
            return@lazy
        } catch (_: UnsatisfiedLinkError) {
            // Fall through to the bundled library.
        }

        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        val os = when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "macos"
            else -> "linux"
        }
        val arch = when (osArch) {
            "amd64", "x86_64", "x64" -> "x64"
            "aarch64", "arm64" -> "arm64"
            else -> osArch
        }
        val libraryName = when (os) {
            "windows" -> "ncnn_jni.dll"
            "macos" -> "libncnn_jni.dylib"
            else -> "libncnn_jni.so"
        }
        val resourceDir = "/io/github/qingshu/ncnn/native/$os-$arch"
        val tempDir = Files.createTempDirectory("ncnn_jni").toFile().apply { deleteOnExit() }

        if (os == "windows") {
            // The official ncnn DLL is MSVC-built; load its runtime first when bundled.
            for (name in listOf("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll", "vcomp140.dll")) {
                loadBundled("$resourceDir/$name", tempDir, required = false)
            }
            loadBundled("$resourceDir/libwinpthread-1.dll", tempDir, required = false)
            loadFirstAvailable(
                listOf("$resourceDir/ncnn.dll", "$resourceDir/libncnn.dll"),
                tempDir,
                description = "ncnn runtime DLL",
            )
        } else if (os == "linux") {
            loadBundled("$resourceDir/libncnn.so.1", tempDir, required = false)
            loadBundled("$resourceDir/libncnn.so", tempDir, required = false)
        }
        loadBundled("$resourceDir/$libraryName", tempDir, required = true)
    }

    private fun loadFirstAvailable(resources: List<String>, tempDir: File, description: String) {
        for (resource in resources) {
            val stream = NativeLoader::class.java.getResourceAsStream(resource)
            if (stream != null) {
                val file = File(tempDir, resource.substringAfterLast('/')).apply { deleteOnExit() }
                stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                System.load(file.absolutePath)
                return
            }
        }
        error("$description not found; looked for $resources")
    }

    private fun loadBundled(resource: String, tempDir: File, required: Boolean) {
        val stream = NativeLoader::class.java.getResourceAsStream(resource)
        if (stream == null) {
            if (required) {
                error(
                    "ncnn JNI library not found. Set -Dncnn.jni.path=<library> or put the library " +
                        "on java.library.path. Bundled resource $resource is missing.",
                )
            }
            return
        }
        val file = File(tempDir, resource.substringAfterLast('/')).apply { deleteOnExit() }
        stream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        System.load(file.absolutePath)
    }

    fun load() {
        loaded
    }
}
