package io.github.qingshu.ncnn_core

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
            return MatImpl(matCreate(w, h, c))
        }

        fun fromFloats(values: FloatArray, w: Int, h: Int = 0, c: Int = 0): Mat {
            require(w > 0) { "w must be > 0" }
            val expected = w.toLong() * maxOf(h, 1) * maxOf(c, 1)
            require(values.size.toLong() == expected) {
                "expected $expected float values for ${w}x${maxOf(h, 1)}x${maxOf(c, 1)}, got ${values.size}"
            }
            val mat = create(w, h, c)
            try {
                mat.writeFloats(values)
            } catch (t: Throwable) {
                mat.close()
                throw t
            }
            return mat
        }

        fun fromPixels(
            pixels: ByteArray,
            format: PixelFormat,
            w: Int,
            h: Int,
            stride: Int = w * format.channels,
        ): Mat {
            requirePixels(pixels, w, h, stride, format.channels)
            return MatImpl(matFromPixels(pixels, format.code, w, h, stride))
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
            return MatImpl(matFromPixels(pixels, type, w, h, stride))
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
            return MatImpl(matFromPixelsResize(pixels, format.code, w, h, stride, targetWidth, targetHeight))
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

internal class MatImpl internal constructor(handle: Long) : Mat {
    private var handle: Long = handle
    private var closed: Boolean = false

    internal fun rawHandle(): Long {
        check(!closed) { "Mat is closed" }
        return handle
    }

    override fun close() {
        if (!closed) {
            closed = true
            matDestroy(handle)
        }
    }

    override val dims: Int get() = matDims(rawHandle())
    override val w: Int get() = matW(rawHandle())
    override val h: Int get() = matH(rawHandle())
    override val d: Int get() = matD(rawHandle())
    override val c: Int get() = matC(rawHandle())
    override val n: Int get() = matN(rawHandle())
    override val elementSize: Int get() = matElementSize(rawHandle())
    override val elementPack: Int get() = matElementPack(rawHandle())
    override val channelStep: Long get() = matChannelStep(rawHandle())
    override val dataSize: Long get() = matDataSize(rawHandle())

    override fun fill(value: Float) {
        matFill(rawHandle(), value)
    }

    override fun clone(): Mat = MatImpl(matClone(rawHandle()))

    override fun reshape(w: Int, h: Int, c: Int): Mat {
        require(w > 0) { "w must be > 0" }
        require(h >= 0) { "h must be >= 0" }
        require(c >= 0) { "c must be >= 0" }
        return MatImpl(matReshape(rawHandle(), w, h, c))
    }

    override fun convertPacking(elementPack: Int): Mat {
        require(elementPack > 0) { "elementPack must be > 0" }
        return MatImpl(matConvertPacking(rawHandle(), elementPack))
    }

    override fun flatten(): Mat = MatImpl(matFlatten(rawHandle()))

    override fun readFloats(): FloatArray {
        requireFp32Pack1("Mat.readFloats")
        return matReadFloats(rawHandle())
    }

    override fun writeFloats(values: FloatArray) {
        requireFp32Pack1("Mat.writeFloats")
        require(values.size.toLong() * 4 <= dataSize) {
            "source has ${values.size} values but the tensor holds ${dataSize / 4}"
        }
        matWriteFloats(rawHandle(), values)
    }

    override fun readBytes(): ByteArray = matReadBytes(rawHandle())

    override fun writeBytes(bytes: ByteArray) {
        require(bytes.size.toLong() <= dataSize) {
            "source has ${bytes.size} bytes but the tensor holds $dataSize"
        }
        matWriteBytes(rawHandle(), bytes)
    }

    override fun substractMeanNormalize(mean: FloatArray?, norm: FloatArray?) {
        require(mean == null || mean.size == 1 || mean.size == 3) {
            "mean must contain 1 or 3 values"
        }
        require(norm == null || norm.size == 1 || norm.size == 3) {
            "norm must contain 1 or 3 values"
        }
        matSubstractMeanNormalize(rawHandle(), mean, norm)
    }

    override fun toPixels(format: PixelFormat): ByteArray {
        requireFp32Pack1("Mat.toPixels")
        require(c == format.channels) {
            "tensor has $c channels but ${format.name} needs ${format.channels}"
        }
        return matToPixels(rawHandle(), format.code, w * format.channels)
    }

    override fun toPixelsResize(format: PixelFormat, width: Int, height: Int, stride: Int): ByteArray {
        requireFp32Pack1("Mat.toPixelsResize")
        require(c == format.channels) {
            "tensor has $c channels but ${format.name} needs ${format.channels}"
        }
        require(width > 0 && height > 0) { "target size must be positive" }
        require(stride >= width * format.channels) { "stride is too small" }
        return matToPixelsResize(rawHandle(), format.code, width, height, stride)
    }
}

internal fun Mat.requireFp32Pack1(operation: String) {
    if (elementSize != 4 || elementPack != 1) {
        throw IllegalStateException(
            "$operation requires fp32 pack1 data, but this tensor has elementSize=$elementSize, " +
                "elementPack=$elementPack; use convertPacking(1) or disable fp16/bf16/int8 storage",
        )
    }
}
