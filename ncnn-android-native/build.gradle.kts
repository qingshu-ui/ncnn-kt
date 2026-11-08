plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.qingshu.ncnn.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    sourceSets["main"].jniLibs.srcDir(
        rootProject.layout.buildDirectory.dir("ncnn/android/jniLibs").get().asFile,
    )
}

// The .so files are produced by the :ncnn module (NDK builds per ABI) so that a
// single toolchain description drives both the KMP Android target and this AAR.
tasks.named("preBuild") {
    dependsOn(":ncnn:buildAndroidJni")
}
