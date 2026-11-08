package io.github.qingshu.ncnn

/**
 * A multi-dimensional tensor backed by ncnn `ncnn::Mat`.
 *
 * Instances own a native allocation and must be [close]d. Instances returned from
 * [Net.createExtractor] / [Extractor.extract] are owned by the caller as well.
 */
interface Mat : AutoCloseable {
    val dims: Int
    val w: Int
    val h: Int
    val d: Int
    val c: Int

    /** Batch count; `1` for tensors created by this API. */
    val n: Int

    /** Bytes per stored element, including element packing. */
    val elementSize: Int

    /** Number of values packed into one stored element. */
    val elementPack: Int

    /** Bytes per channel. */
    val channelStep: Long

    /** Total number of bytes occupied by the tensor data. */
    val dataSize: Long

    fun fill(value: Float)

    /** Creates a deep copy that must be closed separately. */
    fun clone(): Mat

    /** Reshapes the tensor. The returned [Mat] shares data and must be closed separately. */
    fun reshape(w: Int, h: Int = 0, c: Int = 0): Mat

    /** Converts element packing. The returned [Mat] must be closed separately. */
    fun convertPacking(elementPack: Int): Mat

    /** Flattens the tensor to one dimension. The returned [Mat] must be closed separately. */
    fun flatten(): Mat

    /** Copies the tensor contents out as 32-bit floats. Requires fp32, pack1 data. */
    fun readFloats(): FloatArray

    /** Overwrites the tensor contents from 32-bit floats. Requires fp32, pack1 data. */
    fun writeFloats(values: FloatArray)

    /** Copies the raw tensor bytes. */
    fun readBytes(): ByteArray

    /** Overwrites the raw tensor bytes. */
    fun writeBytes(bytes: ByteArray)

    /** In-place `substract_mean_normalize`. */
    fun substractMeanNormalize(mean: FloatArray? = null, norm: FloatArray? = null)

    /** Converts to 8-bit interleaved pixels. Tensor values are expected to be in `0..255`. */
    fun toPixels(format: PixelFormat): ByteArray

    /** Converts and resizes to 8-bit interleaved pixels. */
    fun toPixelsResize(
        format: PixelFormat,
        width: Int,
        height: Int,
        stride: Int = width * format.channels,
    ): ByteArray

    companion object {
        /** Creates a 1D (`h == 0`), 2D (`c == 0`) or 3D tensor. */
        fun create(w: Int, h: Int = 0, c: Int = 0): Mat {
            require(w > 0) { "w must be > 0" }
            require(h >= 0) { "h must be >= 0" }
            require(c >= 0) { "c must be >= 0" }
            return createMat(w, h, c)
        }

        fun fromFloats(values: FloatArray, w: Int, h: Int = 0, c: Int = 0): Mat {
            require(w > 0) { "w must be > 0" }
            val expected = w.toLong() * maxOf(h, 1) * maxOf(c, 1)
            require(values.size.toLong() == expected) {
                "expected $expected float values for ${w}x${maxOf(h, 1)}x${maxOf(c, 1)}, got ${values.size}"
            }
            return createMatFromFloats(values, w, h, c)
        }

        fun fromPixels(
            pixels: ByteArray,
            format: PixelFormat,
            w: Int,
            h: Int,
            stride: Int = w * format.channels,
        ): Mat {
            requirePixels(pixels, w, h, stride, format.channels)
            return createMatFromPixels(pixels, format.code, w, h, stride)
        }

        /** Converts from [from] to [to] while building the tensor, using ncnn's conversion mask. */
        fun fromPixels(
            pixels: ByteArray,
            from: PixelFormat,
            to: PixelFormat,
            w: Int,
            h: Int,
            stride: Int = w * from.channels,
        ): Mat {
            requirePixels(pixels, w, h, stride, from.channels)
            val type = if (from == to) from.code else from.convertTo(to)
            return createMatFromPixels(pixels, type, w, h, stride)
        }

        fun fromPixelsResize(
            pixels: ByteArray,
            format: PixelFormat,
            w: Int,
            h: Int,
            targetWidth: Int,
            targetHeight: Int,
            stride: Int = w * format.channels,
        ): Mat {
            requirePixels(pixels, w, h, stride, format.channels)
            require(targetWidth > 0 && targetHeight > 0) { "target size must be positive" }
            return createMatFromPixelsResize(pixels, format.code, w, h, stride, targetWidth, targetHeight)
        }

        private fun requirePixels(
            pixels: ByteArray,
            w: Int,
            h: Int,
            stride: Int,
            channels: Int,
        ) {
            require(w > 0 && h > 0) { "w and h must be positive" }
            require(stride >= w * channels) { "stride $stride is smaller than w * channels (${w * channels})" }
            val expected = stride.toLong() * h
            require(pixels.size.toLong() >= expected) {
                "expected at least $expected pixel bytes, got ${pixels.size}"
            }
        }
    }
}

internal expect fun createMat(w: Int, h: Int, c: Int): Mat

internal expect fun createMatFromFloats(values: FloatArray, w: Int, h: Int, c: Int): Mat

internal expect fun createMatFromPixels(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
): Mat

internal expect fun createMatFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Mat

internal fun Mat.requireFp32Pack1(operation: String) {
    if (elementSize != 4 || elementPack != 1) {
        throw IllegalStateException(
            "$operation requires fp32 pack1 data, but this tensor has elementSize=$elementSize, " +
                "elementPack=$elementPack; use convertPacking(1) or disable fp16/bf16/int8 storage",
        )
    }
}
