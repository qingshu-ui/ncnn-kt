package io.github.qingshu.ncnn

import kotlin.test.Test
import kotlin.test.assertTrue

class NcnnJniTest {
    @Test
    fun versionIsAvailable() {
        loadNcnnJni()
        assertTrue(ncnnVersionString().startsWith("1.0."), "unexpected ncnn version")
        assertTrue(ncnnVersionNumberJni() > 0, "unexpected ncnn version number")
    }
}
