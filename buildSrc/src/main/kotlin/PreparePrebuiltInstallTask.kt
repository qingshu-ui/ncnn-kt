package ncnn.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Turns an extracted official ncnn release package into the install layout the module
 * expects: `include/ncnn`, `lib/` and (on Windows) `bin/`.
 */
abstract class PreparePrebuiltInstallTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rawDir: DirectoryProperty

    /** One of `windows`, `linux`, `android`, `apple`. */
    @get:Input
    abstract val layout: Property<String>

    /** Android ABI subdirectory, empty otherwise. */
    @get:Input
    abstract val subDir: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val raw = rawDir.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        when (layout.get()) {
            "windows" -> {
                // The Windows package contains arm64 and x64 trees; use the x64 one.
                val root = raw.walkTopDown()
                    .maxDepth(3)
                    .firstOrNull {
                        it.isDirectory && it.name == "x64" && File(it, "include/ncnn/c_api.h").isFile
                    }
                    ?: findContaining(raw, "include/ncnn/c_api.h")
                copyInto(root, out, "include", "lib", "bin")
            }

            "linux" -> {
                val root = findContaining(raw, "include/ncnn/c_api.h")
                copyInto(root, out, "include", "lib")
                // Zip extraction turns the .so symlinks into empty files; replace them
                // with real copies so -lncnn and the runtime lookup both work.
                val lib = File(out, "lib")
                val real = lib.listFiles()?.firstOrNull {
                    it.name.startsWith("libncnn.so.1.") && it.length() > 0
                }
                if (real != null) {
                    real.copyTo(File(lib, "libncnn.so.1"), overwrite = true)
                    real.copyTo(File(lib, "libncnn.so"), overwrite = true)
                }
            }

            "android" -> {
                val abiDir = raw.walkTopDown()
                    .maxDepth(3)
                    .firstOrNull {
                        it.isDirectory && it.name == subDir.get() && File(it, "include/ncnn/c_api.h").isFile
                    }
                    ?: error("Android ABI ${subDir.get()} not found under $raw")
                copyInto(abiDir, out, "include", "lib")
            }

            "apple" -> {
                val framework = findContaining(raw, "Headers/ncnn/c_api.h")
                File(framework, "Headers/ncnn").copyRecursively(File(out, "include/ncnn"), overwrite = true)
                File(out, "lib").mkdirs()
                findFile(raw, "cnn")?.copyTo(File(out, "lib/libncnn.a"), overwrite = true)
                findFile(raw, "openmp")?.copyTo(File(out, "lib/libopenmp.a"), overwrite = true)
            }

            else -> error("unknown prebuilt layout ${layout.get()}")
        }
    }

    private fun findContaining(root: File, relative: String): File {
        if (File(root, relative).isFile) return root
        return root.walkTopDown()
            .maxDepth(4)
            .firstOrNull { it.isDirectory && File(it, relative).isFile }
            ?: error("$relative not found under $root")
    }

    private fun findFile(root: File, name: String): File? = root.walkTopDown()
        .maxDepth(6)
        .firstOrNull { it.isFile && it.name == name && it.length() > 0 }

    private fun copyInto(root: File, out: File, vararg names: String) {
        for (name in names) {
            val source = File(root, name)
            if (source.exists()) source.copyRecursively(File(out, name), overwrite = true)
        }
    }
}
