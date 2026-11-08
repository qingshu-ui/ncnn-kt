package io.github.qingshu.ncnn

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import ncnn.ncnn_mat_t

@OptIn(ExperimentalForeignApi::class)
internal actual fun ncnnVersion(): String = ncnn.ncnn_version()?.toKString() ?: "unknown"

@OptIn(ExperimentalForeignApi::class)
internal actual fun ncnnVersionNumber(): Int = ncnn.ncnn_version_number()

@OptIn(ExperimentalForeignApi::class)
internal actual fun createMat(w: Int, h: Int, c: Int): Mat {
    val mat: ncnn_mat_t? = when {
        c > 0 -> ncnn.ncnn_mat_create_3d(w, h, c, null)
        h > 0 -> ncnn.ncnn_mat_create_2d(w, h, null)
        else -> ncnn.ncnn_mat_create_1d(w, null)
    }
    return MatNative(mat.orThrow("Mat.create"))
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun createMatFromFloats(values: FloatArray, w: Int, h: Int, c: Int): Mat {
    val mat = createMat(w, h, c)
    try {
        mat.writeFloats(values)
    } catch (t: Throwable) {
        mat.close()
        throw t
    }
    return mat
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun createMatFromPixels(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
): Mat {
    val mat = pixels.usePinned { pinned ->
        ncnn.ncnn_mat_from_pixels(pinned.addressOf(0).reinterpret(), type, w, h, stride, null)
    }
    return MatNative(mat.orThrow("Mat.fromPixels"))
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun createMatFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Mat {
    val mat = pixels.usePinned { pinned ->
        ncnn.ncnn_mat_from_pixels_resize(
            pinned.addressOf(0).reinterpret(),
            type,
            w,
            h,
            stride,
            targetWidth,
            targetHeight,
            null,
        )
    }
    return MatNative(mat.orThrow("Mat.fromPixelsResize"))
}

internal actual fun createNet(): Net = NetNative()
