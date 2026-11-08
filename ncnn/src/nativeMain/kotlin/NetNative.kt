package io.github.qingshu.ncnn

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import ncnn.ncnn_net_t
import platform.posix.free
import platform.posix.malloc
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal class NetNative : Net {
    private var handle: ncnn_net_t? = ncnn.ncnn_net_create()
    private var closed: Boolean = false
    private var modelBuffer: kotlinx.cinterop.CPointer<ByteVar>? = null
    private val extractors: MutableSet<ExtractorNative> = mutableSetOf()

    internal fun rawHandle(): ncnn_net_t {
        check(!closed) { "Net is closed" }
        return handle ?: throw NcnnException("Net handle is null")
    }

    internal fun ensureOpen() {
        check(!closed) { "Net is closed" }
    }

    internal fun onExtractorClosed(extractor: ExtractorNative) {
        extractors.remove(extractor)
    }

    internal fun inputBlobIndex(index: Int): Int {
        require(index in 0 until inputCount) { "input index $index is out of range 0..${inputCount - 1}" }
        return ncnn.ncnn_net_get_input_index(rawHandle(), index)
    }

    internal fun outputBlobIndex(index: Int): Int {
        require(index in 0 until outputCount) { "output index $index is out of range 0..${outputCount - 1}" }
        return ncnn.ncnn_net_get_output_index(rawHandle(), index)
    }

    private fun getOption(option: NetOption): Int {
        val native = ncnn.ncnn_net_get_option(rawHandle())
        return when (option) {
            NetOption.NUM_THREADS -> ncnn.ncnn_option_get_num_threads(native)
            NetOption.USE_LOCAL_POOL_ALLOCATOR -> ncnn.ncnn_option_get_use_local_pool_allocator(native)
            NetOption.USE_WINOGRAD_CONVOLUTION -> ncnn.ncnn_option_get_use_winograd_convolution(native)
            NetOption.USE_SGEMM_CONVOLUTION -> ncnn.ncnn_option_get_use_sgemm_convolution(native)
            NetOption.USE_PACKING_LAYOUT -> ncnn.ncnn_option_get_use_packing_layout(native)
            NetOption.USE_FP16_PACKED -> ncnn.ncnn_option_get_use_fp16_packed(native)
            NetOption.USE_FP16_STORAGE -> ncnn.ncnn_option_get_use_fp16_storage(native)
            NetOption.USE_FP16_ARITHMETIC -> ncnn.ncnn_option_get_use_fp16_arithmetic(native)
            NetOption.USE_INT8_PACKED -> ncnn.ncnn_option_get_use_int8_packed(native)
            NetOption.USE_INT8_STORAGE -> ncnn.ncnn_option_get_use_int8_storage(native)
            NetOption.USE_INT8_ARITHMETIC -> ncnn.ncnn_option_get_use_int8_arithmetic(native)
            NetOption.USE_INT16_PACKED -> ncnn.ncnn_option_get_use_int16_packed(native)
            NetOption.USE_INT16_STORAGE -> ncnn.ncnn_option_get_use_int16_storage(native)
            NetOption.USE_BF16_PACKED -> ncnn.ncnn_option_get_use_bf16_packed(native)
            NetOption.USE_BF16_STORAGE -> ncnn.ncnn_option_get_use_bf16_storage(native)
        }
    }

    private fun setOption(option: NetOption, value: Int) {
        val native = ncnn.ncnn_net_get_option(rawHandle())
        when (option) {
            NetOption.NUM_THREADS -> ncnn.ncnn_option_set_num_threads(native, value)
            NetOption.USE_LOCAL_POOL_ALLOCATOR -> ncnn.ncnn_option_set_use_local_pool_allocator(native, value)
            NetOption.USE_WINOGRAD_CONVOLUTION -> ncnn.ncnn_option_set_use_winograd_convolution(native, value)
            NetOption.USE_SGEMM_CONVOLUTION -> ncnn.ncnn_option_set_use_sgemm_convolution(native, value)
            NetOption.USE_PACKING_LAYOUT -> ncnn.ncnn_option_set_use_packing_layout(native, value)
            NetOption.USE_FP16_PACKED -> ncnn.ncnn_option_set_use_fp16_packed(native, value)
            NetOption.USE_FP16_STORAGE -> ncnn.ncnn_option_set_use_fp16_storage(native, value)
            NetOption.USE_FP16_ARITHMETIC -> ncnn.ncnn_option_set_use_fp16_arithmetic(native, value)
            NetOption.USE_INT8_PACKED -> ncnn.ncnn_option_set_use_int8_packed(native, value)
            NetOption.USE_INT8_STORAGE -> ncnn.ncnn_option_set_use_int8_storage(native, value)
            NetOption.USE_INT8_ARITHMETIC -> ncnn.ncnn_option_set_use_int8_arithmetic(native, value)
            NetOption.USE_INT16_PACKED -> ncnn.ncnn_option_set_use_int16_packed(native, value)
            NetOption.USE_INT16_STORAGE -> ncnn.ncnn_option_set_use_int16_storage(native, value)
            NetOption.USE_BF16_PACKED -> ncnn.ncnn_option_set_use_bf16_packed(native, value)
            NetOption.USE_BF16_STORAGE -> ncnn.ncnn_option_set_use_bf16_storage(native, value)
        }
    }

    private fun option(option: NetOption): Boolean = getOption(option) != 0

    private fun option(option: NetOption, value: Boolean) = setOption(option, if (value) 1 else 0)

    override var numThreads: Int
        get() = getOption(NetOption.NUM_THREADS)
        set(value) {
            require(value >= 1) { "numThreads must be >= 1" }
            setOption(NetOption.NUM_THREADS, value)
        }

    override var useLocalPoolAllocator: Boolean
        get() = option(NetOption.USE_LOCAL_POOL_ALLOCATOR)
        set(value) = option(NetOption.USE_LOCAL_POOL_ALLOCATOR, value)

    override var useWinogradConvolution: Boolean
        get() = option(NetOption.USE_WINOGRAD_CONVOLUTION)
        set(value) = option(NetOption.USE_WINOGRAD_CONVOLUTION, value)

    override var useSgemmConvolution: Boolean
        get() = option(NetOption.USE_SGEMM_CONVOLUTION)
        set(value) = option(NetOption.USE_SGEMM_CONVOLUTION, value)

    override var usePackingLayout: Boolean
        get() = option(NetOption.USE_PACKING_LAYOUT)
        set(value) = option(NetOption.USE_PACKING_LAYOUT, value)

    override var useFp16Packed: Boolean
        get() = option(NetOption.USE_FP16_PACKED)
        set(value) = option(NetOption.USE_FP16_PACKED, value)

    override var useFp16Storage: Boolean
        get() = option(NetOption.USE_FP16_STORAGE)
        set(value) = option(NetOption.USE_FP16_STORAGE, value)

    override var useFp16Arithmetic: Boolean
        get() = option(NetOption.USE_FP16_ARITHMETIC)
        set(value) = option(NetOption.USE_FP16_ARITHMETIC, value)

    override var useInt8Packed: Boolean
        get() = option(NetOption.USE_INT8_PACKED)
        set(value) = option(NetOption.USE_INT8_PACKED, value)

    override var useInt8Storage: Boolean
        get() = option(NetOption.USE_INT8_STORAGE)
        set(value) = option(NetOption.USE_INT8_STORAGE, value)

    override var useInt8Arithmetic: Boolean
        get() = option(NetOption.USE_INT8_ARITHMETIC)
        set(value) = option(NetOption.USE_INT8_ARITHMETIC, value)

    override var useInt16Packed: Boolean
        get() = option(NetOption.USE_INT16_PACKED)
        set(value) = option(NetOption.USE_INT16_PACKED, value)

    override var useInt16Storage: Boolean
        get() = option(NetOption.USE_INT16_STORAGE)
        set(value) = option(NetOption.USE_INT16_STORAGE, value)

    override var useBf16Packed: Boolean
        get() = option(NetOption.USE_BF16_PACKED)
        set(value) = option(NetOption.USE_BF16_PACKED, value)

    override var useBf16Storage: Boolean
        get() = option(NetOption.USE_BF16_STORAGE)
        set(value) = option(NetOption.USE_BF16_STORAGE, value)

    override val inputCount: Int get() = ncnn.ncnn_net_get_input_count(rawHandle())
    override val outputCount: Int get() = ncnn.ncnn_net_get_output_count(rawHandle())

    override val inputNames: List<String>
        get() = (0 until inputCount).map { index ->
            ncnn.ncnn_net_get_input_name(rawHandle(), index)?.toKString()
                ?: error("ncnn returned no input name for index $index")
        }

    override val outputNames: List<String>
        get() = (0 until outputCount).map { index ->
            ncnn.ncnn_net_get_output_name(rawHandle(), index)?.toKString()
                ?: error("ncnn returned no output name for index $index")
        }

    override fun loadParam(path: String) {
        netLoadParamPath(rawHandle(), path)
    }

    override fun loadParam(data: ByteArray) {
        require(data.isNotEmpty()) { "param data must not be empty" }
        loadParamMemory(rawHandle(), data)
    }

    override fun loadModel(path: String) {
        netLoadModelPath(rawHandle(), path)
    }

    override fun loadModel(data: ByteArray) {
        if (data.isEmpty()) {
            loadModelMemory(rawHandle(), data)
            return
        }

        // ncnn keeps reference-backed weight Mats pointing into this buffer.
        val buffer = malloc(data.size.toULong())?.reinterpret<ByteVar>()
            ?: throw NcnnException("Net.loadModel(memory) failed to allocate ${data.size} bytes")
        try {
            data.usePinned { pinned ->
                memcpy(buffer, pinned.addressOf(0), data.size.toULong())
            }
            val consumed = ncnn.ncnn_net_load_model_memory(rawHandle(), buffer.reinterpret())
            if (consumed.toLong() == 0L) {
                throw NcnnException("Net.loadModel(memory) failed with ncnn status -1", -1)
            }
            modelBuffer?.let { free(it) }
            modelBuffer = buffer
        } catch (t: Throwable) {
            free(buffer)
            throw t
        }
    }

    override fun createExtractor(): Extractor {
        val extractor = ExtractorNative(
            this,
            ncnn.ncnn_extractor_create(rawHandle())
                ?: throw NcnnException("Net.createExtractor failed: ncnn returned no extractor"),
        )
        extractors += extractor
        return extractor
    }

    override fun clear() {
        check(extractors.isEmpty()) { "close all extractors before clearing the net" }
        ncnn.ncnn_net_clear(rawHandle())
        modelBuffer?.let { free(it) }
        modelBuffer = null
    }

    override fun close() {
        if (closed) return
        closed = true
        extractors.toList().forEach { it.close() }
        extractors.clear()
        handle?.let { ncnn.ncnn_net_destroy(it) }
        handle = null
        modelBuffer?.let { free(it) }
        modelBuffer = null
    }
}
