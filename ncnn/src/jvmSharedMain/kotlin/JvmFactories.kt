package io.github.qingshu.ncnn

internal actual fun ncnnVersion(): String {
    NativeLoader.load()
    return ncnnVersionString()
}

internal actual fun ncnnVersionNumber(): Int {
    NativeLoader.load()
    return ncnnVersionNumberJni()
}

internal actual fun createMat(w: Int, h: Int, c: Int): Mat {
    NativeLoader.load()
    return MatJvm(matCreate(w, h, c))
}

internal actual fun createMatFromFloats(values: FloatArray, w: Int, h: Int, c: Int): Mat {
    val mat = createMat(w, h, c)
    try {
        mat.writeFloats(values)
    } catch (t: Throwable) {
        mat.close()
        throw t
    }
    return mat
}

internal actual fun createMatFromPixels(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
): Mat {
    NativeLoader.load()
    return MatJvm(matFromPixels(pixels, type, w, h, stride))
}

internal actual fun createMatFromPixelsResize(
    pixels: ByteArray,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Mat {
    NativeLoader.load()
    return MatJvm(matFromPixelsResize(pixels, type, w, h, stride, targetWidth, targetHeight))
}

internal actual fun createNet(): Net {
    NativeLoader.load()
    return NetJvm()
}
