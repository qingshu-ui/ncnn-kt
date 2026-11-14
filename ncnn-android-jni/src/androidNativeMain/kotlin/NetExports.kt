@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("UNUSED_PARAMETER")

package io.github.qingshu.ncnn_android_jni

import io.github.qingshu.ncnn.binding.NcnnBindingException
import io.github.qingshu.ncnn.binding.netClear
import io.github.qingshu.ncnn.binding.netCreate
import io.github.qingshu.ncnn.binding.netDestroy
import io.github.qingshu.ncnn.binding.netGetOptionInt
import io.github.qingshu.ncnn.binding.netInputCount
import io.github.qingshu.ncnn.binding.netInputIndex
import io.github.qingshu.ncnn.binding.netInputName
import io.github.qingshu.ncnn.binding.netLoadModelMemory
import io.github.qingshu.ncnn.binding.netLoadModelPath
import io.github.qingshu.ncnn.binding.netLoadParamMemory
import io.github.qingshu.ncnn.binding.netLoadParamPath
import io.github.qingshu.ncnn.binding.netOutputCount
import io.github.qingshu.ncnn.binding.netOutputIndex
import io.github.qingshu.ncnn.binding.netOutputName
import io.github.qingshu.ncnn.binding.netSetOptionInt
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netCreate")
fun netCreateJni(env: CPointer<JNIEnvVar>, clazz: JClass?): Long = jniBoundary(env, 0L) {
    netCreate()
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netDestroy")
fun netDestroyJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Unit = jniBoundary(env, Unit) {
    netDestroy(handle)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netGetOptionInt")
fun netGetOptionIntJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, option: Int): Int = jniBoundary(env, 0) { netGetOptionInt(handle, option) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netSetOptionInt")
fun netSetOptionIntJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, option: Int, value: Int): Unit = jniBoundary(env, Unit) { netSetOptionInt(handle, option, value) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadParamPath")
fun netLoadParamPathJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, path: JString?): Unit = jniBoundary(env, Unit) {
    val nativePath = jniString(env, path)
        ?: throw NcnnBindingException("Net.loadParam(path) failed with ncnn status -1", -1)
    netLoadParamPath(handle, nativePath)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadParamMemory")
fun netLoadParamMemoryJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, data: JByteArray?): Unit = jniBoundary(env, Unit) {
    val bytes = jniByteArray(env, data) ?: return@jniBoundary
    netLoadParamMemory(handle, bytes)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadModelPath")
fun netLoadModelPathJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, path: JString?): Unit = jniBoundary(env, Unit) {
    val nativePath = jniString(env, path)
        ?: throw NcnnBindingException("Net.loadModel(path) failed with ncnn status -1", -1)
    netLoadModelPath(handle, nativePath)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netLoadModelMemory")
fun netLoadModelMemoryJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, data: JByteArray?): Long = jniBoundary(env, 0L) {
    val bytes = jniByteArray(env, data) ?: return@jniBoundary 0L
    netLoadModelMemory(handle, bytes)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netInputCount")
fun netInputCountJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { netInputCount(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netOutputCount")
fun netOutputCountJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Int = jniBoundary(env, 0) { netOutputCount(handle) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netInputName")
fun netInputNameJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, index: Int): JString? = jniBoundary(env, null) { netInputName(handle, index)?.let { jniNewString(env, it) } }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netOutputName")
fun netOutputNameJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, index: Int): JString? = jniBoundary(env, null) { netOutputName(handle, index)?.let { jniNewString(env, it) } }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netInputIndex")
fun netInputIndexJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, index: Int): Int = jniBoundary(env, 0) { netInputIndex(handle, index) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netOutputIndex")
fun netOutputIndexJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long, index: Int): Int = jniBoundary(env, 0) { netOutputIndex(handle, index) }

@CName("Java_io_github_qingshu_ncnn_NcnnJni_netClear")
fun netClearJni(env: CPointer<JNIEnvVar>, clazz: JClass?, handle: Long): Unit = jniBoundary(env, Unit) {
    netClear(handle)
}
