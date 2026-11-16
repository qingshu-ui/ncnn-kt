package io.github.qingshu.ncnn_core

/**
 * A tightly packed 8-bit interleaved pixel buffer that ncnn's drawing primitives modify in place.
 *
 * The buffer stores [channels] bytes per pixel, row by row, with [stride] bytes per row. A buffer
 * obtained from [Mat.toPixels] is already in this layout. Drawing is clipped to the canvas, so
 * shapes may extend past the edges.
 *
 * Rasterization is ncnn's: lines are thresholded by floating-point distance, so a pixel exactly on
 * that boundary can be painted on one platform build and skipped on another.
 *
 * A drawing `color` is a packed 32-bit value whose least significant byte is channel 0, then
 * channel 1, and so on. For an RGB buffer that means red is the lowest byte; [rgb] and [rgba] build
 * these values in the natural component order.
 *
 * The buffer is not copied: every drawing call writes into [pixels] directly.
 */
class PixelImage(
    val pixels: ByteArray,
    val width: Int,
    val height: Int,
    val channels: Int,
) {
    init {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(channels in 1..4) { "channels must be between 1 and 4" }
        val required = width.toLong() * height * channels
        require(pixels.size.toLong() >= required) {
            "pixel buffer holds ${pixels.size} bytes but ${width}x${height}x$channels needs $required"
        }
    }

    /** Bytes per row. */
    val stride: Int get() = width * channels

    /**
     * Draws a rectangle with the top-left corner at ([x], [y]) covering `width` x `height` pixels.
     *
     * Pass [FILLED] as [thickness] to fill the rectangle; any positive value draws an outline of
     * that width. ncnn itself draws outline edges inclusively (the right and bottom edges land on
     * `x + width` and `y + height`); this method compensates so the outline covers exactly the
     * requested area.
     */
    fun drawRectangle(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
        thickness: Int = 1,
    ) {
        require(width > 0 && height > 0) { "rectangle width and height must be positive" }
        requireThickness(thickness, "drawRectangle")
        // ncnn's outline treats rw/rh as the offset of the far edge, while its fill treats them
        // as the extent. Shrink by one for outlines so both agree with the public contract.
        val spanWidth = if (thickness == FILLED) width else width - 1
        val spanHeight = if (thickness == FILLED) height else height - 1
        ncnnDrawRectangle(pixels, this.width, this.height, channels, x, y, spanWidth, spanHeight, color, thickness)
    }

    /**
     * Draws a circle centered at ([centerX], [centerY]).
     *
     * Pass [FILLED] as [thickness] to fill the circle; any positive value draws an outline of that
     * width.
     */
    fun drawCircle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        color: Int,
        thickness: Int = 1,
    ) {
        require(radius > 0) { "radius must be positive" }
        requireThickness(thickness, "drawCircle")
        ncnnDrawCircle(pixels, width, height, channels, centerX, centerY, radius, color, thickness)
    }

    /** Draws a line from ([x0], [y0]) to ([x1], [y1]). */
    fun drawLine(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        color: Int,
        thickness: Int = 1,
    ) {
        require(thickness > 0) { "thickness must be positive" }
        ncnnDrawLine(pixels, width, height, channels, x0, y0, x1, y1, color, thickness)
    }

    /**
     * Draws [text] with its top-left corner at ([x], [y]).
     *
     * ncnn ships a single monospace ASCII font, so [text] must contain printable ASCII only
     * (U+0020..U+007E); draw each line separately instead of using `\n`. [fontPixelSize] is the
     * glyph height in pixels; glyphs are `fontPixelSize / 2` wide.
     */
    fun drawText(
        text: String,
        x: Int,
        y: Int,
        fontPixelSize: Int,
        color: Int,
    ) {
        require(fontPixelSize > 0) { "fontPixelSize must be positive" }
        requireAscii(text)
        if (text.isEmpty()) return
        ncnnDrawText(pixels, width, height, channels, text, x, y, fontPixelSize, color)
    }

    /** Copies the pixels into a new [Mat] whose channel count matches [format]. */
    fun toMat(format: PixelFormat): Mat {
        require(format.channels == channels) {
            "buffer has $channels channels but ${format.name} needs ${format.channels}"
        }
        return Mat.fromPixels(pixels, format, width, height)
    }

    companion object {
        /** Draws a filled shape; pass as `thickness` to [drawRectangle] or [drawCircle]. */
        const val FILLED: Int = -1

        /** Packs an RGB color, with components in `0..255`. */
        fun rgb(red: Int, green: Int, blue: Int): Int = pack(red, green, blue, 0)

        /** Packs an RGBA color, with components in `0..255`. */
        fun rgba(red: Int, green: Int, blue: Int, alpha: Int): Int = pack(red, green, blue, alpha)

        private fun pack(red: Int, green: Int, blue: Int, alpha: Int): Int = channel(red) or (channel(green) shl 8) or (channel(blue) shl 16) or (channel(alpha) shl 24)

        private fun channel(value: Int): Int = value and 0xFF
    }
}

/** Converts the tensor to 8-bit [format] pixels and wraps them in a [PixelImage]. */
fun Mat.toPixelImage(format: PixelFormat): PixelImage = PixelImage(toPixels(format), w, h, format.channels)

private fun requireThickness(thickness: Int, operation: String) {
    require(thickness == PixelImage.FILLED || thickness > 0) {
        "$operation: thickness must be positive or PixelImage.FILLED"
    }
}

private fun requireAscii(text: String) {
    for (char in text) {
        require(char in ' '..'~') {
            "text must contain printable ASCII only, got U+${char.code.toString(16).uppercase()}"
        }
    }
}
