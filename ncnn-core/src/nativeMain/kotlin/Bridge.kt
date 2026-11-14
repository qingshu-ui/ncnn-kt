package io.github.qingshu.ncnn_core

private inline fun <T> bridge(block: () -> T): T = try {
    block()
} catch (error: io.github.qingshu.ncnn.binding.NcnnBindingException) {
    throw NcnnException(error.message ?: "ncnn error", error.code)
}

internal actual fun ncnnVersionString(): String = bridge { io.github.qingshu.ncnn.binding.ncnnVersionString() } ?: error("ncnn returned no version")

internal actual fun ncnnVersionNumber(): Int = bridge { io.github.qingshu.ncnn.binding.ncnnVersionNumber() }

internal actual fun netCreate(): Long = bridge { io.github.qingshu.ncnn.binding.netCreate() }

internal actual fun netDestroy(handle: Long): Unit = bridge { io.github.qingshu.ncnn.binding.netDestroy(handle) }

internal actual fun netGetOptionInt(handle: Long, option: Int): Int = bridge { io.github.qingshu.ncnn.binding.netGetOptionInt(handle, option) }

internal actual fun netSetOptionInt(handle: Long, option: Int, value: Int): Unit = bridge { io.github.qingshu.ncnn.binding.netSetOptionInt(handle, option, value) }

internal actual fun netLoadParamPath(handle: Long, path: String): Unit = bridge { io.github.qingshu.ncnn.binding.netLoadParamPath(handle, path) }

internal actual fun netLoadParamMemory(handle: Long, data: ByteArray): Unit = bridge { io.github.qingshu.ncnn.binding.netLoadParamMemory(handle, data) }

internal actual fun netLoadModelPath(handle: Long, path: String): Unit = bridge { io.github.qingshu.ncnn.binding.netLoadModelPath(handle, path) }

internal actual fun netLoadModelMemory(handle: Long, data: ByteArray): Long = bridge { io.github.qingshu.ncnn.binding.netLoadModelMemory(handle, data) }

internal actual fun netInputCount(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.netInputCount(handle) }

internal actual fun netOutputCount(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.netOutputCount(handle) }

internal actual fun netInputName(handle: Long, index: Int): String? = bridge { io.github.qingshu.ncnn.binding.netInputName(handle, index) }

internal actual fun netOutputName(handle: Long, index: Int): String? = bridge { io.github.qingshu.ncnn.binding.netOutputName(handle, index) }

internal actual fun netInputIndex(handle: Long, index: Int): Int = bridge { io.github.qingshu.ncnn.binding.netInputIndex(handle, index) }

internal actual fun netOutputIndex(handle: Long, index: Int): Int = bridge { io.github.qingshu.ncnn.binding.netOutputIndex(handle, index) }

internal actual fun netClear(handle: Long): Unit = bridge { io.github.qingshu.ncnn.binding.netClear(handle) }

internal actual fun extractorCreate(netHandle: Long): Long = bridge { io.github.qingshu.ncnn.binding.extractorCreate(netHandle) }

internal actual fun extractorDestroy(handle: Long): Unit = bridge { io.github.qingshu.ncnn.binding.extractorDestroy(handle) }

internal actual fun extractorInputName(handle: Long, name: String, matHandle: Long): Unit = bridge { io.github.qingshu.ncnn.binding.extractorInputName(handle, name, matHandle) }

internal actual fun extractorInputIndex(handle: Long, index: Int, matHandle: Long): Unit = bridge { io.github.qingshu.ncnn.binding.extractorInputIndex(handle, index, matHandle) }

internal actual fun extractorExtractName(handle: Long, name: String): Long = bridge { io.github.qingshu.ncnn.binding.extractorExtractName(handle, name) }

internal actual fun extractorExtractIndex(handle: Long, index: Int): Long = bridge { io.github.qingshu.ncnn.binding.extractorExtractIndex(handle, index) }

internal actual fun matCreate(w: Int, h: Int, c: Int): Long = bridge { io.github.qingshu.ncnn.binding.matCreate(w, h, c) }

internal actual fun matDestroy(handle: Long): Unit = bridge { io.github.qingshu.ncnn.binding.matDestroy(handle) }

internal actual fun matDims(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matDims(handle) }

internal actual fun matW(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matW(handle) }

internal actual fun matH(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matH(handle) }

internal actual fun matD(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matD(handle) }

internal actual fun matC(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matC(handle) }

internal actual fun matN(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matN(handle) }

internal actual fun matElementSize(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matElementSize(handle) }

internal actual fun matElementPack(handle: Long): Int = bridge { io.github.qingshu.ncnn.binding.matElementPack(handle) }

internal actual fun matChannelStep(handle: Long): Long = bridge { io.github.qingshu.ncnn.binding.matChannelStep(handle) }

internal actual fun matDataSize(handle: Long): Long = bridge { io.github.qingshu.ncnn.binding.matDataSize(handle) }

internal actual fun matFill(handle: Long, value: Float): Unit = bridge { io.github.qingshu.ncnn.binding.matFill(handle, value) }

internal actual fun matClone(handle: Long): Long = bridge { io.github.qingshu.ncnn.binding.matClone(handle) }

internal actual fun matReshape(handle: Long, w: Int, h: Int, c: Int): Long = bridge { io.github.qingshu.ncnn.binding.matReshape(handle, w, h, c) }

internal actual fun matConvertPacking(handle: Long, elementPack: Int): Long = bridge { io.github.qingshu.ncnn.binding.matConvertPacking(handle, elementPack) }

internal actual fun matFlatten(handle: Long): Long = bridge { io.github.qingshu.ncnn.binding.matFlatten(handle) }

internal actual fun matReadFloats(handle: Long): FloatArray = bridge { io.github.qingshu.ncnn.binding.matReadFloats(handle) }

internal actual fun matWriteFloats(handle: Long, values: FloatArray): Unit = bridge { io.github.qingshu.ncnn.binding.matWriteFloats(handle, values) }

internal actual fun matReadBytes(handle: Long): ByteArray = bridge { io.github.qingshu.ncnn.binding.matReadBytes(handle) }

internal actual fun matWriteBytes(handle: Long, bytes: ByteArray): Unit = bridge { io.github.qingshu.ncnn.binding.matWriteBytes(handle, bytes) }

internal actual fun matSubstractMeanNormalize(handle: Long, mean: FloatArray?, norm: FloatArray?): Unit = bridge { io.github.qingshu.ncnn.binding.matSubstractMeanNormalize(handle, mean, norm) }

internal actual fun matFromPixels(pixels: ByteArray, type: Int, w: Int, h: Int, stride: Int): Long = bridge { io.github.qingshu.ncnn.binding.matFromPixels(pixels, type, w, h, stride) }

internal actual fun matFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long = bridge { io.github.qingshu.ncnn.binding.matFromPixelsResize(pixels, type, w, h, stride, targetWidth, targetHeight) }

internal actual fun matToPixels(handle: Long, type: Int, stride: Int): ByteArray = bridge { io.github.qingshu.ncnn.binding.matToPixels(handle, type, stride) }

internal actual fun matToPixelsResize(
    handle: Long,
    type: Int,
    targetWidth: Int,
    targetHeight: Int,
    stride: Int,
): ByteArray = bridge { io.github.qingshu.ncnn.binding.matToPixelsResize(handle, type, targetWidth, targetHeight, stride) }
