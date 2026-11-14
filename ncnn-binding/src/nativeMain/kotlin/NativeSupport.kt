@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package io.github.qingshu.ncnn.binding

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import ncnn.ncnn_extractor_t
import ncnn.ncnn_mat_t
import ncnn.ncnn_net_t
import platform.posix.free
import platform.posix.malloc

/** Error raised by the binding when ncnn returns a non-zero status. */
class NcnnBindingException(message: String, val code: Int) : Exception(message)

fun checkStatus(status: Int, operation: String) {
    if (status != 0) throw NcnnBindingException("$operation failed with ncnn status $status", status)
}

fun ncnn_mat_t?.orThrow(operation: String): ncnn_mat_t = this ?: throw NcnnBindingException("$operation failed: ncnn returned no tensor", -1)

fun CPointer<*>.toHandle(): Long = rawValue.toLong()

fun <T : CPointed> Long.toNativePointer(): CPointer<T> = toCPointer<T>() ?: error("null native handle")

fun asNet(handle: Long): ncnn_net_t = handle.toNativePointer()

fun asMat(handle: Long): ncnn_mat_t = handle.toNativePointer()

fun asExtractor(handle: Long): ncnn_extractor_t = handle.toNativePointer()

fun requireMat(mat: ncnn_mat_t?, operation: String): Long = mat.orThrow(operation).toHandle()

fun pixelChannels(type: Int): Int = when (type) {
    1, 2 -> 3
    3 -> 1
    4, 5 -> 4
    else -> 0
}

/* ------------------------------------------------------------- mat access */

/** Elements per channel (ncnn pads each channel via cstep). */
fun ncnn_mat_t.matPerChannelElems(): Long {
    val w = ncnn.ncnn_mat_get_w(this).coerceAtLeast(0)
    val h = ncnn.ncnn_mat_get_h(this).coerceAtLeast(1)
    val d = ncnn.ncnn_mat_get_d(this).coerceAtLeast(1)
    return w.toLong() * h * d
}

fun ncnn_mat_t.matChannelCount(): Long {
    val c = ncnn.ncnn_mat_get_c(this).coerceAtLeast(1)
    val n = ncnn.ncnn_mat_get_n(this).coerceAtLeast(1)
    return c.toLong() * n
}

/** Logical data size in bytes, without ncnn's per-channel alignment padding. */
fun ncnn_mat_t.matDataSize(): Long {
    val elemsize = ncnn.ncnn_mat_get_elemsize(this).toLong()
    return matPerChannelElems() * elemsize * matChannelCount()
}

fun ncnn_mat_t.readFloatData(count: Int): FloatArray {
    val data: COpaquePointer = ncnn.ncnn_mat_get_data(this) ?: return FloatArray(0)
    val pointer = data.reinterpret<FloatVar>()
    val perChannel = matPerChannelElems().toInt()
    val channels = matChannelCount().toInt()
    val step = ncnn.ncnn_mat_get_cstep(this).toLong().toInt()
    val out = FloatArray(count)
    var copied = 0
    for (channel in 0 until channels) {
        if (copied >= count) break
        val n = minOf(perChannel, count - copied)
        val base = channel * step
        for (index in 0 until n) {
            out[copied + index] = pointer[base + index]
        }
        copied += n
    }
    return out
}

fun ncnn_mat_t.writeFloatData(values: FloatArray) {
    val data: COpaquePointer = ncnn.ncnn_mat_get_data(this) ?: return
    val pointer = data.reinterpret<FloatVar>()
    val perChannel = matPerChannelElems().toInt()
    val channels = matChannelCount().toInt()
    val step = ncnn.ncnn_mat_get_cstep(this).toLong().toInt()
    var copied = 0
    for (channel in 0 until channels) {
        if (copied >= values.size) break
        val n = minOf(perChannel, values.size - copied)
        val base = channel * step
        for (index in 0 until n) {
            pointer[base + index] = values[copied + index]
        }
        copied += n
    }
}

fun ncnn_mat_t.readRawBytes(size: Int): ByteArray {
    val data: COpaquePointer = ncnn.ncnn_mat_get_data(this) ?: return ByteArray(0)
    val elemsize = ncnn.ncnn_mat_get_elemsize(this).toLong().toInt()
    val perChannelBytes = matPerChannelElems().toInt() * elemsize
    val channels = matChannelCount().toInt()
    val stepBytes = ncnn.ncnn_mat_get_cstep(this).toLong().toInt() * elemsize
    val out = ByteArray(size)
    var copied = 0
    for (channel in 0 until channels) {
        if (copied >= size) break
        val n = minOf(perChannelBytes, size - copied)
        val base = channel * stepBytes
        for (index in 0 until n) {
            out[copied + index] = data.reinterpret<ByteVar>()[base + index]
        }
        copied += n
    }
    return out
}

fun ncnn_mat_t.writeRawBytes(bytes: ByteArray) {
    val data: COpaquePointer = ncnn.ncnn_mat_get_data(this) ?: return
    val elemsize = ncnn.ncnn_mat_get_elemsize(this).toLong().toInt()
    val perChannelBytes = matPerChannelElems().toInt() * elemsize
    val channels = matChannelCount().toInt()
    val stepBytes = ncnn.ncnn_mat_get_cstep(this).toLong().toInt() * elemsize
    val pointer = data.reinterpret<ByteVar>()
    var copied = 0
    for (channel in 0 until channels) {
        if (copied >= bytes.size) break
        val n = minOf(perChannelBytes, bytes.size - copied)
        val base = channel * stepBytes
        for (index in 0 until n) {
            pointer[base + index] = bytes[copied + index]
        }
        copied += n
    }
}

/* ---------------------------------------------------------- model buffers */

private val netModelBuffers: MutableMap<Long, COpaquePointer> = mutableMapOf()

fun storeNetBuffer(handle: Long, buffer: COpaquePointer) {
    netModelBuffers.remove(handle)?.let { free(it) }
    netModelBuffers[handle] = buffer
}

fun releaseNetBuffer(handle: Long) {
    netModelBuffers.remove(handle)?.let { free(it) }
}

fun allocate(size: Int): COpaquePointer? = malloc(size.convert())
