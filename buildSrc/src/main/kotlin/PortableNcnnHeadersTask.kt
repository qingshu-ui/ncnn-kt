package ncnn.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Produces a platform-neutral copy of ncnn's C API headers.
 *
 * Cinterop bindings are needed for every Kotlin/Native target so IDEs can resolve them,
 * but the real install prefix only exists for the host platform (and contains
 * platform-specific `ncnn_export.h`). This task rewrites the tiny generated headers with
 * empty export macros and the C-API feature switches.
 */
@CacheableTask
abstract class PortableNcnnHeadersTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cApiHeader: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        cApiHeader.get().asFile.copyTo(File(out, "c_api.h"), overwrite = true)
        File(out, "ncnn_export.h").writeText(
            """
            #ifndef NCNN_EXPORT_H
            #define NCNN_EXPORT_H
            #define NCNN_EXPORT
            #endif
            """.trimIndent() + "\n",
        )
        File(out, "platform.h").writeText(
            """
            #ifndef NCNN_PLATFORM_H
            #define NCNN_PLATFORM_H
            #define NCNN_C_API 1
            #define NCNN_STDIO 1
            #define NCNN_STRING 1
            #define NCNN_THREADS 1
            #define NCNN_PIXEL 1
            #define NCNN_PIXEL_ROTATE 1
            #define NCNN_PIXEL_AFFINE 1
            #define NCNN_PIXEL_DRAWING 0
            #define NCNN_VULKAN 0
            #define NCNN_INT8 1
            #define NCNN_BF16 1
            #include <stddef.h>
            #include <stdio.h>
            #include "ncnn_export.h"
            #endif
            """.trimIndent() + "\n",
        )
    }
}
