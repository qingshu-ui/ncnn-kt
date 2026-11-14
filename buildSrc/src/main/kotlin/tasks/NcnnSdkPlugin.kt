package ncnn.tasks

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Registers `downloadNcnn` so the main build can resolve the ncnn SDK before cinterop/link. */
class NcnnSdkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("downloadNcnn", DownloadNcnnTask::class.java) {
            group = "ncnn"
            description = "Downloads the pinned ncnn prebuilt SDKs into libs/<platform>."
            ncnnVersion.set(NCNN_VERSION)
            assets.set(DEFAULT_ASSETS)
            libsDir.set(project.layout.projectDirectory.dir("libs"))
            downloadDir.set(project.layout.buildDirectory.dir("ncnn-download"))
        }
    }

    private companion object {
        const val NCNN_VERSION = "20261020"

        val DEFAULT_ASSETS = mapOf(
            "windows-x64" to PrebuiltAsset(
                assetName = "ncnn-20261020-windows-vs2022-shared.zip",
                sha256 = "7f39107af5ee4c1e657b8fd01e8b3cad6c0ab7fdbb0713532f01937134285b3d",
                subDir = "x64",
            ),
            "linux-x64" to PrebuiltAsset(
                assetName = "ncnn-20261020-ubuntu-2404-shared.zip",
                sha256 = "996e07a787550b782dd5891a2a60d7fa5d8f84452bb2f244ce0f3f9c01417842",
                subDir = "",
            ),
            "android-arm64-v8a" to PrebuiltAsset(
                assetName = "ncnn-20261020-android-shared.zip",
                sha256 = "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
                subDir = "arm64-v8a",
            ),
            "android-armeabi-v7a" to PrebuiltAsset(
                assetName = "ncnn-20261020-android-shared.zip",
                sha256 = "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
                subDir = "armeabi-v7a",
            ),
            "android-x86_64" to PrebuiltAsset(
                assetName = "ncnn-20261020-android-shared.zip",
                sha256 = "c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87",
                subDir = "x86_64",
            ),
        )
    }
}
