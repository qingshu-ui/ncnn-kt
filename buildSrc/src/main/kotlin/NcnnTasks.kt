package ncnn.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

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
