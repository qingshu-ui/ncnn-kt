@file:OptIn(ExperimentalForeignApi::class)

package io.github.qingshu.ncnn.binding

import cnames.structs.__ncnn_mat_t
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

fun matCreate(w: Int, h: Int, c: Int): Long {
    val mat = when {
        c > 0 -> ncnn.ncnn_mat_create_3d(w, h, c, null)
        h > 0 -> ncnn.ncnn_mat_create_2d(w, h, null)
        else -> ncnn.ncnn_mat_create_1d(w, null)
    } ?: throw OutOfMemoryError("Mat.create failed: ncnn could not allocate the tensor")
    if (ncnn.ncnn_mat_get_data(mat) == null && ncnn.ncnn_mat_get_cstep(mat).toLong() > 0) {
        ncnn.ncnn_mat_destroy(mat)
        throw OutOfMemoryError("Mat.create failed: ncnn could not allocate the tensor")
    }
    return mat.toHandle()
}

fun matDestroy(handle: Long) {
    if (handle != 0L) ncnn.ncnn_mat_destroy(asMat(handle))
}

fun matDims(handle: Long): Int = ncnn.ncnn_mat_get_dims(asMat(handle))

fun matW(handle: Long): Int = ncnn.ncnn_mat_get_w(asMat(handle))

fun matH(handle: Long): Int = ncnn.ncnn_mat_get_h(asMat(handle))

fun matD(handle: Long): Int = ncnn.ncnn_mat_get_d(asMat(handle))

fun matC(handle: Long): Int = ncnn.ncnn_mat_get_c(asMat(handle))

fun matN(handle: Long): Int = ncnn.ncnn_mat_get_n(asMat(handle))

fun matElementSize(handle: Long): Int = ncnn.ncnn_mat_get_elemsize(asMat(handle)).toInt()

fun matElementPack(handle: Long): Int = ncnn.ncnn_mat_get_elempack(asMat(handle))

fun matChannelStep(handle: Long): Long = ncnn.ncnn_mat_get_cstep(asMat(handle)).toLong()

fun matDataSize(handle: Long): Long = asMat(handle).matDataSize()

fun matFill(handle: Long, value: Float) {
    ncnn.ncnn_mat_fill_float(asMat(handle), value)
}

fun matClone(handle: Long): Long = requireMat(ncnn.ncnn_mat_clone(asMat(handle), null), "Mat.clone")

fun matReshape(handle: Long, w: Int, h: Int, c: Int): Long {
    val mat = asMat(handle)
    val out = when {
        c > 0 -> ncnn.ncnn_mat_reshape_3d(mat, w, h, c, null)
        h > 0 -> ncnn.ncnn_mat_reshape_2d(mat, w, h, null)
        else -> ncnn.ncnn_mat_reshape_1d(mat, w, null)
    }
    return requireMat(out, "Mat.reshape")
}

fun matConvertPacking(handle: Long, elementPack: Int): Long = memScoped {
    val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
    ncnn.ncnn_convert_packing(asMat(handle), out.ptr, elementPack, null)
    requireMat(out.value, "Mat.convertPacking")
}

fun matFlatten(handle: Long): Long = memScoped {
    val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
    ncnn.ncnn_flatten(asMat(handle), out.ptr, null)
    requireMat(out.value, "Mat.flatten")
}

private fun requireFloat32Pack1(mat: ncnn.ncnn_mat_t, operation: String) {
    if (ncnn.ncnn_mat_get_elemsize(mat).toLong() != 4L || ncnn.ncnn_mat_get_elempack(mat) != 1) {
        throw IllegalStateException(
            "$operation requires fp32 pack1 data; use convertPacking(1) or disable fp16/bf16/int8 storage",
        )
    }
}

fun matReadFloats(handle: Long): FloatArray {
    val mat = asMat(handle)
    requireFloat32Pack1(mat, "Mat.readFloats")
    val perChannel = mat.matPerChannelElems()
    val channels = mat.matChannelCount()
    if (perChannel > Int.MAX_VALUE || channels > Int.MAX_VALUE / maxOf(perChannel, 1L)) {
        throw IllegalStateException("Mat is too large to expose as FloatArray")
    }
    val count = (perChannel * channels).toInt()
    return if (ncnn.ncnn_mat_get_data(mat) == null) FloatArray(count) else mat.readFloatData(count)
}

fun matWriteFloats(handle: Long, values: FloatArray) {
    val mat = asMat(handle)
    requireFloat32Pack1(mat, "Mat.writeFloats")
    val total = mat.matPerChannelElems() * mat.matChannelCount()
    if (values.size.toLong() > total) throw IllegalArgumentException("source array is larger than the tensor")
    if (values.isEmpty()) return
    mat.writeFloatData(values)
}

