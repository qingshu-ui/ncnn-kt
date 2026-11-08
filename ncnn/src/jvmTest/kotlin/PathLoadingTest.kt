package io.github.qingshu.ncnn

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class PathLoadingTest {
    @Test
    fun loadsParamAndModelFromUnicodePaths() {
        val directory = Files.createTempDirectory("ncnn-kt")
        val param = directory.resolve("推理模型.param")
        val model = directory.resolve("推理模型.bin")
        try {
            Files.write(param, TestModel.PARAM.encodeToByteArray())
            Files.write(model, TestModel.modelBytes())

            Net.create().use { net ->
                net.loadParam(param.toAbsolutePath().toString())
                net.loadModel(model.toAbsolutePath().toString())

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
                        assertContentEquals(floatArrayOf(0f, 4f, 0f, 8f), it.readFloats())
                    }
                } finally {
                    input.close()
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun missingParamFileThrows() {
        Net.create().use { net ->
            assertFailsWith<NcnnException> { net.loadParam("definitely-not-a-param.param") }
        }
    }
}
