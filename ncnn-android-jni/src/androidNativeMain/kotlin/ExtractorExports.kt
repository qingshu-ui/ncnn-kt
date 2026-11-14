@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("UNUSED_PARAMETER")

package io.github.qingshu.ncnn_android_jni

import io.github.qingshu.ncnn.binding.extractorCreate
import io.github.qingshu.ncnn.binding.extractorDestroy
import io.github.qingshu.ncnn.binding.extractorExtractIndex
import io.github.qingshu.ncnn.binding.extractorExtractName
import io.github.qingshu.ncnn.binding.extractorInputIndex
import io.github.qingshu.ncnn.binding.extractorInputName
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorCreate")
fun extractorCreateJni(env: CPointer<JNIEnvVar>, clazz: JClass?, net: Long): Long = jniBoundary(env, 0L) { extractorCreate(net) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorDestroy")
fun extractorDestroyJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Unit = jniBoundary(env, Unit) { extractorDestroy(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorInputName")
fun extractorInputNameJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    handle: Long,
    name: JString?,
    mat: Long,
): Unit = jniBoundary(env, Unit) {
    val nativeName = jniString(env, name) ?: return@jniBoundary
    extractorInputName(handle, nativeName, mat)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorInputIndex")
fun extractorInputIndexJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, index: Int, mat: Long): Unit = jniBoundary(env, Unit) { extractorInputIndex(handle, index, mat) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorExtractName")
fun extractorExtractNameJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, name: JString?): Long = jniBoundary(env, 0L) {
    val nativeName = jniString(env, name) ?: return@jniBoundary 0L
    extractorExtractName(handle, nativeName)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_extractorExtractIndex")
fun extractorExtractIndexJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, index: Int): Long = jniBoundary(env, 0L) { extractorExtractIndex(handle, index) }
