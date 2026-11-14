package io.github.qingshu.ncnn_core

/*
 * Internal bridge to the native ncnn JNI entry points.
 *
 * JVM/Android actuals call the declarations exported by ncnn-jvm-jni /
 * ncnn-android-jni; native actuals call the ncnn-binding facade.
 */

internal expect fun ncnnVersionString(): String

internal expect fun ncnnVersionNumber(): Int

internal expect fun netCreate(): Long

internal expect fun netDestroy(handle: Long)

internal expect fun netGetOptionInt(handle: Long, option: Int): Int

internal expect fun netSetOptionInt(handle: Long, option: Int, value: Int)

internal expect fun netLoadParamPath(handle: Long, path: String)

internal expect fun netLoadParamMemory(handle: Long, data: ByteArray)

internal expect fun netLoadModelPath(handle: Long, path: String)

internal expect fun netLoadModelMemory(handle: Long, data: ByteArray): Long

internal expect fun netInputCount(handle: Long): Int

internal expect fun netOutputCount(handle: Long): Int

internal expect fun netInputName(handle: Long, index: Int): String?

internal expect fun netOutputName(handle: Long, index: Int): String?

internal expect fun netInputIndex(handle: Long, index: Int): Int

internal expect fun netOutputIndex(handle: Long, index: Int): Int

internal expect fun netClear(handle: Long)

internal expect fun extractorCreate(netHandle: Long): Long

internal expect fun extractorDestroy(handle: Long)

internal expect fun extractorInputName(handle: Long, name: String, matHandle: Long)

internal expect fun extractorInputIndex(handle: Long, index: Int, matHandle: Long)

internal expect fun extractorExtractName(handle: Long, name: String): Long

internal expect fun extractorExtractIndex(handle: Long, index: Int): Long

internal expect fun matCreate(w: Int, h: Int, c: Int): Long

internal expect fun matDestroy(handle: Long)

internal expect fun matDims(handle: Long): Int

internal expect fun matW(handle: Long): Int

internal expect fun matH(handle: Long): Int

internal expect fun matD(handle: Long): Int

internal expect fun matC(handle: Long): Int

internal expect fun matN(handle: Long): Int

internal expect fun matElementSize(handle: Long): Int

internal expect fun matElementPack(handle: Long): Int

internal expect fun matChannelStep(handle: Long): Long

internal expect fun matDataSize(handle: Long): Long

internal expect fun matFill(handle: Long, value: Float)

internal expect fun matClone(handle: Long): Long

internal expect fun matReshape(handle: Long, w: Int, h: Int, c: Int): Long

internal expect fun matConvertPacking(handle: Long, elementPack: Int): Long

internal expect fun matFlatten(handle: Long): Long

internal expect fun matReadFloats(handle: Long): FloatArray

internal expect fun matWriteFloats(handle: Long, values: FloatArray)

internal expect fun matReadBytes(handle: Long): ByteArray

internal expect fun matWriteBytes(handle: Long, bytes: ByteArray)

internal expect fun matSubstractMeanNormalize(handle: Long, mean: FloatArray?, norm: FloatArray?)

internal expect fun matFromPixels(pixels: ByteArray, type: Int, w: Int, h: Int, stride: Int): Long

internal expect fun matFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long

internal expect fun matToPixels(handle: Long, type: Int, stride: Int): ByteArray

internal expect fun matToPixelsResize(
    handle: Long,
    type: Int,
    targetWidth: Int,
    targetHeight: Int,
    stride: Int,
): ByteArray
