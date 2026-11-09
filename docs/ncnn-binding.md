# ncnn-kt: Kotlin Multiplatform bindings for ncnn

This document describes the design and usage of the `ncnn` module in this repository.
It supersedes the obsolete scratch documents in `tmp/` (in particular the claim there
that cinterop can bind ncnn's C++ headers — Kotlin/Native cinterop only binds C).

## Version pinning

- ncnn: **20261020**, consumed from Tencent's official prebuilt release packages. Each
  package is pinned by SHA-256 in `buildSrc/src/main/kotlin/NcnnBuilds.kt`.
- Kotlin: **2.3.21**, Gradle: **9.4.0**, AGP: **9.0.0**, JDK: **21**.
- Android NDK: **27.2.12479018** (JNI only; ncnn itself is prebuilt).

No ncnn sources are downloaded or compiled. Release archives are downloaded, verified and
unpacked into `ncnn/build/prebuilt/` (or restored from CI cache). Set
`ncnn.prebuiltBaseUrl` in `local.properties` (or `NCNN_PREBUILT_BASE_URL`) when GitHub is
not directly reachable.

## What is bound

Both backends call ncnn's **C API** (`c_api.h` from the prebuilt package), which covers
everything exposed here:

| Kotlin | ncnn C API |
|---|---|
| `Ncnn.version` / `Ncnn.versionNumber` | `ncnn_version`, `ncnn_version_number` |
| `Net`, `Extractor`, `Mat` | `ncnn_net_*`, `ncnn_extractor_*`, `ncnn_mat_*` |
| `Net` option properties | `ncnn_option_get_*` / `ncnn_option_set_*` |
| Pixel conversion / resize | `ncnn_mat_from_pixels*`, `ncnn_mat_to_pixels*` |
| Packing / flatten / normalize | `ncnn_convert_packing`, `ncnn_flatten`, `ncnn_mat_substract_mean_normalize` |

Backends:

- **JVM / Android**: a thin JNI C shim (`ncnn/src/jvmMain/cpp/ncnn_jni.c`) over the C API.
- **Kotlin/Native**: cinterop over `c_api.h`, linked against the prebuilt library for the
  target.

Layers are not bound individually. Layer functionality is reached by loading ncnn
`.param` / `.bin` models.

## Platform support

| Platform | Target | Backend | Status |
|---|---|---|---|
| JVM on Windows x64 | `jvm` | JNI | built and tested locally + CI |
| JVM on Linux x64 | `jvm` | JNI | CI |
| JVM on macOS arm64 / x64 | `jvm` | JNI | CI |
| Windows x64 native | `mingwX64` | cinterop | built and tested locally + CI |
| Linux x64 native | `linuxX64` | cinterop | CI |
| macOS arm64 native | `macosArm64` | cinterop | CI |
| iOS device / simulator | `iosArm64`, `iosSimulatorArm64` | cinterop | CI (device slice link-only) |
| Android `arm64-v8a`, `armeabi-v7a`, `x86_64` | `android` | JNI via NDK in an AAR | CI build (AAR) |

Kotlin/Native targets are declared **unconditionally** in the `ncnn` module so that
IntelliJ IDEA indexes every platform source set (`mingwMain`, `linuxMain`, `appleMain`,
`unixNativeMain`, …) regardless of the host. Cinterop and Kotlin compilation for Linux
targets work as **cross-compilation** on Windows/macOS hosts (using a portable, platform-
neutral copy of ncnn's C API headers), so IDE resolution for `linuxMain` works everywhere.
Linking and test execution for non-host targets are disabled; Apple targets are disabled by
the Kotlin Gradle plugin on non-macOS hosts.

Kotlin 2.3 cross-compiles cinterop as an **unpacked** klib (`-nopack`), but the IDE's Gradle
model points at the packed `build/libs/*.klib`. A `packCinteropKlib<target>` task therefore
writes the packed klib at that path as well (wired to the Linux compile task), so IDE
resolution works without affecting the directory klib consumed by the compiler.

`macosX64` is deprecated/removed in Kotlin 2.3, but JVM on Intel macOS is still supported by
building the JNI library for `macos-x64` (the macOS prebuilt is a universal static library).

**Linux arm64 is not supported in this version** because Tencent publishes no Linux arm64
release package. It was previously built from source; using prebuilt packages means the
target cannot be provided.

Explicitly out of scope for this version:

- **Vulkan / GPU**: the Kotlin API does not expose Vulkan options or GPU execution.
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
- `-Pncnn.prebuiltBaseUrl=<url>` — alternate base URL for the pinned release packages
  (also `ncnn.prebuiltBaseUrl` in `local.properties` or `NCNN_PREBUILT_BASE_URL`).

CMake and Ninja are still required, but only to compile the small JNI bridge — ncnn itself
is not compiled.

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

The module consumes Tencent's official prebuilt packages directly:

| Platform | Package | Link artifact |
|---|---|---|
| Windows x64 | `windows-vs2022-shared` | `ncnn.dll` + MSVC import library `ncnn.lib` |
| Linux x64 | `ubuntu-2404-shared` | `libncnn.so.1` (soname `libncnn.so.1`) |
| macOS / iOS | `macos.zip`, `ios.zip`, `ios-simulator.zip` | universal/static `libncnn.a` + `libopenmp.a` |
| Android | `android-shared` | `libncnn.so` per ABI |

- **Windows**: the official DLL is MSVC-built. `ncnn_jni.dll` and the `mingwX64` test binary
  link `ncnn.lib` (COFF import library; MinGW's ld/lld handle it). `ncnn.dll` plus the VC++
  runtime DLLs (`msvcp140`, `vcruntime140`, `vcruntime140_1`, `vcomp140`) are bundled for
  the JVM and copied next to native test binaries. Consumers of the native library must ship
  the same DLLs.
- **Linux**: `libncnn_jni.so` and the native test binary link `libncnn.so`; `libncnn.so.1`
  is bundled/preloaded for the JVM and copied next to the test binary (`-Wl,-rpath,$ORIGIN`).
  The prebuilt library requires the system `libgomp.so.1`, `libstdc++.so.6` and `libmvec`.
  Consumers of the native library must ship `libncnn.so.1` and have an OpenMP runtime.
- **macOS/iOS**: static libraries; the JNI library and cinterop link `libncnn.a` and
  `libopenmp.a` with `-lc++`. The JNI library is bundled into the JVM resources under
  `io/github/qingshu/ncnn/native/<os>-<arch>/`. `NativeLoader` resolves, in order:
  `-Dncnn.jni.path`, `NCNN_JNI_PATH`, `System.loadLibrary("ncnn_jni")`, then the bundled
  resource.
- **Android**: `libncnn_jni.so` and `libncnn.so` are packaged per ABI in
  `ncnn-android-native` (an AAR); the JNI is loaded with `System.loadLibrary` and the
  prebuilt shared library is resolved from the same ABI directory. The KMP Android target
  depends on that AAR at **runtime** (`runtimeOnly`), so Kotlin compilation does not require
  the NDK.

## Prebuilt packages

Pinned assets and SHA-256 values live in `NcnnBuilds.kt`:

```
windows-vs2022-shared   7f39107a…b85b3d
ubuntu-2404-shared      996e07a7…417842
android-shared          c2f86678…669a87
macos                   e33de27e…e3f2d
ios                     a9f64778…05481be
ios-simulator           dd842be6…01d6e4
```

Packages are downloaded from
`https://github.com/Tencent/ncnn/releases/download/<version>/`, verified, unpacked into
`ncnn/build/prebuilt/` and normalized to `include/ncnn`, `lib/` (plus `bin/` on Windows).

## Tests

`ncnn/src/commonTest` runs on every JVM and native target:

- version, Mat create/fill/clone/reshape/read/write, close semantics;
- pixel round-trip, RGB→GRAY conversion, resize, mean/normalize;
- end-to-end inference from memory with a generated `Input → Convolution(1×1, weight 2) →
  ReLU` network (`[-1, 2, -3, 4]` → `[0, 4, 0, 8]`);
- error paths for missing files and invalid param bytes.

The JVM and native suites additionally load files from temporary paths (the JVM test also
uses a non-ASCII file name to exercise the Windows `_w` path conversion). The Android
instrumentation test in `android-test-app` can run the same inference through the AAR on an
x86_64 emulator (not part of CI; Android CI only compiles the AAR).

`## Upgrading ncnn

1. Set `ncnnVersion` in `gradle/libs.versions.toml`.
2. Update the six asset names and SHA-256 values in `NcnnBuilds.kt` (all assets carry the version in their file name).
3. Merge any new C API declarations needed into `ncnn_jni.c` and the native wrappers.
4. Run the CI matrix; the prebuilt download cache keys include the version and hashes.

`## What has been verified

Locally (Windows x64, JDK 21, Kotlin/Native 2.3.21, CMake 4.4.2, CLion MinGW GCC 15.2):

- `./gradlew :ncnn:jvmTest :ncnn:mingwX64Test` — all tests pass against the prebuilt
  Windows release package (also with the configuration cache enabled).
- `./gradlew :ncnn:compileAndroidMain` — the Android Kotlin target compiles.
- `./gradlew :ncnn:cinteropNcnnLinuxX64` — Linux cinterop/klib cross-compiles on Windows.
- `./gradlew spotlessCheck` — formatting is clean.

CI (`.github/workflows/ci.yml`) covers five jobs:

- Windows x64: JVM + mingwX64 tests, JVM jar
- Linux x64: JVM + Linux native tests
- macOS arm64: JVM + macOS native + iOS simulator tests, iOS device-slice link
- Android: all-ABI AAR build (compile only; no emulator tests)
- Formatting

Run: https://github.com/qingshu-ui/ncnn-kt/actions

## Continuous integration

`.github/workflows/ci.yml` runs:

- Windows x64: `:ncnn:jvmTest :ncnn:mingwX64Test`
- Linux x64: `:ncnn:jvmTest :ncnn:linuxX64Test`
- macOS arm64: `:ncnn:jvmTest :ncnn:macosArm64Test :ncnn:iosSimulatorArm64Test` and the
  iOS device-slice test link
- Android: all-ABI AAR build (no emulator tests)
- `spotlessCheck`
