package io.github.qingshu.ncnn_core

/**
 * An ncnn network.
 *
 * A [Net] is not thread-safe: do not use one instance from several threads concurrently.
 * Instances own a native allocation and must be [close]d.
 */
interface Net : AutoCloseable {
    /** Number of inference threads; `1` disables parallelism. */
    var numThreads: Int

    var useLocalPoolAllocator: Boolean
    var useWinogradConvolution: Boolean
    var useSgemmConvolution: Boolean
    var usePackingLayout: Boolean
    var useFp16Packed: Boolean
    var useFp16Storage: Boolean
    var useFp16Arithmetic: Boolean
    var useInt8Packed: Boolean
    var useInt8Storage: Boolean
    var useInt8Arithmetic: Boolean
    var useInt16Packed: Boolean
    var useInt16Storage: Boolean
    var useBf16Packed: Boolean
    var useBf16Storage: Boolean

    val inputCount: Int
    val outputCount: Int
    val inputNames: List<String>
    val outputNames: List<String>

    /** Loads a text `.param` file. */
    fun loadParam(path: String)

    /** Loads text `.param` content from memory. */
    fun loadParam(data: ByteArray)

    /** Loads a binary `.bin` model file. */
    fun loadModel(path: String)

    /** Loads binary model content from memory. */
    fun loadModel(data: ByteArray)

    fun createExtractor(): Extractor

    /** Clears the loaded network. All child extractors must be closed first. */
    fun clear()

    companion object {
        fun create(): Net = NetImpl()
    }
}

/**
 * Runs inference on a [Net].
 *
 * Extractors are not thread-safe and must be closed before their [Net] is cleared or closed.
 */
interface Extractor : AutoCloseable {
    fun input(name: String, mat: Mat)

    fun input(index: Int, mat: Mat)

    fun extract(name: String): Mat

    fun extract(index: Int): Mat
}

internal enum class NetOption(val id: Int) {
    NUM_THREADS(0),
    USE_LOCAL_POOL_ALLOCATOR(1),
    USE_WINOGRAD_CONVOLUTION(2),
    USE_SGEMM_CONVOLUTION(3),
    USE_PACKING_LAYOUT(4),
    USE_FP16_PACKED(5),
    USE_FP16_STORAGE(6),
    USE_FP16_ARITHMETIC(7),
    USE_INT8_PACKED(8),
    USE_INT8_STORAGE(9),
    USE_INT8_ARITHMETIC(10),
    USE_INT16_PACKED(11),
    USE_INT16_STORAGE(12),
    USE_BF16_PACKED(13),
    USE_BF16_STORAGE(14),
}

internal class NetImpl : Net {
    private var handle: Long = netCreate()
    private var closed: Boolean = false
    private val extractors: MutableSet<ExtractorImpl> = mutableSetOf()

    private fun rawHandle(): Long {
        check(!closed) { "Net is closed" }
        return handle
    }

    internal fun ensureOpen() {
        check(!closed) { "Net is closed" }
    }

    internal fun onExtractorClosed(extractor: ExtractorImpl) {
        extractors.remove(extractor)
    }

    internal fun inputBlobIndex(index: Int): Int {
        require(index in 0 until inputCount) { "input index $index is out of range 0..${inputCount - 1}" }
        return netInputIndex(rawHandle(), index)
    }

    internal fun outputBlobIndex(index: Int): Int {
        require(index in 0 until outputCount) { "output index $index is out of range 0..${outputCount - 1}" }
        return netOutputIndex(rawHandle(), index)
    }

    private fun option(option: NetOption): Boolean = netGetOptionInt(rawHandle(), option.id) != 0

    private fun option(option: NetOption, value: Boolean) {
        netSetOptionInt(rawHandle(), option.id, if (value) 1 else 0)
    }

    override var numThreads: Int
        get() = netGetOptionInt(rawHandle(), NetOption.NUM_THREADS.id)
        set(value) {
            require(value >= 1) { "numThreads must be >= 1" }
            netSetOptionInt(rawHandle(), NetOption.NUM_THREADS.id, value)
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

    override val inputCount: Int get() = netInputCount(rawHandle())
    override val outputCount: Int get() = netOutputCount(rawHandle())

    override val inputNames: List<String>
        get() = (0 until inputCount).map { index ->
            netInputName(rawHandle(), index) ?: error("ncnn returned no input name for index $index")
        }

    override val outputNames: List<String>
        get() = (0 until outputCount).map { index ->
            netOutputName(rawHandle(), index) ?: error("ncnn returned no output name for index $index")
        }

    override fun loadParam(path: String) {
        netLoadParamPath(rawHandle(), path)
    }

    override fun loadParam(data: ByteArray) {
        require(data.isNotEmpty()) { "param data must not be empty" }
        netLoadParamMemory(rawHandle(), data)
    }

    override fun loadModel(path: String) {
        netLoadModelPath(rawHandle(), path)
    }

    override fun loadModel(data: ByteArray) {
        netLoadModelMemory(rawHandle(), data)
    }

    override fun createExtractor(): Extractor {
        val extractor = ExtractorImpl(this, extractorCreate(rawHandle()))
        extractors += extractor
        return extractor
    }

    override fun clear() {
        check(extractors.isEmpty()) { "close all extractors before clearing the net" }
        netClear(rawHandle())
    }

    override fun close() {
        if (closed) return
        closed = true
        extractors.toList().forEach { it.close() }
        extractors.clear()
        netDestroy(handle)
    }
}

internal class ExtractorImpl internal constructor(
    private val net: NetImpl,
    private val handle: Long,
) : Extractor {
    private var closed: Boolean = false

    private fun rawHandle(): Long {
        check(!closed) { "Extractor is closed" }
        net.ensureOpen()
        return handle
    }

    override fun input(name: String, mat: Mat) {
        extractorInputName(rawHandle(), name, (mat as MatImpl).rawHandle())
    }

    override fun input(index: Int, mat: Mat) {
        extractorInputIndex(rawHandle(), net.inputBlobIndex(index), (mat as MatImpl).rawHandle())
    }

    override fun extract(name: String): Mat = MatImpl(extractorExtractName(rawHandle(), name))

    override fun extract(index: Int): Mat = MatImpl(extractorExtractIndex(rawHandle(), net.outputBlobIndex(index)))

    override fun close() {
        if (closed) return
        closed = true
        extractorDestroy(handle)
        net.onExtractorClosed(this)
    }
}
