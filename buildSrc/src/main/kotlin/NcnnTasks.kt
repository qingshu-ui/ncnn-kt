package ncnn.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import javax.inject.Inject

/** Downloads a pinned artifact and verifies its SHA-256 digest. */
@CacheableTask
abstract class DownloadFileTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun download() {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        val expected = sha256.get().lowercase()

        if (target.isFile && target.sha256() == expected) {
            return
        }

        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()

        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofMinutes(2))
            .build()
        val request = HttpRequest.newBuilder(URI.create(url.get()))
            .timeout(java.time.Duration.ofMinutes(10))
            .header("User-Agent", "ncnn-kt-build")
            .GET()
            .build()
        client.send(request, HttpResponse.BodyHandlers.ofFile(partial.toPath()))

        val actual = partial.sha256()
        check(actual == expected) {
            "SHA-256 mismatch for ${url.get()}: expected $expected but got $actual"
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }
}

/**
 * Configures, builds and installs a CMake project with a declared install prefix.
 * The install directory is the cacheable output; the build directory is scratch.
 */
@CacheableTask
abstract class CMakeBuildTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val cmakeExecutable: Property<String>

    @get:Input
    abstract val generator: Property<String>

    @get:Input
    abstract val configureArgs: ListProperty<String>

    @get:Input
    abstract val environment: MapProperty<String, String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val extraInputs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val installDir: DirectoryProperty

    @get:Internal
    abstract val buildDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun build() {
        val source = sourceDir.get().asFile
        val build = buildDir.get().asFile
        val install = installDir.get().asFile
        val cmake = cmakeExecutable.get()
        val env = environment.get()

        build.deleteRecursively()
        build.mkdirs()
        install.deleteRecursively()
        install.mkdirs()

        fun ExecSpec.base() {
            workingDir = build
            environment.putAll(env)
        }

        execOperations.exec {
            base()
            commandLine(
                buildList {
                    add(cmake)
                    add("-S"); add(source.absolutePath)
                    add("-B"); add(build.absolutePath)
                    add("-G"); add(generator.get())
                    add("-DCMAKE_INSTALL_PREFIX=${install.absolutePath}")
                    addAll(configureArgs.get())
                },
            )
        }

        execOperations.exec {
            base()
            commandLine(cmake, "--build", build.absolutePath, "--parallel")
        }

        execOperations.exec {
            base()
            commandLine(cmake, "--install", build.absolutePath, "--prefix", install.absolutePath)
        }
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
