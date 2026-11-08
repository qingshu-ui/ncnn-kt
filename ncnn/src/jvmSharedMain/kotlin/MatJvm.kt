package io.github.qingshu.ncnn

internal class MatJvm internal constructor(handle: Long) : Mat {
    private var handle: Long = handle
    private var closed: Boolean = false

    internal fun rawHandle(): Long {
        check(!closed) { "Mat is closed" }
        NativeLoader.load()
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

    override fun clone(): Mat = MatJvm(matClone(rawHandle()))

    override fun reshape(w: Int, h: Int, c: Int): Mat {
        require(w > 0) { "w must be > 0" }
        require(h >= 0) { "h must be >= 0" }
        require(c >= 0) { "c must be >= 0" }
        return MatJvm(matReshape(rawHandle(), w, h, c))
    }

    override fun convertPacking(elementPack: Int): Mat {
        require(elementPack > 0) { "elementPack must be > 0" }
        return MatJvm(matConvertPacking(rawHandle(), elementPack))
    }

    override fun flatten(): Mat = MatJvm(matFlatten(rawHandle()))

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
