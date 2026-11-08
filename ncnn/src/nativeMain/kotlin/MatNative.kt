package io.github.qingshu.ncnn

import cnames.structs.__ncnn_mat_t
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import ncnn.ncnn_mat_t

@OptIn(ExperimentalForeignApi::class)
internal class MatNative internal constructor(handle: ncnn_mat_t?) : Mat {
    private var handle: ncnn_mat_t? = handle
    private var closed: Boolean = false

    internal fun rawHandle(): ncnn_mat_t {
        check(!closed) { "Mat is closed" }
        return handle ?: throw NcnnException("Mat handle is null")
    }

    override fun close() {
        if (!closed) {
            closed = true
            handle?.let { ncnn.ncnn_mat_destroy(it) }
            handle = null
        }
    }

    override val dims: Int get() = ncnn.ncnn_mat_get_dims(rawHandle())
    override val w: Int get() = ncnn.ncnn_mat_get_w(rawHandle())
    override val h: Int get() = ncnn.ncnn_mat_get_h(rawHandle())
    override val d: Int get() = ncnn.ncnn_mat_get_d(rawHandle())
    override val c: Int get() = ncnn.ncnn_mat_get_c(rawHandle())
    override val n: Int get() = ncnn.ncnn_mat_get_n(rawHandle())
    override val elementSize: Int get() = ncnn.ncnn_mat_get_elemsize(rawHandle()).toInt()
    override val elementPack: Int get() = ncnn.ncnn_mat_get_elempack(rawHandle())
    override val channelStep: Long get() = ncnn.ncnn_mat_get_cstep(rawHandle()).toLong()
    override val dataSize: Long get() = rawHandle().matDataSize()

    override fun fill(value: Float) {
        ncnn.ncnn_mat_fill_float(rawHandle(), value)
    }

    override fun clone(): Mat = MatNative(ncnn.ncnn_mat_clone(rawHandle(), null).orThrow("Mat.clone"))

    override fun reshape(w: Int, h: Int, c: Int): Mat {
        require(w > 0) { "w must be > 0" }
        require(h >= 0) { "h must be >= 0" }
        require(c >= 0) { "c must be >= 0" }
        val reshaped = when {
            c > 0 -> ncnn.ncnn_mat_reshape_3d(rawHandle(), w, h, c, null)
            h > 0 -> ncnn.ncnn_mat_reshape_2d(rawHandle(), w, h, null)
            else -> ncnn.ncnn_mat_reshape_1d(rawHandle(), w, null)
        }
        return MatNative(reshaped.orThrow("Mat.reshape"))
    }

    override fun convertPacking(elementPack: Int): Mat {
        require(elementPack > 0) { "elementPack must be > 0" }
        return memScoped {
            val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
            ncnn.ncnn_convert_packing(rawHandle(), out.ptr, elementPack, null)
            val mat: ncnn_mat_t? = out.value
            MatNative(mat.orThrow("Mat.convertPacking"))
        }
    }

    override fun flatten(): Mat = memScoped {
        val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
        ncnn.ncnn_flatten(rawHandle(), out.ptr, null)
        val mat: ncnn_mat_t? = out.value
        MatNative(mat.orThrow("Mat.flatten"))
    }

    override fun readFloats(): FloatArray {
        requireFp32Pack1("Mat.readFloats")
        val size = dataSize
        require(size <= Int.MAX_VALUE) { "tensor is too large to expose as FloatArray" }
        return rawHandle().readFloatData((size / 4).toInt())
    }

    override fun writeFloats(values: FloatArray) {
        requireFp32Pack1("Mat.writeFloats")
        require(values.size.toLong() * 4 <= dataSize) {
            "source has ${values.size} values but the tensor holds ${dataSize / 4}"
        }
        rawHandle().writeFloatData(values)
    }

    override fun readBytes(): ByteArray {
        val size = dataSize
        require(size <= Int.MAX_VALUE) { "tensor is too large to expose as ByteArray" }
        return rawHandle().readRawBytes(size.toInt())
    }

    override fun writeBytes(bytes: ByteArray) {
        require(bytes.size.toLong() <= dataSize) {
            "source has ${bytes.size} bytes but the tensor holds $dataSize"
        }
        rawHandle().writeRawBytes(bytes)
    }

    override fun substractMeanNormalize(mean: FloatArray?, norm: FloatArray?) {
        require(mean == null || mean.size == 1 || mean.size == 3) {
            "mean must contain 1 or 3 values"
        }
        require(norm == null || norm.size == 1 || norm.size == 3) {
            "norm must contain 1 or 3 values"
        }
        if (mean == null && norm == null) return

        when {
            mean != null && norm != null -> mean.usePinned { meanPinned ->
                norm.usePinned { normPinned ->
                    ncnn.ncnn_mat_substract_mean_normalize(
                        rawHandle(),
                        meanPinned.addressOf(0),
                        normPinned.addressOf(0),
                    )
                }
            }

            mean != null -> mean.usePinned { meanPinned ->
                ncnn.ncnn_mat_substract_mean_normalize(rawHandle(), meanPinned.addressOf(0), null)
            }

            norm != null -> norm.usePinned { normPinned ->
                ncnn.ncnn_mat_substract_mean_normalize(rawHandle(), null, normPinned.addressOf(0))
            }
        }
    }

    override fun toPixels(format: PixelFormat): ByteArray {
        requireFp32Pack1("Mat.toPixels")
        require(c == format.channels) {
            "tensor has $c channels but ${format.name} needs ${format.channels}"
        }
        val stride = w * format.channels
        return toPixelsInternal(format, stride, h)
    }

    override fun toPixelsResize(format: PixelFormat, width: Int, height: Int, stride: Int): ByteArray {
        requireFp32Pack1("Mat.toPixelsResize")
        require(c == format.channels) {
            "tensor has $c channels but ${format.name} needs ${format.channels}"
        }
        require(width > 0 && height > 0) { "target size must be positive" }
        require(stride >= width * format.channels) { "stride is too small" }
        val buffer = ByteArray(stride * height)
        buffer.usePinned { pinned ->
            ncnn.ncnn_mat_to_pixels_resize(
                rawHandle(),
                pinned.addressOf(0).reinterpret(),
                format.code,
                width,
                height,
                stride,
            )
        }
        return buffer
    }

    private fun toPixelsInternal(format: PixelFormat, stride: Int, height: Int): ByteArray {
        val buffer = ByteArray(stride * height)
        buffer.usePinned { pinned ->
            ncnn.ncnn_mat_to_pixels(rawHandle(), pinned.addressOf(0).reinterpret(), format.code, stride)
        }
        return buffer
    }
}
