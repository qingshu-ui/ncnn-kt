package ncnn.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.Serializable
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** One official ncnn release asset and the sub directory it contributes to `libs/<platform>`. */
data class PrebuiltAsset(
    val assetName: String,
    val sha256: String,
    val subDir: String,
) : Serializable

/**
 * Downloads the pinned ncnn prebuilt release packages and normalizes them into
 * `libs/<platform>`: the three cinterop headers, the link libraries and, on
 * Windows, the runtime DLL. Linux soname links are recreated as symlinks.
 */
@DisableCachingByDefault(because = "Downloads from the network into the shared libs directory")
abstract class DownloadNcnnTask : DefaultTask() {
    @get:Input
    abstract val ncnnVersion: Property<String>

    /** Platform id to release asset. */
    @get:Input
    abstract val assets: MapProperty<String, PrebuiltAsset>

    @get:OutputDirectory
    abstract val libsDir: DirectoryProperty

    @get:LocalState
    abstract val downloadDir: DirectoryProperty

    @TaskAction
    fun download() {
        val libs = libsDir.get().asFile
        val cache = downloadDir.get().asFile.apply { mkdirs() }
        val baseUrl = "https://github.com/Tencent/ncnn/releases/download/${ncnnVersion.get()}"

        assets.get().forEach { (platform, asset) ->
            val target = File(libs, platform)
            if (isComplete(platform, target)) return@forEach

            val zip = File(cache, asset.assetName)
            if (!zip.isFile || sha256(zip) != asset.sha256) {
                logger.lifecycle("Downloading ${asset.assetName} ...")
                URI.create("$baseUrl/${asset.assetName}").toURL().openStream().use { input ->
                    zip.outputStream().use { output -> input.copyTo(output) }
                }
            }
            check(sha256(zip) == asset.sha256) { "SHA-256 mismatch for ${asset.assetName}" }
            extract(zip, asset.assetName.removeSuffix(".zip"), asset.subDir, target, platform)
        }
    }

    private fun isComplete(platform: String, target: File): Boolean {
        if (!File(target, "include/ncnn/c_api.h").isFile) return false
        if (!File(target, "lib/libncnn.so").exists() && !File(target, "lib/ncnn.lib").isFile) return false
        return platform != "windows-x64" || File(target, "bin/ncnn.dll").isFile
    }

    private fun extract(zip: File, root: String, subDir: String, target: File, platform: String) {
        val includeDir = File(target, "include/ncnn").apply { mkdirs() }
        File(target, "lib").mkdirs()
        if (platform == "windows-x64") File(target, "bin").mkdirs()

        ZipFile(zip).use { zipFile ->
            val prefix = if (subDir.isEmpty()) "$root/" else "$root/$subDir/"
            zipFile.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(prefix) }
                .forEach { entry ->
                    when (val rel = entry.name.removePrefix(prefix)) {
                        "lib/libncnn.so", "lib/libncnn.so.1" -> Unit // recreated as symlinks below
                        "lib/ncnn.lib", "bin/ncnn.dll" -> copyEntry(zipFile, entry, File(target, rel))
                        else -> {
                            if (rel.startsWith("include/ncnn/")) {
                                val name = rel.removePrefix("include/ncnn/")
                                if (name in HEADERS) copyEntry(zipFile, entry, File(includeDir, name))
                            } else if (rel.startsWith("lib/libncnn.so.")) {
                                copyEntry(zipFile, entry, File(target, rel))
                            }
                        }
                    }
                }
        }

        if (platform == "linux-x64") createUnixSymlinks(File(target, "lib"))
    }

    private fun copyEntry(zip: ZipFile, entry: ZipEntry, output: File) {
        output.parentFile.mkdirs()
        zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
    }

    private fun createUnixSymlinks(libDir: File) {
        val versioned = libDir.listFiles()
            ?.firstOrNull { it.name.matches(Regex("libncnn\\.so\\.\\d+\\.\\d+\\.\\d+")) }
            ?: return
        listOf("libncnn.so.1" to versioned.name, "libncnn.so" to "libncnn.so.1").forEach { (name, linkTarget) ->
            val link = File(libDir, name)
            if (link.exists()) link.delete()
            try {
                Files.createSymbolicLink(link.toPath(), Paths.get(linkTarget))
            } catch (_: Exception) {
                File(libDir, linkTarget).copyTo(link, overwrite = true)
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val HEADERS = setOf("c_api.h", "platform.h", "ncnn_export.h")
    }
}
