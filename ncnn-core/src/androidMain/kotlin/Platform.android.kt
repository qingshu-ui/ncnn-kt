package io.github.qingshu.ncnn_core

private inline fun <T> ncnn(block: () -> T): T {
    io.github.qingshu.ncnn.loadNcnnJni()
    return block()
}

internal actual fun ncnnVersionString(): String = ncnn { io.github.qingshu.ncnn.ncnnVersionString() }

internal actual fun ncnnVersionNumber(): Int = ncnn { io.github.qingshu.ncnn.ncnnVersionNumberJni() }

internal actual fun netCreate(): Long = ncnn { io.github.qingshu.ncnn.netCreate() }

internal actual fun netDestroy(handle: Long): Unit = ncnn { io.github.qingshu.ncnn.netDestroy(handle) }

internal actual fun netGetOptionInt(handle: Long, option: Int): Int = ncnn { io.github.qingshu.ncnn.netGetOptionInt(handle, option) }

internal actual fun netSetOptionInt(handle: Long, option: Int, value: Int): Unit = ncnn { io.github.qingshu.ncnn.netSetOptionInt(handle, option, value) }

internal actual fun netLoadParamPath(handle: Long, path: String): Unit = ncnn { io.github.qingshu.ncnn.netLoadParamPath(handle, path) }

internal actual fun netLoadParamMemory(handle: Long, data: ByteArray): Unit = ncnn { io.github.qingshu.ncnn.netLoadParamMemory(handle, data) }

internal actual fun netLoadModelPath(handle: Long, path: String): Unit = ncnn { io.github.qingshu.ncnn.netLoadModelPath(handle, path) }

internal actual fun netLoadModelMemory(handle: Long, data: ByteArray): Long = ncnn { io.github.qingshu.ncnn.netLoadModelMemory(handle, data) }

internal actual fun netInputCount(handle: Long): Int = ncnn { io.github.qingshu.ncnn.netInputCount(handle) }

internal actual fun netOutputCount(handle: Long): Int = ncnn { io.github.qingshu.ncnn.netOutputCount(handle) }

internal actual fun netInputName(handle: Long, index: Int): String? = ncnn { io.github.qingshu.ncnn.netInputName(handle, index) }

internal actual fun netOutputName(handle: Long, index: Int): String? = ncnn { io.github.qingshu.ncnn.netOutputName(handle, index) }

internal actual fun netInputIndex(handle: Long, index: Int): Int = ncnn { io.github.qingshu.ncnn.netInputIndex(handle, index) }

internal actual fun netOutputIndex(handle: Long, index: Int): Int = ncnn { io.github.qingshu.ncnn.netOutputIndex(handle, index) }

internal actual fun netClear(handle: Long): Unit = ncnn { io.github.qingshu.ncnn.netClear(handle) }

internal actual fun extractorCreate(netHandle: Long): Long = ncnn { io.github.qingshu.ncnn.extractorCreate(netHandle) }

internal actual fun extractorDestroy(handle: Long): Unit = ncnn { io.github.qingshu.ncnn.extractorDestroy(handle) }

internal actual fun extractorInputName(handle: Long, name: String, matHandle: Long): Unit = ncnn { io.github.qingshu.ncnn.extractorInputName(handle, name, matHandle) }

internal actual fun extractorInputIndex(handle: Long, index: Int, matHandle: Long): Unit = ncnn { io.github.qingshu.ncnn.extractorInputIndex(handle, index, matHandle) }

internal actual fun extractorExtractName(handle: Long, name: String): Long = ncnn { io.github.qingshu.ncnn.extractorExtractName(handle, name) }

internal actual fun extractorExtractIndex(handle: Long, index: Int): Long = ncnn { io.github.qingshu.ncnn.extractorExtractIndex(handle, index) }

internal actual fun matCreate(w: Int, h: Int, c: Int): Long = ncnn { io.github.qingshu.ncnn.matCreate(w, h, c) }

internal actual fun matDestroy(handle: Long): Unit = ncnn { io.github.qingshu.ncnn.matDestroy(handle) }

internal actual fun matDims(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matDims(handle) }

internal actual fun matW(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matW(handle) }

internal actual fun matH(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matH(handle) }

internal actual fun matD(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matD(handle) }

internal actual fun matC(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matC(handle) }

internal actual fun matN(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matN(handle) }

internal actual fun matElementSize(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matElementSize(handle) }

internal actual fun matElementPack(handle: Long): Int = ncnn { io.github.qingshu.ncnn.matElementPack(handle) }

internal actual fun matChannelStep(handle: Long): Long = ncnn { io.github.qingshu.ncnn.matChannelStep(handle) }

internal actual fun matDataSize(handle: Long): Long = ncnn { io.github.qingshu.ncnn.matDataSize(handle) }

internal actual fun matFill(handle: Long, value: Float): Unit = ncnn { io.github.qingshu.ncnn.matFill(handle, value) }

internal actual fun matClone(handle: Long): Long = ncnn { io.github.qingshu.ncnn.matClone(handle) }

internal actual fun matReshape(handle: Long, w: Int, h: Int, c: Int): Long = ncnn { io.github.qingshu.ncnn.matReshape(handle, w, h, c) }

internal actual fun matConvertPacking(handle: Long, elementPack: Int): Long = ncnn { io.github.qingshu.ncnn.matConvertPacking(handle, elementPack) }

internal actual fun matFlatten(handle: Long): Long = ncnn { io.github.qingshu.ncnn.matFlatten(handle) }

internal actual fun matReadFloats(handle: Long): FloatArray = ncnn { io.github.qingshu.ncnn.matReadFloats(handle) }

internal actual fun matWriteFloats(handle: Long, values: FloatArray): Unit = ncnn { io.github.qingshu.ncnn.matWriteFloats(handle, values) }

internal actual fun matReadBytes(handle: Long): ByteArray = ncnn { io.github.qingshu.ncnn.matReadBytes(handle) }

internal actual fun matWriteBytes(handle: Long, bytes: ByteArray): Unit = ncnn { io.github.qingshu.ncnn.matWriteBytes(handle, bytes) }

internal actual fun matSubstractMeanNormalize(handle: Long, mean: FloatArray?, norm: FloatArray?): Unit = ncnn { io.github.qingshu.ncnn.matSubstractMeanNormalize(handle, mean, norm) }

internal actual fun matFromPixels(pixels: ByteArray, type: Int, w: Int, h: Int, stride: Int): Long = ncnn { io.github.qingshu.ncnn.matFromPixels(pixels, type, w, h, stride) }

internal actual fun matFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long = ncnn { io.github.qingshu.ncnn.matFromPixelsResize(pixels, type, w, h, stride, targetWidth, targetHeight) }

internal actual fun matToPixels(handle: Long, type: Int, stride: Int): ByteArray = ncnn { io.github.qingshu.ncnn.matToPixels(handle, type, stride) }

internal actual fun matToPixelsResize(
    handle: Long,
    type: Int,
    targetWidth: Int,
    targetHeight: Int,
    stride: Int,
): ByteArray = ncnn { io.github.qingshu.ncnn.matToPixelsResize(handle, type, targetWidth, targetHeight, stride) }
