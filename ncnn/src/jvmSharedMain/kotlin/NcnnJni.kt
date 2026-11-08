@file:JvmName("NcnnJni")

package io.github.qingshu.ncnn

internal external fun ncnnVersionString(): String

internal external fun ncnnVersionNumberJni(): Int

internal external fun netCreate(): Long

internal external fun netDestroy(handle: Long)

internal external fun netGetOptionInt(handle: Long, option: Int): Int

internal external fun netSetOptionInt(handle: Long, option: Int, value: Int)

internal external fun netLoadParamPath(handle: Long, path: String)

internal external fun netLoadParamMemory(handle: Long, data: ByteArray)

internal external fun netLoadModelPath(handle: Long, path: String)

internal external fun netLoadModelMemory(handle: Long, data: ByteArray): Long

internal external fun netInputCount(handle: Long): Int

internal external fun netOutputCount(handle: Long): Int

internal external fun netInputName(handle: Long, index: Int): String?

internal external fun netOutputName(handle: Long, index: Int): String?

internal external fun netInputIndex(handle: Long, index: Int): Int

internal external fun netOutputIndex(handle: Long, index: Int): Int

internal external fun netClear(handle: Long)

internal external fun extractorCreate(netHandle: Long): Long

internal external fun extractorDestroy(handle: Long)

internal external fun extractorInputName(handle: Long, name: String, matHandle: Long)

internal external fun extractorInputIndex(handle: Long, index: Int, matHandle: Long)

internal external fun extractorExtractName(handle: Long, name: String): Long

internal external fun extractorExtractIndex(handle: Long, index: Int): Long

internal external fun matCreate(w: Int, h: Int, c: Int): Long

internal external fun matDestroy(handle: Long)

internal external fun matDims(handle: Long): Int

internal external fun matW(handle: Long): Int

internal external fun matH(handle: Long): Int

internal external fun matD(handle: Long): Int

internal external fun matC(handle: Long): Int

internal external fun matN(handle: Long): Int

internal external fun matElementSize(handle: Long): Int

internal external fun matElementPack(handle: Long): Int

internal external fun matChannelStep(handle: Long): Long

internal external fun matDataSize(handle: Long): Long

internal external fun matFill(handle: Long, value: Float)

internal external fun matClone(handle: Long): Long

internal external fun matReshape(handle: Long, w: Int, h: Int, c: Int): Long

internal external fun matConvertPacking(handle: Long, elementPack: Int): Long

internal external fun matFlatten(handle: Long): Long

internal external fun matReadFloats(handle: Long): FloatArray

internal external fun matWriteFloats(handle: Long, values: FloatArray)

internal external fun matReadBytes(handle: Long): ByteArray

internal external fun matWriteBytes(handle: Long, bytes: ByteArray)

internal external fun matSubstractMeanNormalize(handle: Long, mean: FloatArray?, norm: FloatArray?)

internal external fun matFromPixels(pixels: ByteArray, type: Int, w: Int, h: Int, stride: Int): Long

internal external fun matFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long

internal external fun matToPixels(handle: Long, type: Int, stride: Int): ByteArray

internal external fun matToPixelsResize(
    handle: Long,
    type: Int,
    targetWidth: Int,
    targetHeight: Int,
    stride: Int,
): ByteArray
