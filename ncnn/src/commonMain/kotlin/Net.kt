package io.github.qingshu.ncnn

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
        fun create(): Net = createNet()
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

internal expect fun createNet(): Net

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
