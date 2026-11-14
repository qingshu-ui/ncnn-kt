# ncnn-kt

[English](README.md) | 简体中文

[腾讯 ncnn](https://github.com/Tencent/ncnn) 的 Kotlin Multiplatform 绑定。ncnn 是一个面向移动端和桌面端的
高性能神经网络推理框架。

本库用简洁的 Kotlin API 封装 ncnn 的 C 接口，并自带原生运行时，因此 JVM 或 Android 项目只需声明一个依赖即可使用。
Kotlin/Native 目标通过 cinterop 链接同一版本的 ncnn。

## 特性

- `Net` / `Extractor`：从文件或内存加载 `.param` 与 `.bin` 模型。
- `Mat` 张量：创建、像素输入输出、缩放、packing、浮点与字节读写、均值/归一化。
- `Ncnn` 版本信息，以及 `NcnnException` 统一表示 ncnn 返回的失败。
- JVM jar 内置 `windows-x64` 与 `linux-x64` 原生库。
- Android AAR 内置 `arm64-v8a`、`armeabi-v7a`、`x86_64` 的 `jniLibs`。
- 支持 Kotlin/Native 的 `mingwX64` 与 `linuxX64` 目标。
- 在 JVM、MinGW、Linux 上均有推理测试，测试内嵌一个三层小模型。

## 模块

| 模块 | 用途 |
| --- | --- |
| [`ncnn-core`](ncnn-core) | 公共 API：`Ncnn`、`NcnnException`、`Mat`、`Net`、`Extractor`、`PixelFormat` |
| [`ncnn-jvm-jni`](ncnn-jvm-jni) | JVM 的 JNI 桥接，打包桌面端 ncnn 运行时 |
| [`ncnn-android-jni`](ncnn-android-jni) | Android 的 JNI 桥接，打包各 ABI 的 `jniLibs` |
| [`ncnn-binding`](ncnn-binding) | Kotlin/Native 下基于 ncnn C 接口的 cinterop 封装 |

`ncnn-core` 依赖各桥接模块，通常只需声明 `ncnn-core`。

## 安装

```kotlin
dependencies {
    implementation("io.github.qingshu-ui:ncnn-core:1.0.0")
}
```

Kotlin Multiplatform 项目在对应 source set 中声明：

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.qingshu-ui:ncnn-core:1.0.0")
        }
    }
}
```

Gradle 会按目标自动选择 JVM、Android 或 Kotlin/Native 变体，并传递依赖 `ncnn-jvm-jni`、
`ncnn-android-jni` 与 `ncnn-binding`。

### 平台支持

| 平台 | 内置的 ncnn 运行时 |
| --- | --- |
| JVM，Windows x64 | `ncnn.dll` |
| JVM，Linux x64 | `libncnn.so.1` |
| Android | `arm64-v8a`、`armeabi-v7a`、`x86_64` |
| Kotlin/Native `mingwX64`、`linuxX64` | 需自行提供 ncnn SDK 并链接（见下文） |

JVM jar 与构建主机相关：在 Windows 上构建同时包含 Windows 与 Linux 原生库，在 Linux 上构建只包含 Linux 版本。
Android AAR 始终包含三个 ABI。

Kotlin/Native 产物是 klib，不含原生二进制。使用 `mingwX64` / `linuxX64` 的调用方需要在 cinterop 与链接路径中
提供对应平台的 ncnn 预编译 SDK（`libs/<platform>`）。

### 如何选择原生目标所需的 ncnn 版本

klib 只包含 Kotlin 元数据，C 库需要应用自行链接。每个 ncnn-kt 版本固定对应一个 ncnn 版本：

| ncnn-kt | ncnn |
| --- | --- |
| 1.0.0 | 20261020 |

`1.0.0` 使用 `20261020` 的发布包：

| 目标 | 资源 |
| --- | --- |
| `mingwX64` | `ncnn-20261020-windows-vs2022-shared.zip` |
| `linuxX64` | `ncnn-20261020-ubuntu-2404-shared.zip` |

从 <https://github.com/Tencent/ncnn/releases/tag/20261020> 下载并校验 SHA-256：

```
windows-x64  7f39107af5ee4c1e657b8fd01e8b3cad6c0ab7fdbb0713532f01937134285b3d
linux-x64    996e07a787550b782dd5891a2a60d7fa5d8f84452bb2f244ce0f3f9c01417842
android      c2f866782cb6b8337bcd062f12b0da1b75e703f4e90fc7905d00c7c74d669a87
```

解压后确保存在 `libs/<platform>/include/ncnn` 与 `libs/<platform>/lib`。运行时 `Ncnn.versionNumber` 返回固定值
（`20261020`），可立即发现原生库版本不匹配。

版本固定值定义在 [`NcnnSdkPlugin.kt`](buildSrc/src/main/kotlin/NcnnSdkPlugin.kt)，本仓库的 `downloadNcnn`
任务会执行同样的下载。

## 快速开始

```kotlin
import io.github.qingshu.ncnn_core.Mat
import io.github.qingshu.ncnn_core.Ncnn
import io.github.qingshu.ncnn_core.Net
import io.github.qingshu.ncnn_core.PixelFormat

fun classify(rgbPixels: ByteArray): FloatArray {
    println("ncnn ${Ncnn.version}") // 例如 1.0.20261020

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

`Net`、`Extractor`、`Mat` 都持有原生内存，必须关闭；它们都实现了 `AutoCloseable`，因此可以直接使用 Kotlin 的
`use { }`。`Net` 不是线程安全的。

从内存加载模型（例如随资源打包的模型）：

```kotlin
net.loadParam(paramBytes)
net.loadModel(modelBytes)
```

`Extractor` 也支持按索引输入和输出（`input(0, mat)`、`extract(0)`），并且可以在其 `Net` 打开期间复用。

## 从源码构建

环境要求：

- JDK 21
- Android SDK，需安装 `android-37.0` 平台与 `37.0.0` build-tools（Android 模块）
- Kotlin/Native 工具链由 Gradle 在首次构建时自动下载

```bash
./gradlew build
```

固定版本的 ncnn（`20261020`）由 `downloadNcnn` 任务下载到 `libs/<platform>`，cinterop 与链接任务会自动依赖它。
`libs/` 不纳入 Git。

常用任务：

```bash
./gradlew :ncnn-core:jvmTest          # JVM 测试
./gradlew :ncnn-core:mingwX64Test     # Kotlin/Native MinGW 测试
./gradlew :ncnn-core:linuxX64Test     # Kotlin/Native Linux 测试
./gradlew :ncnn-android-jni:bundleAndroidMainAar
./gradlew spotlessCheck
```

## 许可证

本项目采用 BSD 3-Clause 许可证，详见 [LICENSE.txt](LICENSE.txt)。

ncnn 本身版权归 Tencent (C) 2017 所有，以 BSD 3-Clause 许可证分发，其中部分第三方组件使用各自的许可证。
详情见 [ncnn 仓库](https://github.com/Tencent/ncnn)。
