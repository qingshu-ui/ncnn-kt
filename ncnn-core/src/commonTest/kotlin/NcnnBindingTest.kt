package io.github.qingshu.ncnn_core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NcnnBindingTest {
    @Test
    fun versionIsAvailable() {
        assertTrue(Ncnn.version.isNotEmpty())
        assertTrue(Ncnn.versionNumber > 0)
    }

    @Test
    fun createsAndFillsMat() {
        val mat = Mat.create(w = 3, h = 2, c = 1)
        try {
            assertEquals(3, mat.dims)
            assertEquals(3, mat.w)
            assertEquals(2, mat.h)
            assertEquals(1, mat.c)
            assertEquals(4, mat.elementSize)
            assertEquals(1, mat.elementPack)
            assertEquals(24L, mat.dataSize)

            mat.fill(1.5f)
            val values = mat.readFloats()
            assertEquals(6, values.size)
            assertTrue(values.all { it == 1.5f })
        } finally {
            mat.close()
        }
    }

    @Test
    fun clonesReshapesAndReadsBack() {
        val mat = Mat.fromFloats(floatArrayOf(1f, 2f, 3f, 4f), w = 2, h = 2, c = 1)
        try {
            val copy = mat.clone()
            try {
                mat.writeFloats(floatArrayOf(9f, 8f, 7f, 6f))
                assertContentEquals(floatArrayOf(1f, 2f, 3f, 4f), copy.readFloats())
                assertContentEquals(floatArrayOf(9f, 8f, 7f, 6f), mat.readFloats())
            } finally {
                copy.close()
            }

            val flattened = mat.reshape(w = 4)
            try {
                assertEquals(1, flattened.dims)
                assertEquals(4, flattened.w)
                assertContentEquals(floatArrayOf(9f, 8f, 7f, 6f), flattened.readFloats())
            } finally {
                flattened.close()
            }
        } finally {
            mat.close()
        }
    }

    @Test
    fun convertsPixelsRoundTrip() {
        val pixels = byteArrayOf(
            10, 20, 30,
            40, 50, 60,
            70, 80, 90,
            100, 110, 120,
        )
        val mat = Mat.fromPixels(pixels, PixelFormat.RGB, w = 2, h = 2)
        try {
            assertEquals(3, mat.c)
            assertEquals(12, mat.readFloats().size)
            assertContentEquals(pixels, mat.toPixels(PixelFormat.RGB))
        } finally {
            mat.close()
        }
    }

    @Test
    fun convertsPixelFormats() {
        val pixels = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)
        val gray = Mat.fromPixels(pixels, PixelFormat.RGB, PixelFormat.GRAY, w = 2, h = 2)
        try {
            assertEquals(1, gray.c)
            val grayPixels = gray.toPixels(PixelFormat.GRAY)
            assertEquals(4, grayPixels.size)
        } finally {
            gray.close()
        }
    }

    @Test
    fun resizesPixels() {
        val pixels = ByteArray(2 * 2 * 3) { (it % 255).toByte() }
        val mat = Mat.fromPixelsResize(pixels, PixelFormat.RGB, 2, 2, 4, 4)
        try {
            assertEquals(4, mat.w)
            assertEquals(4, mat.h)
            assertEquals(3, mat.c)
            assertEquals(4 * 4 * 3, mat.toPixels(PixelFormat.RGB).size)
        } finally {
            mat.close()
        }
    }

    @Test
    fun normalizesWithMeanAndNorm() {
        val pixels = byteArrayOf(
            100.toByte(),
            150.toByte(),
            200.toByte(),
            100.toByte(),
            150.toByte(),
            200.toByte(),
        )
        val mat = Mat.fromPixels(pixels, PixelFormat.RGB, w = 2, h = 1)
        try {
            mat.substractMeanNormalize(mean = null, norm = floatArrayOf(2f, 2f, 2f))
            val values = mat.readFloats()
            assertContentEquals(floatArrayOf(200f, 200f, 300f, 300f, 400f, 400f), values)
        } finally {
            mat.close()
        }
    }

    @Test
    fun loadsModelFromMemoryAndRunsInference() {
        Net.create().use { net ->
            net.loadParam(TestModel.PARAM.encodeToByteArray())
            net.loadModel(TestModel.modelBytes())

            assertEquals(listOf("data"), net.inputNames)
            assertEquals(listOf("output"), net.outputNames)
            assertEquals(1, net.inputCount)
            assertEquals(1, net.outputCount)

            val input = Mat.fromFloats(floatArrayOf(-1f, 2f, -3f, 4f), w = 2, h = 2, c = 1)
            try {
                val extractor = net.createExtractor()
                val output = try {
                    extractor.input("data", input)
                    extractor.extract("output")
                } finally {
                    extractor.close()
                }
                output.use {
                    assertEquals(2, it.w)
                    assertEquals(2, it.h)
                    assertEquals(1, it.c)
                    assertContentEquals(floatArrayOf(0f, 4f, 0f, 8f), it.readFloats())
                }
            } finally {
                input.close()
            }
        }
    }

    @Test
    fun extractorByIndexWorks() {
        Net.create().use { net ->
            net.loadParam(TestModel.PARAM.encodeToByteArray())
            net.loadModel(TestModel.modelBytes())
            val input = Mat.fromFloats(floatArrayOf(1f, 1f, 1f, 1f), w = 2, h = 2, c = 1)
            try {
                val extractor = net.createExtractor()
                val output = try {
                    extractor.input(0, input)
                    extractor.extract(0)
                } finally {
                    extractor.close()
                }
                output.use {
                    assertContentEquals(floatArrayOf(2f, 2f, 2f, 2f), it.readFloats())
                }
            } finally {
                input.close()
            }
        }
    }

    @Test
    fun netCloseClosesOpenExtractors() {
        val net = Net.create()
        net.loadParam(TestModel.PARAM.encodeToByteArray())
        net.loadModel(TestModel.modelBytes())
        val extractor = net.createExtractor()
        net.close()

        assertFailsWith<IllegalStateException> { extractor.extract("output") }
        extractor.close()
    }

    @Test
    fun useAfterCloseFails() {
        val mat = Mat.create(2)
        mat.close()
        mat.close()
        assertFailsWith<IllegalStateException> { mat.readFloats() }

        val net = Net.create()
        net.close()
        assertFailsWith<IllegalStateException> { net.inputNames }
    }

    @Test
    fun clearRequiresClosedExtractors() {
        Net.create().use { net ->
            net.loadParam(TestModel.PARAM.encodeToByteArray())
            net.loadModel(TestModel.modelBytes())
            val extractor = net.createExtractor()
            assertFailsWith<IllegalStateException> { net.clear() }
            extractor.close()
            net.clear()
        }
    }

    @Test
    fun missingModelFileThrows() {
        Net.create().use { net ->
            net.loadParam(TestModel.PARAM.encodeToByteArray())
            assertFailsWith<NcnnException> { net.loadModel("definitely-not-a-model.bin") }
        }
    }

    @Test
    fun invalidParamBytesThrow() {
        Net.create().use { net ->
            assertFailsWith<NcnnException> { net.loadParam("not an ncnn param".encodeToByteArray()) }
        }
    }
}
