# ncnn-kt: Kotlin Multiplatform bindings for ncnn

This document describes the design and usage of the `ncnn` module in this repository.
It supersedes the obsolete scratch documents in `tmp/` (in particular the claim there
that cinterop can bind ncnn's C++ headers — Kotlin/Native cinterop only binds C).

## Version pinning

- ncnn: **20261020** (`gradle/libs.versions.toml`, `ncnnVersion`), verified with the
  release asset SHA-256 stored in `ncnnSourceSha256`.
- Kotlin: **2.3.21**, Gradle: **9.4.0**, AGP: **9.0.0**, JDK: **21**.
- Android NDK: **27.2.12479018**.

The ncnn source archive is downloaded from the pinned GitHub release and verified before
it is unpacked into `ncnn/build/ncnn/src/<version>`. Set `ncnn.sourceUrl` in
`local.properties` (or `NCNN_SOURCE_URL`) when GitHub is not directly reachable, and
`ncnn.srcDir` to reuse an existing source tree.

## What is bound

Both backends call ncnn's **C API** (`src/c_api.h`), which covers everything exposed here:

| Kotlin | ncnn C API |
|---|---|
| `Ncnn.version` / `Ncnn.versionNumber` | `ncnn_version`, `ncnn_version_number` |
| `Net`, `Extractor`, `Mat` | `ncnn_net_*`, `ncnn_extractor_*`, `ncnn_mat_*` |
| `Net` option properties | `ncnn_option_get_*` / `ncnn_option_set_*` |
| Pixel conversion / resize | `ncnn_mat_from_pixels*`, `ncnn_mat_to_pixels*` |
| Packing / flatten / normalize | `ncnn_convert_packing`, `ncnn_flatten`, `ncnn_mat_substract_mean_normalize` |

Backends:

- **JVM / Android**: a thin JNI C shim (`ncnn/src/jvmMain/cpp/ncnn_jni.c`) over the C API.
- **Kotlin/Native**: cinterop over `c_api.h`, linked against a per-target static
  `libncnn.a`.

Layers are not bound individually. Layer functionality is reached by loading ncnn
`.param` / `.bin` models.

## Platform support

| Platform | Target | Backend | Status |
|---|---|---|---|
| JVM on Windows x64 | `jvm` | JNI | built and tested locally + CI |
| JVM on Linux x64 / arm64 | `jvm` | JNI | CI |
| JVM on macOS arm64 / x64 | `jvm` | JNI | CI |
| Windows x64 native | `mingwX64` | cinterop | built and tested locally + CI |
| Linux x64 / arm64 native | `linuxX64`, `linuxArm64` | cinterop | CI |
| macOS arm64 native | `macosArm64` | cinterop | CI |
| iOS device / simulator | `iosArm64`, `iosSimulatorArm64` | cinterop | CI (device slice link-only) |
| Android `arm64-v8a`, `armeabi-v7a`, `x86_64` | `android` | JNI via NDK in an AAR | CI build + emulator test |

Kotlin/Native targets are declared only on hosts that can build them (Windows →
`mingwX64`; Linux → `linuxX64` or `linuxArm64`; macOS → `macosArm64`, `iosArm64`,
`iosSimulatorArm64`). `macosX64` is deprecated/removed in Kotlin 2.3, but JVM on
Intel macOS is still supported by building the JNI library for `macos-x64`.

Explicitly out of scope for this version:

- **Vulkan / GPU**: ncnn is built with `NCNN_VULKAN=OFF`; the option API is not exposed.
- **Batch inference**: `Mat.n` is exposed read-only, but batch construction is not.
- **tvOS / watchOS / visionOS and iosX64**: the same cinterop model applies; only iOS and
  macOS are wired up.
- **WebAssembly / JS**: ncnn ships an Emscripten *static library*, not a JS API. Kotlin/Wasm
  cannot link C static libraries; a future binding needs an Emscripten side module with a
  JS bridge. Kotlin/JS cannot call native code.
- **HarmonyOS, Windows arm64/x86, Linux riscv64**: no matching Kotlin/Native target (or no
  toolchain) in this project.
- **Publishing/signing** to Maven Central is not configured.

## Build prerequisites

| Host | Required |
|---|---|
| Windows | JDK 21, CMake ≥ 3.20, Ninja, a complete MinGW-w64 GCC. The MSYS2 toolchain bundled under `~/.konan/dependencies` is **linker-only** (no `cc1`), so set `ncnn.mingw.bin` / `NCNN_MINGW_BIN` to a full MinGW (for example CLion's bundled toolchain) or put `g++.exe` on `PATH`. |
| Linux | JDK 21, CMake ≥ 3.20, Ninja, g++ |
| macOS | JDK 21, CMake ≥ 3.20, Ninja, Xcode command line tools |
| Android | Android SDK with `platforms;android-37.0`, `build-tools;37.0.0`, and NDK `27.2.12479018` |

Toolchain discovery order (Gradle property → `local.properties` → environment → `PATH`):

| Key | Environment | Meaning |
|---|---|---|
| `ncnn.cmake` | `NCNN_CMAKE` | `cmake` executable |
| `ncnn.ninja` | `NCNN_NINJA` | `ninja` executable |
| `ncnn.mingw.bin` | `NCNN_MINGW_BIN` | directory containing `gcc.exe` / `g++.exe` |
| `ncnn.ndk` | `ANDROID_NDK_HOME` | NDK root (default: `<sdk.dir>/ndk/27.2.12479018`) |
| — | `ANDROID_HOME` / `ANDROID_SDK_ROOT` | Android SDK root |

Additional Gradle properties:

- `-Pncnn.android=true|false` — enable/disable the Android target and modules (default `true`).
- `-Pncnn.android.abis=arm64-v8a,armeabi-v7a,x86_64` — Android ABI subset.
- `-Pncnn.srcDir=<path>` — use an existing ncnn source tree instead of downloading.
- `-Pncnn.sourceUrl=<url>` — alternate download URL for the pinned archive.

## Usage

```kotlin
import io.github.qingshu.ncnn.*

Net.create().use { net ->
    net.numThreads = 4
    net.loadParam("model.param")
    net.loadModel("model.bin")

    val input = Mat.fromPixels(rgbBytes, PixelFormat.RGB, width = 224, height = 224)
    try {
        val extractor = net.createExtractor()
        val output = try {
            extractor.input("data", input)
            extractor.extract("output")
        } finally {
            extractor.close()
        }
        output.use {
            val floats = it.readFloats()   // fp32 pack1
            // ...
        }
    } finally {
        input.close()
    }
}
```

`Mat.fromPixels(pixels, from, to, ...)` performs an ncnn pixel conversion while building
the tensor; `Mat.toPixels(format)` and `toPixelsResize(format, w, h)` convert back.
`substractMeanNormalize(mean, norm)` applies the usual ncnn normalization.

## Ownership and lifetime

- Every `Mat`, `Net` and `Extractor` owns a native handle and implements `AutoCloseable`.
  Use `use {}` or `close()`; `close()` is idempotent.
- `Mat.reshape`, `clone`, `convertPacking` and `flatten` return **new** tensors that must be
  closed separately. `reshape`, `convertPacking` and `flatten` share data with the source.
- `Extractor.input(name, mat)` copies a reference-counted tensor header, so the input `Mat`
  may be closed immediately after `input`.
- `Net.close()` closes all extractors that are still open; using an extractor after its net
  was closed throws `IllegalStateException`.
- `Net.clear()` throws if extractors are still open.
- `Net` and `Extractor` are **not thread-safe**. Do not share them between threads. Create
  one extractor per thread (and a net per thread for fully independent execution).

## Errors

- ncnn failures surface as `NcnnException(message, code)`. `code` is the ncnn status
  (usually `-1`, or `-100` for allocation/layer failures).
- `loadModel(memory)` returning 0 for a non-empty model blob is treated as a failure.
- Invalid arguments throw `IllegalArgumentException`; closed handles throw
  `IllegalStateException`; allocation failures throw `OutOfMemoryError` where ncnn reports
  them.
- `readFloats` / `writeFloats` require fp32 pack1 data. Extractor outputs are unpacked to
  pack1 by ncnn; for custom tensors use `convertPacking(1)` (and disable fp16/bf16 storage
  when running on ARM).

## Native library layout

- Windows and Linux build ncnn as a **shared library** (`libncnn.dll` + import library on
  Windows, `libncnn.so` with a stable soname on Linux). This keeps the C++ runtime and the
  host glibc out of the Kotlin/Native link, which uses its own sysroot.
  - Windows JVM: `ncnn_jni.dll` links the import library; `libncnn.dll` and
    `libwinpthread-1.dll` are bundled as resources and preloaded before the JNI library.
  - Windows Kotlin/Native: cinterop links the import library; `libncnn.dll` and
    `libwinpthread-1.dll` are copied next to the test/binary by Gradle
    (`copyMingwTestRuntime`). Consumers of the native library must ship these DLLs too.
  - Linux JVM: `libncnn_jni.so` links `libncnn.so`; both are bundled as resources and
    `libncnn.so` is preloaded first.
  - Linux Kotlin/Native: the test binary links `-lncnn` and uses
    `-Wl,-rpath,$ORIGIN`; `libncnn.so` is copied next to the test binary by Gradle.
    Consumers of the native library must ship `libncnn.so`.
- macOS/iOS build a static `libncnn.a`; the JNI library statically links it. The JNI
  library is bundled into the JVM resources under
  `io/github/qingshu/ncnn/native/<os>-<arch>/`.
  `NativeLoader` resolves, in order: `-Dncnn.jni.path`, `NCNN_JNI_PATH`,
  `System.loadLibrary("ncnn_jni")`, then the bundled resource.
- Android: `libncnn_jni.so` is packaged per ABI in `ncnn-android-native` (an AAR) and loaded
  with `System.loadLibrary`. The KMP Android target depends on that AAR at **runtime**
  (`runtimeOnly`), so Kotlin compilation does not require the NDK.

## ncnn build configuration

ncnn is built from source with:

```
-DNCNN_SHARED_LIB=ON (Windows, Linux) / OFF (macOS, iOS, Android)
-DNCNN_OPENMP=OFF -DNCNN_SIMPLEOMP=ON
-DNCNN_BUILD_TOOLS=OFF -DNCNN_BUILD_EXAMPLES=OFF -DNCNN_BUILD_BENCHMARK=OFF
-DNCNN_BUILD_TESTS=OFF -DNCNN_VULKAN=OFF -DNCNN_PIXEL=ON
-DNCNN_PIXEL_DRAWING=OFF -DNCNN_INSTALL_SDK=ON
-DCMAKE_POLICY_VERSION_MINIMUM=3.5
```

`CMAKE_POLICY_VERSION_MINIMUM` is required because ncnn still declares
`cmake_minimum_required(VERSION 2.8.12...3.10)`, which CMake 4.x rejects otherwise.
`NCNN_SIMPLEOMP` avoids an OpenMP runtime dependency; on Windows ncnn's `simpleomp.cpp`
additionally needs `-include pthread.h` because ncnn's `platform.h` does not include
`<pthread.h>` on Windows. The Windows shared build uses
`-DCMAKE_SHARED_LINKER_FLAGS=-static-libgcc -static-libstdc++`.

## Tests

`ncnn/src/commonTest` runs on every JVM and native target:

- version, Mat create/fill/clone/reshape/read/write, close semantics;
- pixel round-trip, RGB→GRAY conversion, resize, mean/normalize;
- end-to-end inference from memory with a generated `Input → Convolution(1×1, weight 2) →
  ReLU` network (`[-1, 2, -3, 4]` → `[0, 4, 0, 8]`);
- error paths for missing files and invalid param bytes.

The JVM and native suites additionally load files from temporary paths (the JVM test also
uses a non-ASCII file name to exercise the Windows `_w` path conversion). The Android
instrumentation test in `android-test-app` runs the same inference through the AAR on an
x86_64 emulator.

## Upgrading ncnn

1. Set `ncnnVersion` and `ncnnSourceSha256` in `gradle/libs.versions.toml`.
2. Merge any new C API declarations needed into `ncnn_jni.c` and the native wrappers.
3. Run the CI matrix; the ncnn source build cache keys include the version and sha256.

## What has been verified

Locally (Windows x64, JDK 21, Kotlin/Native 2.3.21, CMake 4.4.2, CLion MinGW GCC 15.2):

- `./gradlew :ncnn:jvmTest :ncnn:mingwX64Test` — all tests pass (also with the configuration
  cache enabled).
- `./gradlew :ncnn:compileAndroidMain` — the Android Kotlin target compiles.
- `./gradlew spotlessCheck` — formatting is clean.

CI (`.github/workflows/ci.yml`) is green for all six jobs:

- Windows x64: JVM + mingwX64 tests, JVM jar
- Linux x64 and Linux arm64: JVM + native tests
- macOS arm64: JVM + macOS native + iOS simulator tests, iOS device-slice link
- Android: all-ABI AAR build + x86_64 emulator instrumentation test
- Formatting

Run: https://github.com/qingshu-ui/ncnn-kt/actions

## Continuous integration

`.github/workflows/ci.yml` runs:

- Windows x64: `:ncnn:jvmTest :ncnn:mingwX64Test`
- Linux x64 and arm64: `:ncnn:jvmTest` plus the matching native test
- macOS arm64: `:ncnn:jvmTest :ncnn:macosArm64Test :ncnn:iosSimulatorArm64Test` and the
  iOS device-slice test link
- Android: all-ABI AAR build plus an x86_64 emulator instrumentation test
- `spotlessCheck`
