package ncnn.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/** Copies the ncnn JNI bridge and runtime into `outputDir/<abi>/` for Android packaging. */
@DisableCachingByDefault(because = "Copies native libraries into a generated source directory")
abstract class SyncJniLibsTask : DefaultTask() {
    @get:InputFiles
    abstract val libraries: ConfigurableFileCollection

    @get:Input
    abstract val abi: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val abiDir = File(outputDir.get().asFile, abi.get()).apply { mkdirs() }
        libraries.files.forEach { file ->
            file.copyTo(File(abiDir, file.name), overwrite = true)
        }
    }
}
