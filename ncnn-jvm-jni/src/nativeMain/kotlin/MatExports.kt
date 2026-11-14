@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("UNUSED_PARAMETER")

package io.github.qingshu.ncnn_jvm_jni

import io.github.qingshu.ncnn.binding.matC
import io.github.qingshu.ncnn.binding.matChannelStep
import io.github.qingshu.ncnn.binding.matClone
import io.github.qingshu.ncnn.binding.matConvertPacking
import io.github.qingshu.ncnn.binding.matCreate
import io.github.qingshu.ncnn.binding.matD
import io.github.qingshu.ncnn.binding.matDataSize
import io.github.qingshu.ncnn.binding.matDestroy
import io.github.qingshu.ncnn.binding.matDims
import io.github.qingshu.ncnn.binding.matElementPack
import io.github.qingshu.ncnn.binding.matElementSize
import io.github.qingshu.ncnn.binding.matFill
import io.github.qingshu.ncnn.binding.matFlatten
import io.github.qingshu.ncnn.binding.matFromPixels
import io.github.qingshu.ncnn.binding.matFromPixelsResize
import io.github.qingshu.ncnn.binding.matH
import io.github.qingshu.ncnn.binding.matN
import io.github.qingshu.ncnn.binding.matReadBytes
import io.github.qingshu.ncnn.binding.matReadFloats
import io.github.qingshu.ncnn.binding.matReshape
import io.github.qingshu.ncnn.binding.matSubstractMeanNormalize
import io.github.qingshu.ncnn.binding.matToPixels
import io.github.qingshu.ncnn.binding.matToPixelsResize
import io.github.qingshu.ncnn.binding.matW
import io.github.qingshu.ncnn.binding.matWriteBytes
import io.github.qingshu.ncnn.binding.matWriteFloats
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matCreate")
fun matCreateJni(env: CPointer<JNIEnvVar>, clazz: JClass?, w: Int, h: Int, c: Int): Long = jniBoundary(env, 0L) { matCreate(w, h, c) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matDestroy")
fun matDestroyJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Unit = jniBoundary(env, Unit) { matDestroy(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matDims")
fun matDimsJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matDims(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matW")
fun matWJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matW(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matH")
fun matHJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matH(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matD")
fun matDJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matD(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matC")
fun matCJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matC(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matN")
fun matNJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matN(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matElementSize")
fun matElementSizeJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matElementSize(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matElementPack")
fun matElementPackJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { matElementPack(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matChannelStep")
fun matChannelStepJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Long = jniBoundary(env, 0L) { matChannelStep(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matDataSize")
fun matDataSizeJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Long = jniBoundary(env, 0L) { matDataSize(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFill")
fun matFillJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, value: Float): Unit = jniBoundary(env, Unit) { matFill(handle, value) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matClone")
fun matCloneJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Long = jniBoundary(env, 0L) { matClone(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matReshape")
fun matReshapeJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, w: Int, h: Int, c: Int): Long = jniBoundary(env, 0L) { matReshape(handle, w, h, c) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matConvertPacking")
fun matConvertPackingJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, elementPack: Int): Long = jniBoundary(env, 0L) { matConvertPacking(handle, elementPack) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFlatten")
fun matFlattenJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Long = jniBoundary(env, 0L) { matFlatten(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matReadFloats")
fun matReadFloatsJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): JFloatArray? = jniBoundary(env, null) { newJFloatArray(env, matReadFloats(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matWriteFloats")
fun matWriteFloatsJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, values: JFloatArray?): Unit = jniBoundary(env, Unit) {
    val source = jniFloatArray(env, values) ?: return@jniBoundary
    matWriteFloats(handle, source)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matReadBytes")
fun matReadBytesJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): JByteArray? = jniBoundary(env, null) { newJByteArray(env, matReadBytes(handle)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matWriteBytes")
fun matWriteBytesJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, bytes: JByteArray?): Unit = jniBoundary(env, Unit) {
    val source = jniByteArray(env, bytes) ?: return@jniBoundary
    matWriteBytes(handle, source)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matSubstractMeanNormalize")
fun matSubstractMeanNormalizeJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    handle: Long,
    mean: JFloatArray?,
    norm: JFloatArray?,
): Unit = jniBoundary(env, Unit) {
    matSubstractMeanNormalize(handle, jniFloatArray(env, mean), jniFloatArray(env, norm))
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFromPixels")
fun matFromPixelsJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    pixels: JByteArray?,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
): Long = jniBoundary(env, 0L) {
    val bytes = jniByteArray(env, pixels) ?: return@jniBoundary 0L
    matFromPixels(bytes, type, w, h, stride)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matFromPixelsResize")
fun matFromPixelsResizeJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    pixels: JByteArray?,
    type: Int,
    w: Int,
    h: Int,
    stride: Int,
    targetWidth: Int,
    targetHeight: Int,
): Long = jniBoundary(env, 0L) {
    val bytes = jniByteArray(env, pixels) ?: return@jniBoundary 0L
    matFromPixelsResize(bytes, type, w, h, stride, targetWidth, targetHeight)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matToPixels")
fun matToPixelsJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, type: Int, stride: Int): JByteArray? = jniBoundary(env, null) { newJByteArray(env, matToPixels(handle, type, stride)) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_matToPixelsResize")
fun matToPixelsResizeJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    handle: Long,
    type: Int,
    targetWidth: Int,
    targetHeight: Int,
    stride: Int,
): JByteArray? = jniBoundary(env, null) {
    newJByteArray(env, matToPixelsResize(handle, type, targetWidth, targetHeight, stride))
}
