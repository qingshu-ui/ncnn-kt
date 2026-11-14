@file:JvmName("NcnnJni")

package io.github.qingshu.ncnn

/** Loads the JVM JNI bridge once per process; call before any bridge function. */
fun loadNcnnJni() = NativeLoader.load()

external fun ncnnVersionString(): String

external fun ncnnVersionNumberJni(): Int

external fun netCreate(): Long

external fun netDestroy(handle: Long)

external fun netGetOptionInt(handle: Long, option: Int): Int

external fun netSetOptionInt(handle: Long, option: Int, value: Int)

external fun netLoadParamPath(handle: Long, path: String)

external fun netLoadParamMemory(handle: Long, data: ByteArray)

external fun netLoadModelPath(handle: Long, path: String)

external fun netLoadModelMemory(handle: Long, data: ByteArray): Long

external fun netInputCount(handle: Long): Int

external fun netOutputCount(handle: Long): Int

external fun netInputName(handle: Long, index: Int): String?

external fun netOutputName(handle: Long, index: Int): String?

external fun netInputIndex(handle: Long, index: Int): Int

external fun netOutputIndex(handle: Long, index: Int): Int

external fun netClear(handle: Long)

external fun extractorCreate(netHandle: Long): Long

external fun extractorDestroy(handle: Long)

external fun extractorInputName(handle: Long, name: String, matHandle: Long)

external fun extractorInputIndex(handle: Long, index: Int, matHandle: Long)

external fun extractorExtractName(handle: Long, name: String): Long

external fun extractorExtractIndex(handle: Long, index: Int): Long

external fun matCreate(w: Int, h: Int, c: Int): Long

external fun matDestroy(handle: Long)

external fun matDims(handle: Long): Int

external fun matW(handle: Long): Int

external fun matH(handle: Long): Int

external fun matD(handle: Long): Int

external fun matC(handle: Long): Int

external fun matN(handle: Long): Int

external fun matElementSize(handle: Long): Int

external fun matElementPack(handle: Long): Int

external fun matChannelStep(handle: Long): Long

external fun matDataSize(handle: Long): Long

external fun matFill(handle: Long, value: Float)

external fun matClone(handle: Long): Long

external fun matReshape(handle: Long, w: Int, h: Int, c: Int): Long

external fun matConvertPacking(handle: Long, elementPack: Int): Long

external fun matFlatten(handle: Long): Long

external fun matReadFloats(handle: Long): FloatArray

external fun matWriteFloats(handle: Long, values: FloatArray)

external fun matReadBytes(handle: Long): ByteArray

external fun matWriteBytes(handle: Long, bytes: ByteArray)

external fun matSubstractMeanNormalize(handle: Long, mean: FloatArray?, norm: FloatArray?)

external fun matFromPixels(pixels: ByteArray, type: Int, w: Int, h: Int, stride: Int): Long

external fun matFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long

external fun matToPixels(handle: Long, type: Int, stride: Int): ByteArray

external fun matToPixelsResize(
    handle: Long,
    type: Int,
    targetWidth: Int,
    targetHeight: Int,
    stride: Int,
): ByteArray
