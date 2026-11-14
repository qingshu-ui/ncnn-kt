package io.github.qingshu.ncnn

/**
 * Loads the JNI bridge once per process. The AAR ships both libraries under
 * `jni/<abi>/`, and `libncnn.so` must be resident before `libncnn_jni.so`.
 */
internal object NativeLoader {
    private val loaded: Unit by lazy {
        System.loadLibrary("ncnn")
        System.loadLibrary("ncnn_jni")
    }

    fun load() {
        loaded
    }
}