fun matReadBytes(handle: Long): ByteArray {
    val mat = asMat(handle)
    val elemsize = ncnn.ncnn_mat_get_elemsize(mat).toLong()
    val perChannelBytes = mat.matPerChannelElems() * elemsize
    val channels = mat.matChannelCount()
    if (perChannelBytes > Int.MAX_VALUE || channels > Int.MAX_VALUE / maxOf(perChannelBytes, 1L)) {
        throw IllegalStateException("Mat is too large to expose as ByteArray")
    }
    val size = (perChannelBytes * channels).toInt()
    return if (ncnn.ncnn_mat_get_data(mat) == null) ByteArray(size) else mat.readRawBytes(size)
}

fun matWriteBytes(handle: Long, bytes: ByteArray) {
    val mat = asMat(handle)
    val elemsize = ncnn.ncnn_mat_get_elemsize(mat).toLong()
    val total = mat.matPerChannelElems() * elemsize * mat.matChannelCount()
    if (bytes.size.toLong() > total) throw IllegalArgumentException("source array is larger than the tensor")
    if (bytes.isEmpty()) return
    mat.writeRawBytes(bytes)
}

fun matSubstractMeanNormalize(handle: Long, mean: FloatArray?, norm: FloatArray?) {
    val mat = asMat(handle)
    val meanValues = mean?.takeIf { it.isNotEmpty() }
    val normValues = norm?.takeIf { it.isNotEmpty() }
    when {
        meanValues != null && normValues != null -> meanValues.usePinned { m ->
            normValues.usePinned { n ->
                ncnn.ncnn_mat_substract_mean_normalize(mat, m.addressOf(0), n.addressOf(0))
            }
        }

        meanValues != null -> meanValues.usePinned { m ->
            ncnn.ncnn_mat_substract_mean_normalize(mat, m.addressOf(0), null)
        }

        normValues != null -> normValues.usePinned { n ->
            ncnn.ncnn_mat_substract_mean_normalize(mat, null, n.addressOf(0))
        }

        else -> ncnn.ncnn_mat_substract_mean_normalize(mat, null, null)
    }
}

fun matFromPixels(pixels: ByteArray, type: Int, w: Int, h: Int, stride: Int): Long {
    if (pixels.isEmpty()) return 0L
    return pixels.usePinned { pinned ->
        requireMat(
            ncnn.ncnn_mat_from_pixels(pinned.addressOf(0).reinterpret<UByteVar>(), type, w, h, stride, null),
            "Mat.fromPixels",
        )
    }
}

fun matFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long {
    if (pixels.isEmpty()) return 0L
    return pixels.usePinned { pinned ->
        requireMat(
            ncnn.ncnn_mat_from_pixels_resize(
                pinned.addressOf(0).reinterpret<UByteVar>(),
                type,
                w,
                h,
                stride,
                targetWidth,
                targetHeight,
                null,
            ),
            "Mat.fromPixelsResize",
        )
    }
}

fun matToPixels(handle: Long, type: Int, stride: Int): ByteArray {
    val channels = pixelChannels(type)
    if (channels == 0) throw IllegalArgumentException("unsupported pixel type")
    val mat = asMat(handle)
    val w = ncnn.ncnn_mat_get_w(mat)
    val h = ncnn.ncnn_mat_get_h(mat)
    val actualStride = if (stride <= 0) w * channels else stride
    val size = actualStride.toLong() * h
    if (size > Int.MAX_VALUE) throw IllegalStateException("pixel buffer would be too large")
    val out = ByteArray(size.toInt())
    if (out.isNotEmpty()) {
        out.usePinned { pinned ->
            ncnn.ncnn_mat_to_pixels(mat, pinned.addressOf(0).reinterpret<UByteVar>(), type, actualStride)
        }
    }
    return out
}

fun matToPixelsResize(handle: Long, type: Int, targetWidth: Int, targetHeight: Int, stride: Int): ByteArray {
    val channels = pixelChannels(type)
    if (channels == 0) throw IllegalArgumentException("unsupported pixel type")
    val actualStride = if (stride <= 0) targetWidth * channels else stride
    val size = actualStride.toLong() * targetHeight
    if (size > Int.MAX_VALUE) throw IllegalStateException("pixel buffer would be too large")
    val mat = asMat(handle)
    val out = ByteArray(size.toInt())
    if (out.isNotEmpty()) {
        out.usePinned { pinned ->
            ncnn.ncnn_mat_to_pixels_resize(mat, pinned.addressOf(0).reinterpret<UByteVar>(), type, targetWidth, targetHeight, actualStride)
        }
    }
    return out
}
