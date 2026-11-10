@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("UNUSED_PARAMETER")

package io.github.qingshu.ncnn

import cnames.structs.__ncnn_mat_t
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.android.JNIEnvVar
import platform.android.jbyteArray
import platform.android.jclass
import platform.android.jfloatArray
import platform.android.jstring
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

/* -------------------------------------------------------------- global */

@CName("JNI_OnLoad")
fun jniOnLoad(vm: CPointer<*>?, reserved: CPointer<*>?): Int = 0x00010006

@CName("Java_io_github_qingshu_ncnn_NcnnJni_ncnnVersionString")
fun ncnnVersionStringJni(env: CPointer<JNIEnvVar>, clazz: jclass?): jstring? = jniBoundary(env, null) {
    val table = env.jniTable() ?: return@jniBoundary null
    val version = ncnn.ncnn_version() ?: return@jniBoundary null
    jniNewStringUtf8(table, env, version)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_ncnnVersionNumberJni")
fun ncnnVersionNumberJni(env: CPointer<JNIEnvVar>, clazz: jclass?): Int = jniBoundary(env, 0) { ncnn.ncnn_version_number() }

/* ----------------------------------------------------------------- net */

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netCreate")
fun netCreateJni(env: CPointer<JNIEnvVar>, clazz: jclass?): Long = jniBoundary(env, 0L) {
    val net = ncnn.ncnn_net_create()
        ?: throw OutOfMemoryError("Net.create failed: ncnn could not allocate the tensor")
    net.toHandle()
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netDestroy")
fun netDestroyJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Unit = jniBoundary(env, Unit) {
    if (handle != 0L) {
        ncnn.ncnn_net_destroy(asNet(handle))
        releaseNetBuffer(handle)
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netGetOptionInt")
fun netGetOptionIntJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    option: Int,
): Int = jniBoundary(env, 0) {
    val opt = ncnn.ncnn_net_get_option(asNet(handle))
    when (option) {
        0 -> ncnn.ncnn_option_get_num_threads(opt)
        1 -> ncnn.ncnn_option_get_use_local_pool_allocator(opt)
        2 -> ncnn.ncnn_option_get_use_winograd_convolution(opt)
        3 -> ncnn.ncnn_option_get_use_sgemm_convolution(opt)
        4 -> ncnn.ncnn_option_get_use_packing_layout(opt)
        5 -> ncnn.ncnn_option_get_use_fp16_packed(opt)
        6 -> ncnn.ncnn_option_get_use_fp16_storage(opt)
        7 -> ncnn.ncnn_option_get_use_fp16_arithmetic(opt)
        8 -> ncnn.ncnn_option_get_use_int8_packed(opt)
        9 -> ncnn.ncnn_option_get_use_int8_storage(opt)
        10 -> ncnn.ncnn_option_get_use_int8_arithmetic(opt)
        11 -> ncnn.ncnn_option_get_use_int16_packed(opt)
        12 -> ncnn.ncnn_option_get_use_int16_storage(opt)
        13 -> ncnn.ncnn_option_get_use_bf16_packed(opt)
        14 -> ncnn.ncnn_option_get_use_bf16_storage(opt)
        else -> 0
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netSetOptionInt")
fun netSetOptionIntJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    option: Int,
    value: Int,
): Unit = jniBoundary(env, Unit) {
    val opt = ncnn.ncnn_net_get_option(asNet(handle))
    when (option) {
        0 -> ncnn.ncnn_option_set_num_threads(opt, value)
        1 -> ncnn.ncnn_option_set_use_local_pool_allocator(opt, value)
        2 -> ncnn.ncnn_option_set_use_winograd_convolution(opt, value)
        3 -> ncnn.ncnn_option_set_use_sgemm_convolution(opt, value)
        4 -> ncnn.ncnn_option_set_use_packing_layout(opt, value)
        5 -> ncnn.ncnn_option_set_use_fp16_packed(opt, value)
        6 -> ncnn.ncnn_option_set_use_fp16_storage(opt, value)
        7 -> ncnn.ncnn_option_set_use_fp16_arithmetic(opt, value)
        8 -> ncnn.ncnn_option_set_use_int8_packed(opt, value)
        9 -> ncnn.ncnn_option_set_use_int8_storage(opt, value)
        10 -> ncnn.ncnn_option_set_use_int8_arithmetic(opt, value)
        11 -> ncnn.ncnn_option_set_use_int16_packed(opt, value)
        12 -> ncnn.ncnn_option_set_use_int16_storage(opt, value)
        13 -> ncnn.ncnn_option_set_use_bf16_packed(opt, value)
        14 -> ncnn.ncnn_option_set_use_bf16_storage(opt, value)
        else -> throw IllegalArgumentException("unknown ncnn option id")
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadParamPath")
fun netLoadParamPathJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    path: jstring?,
): Unit = jniBoundary(env, Unit) {
    val table = env.jniTable() ?: return@jniBoundary
    val nativePath = jniString(table, env, path)
        ?: throw NcnnException("Net.loadParam(path) failed with ncnn status -1", -1)
    netLoadParamPath(asNet(handle), nativePath)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadParamMemory")
fun netLoadParamMemoryJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    data: jbyteArray?,
): Unit = jniBoundary(env, Unit) {
    val table = env.jniTable() ?: return@jniBoundary
    val bytes = jniByteArray(table, env, data) ?: return@jniBoundary
    loadParamMemory(asNet(handle), bytes)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadModelPath")
fun netLoadModelPathJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    path: jstring?,
): Unit = jniBoundary(env, Unit) {
    val table = env.jniTable() ?: return@jniBoundary
    val nativePath = jniString(table, env, path)
        ?: throw NcnnException("Net.loadModel(path) failed with ncnn status -1", -1)
    netLoadModelPath(asNet(handle), nativePath)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadModelMemory")
fun netLoadModelMemoryJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    data: jbyteArray?,
): Long = jniBoundary(env, 0L) {
    val table = env.jniTable() ?: return@jniBoundary 0L
    if (data == null) return@jniBoundary 0L
    val length = jniByteArrayLength(table, env, data)
    if (length == 0) return@jniBoundary 0L
    val raw = allocate(length)
        ?: throw OutOfMemoryError("Net.loadModel(memory) failed: ncnn could not allocate the tensor")
    val buffer = raw.reinterpret<ByteVar>()
    var stored = false
    try {
        table.pointed.GetByteArrayRegion!!(env, data, 0, length, buffer)
        val consumed = ncnn.ncnn_net_load_model_memory(asNet(handle), buffer.reinterpret()).toLong()
        if (consumed == 0L) throw NcnnException("Net.loadModel(memory) failed with ncnn status -1", -1)
        storeNetBuffer(handle, raw)
        stored = true
        consumed
    } finally {
        if (!stored) platform.posix.free(raw)
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netInputCount")
fun netInputCountJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_net_get_input_count(asNet(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netOutputCount")
fun netOutputCountJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_net_get_output_count(asNet(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netInputName")
fun netInputNameJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long, index: Int): jstring? = jniBoundary(env, null) {
    val table = env.jniTable() ?: return@jniBoundary null
    val name = ncnn.ncnn_net_get_input_name(asNet(handle), index) ?: return@jniBoundary null
    jniNewStringUtf8(table, env, name)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netOutputName")
fun netOutputNameJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long, index: Int): jstring? = jniBoundary(env, null) {
    val table = env.jniTable() ?: return@jniBoundary null
    val name = ncnn.ncnn_net_get_output_name(asNet(handle), index) ?: return@jniBoundary null
    jniNewStringUtf8(table, env, name)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netInputIndex")
fun netInputIndexJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long, index: Int): Int = jniBoundary(env, 0) { ncnn.ncnn_net_get_input_index(asNet(handle), index) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netOutputIndex")
fun netOutputIndexJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long, index: Int): Int = jniBoundary(env, 0) { ncnn.ncnn_net_get_output_index(asNet(handle), index) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netClear")
fun netClearJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Unit = jniBoundary(env, Unit) {
    val net = asNet(handle)
    ncnn.ncnn_net_clear(net)
    releaseNetBuffer(handle)
}

/* ----------------------------------------------------------- extractor */

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorCreate")
fun extractorCreateJni(env: CPointer<JNIEnvVar>, clazz: jclass?, net: Long): Long = jniBoundary(env, 0L) {
    val extractor = ncnn.ncnn_extractor_create(asNet(net))
        ?: throw OutOfMemoryError("Net.createExtractor failed: ncnn could not allocate the tensor")
    extractor.toHandle()
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorDestroy")
fun extractorDestroyJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Unit = jniBoundary(env, Unit) {
    if (handle != 0L) ncnn.ncnn_extractor_destroy(asExtractor(handle))
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorInputName")
fun extractorInputNameJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    name: jstring?,
    mat: Long,
): Unit = jniBoundary(env, Unit) {
    val table = env.jniTable() ?: return@jniBoundary
    val nativeName = jniString(table, env, name) ?: return@jniBoundary
    checkStatus(
        ncnn.ncnn_extractor_input(asExtractor(handle), nativeName, asMat(mat)),
        "Extractor.input(name)",
    )
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorInputIndex")
fun extractorInputIndexJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    index: Int,
    mat: Long,
): Unit = jniBoundary(env, Unit) {
    checkStatus(
        ncnn.ncnn_extractor_input_index(asExtractor(handle), index, asMat(mat)),
        "Extractor.input(index)",
    )
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorExtractName")
fun extractorExtractNameJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    name: jstring?,
): Long = jniBoundary(env, 0L) {
    val table = env.jniTable() ?: return@jniBoundary 0L
    val nativeName = jniString(table, env, name) ?: return@jniBoundary 0L
    memScoped {
        val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
        val status = ncnn.ncnn_extractor_extract(asExtractor(handle), nativeName, out.ptr)
        if (status != 0) {
            out.value?.let { ncnn.ncnn_mat_destroy(it) }
            checkStatus(status, "Extractor.extract(name)")
        }
        requireMat(out.value, "Extractor.extract(name)")
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorExtractIndex")
fun extractorExtractIndexJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    index: Int,
): Long = jniBoundary(env, 0L) {
    memScoped {
        val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
        val status = ncnn.ncnn_extractor_extract_index(asExtractor(handle), index, out.ptr)
        if (status != 0) {
            out.value?.let { ncnn.ncnn_mat_destroy(it) }
            checkStatus(status, "Extractor.extract(index)")
        }
        requireMat(out.value, "Extractor.extract(index)")
    }
}

/* ----------------------------------------------------------------- mat */

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matCreate")
fun matCreateJni(env: CPointer<JNIEnvVar>, clazz: jclass?, w: Int, h: Int, c: Int): Long = jniBoundary(env, 0L) {
    val mat = when {
        c > 0 -> ncnn.ncnn_mat_create_3d(w, h, c, null)
        h > 0 -> ncnn.ncnn_mat_create_2d(w, h, null)
        else -> ncnn.ncnn_mat_create_1d(w, null)
    } ?: throw OutOfMemoryError("Mat.create failed: ncnn could not allocate the tensor")
    if (ncnn.ncnn_mat_get_data(mat) == null && ncnn.ncnn_mat_get_cstep(mat).toLong() > 0) {
        ncnn.ncnn_mat_destroy(mat)
        throw OutOfMemoryError("Mat.create failed: ncnn could not allocate the tensor")
    }
    mat.toHandle()
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matDestroy")
fun matDestroyJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Unit = jniBoundary(env, Unit) {
    if (handle != 0L) ncnn.ncnn_mat_destroy(asMat(handle))
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matDims")
fun matDimsJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_dims(asMat(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matW")
fun matWJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_w(asMat(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matH")
fun matHJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_h(asMat(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matD")
fun matDJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_d(asMat(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matC")
fun matCJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_c(asMat(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matN")
fun matNJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_n(asMat(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matElementSize")
fun matElementSizeJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_elemsize(asMat(handle)).toInt() }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matElementPack")
fun matElementPackJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Int = jniBoundary(env, 0) { ncnn.ncnn_mat_get_elempack(asMat(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matChannelStep")
fun matChannelStepJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Long = jniBoundary(env, 0L) { ncnn.ncnn_mat_get_cstep(asMat(handle)).toLong() }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matDataSize")
fun matDataSizeJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Long = jniBoundary(env, 0L) { asMat(handle).matDataSize() }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFill")
fun matFillJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long, value: Float): Unit = jniBoundary(env, Unit) { ncnn.ncnn_mat_fill_float(asMat(handle), value) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matClone")
fun matCloneJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Long = jniBoundary(env, 0L) {
    requireMat(ncnn.ncnn_mat_clone(asMat(handle), null), "Mat.clone")
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matReshape")
fun matReshapeJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long, w: Int, h: Int, c: Int): Long = jniBoundary(env, 0L) {
    val mat = asMat(handle)
    val out = when {
        c > 0 -> ncnn.ncnn_mat_reshape_3d(mat, w, h, c, null)
        h > 0 -> ncnn.ncnn_mat_reshape_2d(mat, w, h, null)
        else -> ncnn.ncnn_mat_reshape_1d(mat, w, null)
    }
    requireMat(out, "Mat.reshape")
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matConvertPacking")
fun matConvertPackingJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long, elementPack: Int): Long = jniBoundary(env, 0L) {
    memScoped {
        val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
        ncnn.ncnn_convert_packing(asMat(handle), out.ptr, elementPack, null)
        requireMat(out.value, "Mat.convertPacking")
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFlatten")
fun matFlattenJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): Long = jniBoundary(env, 0L) {
    memScoped {
        val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
        ncnn.ncnn_flatten(asMat(handle), out.ptr, null)
        requireMat(out.value, "Mat.flatten")
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matReadFloats")
fun matReadFloatsJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): jfloatArray? = jniBoundary(env, null) {
    val table = env.jniTable() ?: return@jniBoundary null
    val mat = asMat(handle)
    if (ncnn.ncnn_mat_get_elemsize(mat).toLong() != 4L || ncnn.ncnn_mat_get_elempack(mat) != 1) {
        throw IllegalStateException(
            "Mat.readFloats requires fp32 pack1 data; use convertPacking(1) or disable fp16/bf16/int8 storage",
        )
    }
    val perChannel = mat.matPerChannelElems()
    val channels = mat.matChannelCount()
    if (perChannel > Int.MAX_VALUE || channels > Int.MAX_VALUE / maxOf(perChannel, 1L)) {
        throw IllegalStateException("Mat is too large to expose as FloatArray")
    }
    val count = (perChannel * channels).toInt()
    val values = if (ncnn.ncnn_mat_get_data(mat) == null) FloatArray(count) else mat.readFloatData(count)
    val result = table.pointed.NewFloatArray!!(env, count) ?: return@jniBoundary null
    if (count > 0) {
        values.usePinned {
            table.pointed.SetFloatArrayRegion!!(env, result, 0, count, it.addressOf(0))
        }
    }
    result
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matWriteFloats")
fun matWriteFloatsJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    values: jfloatArray?,
): Unit = jniBoundary(env, Unit) {
    val table = env.jniTable() ?: return@jniBoundary
    val mat = asMat(handle)
    if (ncnn.ncnn_mat_get_elemsize(mat).toLong() != 4L || ncnn.ncnn_mat_get_elempack(mat) != 1) {
        throw IllegalStateException(
            "Mat.writeFloats requires fp32 pack1 data; use convertPacking(1) or disable fp16/bf16/int8 storage",
        )
    }
    val total = mat.matPerChannelElems() * mat.matChannelCount()
    val length = jniFloatArrayLength(table, env, values)
    if (length.toLong() > total) throw IllegalArgumentException("source array is larger than the tensor")
    if (length == 0) return@jniBoundary
    val source = FloatArray(length)
    source.usePinned {
        table.pointed.GetFloatArrayRegion!!(env, values, 0, length, it.addressOf(0))
    }
    mat.writeFloatData(source)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matReadBytes")
fun matReadBytesJni(env: CPointer<JNIEnvVar>, clazz: jclass?, handle: Long): jbyteArray? = jniBoundary(env, null) {
    val table = env.jniTable() ?: return@jniBoundary null
    val mat = asMat(handle)
    val elemsize = ncnn.ncnn_mat_get_elemsize(mat).toLong()
    val perChannelBytes = mat.matPerChannelElems() * elemsize
    val channels = mat.matChannelCount()
    if (perChannelBytes > Int.MAX_VALUE || channels > Int.MAX_VALUE / maxOf(perChannelBytes, 1L)) {
        throw IllegalStateException("Mat is too large to expose as ByteArray")
    }
    val size = (perChannelBytes * channels).toInt()
    val bytes = if (ncnn.ncnn_mat_get_data(mat) == null) ByteArray(size) else mat.readRawBytes(size)
    val result = table.pointed.NewByteArray!!(env, size) ?: return@jniBoundary null
    if (size > 0) {
        bytes.usePinned {
            table.pointed.SetByteArrayRegion!!(env, result, 0, size, it.addressOf(0))
        }
    }
    result
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matWriteBytes")
fun matWriteBytesJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    bytes: jbyteArray?,
): Unit = jniBoundary(env, Unit) {
    val table = env.jniTable() ?: return@jniBoundary
    val mat = asMat(handle)
    val elemsize = ncnn.ncnn_mat_get_elemsize(mat).toLong()
    val perChannelBytes = mat.matPerChannelElems() * elemsize
    val total = perChannelBytes * mat.matChannelCount()
    val length = jniByteArrayLength(table, env, bytes)
    if (length.toLong() > total) throw IllegalArgumentException("source array is larger than the tensor")
    if (length == 0) return@jniBoundary
    val source = ByteArray(length)
    source.usePinned {
        table.pointed.GetByteArrayRegion!!(env, bytes, 0, length, it.addressOf(0))
    }
    mat.writeRawBytes(source)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matSubstractMeanNormalize")
fun matSubstractMeanNormalizeJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    mean: jfloatArray?,
    norm: jfloatArray?,
): Unit = jniBoundary(env, Unit) {
    val table = env.jniTable() ?: return@jniBoundary
    val mat = asMat(handle)
    val meanValues = jniFloatArray(table, env, mean)
    val normValues = jniFloatArray(table, env, norm)
    fun apply(meanPtr: CPointer<FloatVar>?, normPtr: CPointer<FloatVar>?) {
        ncnn.ncnn_mat_substract_mean_normalize(mat, meanPtr, normPtr)
    }
    when {
        meanValues != null && normValues != null -> meanValues.usePinned { mp ->
            normValues.usePinned { np ->
                apply(mp.addressOf(0), np.addressOf(0))
            }
        }

        meanValues != null -> meanValues.usePinned { mp -> apply(mp.addressOf(0), null) }

        normValues != null -> normValues.usePinned { np -> apply(null, np.addressOf(0)) }

        else -> apply(null, null)
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFromPixels")
fun matFromPixelsJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    pixels: jbyteArray?,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
): Long = jniBoundary(env, 0L) {
    val table = env.jniTable() ?: return@jniBoundary 0L
    if (pixels == null) return@jniBoundary 0L
    val buffer = table.pointed.GetByteArrayElements!!(env, pixels, null)
        ?: throw OutOfMemoryError("Mat.fromPixels failed: ncnn could not allocate the tensor")
    try {
        requireMat(
            ncnn.ncnn_mat_from_pixels(buffer.reinterpret(), type, w, h, stride, null),
            "Mat.fromPixels",
        )
    } finally {
        table.pointed.ReleaseByteArrayElements!!(env, pixels, buffer, 2)
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFromPixelsResize")
fun matFromPixelsResizeJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    pixels: jbyteArray?,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long = jniBoundary(env, 0L) {
    val table = env.jniTable() ?: return@jniBoundary 0L
    if (pixels == null) return@jniBoundary 0L
    val buffer = table.pointed.GetByteArrayElements!!(env, pixels, null)
        ?: throw OutOfMemoryError("Mat.fromPixelsResize failed: ncnn could not allocate the tensor")
    try {
        requireMat(
            ncnn.ncnn_mat_from_pixels_resize(
                buffer.reinterpret(),
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
    } finally {
        table.pointed.ReleaseByteArrayElements!!(env, pixels, buffer, 2)
    }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matToPixels")
fun matToPixelsJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    type: Int,
    stride: Int,
): jbyteArray? = jniBoundary(env, null) {
    val table = env.jniTable() ?: return@jniBoundary null
    val channels = pixelChannels(type)
    if (channels == 0) throw IllegalArgumentException("unsupported pixel type")
    val mat = asMat(handle)
    val w = ncnn.ncnn_mat_get_w(mat)
    val h = ncnn.ncnn_mat_get_h(mat)
    val actualStride = if (stride <= 0) w * channels else stride
    val size = actualStride.toLong() * h
    if (size > Int.MAX_VALUE) throw IllegalStateException("pixel buffer would be too large")
    val result = table.pointed.NewByteArray!!(env, size.toInt()) ?: return@jniBoundary null
    val buffer = table.pointed.GetByteArrayElements!!(env, result, null) ?: return@jniBoundary null
    try {
        ncnn.ncnn_mat_to_pixels(mat, buffer.reinterpret(), type, actualStride)
    } finally {
        table.pointed.ReleaseByteArrayElements!!(env, result, buffer, 0)
    }
    result
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matToPixelsResize")
fun matToPixelsResizeJni(
    env: CPointer<JNIEnvVar>,
    clazz: jclass?,
    handle: Long,
    type: Int,
    targetWidth: Int,
    targetHeight: Int,
    stride: Int,
): jbyteArray? = jniBoundary(env, null) {
    val table = env.jniTable() ?: return@jniBoundary null
    val channels = pixelChannels(type)
    if (channels == 0) throw IllegalArgumentException("unsupported pixel type")
    val actualStride = if (stride <= 0) targetWidth * channels else stride
    val size = actualStride.toLong() * targetHeight
    if (size > Int.MAX_VALUE) throw IllegalStateException("pixel buffer would be too large")
    val mat = asMat(handle)
    val result = table.pointed.NewByteArray!!(env, size.toInt()) ?: return@jniBoundary null
    val buffer = table.pointed.GetByteArrayElements!!(env, result, null) ?: return@jniBoundary null
    try {
        ncnn.ncnn_mat_to_pixels_resize(mat, buffer.reinterpret(), type, targetWidth, targetHeight, actualStride)
    } finally {
        table.pointed.ReleaseByteArrayElements!!(env, result, buffer, 0)
    }
    result
}
