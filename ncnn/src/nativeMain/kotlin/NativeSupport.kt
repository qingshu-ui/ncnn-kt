package io.github.qingshu.ncnn

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import ncnn.ncnn_mat_t

@OptIn(ExperimentalForeignApi::class)
internal fun checkStatus(status: Int, operation: String) {
    if (status != 0) {
        throw NcnnException("$operation failed with ncnn status $status", status)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t?.orThrow(operation: String): ncnn_mat_t = this ?: throw NcnnException("$operation failed: ncnn returned no tensor")

/** Elements per channel (ncnn pads each channel via cstep). */
@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t.matPerChannelElems(): Long {
    val w = ncnn.ncnn_mat_get_w(this).coerceAtLeast(0)
    val h = ncnn.ncnn_mat_get_h(this).coerceAtLeast(1)
    val d = ncnn.ncnn_mat_get_d(this).coerceAtLeast(1)
    return w.toLong() * h * d
}

@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t.matChannelCount(): Long {
    val c = ncnn.ncnn_mat_get_c(this).coerceAtLeast(1)
    val n = ncnn.ncnn_mat_get_n(this).coerceAtLeast(1)
    return c.toLong() * n
}

/** Logical data size in bytes, without ncnn's per-channel alignment padding. */
@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t.matDataSize(): Long {
    val elemsize = ncnn.ncnn_mat_get_elemsize(this).toLong()
    return matPerChannelElems() * elemsize * matChannelCount()
}

@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t.readFloatData(count: Int): FloatArray {
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

@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t.writeFloatData(values: FloatArray) {
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

@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t.readRawBytes(size: Int): ByteArray {
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

@OptIn(ExperimentalForeignApi::class)
internal fun ncnn_mat_t.writeRawBytes(bytes: ByteArray) {
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

@OptIn(ExperimentalForeignApi::class)
internal fun loadParamMemory(net: ncnn.ncnn_net_t, data: ByteArray) {
    checkStatus(
        ncnn.ncnn_net_load_param_memory(net, data.decodeToString()),
        "Net.loadParam(memory)",
    )
}

@OptIn(ExperimentalForeignApi::class)
internal fun loadModelMemory(net: ncnn.ncnn_net_t, data: ByteArray): Long {
    val consumed = if (data.isEmpty()) {
        byteArrayOf(0).usePinned { pinned ->
            ncnn.ncnn_net_load_model_memory(net, pinned.addressOf(0).reinterpret())
        }.toLong()
    } else {
        data.usePinned { pinned ->
            ncnn.ncnn_net_load_model_memory(net, pinned.addressOf(0).reinterpret())
        }.toLong()
    }
    if (consumed == 0L && data.isNotEmpty()) {
        throw NcnnException("Net.loadModel(memory) failed with ncnn status -1", -1)
    }
    return consumed
}
