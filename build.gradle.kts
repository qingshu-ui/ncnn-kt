import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.kotlin.dsl.configure
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}

val spotlessPlugin = libs.plugins.spotless.get().pluginId
allprojects {
    apply(plugin = spotlessPlugin)

    configure<SpotlessExtension> {
        val overrideEditConfig = mapOf(
            "ktlint_standard_package-name" to "disabled",
            "ktlint_standard_function-naming" to "disabled",
            "ktlint_standard_no-unused-imports" to "enabled",
            "ktlint_compose_modifier-missing-check" to "disabled",
            "ktlint_compose_compositionlocal-allowlist" to "disabled",
        )
        kotlin {
            target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
            ktlint().editorConfigOverride(overrideEditConfig)
            val composeProject = buildList<String> {
                // Put compose project name here.
            }
            if (this@allprojects.name in composeProject) {
                logger.info("project: ${this@allprojects.name}, add compose rule.")
                ktlint().customRuleSets(listOf("io.nlopez.compose.rules:ktlint:0.5.6"))
            }
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint().editorConfigOverride(overrideEditConfig)
        }
    }
}

private val ncnnAssets = mapOf(
    "windows-x64" to listOf(
        "ncnn-20261020-windows-vs2022-shared.zip",
        "7f39107af5ee4c1e657b8fd01e8b3cad6c0ab7fdbb0713532f01937134285b3d",
        "x64",
    ),
    "linux-x64" to listOf(
        "ncnn-20261020-ubuntu-2404-shared.zip",
        "996e07a787550b782dd5891a2a60d7fa5d8f84452bb2f244ce0f3f9c01417842",
        "",
    ),
    "android-arm64-v8a" to listOf(
        "ncnn-20261020-android-shared.zip",
        "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
        "arm64-v8a",
    ),
    "android-armeabi-v7a" to listOf(
        "ncnn-20261020-android-shared.zip",
        "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
        "armeabi-v7a",
    ),
    "android-x86_64" to listOf(
        "ncnn-20261020-android-shared.zip",
        "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
        "x86_64",
    ),
)

// Downloads the pinned ncnn prebuilt release packages and normalizes them into
// `libs/<platform>`: the three cinterop headers, the link libraries and, on
// Windows, the runtime DLL. Linux soname links are recreated as symlinks.
tasks.register("downloadNcnn") {
    group = "ncnn"
    description = "Downloads the pinned ncnn prebuilt SDKs into libs/<platform>."
    inputs.property("version", "20261020")
    inputs.property("assets", ncnnAssets)
    outputs.dir(layout.projectDirectory.dir("libs"))

    doLast {
        @Suppress("UNCHECKED_CAST")
        val assets = inputs.properties["assets"] as Map<String, List<String>>
        val version = inputs.properties["version"] as String
        val libs = outputs.files.singleFile
        val cache = File(libs.parentFile, "build/ncnn-download").apply { mkdirs() }
        val headers = setOf("c_api.h", "platform.h", "ncnn_export.h")

        fun sha256(file: File): String {
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

        fun copyEntry(zip: ZipFile, entry: ZipEntry, output: File) {
            output.parentFile.mkdirs()
            zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
        }

        fun createUnixSymlinks(libDir: File) {
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

        fun isComplete(platform: String, target: File): Boolean {
            if (!File(target, "include/ncnn/c_api.h").isFile) return false
            if (!File(target, "lib/libncnn.so").exists() && !File(target, "lib/ncnn.lib").isFile) return false
            return platform != "windows-x64" || File(target, "bin/ncnn.dll").isFile
        }

        fun extract(zip: File, root: String, subDir: String, target: File, platform: String) {
            val includeDir = File(target, "include/ncnn").apply { mkdirs() }
            File(target, "lib").mkdirs()
            if (platform == "windows-x64") File(target, "bin").mkdirs()

            ZipFile(zip).use { zipFile ->
                val prefix = if (subDir.isEmpty()) "$root/" else "$root/$subDir/"
                zipFile.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(prefix) }
                    .forEach { entry ->
                        when (val rel = entry.name.removePrefix(prefix)) {
                            "lib/libncnn.so", "lib/libncnn.so.1" -> Unit

                            // recreated as symlinks below
                            "lib/ncnn.lib", "bin/ncnn.dll" -> copyEntry(zipFile, entry, File(target, rel))

                            else -> {
                                if (rel.startsWith("include/ncnn/")) {
                                    val name = rel.removePrefix("include/ncnn/")
                                    if (name in headers) copyEntry(zipFile, entry, File(includeDir, name))
                                } else if (rel.startsWith("lib/libncnn.so.")) {
                                    copyEntry(zipFile, entry, File(target, rel))
                                }
                            }
                        }
                    }
            }

            if (platform == "linux-x64") createUnixSymlinks(File(target, "lib"))
        }

        val baseUrl = "https://github.com/Tencent/ncnn/releases/download/$version"

        assets.forEach { (platform, spec) ->
            val assetName = spec[0]
            val sha = spec[1]
            val subDir = spec[2]
            val target = File(libs, platform)
            if (isComplete(platform, target)) return@forEach

            val zip = File(cache, assetName)
            if (!zip.isFile || sha256(zip) != sha) {
                logger.lifecycle("Downloading $assetName ...")
                URI.create("$baseUrl/$assetName").toURL().openStream().use { input ->
                    zip.outputStream().use { output -> input.copyTo(output) }
                }
            }
            check(sha256(zip) == sha) { "SHA-256 mismatch for $assetName" }
            extract(zip, assetName.removeSuffix(".zip"), subDir, target, platform)
        }
    }
}
