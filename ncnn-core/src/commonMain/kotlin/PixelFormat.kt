package io.github.qingshu.ncnn_core

/** Pixel layouts understood by ncnn's `from_pixels` / `to_pixels` helpers. */
enum class PixelFormat(val code: Int, val channels: Int) {
    RGB(1, 3),
    BGR(2, 3),
    GRAY(3, 1),
    RGBA(4, 4),
    BGRA(5, 4),
    ;

    /** Encodes ncnn's conversion mask (`from | (to << 16)`). */
    internal fun convertTo(target: PixelFormat): Int = code or (target.code shl 16)
}
