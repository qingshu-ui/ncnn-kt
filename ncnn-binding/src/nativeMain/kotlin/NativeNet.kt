@file:OptIn(ExperimentalForeignApi::class)

package io.github.qingshu.ncnn.binding

import cnames.structs.__ncnn_mat_t
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.free
import platform.posix.memcpy

/* -------------------------------------------------------------- global */

fun ncnnVersionString(): String? = ncnn.ncnn_version()?.toKString()

fun ncnnVersionNumber(): Int = ncnn.ncnn_version_number()

/* ----------------------------------------------------------------- net */

fun netCreate(): Long = (
    ncnn.ncnn_net_create()
        ?: throw OutOfMemoryError("Net.create failed: ncnn could not allocate the tensor")
    ).toHandle()

fun netDestroy(handle: Long) {
    if (handle == 0L) return
    ncnn.ncnn_net_destroy(asNet(handle))
    releaseNetBuffer(handle)
}

fun netGetOptionInt(handle: Long, option: Int): Int {
    val native = ncnn.ncnn_net_get_option(asNet(handle))
    return when (option) {
        0 -> ncnn.ncnn_option_get_num_threads(native)
        1 -> ncnn.ncnn_option_get_use_local_pool_allocator(native)
        2 -> ncnn.ncnn_option_get_use_winograd_convolution(native)
        3 -> ncnn.ncnn_option_get_use_sgemm_convolution(native)
        4 -> ncnn.ncnn_option_get_use_packing_layout(native)
        5 -> ncnn.ncnn_option_get_use_fp16_packed(native)
        6 -> ncnn.ncnn_option_get_use_fp16_storage(native)
        7 -> ncnn.ncnn_option_get_use_fp16_arithmetic(native)
        8 -> ncnn.ncnn_option_get_use_int8_packed(native)
        9 -> ncnn.ncnn_option_get_use_int8_storage(native)
        10 -> ncnn.ncnn_option_get_use_int8_arithmetic(native)
        11 -> ncnn.ncnn_option_get_use_int16_packed(native)
        12 -> ncnn.ncnn_option_get_use_int16_storage(native)
        13 -> ncnn.ncnn_option_get_use_bf16_packed(native)
        14 -> ncnn.ncnn_option_get_use_bf16_storage(native)
        else -> 0
    }
}

fun netSetOptionInt(handle: Long, option: Int, value: Int) {
    val native = ncnn.ncnn_net_get_option(asNet(handle))
    when (option) {
        0 -> ncnn.ncnn_option_set_num_threads(native, value)
        1 -> ncnn.ncnn_option_set_use_local_pool_allocator(native, value)
        2 -> ncnn.ncnn_option_set_use_winograd_convolution(native, value)
        3 -> ncnn.ncnn_option_set_use_sgemm_convolution(native, value)
        4 -> ncnn.ncnn_option_set_use_packing_layout(native, value)
        5 -> ncnn.ncnn_option_set_use_fp16_packed(native, value)
        6 -> ncnn.ncnn_option_set_use_fp16_storage(native, value)
        7 -> ncnn.ncnn_option_set_use_fp16_arithmetic(native, value)
        8 -> ncnn.ncnn_option_set_use_int8_packed(native, value)
        9 -> ncnn.ncnn_option_set_use_int8_storage(native, value)
        10 -> ncnn.ncnn_option_set_use_int8_arithmetic(native, value)
        11 -> ncnn.ncnn_option_set_use_int16_packed(native, value)
        12 -> ncnn.ncnn_option_set_use_int16_storage(native, value)
        13 -> ncnn.ncnn_option_set_use_bf16_packed(native, value)
        14 -> ncnn.ncnn_option_set_use_bf16_storage(native, value)
        else -> throw IllegalArgumentException("unknown ncnn option id")
    }
}

fun netLoadParamPath(handle: Long, path: String) = loadParamPath(asNet(handle), path)

fun netLoadModelPath(handle: Long, path: String) = loadModelPath(asNet(handle), path)

fun netLoadParamMemory(handle: Long, data: ByteArray) {
    checkStatus(
        ncnn.ncnn_net_load_param_memory(asNet(handle), data.decodeToString()),
        "Net.loadParam(memory)",
    )
}

fun netLoadModelMemory(handle: Long, data: ByteArray): Long {
    if (data.isEmpty()) return 0L
    val raw = allocate(data.size)
        ?: throw OutOfMemoryError("Net.loadModel(memory) failed: ncnn could not allocate the tensor")
    val buffer = raw.reinterpret<ByteVar>()
    data.usePinned { pinned -> memcpy(buffer, pinned.addressOf(0), data.size.convert()) }
    val consumed = try {
        ncnn.ncnn_net_load_model_memory(asNet(handle), raw.reinterpret()).toLong()
    } catch (error: Throwable) {
        free(raw)
        throw error
    }
    if (consumed == 0L) {
        free(raw)
        throw NcnnBindingException("Net.loadModel(memory) failed with ncnn status -1", -1)
    }
    storeNetBuffer(handle, raw)
    return consumed
}

fun netInputCount(handle: Long): Int = ncnn.ncnn_net_get_input_count(asNet(handle))

fun netOutputCount(handle: Long): Int = ncnn.ncnn_net_get_output_count(asNet(handle))

fun netInputName(handle: Long, index: Int): String? = ncnn.ncnn_net_get_input_name(asNet(handle), index)?.toKString()

fun netOutputName(handle: Long, index: Int): String? = ncnn.ncnn_net_get_output_name(asNet(handle), index)?.toKString()

fun netInputIndex(handle: Long, index: Int): Int = ncnn.ncnn_net_get_input_index(asNet(handle), index)

fun netOutputIndex(handle: Long, index: Int): Int = ncnn.ncnn_net_get_output_index(asNet(handle), index)

fun netClear(handle: Long) {
    ncnn.ncnn_net_clear(asNet(handle))
    releaseNetBuffer(handle)
}

/* ----------------------------------------------------------- extractor */

fun extractorCreate(net: Long): Long = (
    ncnn.ncnn_extractor_create(asNet(net))
        ?: throw OutOfMemoryError("Net.createExtractor failed: ncnn could not allocate the tensor")
    ).toHandle()

fun extractorDestroy(handle: Long) {
    if (handle != 0L) ncnn.ncnn_extractor_destroy(asExtractor(handle))
}

fun extractorInputName(handle: Long, name: String, mat: Long) {
    checkStatus(
        ncnn.ncnn_extractor_input(asExtractor(handle), name, asMat(mat)),
        "Extractor.input(name)",
    )
}

fun extractorInputIndex(handle: Long, index: Int, mat: Long) {
    checkStatus(
        ncnn.ncnn_extractor_input_index(asExtractor(handle), index, asMat(mat)),
        "Extractor.input(index)",
    )
}

fun extractorExtractName(handle: Long, name: String): Long = memScoped {
    val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
    val status = ncnn.ncnn_extractor_extract(asExtractor(handle), name, out.ptr)
    if (status != 0) {
        out.value?.let { ncnn.ncnn_mat_destroy(it) }
        checkStatus(status, "Extractor.extract(name)")
    }
    requireMat(out.value, "Extractor.extract(name)")
}

fun extractorExtractIndex(handle: Long, index: Int): Long = memScoped {
    val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
    val status = ncnn.ncnn_extractor_extract_index(asExtractor(handle), index, out.ptr)
    if (status != 0) {
        out.value?.let { ncnn.ncnn_mat_destroy(it) }
        checkStatus(status, "Extractor.extract(index)")
    }
    requireMat(out.value, "Extractor.extract(index)")
}
