# ncnn-kt

Kotlin Multiplatform bindings for [Tencent ncnn](https://github.com/Tencent/ncnn), the high-performance
neural network inference framework for mobile and desktop.

The library wraps ncnn's C API with a small Kotlin API and ships the native runtime, so a JVM or Android
application only needs one dependency. Kotlin/Native targets use cinterop against the same ncnn release.

## Features

- `Net` / `Extractor` for loading `.param` and `.bin` models, from a file or from memory.
- `Mat` tensors: creation, pixels in and out, resizing, packing, float and byte access, mean/normalize.
- `Ncnn` version information and `NcnnException` for failures reported by ncnn.
- JVM jar with `windows-x64` and `linux-x64` natives bundled.
- Android AAR with `arm64-v8a`, `armeabi-v7a` and `x86_64` `jniLibs`.
- Kotlin/Native `mingwX64` and `linuxX64` targets.
- Tested inference on JVM, MinGW and Linux, including a three-layer model embedded in the test suite.

## Modules

| Module | Purpose |
| --- | --- |
| [`ncnn-core`](ncnn-core) | Public API: `Ncnn`, `NcnnException`, `Mat`, `Net`, `Extractor`, `PixelFormat` |
| [`ncnn-jvm-jni`](ncnn-jvm-jni) | JNI bridge for the JVM, bundles the desktop ncnn runtime |
| [`ncnn-android-jni`](ncnn-android-jni) | JNI bridge for Android, packages the ABI `jniLibs` |
| [`ncnn-binding`](ncnn-binding) | cinterop facade over ncnn's C API for Kotlin/Native |

`ncnn-core` depends on the bridges; you normally declare only `ncnn-core`.

## Installation

```kotlin
dependencies {
    implementation("io.github.qingshu-ui:ncnn-core:1.0.0")
}
```

For a Kotlin Multiplatform project, add it to the source set that uses the API:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.qingshu-ui:ncnn-core:1.0.0")
        }
    }
}
```

Gradle resolves the JVM, Android or Kotlin/Native variant for each target. The published
`ncnn-jvm-jni`, `ncnn-android-jni` and `ncnn-binding` artifacts are pulled in transitively.

### Platform support

| Platform | Bundled ncnn runtime |
| --- | --- |
| JVM, Windows x64 | `ncnn.dll` |
| JVM, Linux x64 | `libncnn.so.1` |
| Android | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Kotlin/Native `mingwX64`, `linuxX64` | link against the ncnn SDK (see below) |

The JVM jar is host-built: a Windows release contains the Windows and Linux natives, a Linux release
only the Linux ones. The Android AAR always contains all three ABIs.

Kotlin/Native artifacts are klibs, not native binaries. Consumers of the `mingwX64` / `linuxX64`
targets must provide the matching ncnn prebuilt SDK (`libs/<platform>`) on the cinterop and link path.

### Choosing the ncnn release for native targets

A klib only carries Kotlin metadata, so the application links the C library itself. Each ncnn-kt
release is built against exactly one ncnn release:

| ncnn-kt | ncnn |
| --- | --- |
| 1.0.0 | 20261020 |

For `1.0.0`, use the `20261020` assets:

| Target | Asset |
| --- | --- |
| `mingwX64` | `ncnn-20261020-windows-vs2022-shared.zip` |
| `linuxX64` | `ncnn-20261020-ubuntu-2404-shared.zip` |

Download them from <https://github.com/Tencent/ncnn/releases/tag/20261020>, verify the SHA-256:

```
windows-x64  7f39107af5ee4c1e657b8fd01e8b3cad6c0ab7fdbb0713532f01937134285b3d
linux-x64    996e07a787550b782dd5891a2a60d7fa5d8f84452bb2f244ce0f3f9c01417842
android      c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87
```

and extract them so that `libs/<platform>/include/ncnn` and `libs/<platform>/lib` exist. At runtime,
`Ncnn.versionNumber` returns the pinned value (`20261020`), so a mismatched native library is visible
immediately.

The pinned values are defined in
[`NcnnSdkPlugin.kt`](buildSrc/src/main/kotlin/NcnnSdkPlugin.kt); the `downloadNcnn` task performs the
same download for this repository.

## Quick start

```kotlin
import io.github.qingshu.ncnn_core.Mat
import io.github.qingshu.ncnn_core.Ncnn
import io.github.qingshu.ncnn_core.Net
import io.github.qingshu.ncnn_core.PixelFormat

fun classify(rgbPixels: ByteArray): FloatArray {
    println("ncnn ${Ncnn.version}") // e.g. 1.0.20261020

    Net.create().use { net ->
        net.numThreads = 4
        net.loadParam("model.param")
        net.loadModel("model.bin")

        net.createExtractor().use { extractor ->
            Mat.fromPixels(rgbPixels, PixelFormat.RGB, w = 224, h = 224).use { input ->
                extractor.input("data", input)
                extractor.extract("output").use { output ->
                    return output.readFloats()
                }
            }
        }
    }
}
```

`Net`, `Extractor` and `Mat` own native memory and must be closed; every type implements `AutoCloseable`
so Kotlin's `use { }` works. A `Net` is not thread-safe.

Loading from memory, for models packaged as resources:

```kotlin
net.loadParam(paramBytes)
net.loadModel(modelBytes)
```

Extractors also accept and return inputs by index (`input(0, mat)`, `extract(0)`) and can be reused for
several inferences while their `Net` is open.

## Building from source

Requirements:

- JDK 21
- Android SDK with platform `android-37.0` and build-tools `37.0.0` (Android modules)
- Kotlin/Native toolchain is downloaded by Gradle on first use

```bash
./gradlew build
```

The pinned ncnn release (`20261020`) is downloaded into `libs/<platform>` by the `downloadNcnn` task,
which the cinterop and link tasks depend on. `libs/` is not tracked by Git.

Useful tasks:

```bash
./gradlew :ncnn-core:jvmTest          # JVM tests
./gradlew :ncnn-core:mingwX64Test     # Kotlin/Native MinGW tests
./gradlew :ncnn-core:linuxX64Test     # Kotlin/Native Linux tests
./gradlew :ncnn-android-jni:bundleAndroidMainAar
./gradlew spotlessCheck
```

## License

This project is licensed under the BSD 3-Clause License; see [LICENSE.txt](LICENSE.txt).

ncnn itself is Copyright (C) 2017 Tencent and is distributed under the BSD 3-Clause License, with
third-party components under their own licenses. See the
[ncnn repository](https://github.com/Tencent/ncnn) for details.
