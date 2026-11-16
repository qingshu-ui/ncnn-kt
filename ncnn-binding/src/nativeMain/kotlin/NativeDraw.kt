@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package io.github.qingshu.ncnn.binding

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned

/*
 * ncnn pixel drawing (`ncnn_draw_*`, enabled by NCNN_PIXEL_DRAWING).
 *
 * The drawing functions mutate a tightly packed 8-bit interleaved buffer in place.
 * `color` is a packed 32-bit value whose least significant byte is channel 0.
 */

fun drawRectangle(
    pixels: ByteArray,
    width: Int,
    height: Int,
    channels: Int,
    x: Int,
    y: Int,
    rectWidth: Int,
    rectHeight: Int,
    color: Int,
    thickness: Int,
) {
    requireCanvas(pixels, width, height, channels, "drawRectangle")
    pixels.usePinned { pinned ->
        val data = pinned.addressOf(0).reinterpret<UByteVar>()
        when (channels) {
            1 -> ncnn.ncnn_draw_rectangle_c1(data, width, height, x, y, rectWidth, rectHeight, color.toUInt(), thickness)
            2 -> ncnn.ncnn_draw_rectangle_c2(data, width, height, x, y, rectWidth, rectHeight, color.toUInt(), thickness)
            3 -> ncnn.ncnn_draw_rectangle_c3(data, width, height, x, y, rectWidth, rectHeight, color.toUInt(), thickness)
            4 -> ncnn.ncnn_draw_rectangle_c4(data, width, height, x, y, rectWidth, rectHeight, color.toUInt(), thickness)
        }
    }
}

fun drawCircle(
    pixels: ByteArray,
    width: Int,
    height: Int,
    channels: Int,
    centerX: Int,
    centerY: Int,
    radius: Int,
    color: Int,
    thickness: Int,
) {
    requireCanvas(pixels, width, height, channels, "drawCircle")
    pixels.usePinned { pinned ->
        val data = pinned.addressOf(0).reinterpret<UByteVar>()
        when (channels) {
            1 -> ncnn.ncnn_draw_circle_c1(data, width, height, centerX, centerY, radius, color.toUInt(), thickness)
            2 -> ncnn.ncnn_draw_circle_c2(data, width, height, centerX, centerY, radius, color.toUInt(), thickness)
            3 -> ncnn.ncnn_draw_circle_c3(data, width, height, centerX, centerY, radius, color.toUInt(), thickness)
            4 -> ncnn.ncnn_draw_circle_c4(data, width, height, centerX, centerY, radius, color.toUInt(), thickness)
        }
    }
}

fun drawLine(
    pixels: ByteArray,
    width: Int,
    height: Int,
    channels: Int,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    color: Int,
    thickness: Int,
) {
    requireCanvas(pixels, width, height, channels, "drawLine")
    pixels.usePinned { pinned ->
        val data = pinned.addressOf(0).reinterpret<UByteVar>()
        when (channels) {
            1 -> ncnn.ncnn_draw_line_c1(data, width, height, x0, y0, x1, y1, color.toUInt(), thickness)
            2 -> ncnn.ncnn_draw_line_c2(data, width, height, x0, y0, x1, y1, color.toUInt(), thickness)
            3 -> ncnn.ncnn_draw_line_c3(data, width, height, x0, y0, x1, y1, color.toUInt(), thickness)
            4 -> ncnn.ncnn_draw_line_c4(data, width, height, x0, y0, x1, y1, color.toUInt(), thickness)
        }
    }
}

fun drawText(
    pixels: ByteArray,
    width: Int,
    height: Int,
    channels: Int,
    text: String,
    x: Int,
    y: Int,
    fontPixelSize: Int,
    color: Int,
) {
    requireCanvas(pixels, width, height, channels, "drawText")
    pixels.usePinned { pinned ->
        val data = pinned.addressOf(0).reinterpret<UByteVar>()
        when (channels) {
            1 -> ncnn.ncnn_draw_text_c1(data, width, height, text, x, y, fontPixelSize, color.toUInt())
            2 -> ncnn.ncnn_draw_text_c2(data, width, height, text, x, y, fontPixelSize, color.toUInt())
            3 -> ncnn.ncnn_draw_text_c3(data, width, height, text, x, y, fontPixelSize, color.toUInt())
            4 -> ncnn.ncnn_draw_text_c4(data, width, height, text, x, y, fontPixelSize, color.toUInt())
        }
    }
}

private fun requireCanvas(
    pixels: ByteArray,
    width: Int,
    height: Int,
    channels: Int,
    operation: String,
) {
    if (width <= 0 || height <= 0) throw IllegalArgumentException("$operation: width and height must be positive")
    if (channels !in 1..4) throw IllegalArgumentException("$operation: channels must be between 1 and 4")
    val required = width.toLong() * height * channels
    if (pixels.size.toLong() < required) {
        throw IllegalArgumentException("$operation: pixel buffer holds ${pixels.size} bytes but needs at least $required")
    }
}
