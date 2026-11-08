package io.github.qingshu.ncnn

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.remove
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

@OptIn(ExperimentalForeignApi::class)
class PathLoadingTest {
    @Test
    fun loadsParamAndModelFromFiles() {
        val suffix = Random.nextInt().toString(16)
        val paramPath = "ncnn-kt-$suffix.param"
        val modelPath = "ncnn-kt-$suffix.bin"
        writeFile(paramPath, TestModel.PARAM.encodeToByteArray())
        writeFile(modelPath, TestModel.modelBytes())

        try {
            Net.create().use { net ->
                net.loadParam(paramPath)
                net.loadModel(modelPath)

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
            remove(paramPath)
            remove(modelPath)
        }
    }

    private fun writeFile(path: String, bytes: ByteArray) {
        val file = fopen(path, "wb") ?: error("fopen failed for $path")
        try {
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
            }
        } finally {
            fclose(file)
        }
    }
}
