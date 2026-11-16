package io.github.qingshu.ncnn_core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PixelImageTest {
    private fun ByteArray.channelAt(width: Int, channels: Int, x: Int, y: Int, channel: Int = 0): Int = this[(y * width + x) * channels + channel].toInt() and 0xFF

    @Test
    fun packsColorsInChannelOrder() {
        assertEquals(0x030201, PixelImage.rgb(1, 2, 3))
        assertEquals(0x04030201, PixelImage.rgba(1, 2, 3, 4))
        assertEquals(0x0000FF, PixelImage.rgb(255, 0, 0))
        assertEquals(0x00FF00, PixelImage.rgb(0, 255, 0))
        assertEquals(0xFF0000, PixelImage.rgb(0, 0, 255))
    }

    @Test
    fun exposesStride() {
        val image = PixelImage(ByteArray(2 * 2 * 3), 2, 2, 3)
        assertEquals(6, image.stride)
    }

    @Test
    fun drawsFilledRectangle() {
        val image = PixelImage(ByteArray(5 * 5 * 3), 5, 5, 3)
        image.drawRectangle(1, 1, 3, 2, PixelImage.rgb(255, 0, 0), PixelImage.FILLED)

        for (y in 1..2) {
            for (x in 1..3) {
                assertEquals(255, image.pixels.channelAt(5, 3, x, y, 0), "red at ($x,$y)")
                assertEquals(0, image.pixels.channelAt(5, 3, x, y, 1), "green at ($x,$y)")
                assertEquals(0, image.pixels.channelAt(5, 3, x, y, 2), "blue at ($x,$y)")
            }
        }
        assertEquals(0, image.pixels.channelAt(5, 3, 0, 0))
        assertEquals(0, image.pixels.channelAt(5, 3, 4, 4))
        // Only the red channel is non-zero, so one byte per pixel changes.
        assertEquals(3 * 2, image.pixels.count { it.toInt() != 0 })
    }

    @Test
    fun drawsRectangleOutline() {
        val image = PixelImage(ByteArray(8 * 8), 8, 8, 1)
        image.drawRectangle(2, 2, 4, 3, 0x7F)

        // The outline covers exactly x = 2..5 and y = 2..4.
        for (x in 2..5) {
            assertEquals(0x7F, image.pixels.channelAt(8, 1, x, 2), "top edge at x=$x")
            assertEquals(0x7F, image.pixels.channelAt(8, 1, x, 4), "bottom edge at x=$x")
        }
        for (y in 2..4) {
            assertEquals(0x7F, image.pixels.channelAt(8, 1, 2, y), "left edge at y=$y")
            assertEquals(0x7F, image.pixels.channelAt(8, 1, 5, y), "right edge at y=$y")
        }

        assertEquals(0, image.pixels.channelAt(8, 1, 3, 3), "interior stays clear")
        assertEquals(0, image.pixels.channelAt(8, 1, 4, 3), "interior stays clear")
        assertEquals(0, image.pixels.channelAt(8, 1, 1, 2), "outside stays clear")
        assertEquals(0, image.pixels.channelAt(8, 1, 6, 2), "outside stays clear")
        assertEquals(2 * 4 + 2 * 3 - 4, image.pixels.count { it.toInt() != 0 })
    }

    @Test
    fun drawsThinRectangleOutline() {
        val image = PixelImage(ByteArray(8 * 8), 8, 8, 1)
        image.drawRectangle(2, 2, 1, 3, 0x7F)

        for (y in 2..4) {
            assertEquals(0x7F, image.pixels.channelAt(8, 1, 2, y))
        }
        assertEquals(3, image.pixels.count { it.toInt() != 0 })
    }

    @Test
    fun clipsShapesAtTheCanvasEdge() {
        val image = PixelImage(ByteArray(4 * 4), 4, 4, 1)
        image.drawRectangle(-2, -2, 4, 4, 0x7F, PixelImage.FILLED)

        for (y in 0..1) {
            for (x in 0..1) {
                assertEquals(0x7F, image.pixels.channelAt(4, 1, x, y), "clipped area at ($x,$y)")
            }
        }
        assertEquals(0, image.pixels.channelAt(4, 1, 2, 2), "outside the shape stays clear")
        assertEquals(4, image.pixels.count { it.toInt() != 0 })

        val circle = PixelImage(ByteArray(8 * 8), 8, 8, 1)
        circle.drawCircle(0, 0, 3, 0x7F)
        assertTrue(circle.pixels.any { it.toInt() != 0 })
    }

    @Test
    fun drawsFilledCircle() {
        val image = PixelImage(ByteArray(11 * 11), 11, 11, 1)
        image.drawCircle(5, 5, 4, 0x7F, PixelImage.FILLED)

        assertEquals(0x7F, image.pixels.channelAt(11, 1, 5, 5), "center")
        for (offset in 1..2) {
            assertEquals(0x7F, image.pixels.channelAt(11, 1, 5 + offset, 5), "right interior")
            assertEquals(0x7F, image.pixels.channelAt(11, 1, 5, 5 - offset), "top interior")
        }
        assertEquals(0, image.pixels.channelAt(11, 1, 0, 0), "corner")
        assertEquals(0, image.pixels.channelAt(11, 1, 10, 10), "corner")
    }

    @Test
    fun drawsCircleOutline() {
        val image = PixelImage(ByteArray(11 * 11), 11, 11, 1)
        image.drawCircle(5, 5, 4, 0x7F)

        assertEquals(0x7F, image.pixels.channelAt(11, 1, 1, 5), "left edge")
        assertEquals(0x7F, image.pixels.channelAt(11, 1, 9, 5), "right edge")
        assertEquals(0x7F, image.pixels.channelAt(11, 1, 5, 1), "top edge")
        assertEquals(0x7F, image.pixels.channelAt(11, 1, 5, 9), "bottom edge")
        assertEquals(0, image.pixels.channelAt(11, 1, 5, 5), "interior stays clear")
    }

    @Test
    fun drawsLine() {
        val image = PixelImage(ByteArray(8 * 8), 8, 8, 1)
        image.drawLine(0, 0, 7, 7, 0x7F)

        // Endpoints always land on the line.
        assertEquals(0x7F, image.pixels.channelAt(8, 1, 0, 0))
        assertEquals(0x7F, image.pixels.channelAt(8, 1, 7, 7))

        // ncnn rasterizes by squared distance to the segment, thresholded at 0.5 for thickness 1.
        // A pixel one unit off the diagonal sits exactly on that float boundary, so whether it is
        // painted depends on the compiler's floating-point rounding. Accept it, nothing further.
        var drawn = 0
        for (y in 0..7) {
            for (x in 0..7) {
                if (image.pixels.channelAt(8, 1, x, y) != 0) {
                    drawn++
                    assertTrue(abs(x - y) <= 1, "pixel ($x,$y) is off the diagonal")
                }
            }
        }
        assertTrue(drawn in 8..10, "unexpected line coverage: $drawn")
        assertEquals(0, image.pixels.channelAt(8, 1, 2, 0))
        assertEquals(0, image.pixels.channelAt(8, 1, 0, 2))
    }

    @Test
    fun drawsText() {
        val image = PixelImage(ByteArray(32 * 16), 32, 16, 1)
        image.drawText("Hi", 1, 1, 8, 0x7F)

        val colored = image.pixels.count { it.toInt() != 0 }
        assertTrue(colored in 20..300, "unexpected glyph coverage: $colored")
        // The first glyph starts at the requested origin.
        assertTrue(image.pixels.channelAt(32, 1, 2, 4) != 0)
        // Drawing outside the canvas neither throws nor wraps around.
        val clipped = PixelImage(ByteArray(8 * 8), 8, 8, 1)
        clipped.drawText("A", 100, 0, 8, 0x7F)
        assertEquals(0, clipped.pixels.count { it.toInt() != 0 })
    }

    @Test
    fun emptyTextIsANoOp() {
        val image = PixelImage(ByteArray(8 * 8), 8, 8, 1)
        image.drawText("", 0, 0, 8, 0x7F)
        assertEquals(0, image.pixels.count { it.toInt() != 0 })
    }

    @Test
    fun convertsToAndFromMat() {
        val pixels = ByteArray(2 * 2 * 3) { (it * 8).toByte() }
        val image = PixelImage(pixels.copyOf(), 2, 2, 3)
        val mat = image.toMat(PixelFormat.RGB)
        try {
            assertEquals(3, mat.c)
            assertContentEquals(pixels, mat.toPixels(PixelFormat.RGB))
            val back = mat.toPixelImage(PixelFormat.RGB)
            assertContentEquals(pixels, back.pixels)
        } finally {
            mat.close()
        }

        assertFailsWith<IllegalArgumentException> { image.toMat(PixelFormat.GRAY) }
    }

    @Test
    fun rejectsInvalidCanvas() {
        assertFailsWith<IllegalArgumentException> { PixelImage(ByteArray(4), 0, 2, 1) }
        assertFailsWith<IllegalArgumentException> { PixelImage(ByteArray(4), 2, 0, 1) }
        assertFailsWith<IllegalArgumentException> { PixelImage(ByteArray(8), 2, 2, 0) }
        assertFailsWith<IllegalArgumentException> { PixelImage(ByteArray(8), 2, 2, 5) }
        assertFailsWith<IllegalArgumentException> { PixelImage(ByteArray(3), 2, 2, 1) }
    }

    @Test
    fun rejectsInvalidArguments() {
        val image = PixelImage(ByteArray(16 * 16), 16, 16, 1)
        assertFailsWith<IllegalArgumentException> {
            image.drawRectangle(0, 0, 0, 4, 0x7F)
        }
        assertFailsWith<IllegalArgumentException> {
            image.drawRectangle(0, 0, 4, 4, 0x7F, thickness = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            image.drawCircle(4, 4, 0, 0x7F)
        }
        assertFailsWith<IllegalArgumentException> {
            image.drawCircle(4, 4, 4, 0x7F, thickness = -2)
        }
        assertFailsWith<IllegalArgumentException> {
            image.drawLine(0, 0, 4, 4, 0x7F, thickness = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            image.drawText("A", 0, 0, 0, 0x7F)
        }
        assertFailsWith<IllegalArgumentException> {
            image.drawText("café", 0, 0, 8, 0x7F)
        }
    }
}
