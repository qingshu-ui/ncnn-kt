# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `PixelImage`: in-place pixel drawing with ncnn's `ncnn_draw_*` primitives — rectangles, circles,
  lines and built-in monospace ASCII text — plus `PixelImage.rgb` / `rgba` color packing and
  `Mat.toPixelImage` / `PixelImage.toMat` conversions.

## [1.0.0] - 2026-11-14

### Added

- Kotlin Multiplatform bindings for ncnn `20261020`, with `jvm`, `android`, `mingwX64` and `linuxX64` targets.
- Public API in `io.github.qingshu.ncnn_core`: `Ncnn`, `NcnnException`, `Mat`, `Net`, `Extractor` and `PixelFormat`.
- `ncnn-jvm-jni`: JNI bridge for the JVM, bundling the `windows-x64` and `linux-x64` ncnn natives.
- `ncnn-android-jni`: JNI bridge and AAR with `arm64-v8a`, `armeabi-v7a` and `x86_64` `jniLibs`.
- `ncnn-binding`: cinterop facade over ncnn's C API for Kotlin/Native.
- Tests covering version, tensor creation and access, pixel conversion, resizing, normalization, inference from memory, extraction by name and index, and error handling, on JVM, MinGW and Linux.
- Maven Central publishing under `io.github.qingshu-ui`, driven by `publish_all.sh` and the `Build & Publish` workflow.
- BSD 3-Clause [`LICENSE.txt`](LICENSE.txt).

### Notes

- Kotlin/Native consumers must provide the matching ncnn prebuilt SDK at cinterop and link time; the published klibs do not contain native binaries.
- The JVM jar is host-built: a Windows release contains both desktop natives, a Linux release only the Linux one.
