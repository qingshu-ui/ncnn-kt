package io.github.qingshu.ncnn

import cnames.structs.__ncnn_mat_t
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import ncnn.ncnn_extractor_t

@OptIn(ExperimentalForeignApi::class)
internal class ExtractorNative internal constructor(
    private val net: NetNative,
    private val handle: ncnn_extractor_t,
) : Extractor {
    private var closed: Boolean = false

    private fun rawHandle(): ncnn_extractor_t {
        check(!closed) { "Extractor is closed" }
        net.ensureOpen()
        return handle
    }

    override fun input(name: String, mat: Mat) {
        val native = (mat as MatNative).rawHandle()
        val status = memScoped {
            ncnn.ncnn_extractor_input(rawHandle(), name, native)
        }
        checkStatus(status, "Extractor.input(name)")
    }

    override fun input(index: Int, mat: Mat) {
        val native = (mat as MatNative).rawHandle()
        val status = ncnn.ncnn_extractor_input_index(rawHandle(), net.inputBlobIndex(index), native)
        checkStatus(status, "Extractor.input(index)")
    }

    override fun extract(name: String): Mat = memScoped {
        val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
        val status = ncnn.ncnn_extractor_extract(rawHandle(), name, out.ptr)
        if (status != 0) {
            out.value?.let { ncnn.ncnn_mat_destroy(it) }
            checkStatus(status, "Extractor.extract(name)")
        }
        MatNative(out.value.orThrow("Extractor.extract(name)"))
    }

    override fun extract(index: Int): Mat = memScoped {
        val out = alloc<CPointerVarOf<CPointer<__ncnn_mat_t>>>()
        val status = ncnn.ncnn_extractor_extract_index(rawHandle(), net.outputBlobIndex(index), out.ptr)
        if (status != 0) {
            out.value?.let { ncnn.ncnn_mat_destroy(it) }
            checkStatus(status, "Extractor.extract(index)")
        }
        MatNative(out.value.orThrow("Extractor.extract(index)"))
    }

    override fun close() {
        if (closed) return
        closed = true
        ncnn.ncnn_extractor_destroy(handle)
        net.onExtractorClosed(this)
    }
}
