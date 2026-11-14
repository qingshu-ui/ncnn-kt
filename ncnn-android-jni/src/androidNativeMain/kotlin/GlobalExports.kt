@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("UNUSED_PARAMETER")

package io.github.qingshu.ncnn_android_jni

import io.github.qingshu.ncnn.binding.ncnnVersionNumber
import io.github.qingshu.ncnn.binding.ncnnVersionString
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

@CName("JNI_OnLoad")
fun jniOnLoad(vm: CPointer<*>?, reserved: CPointer<*>?): Int = 0x00010006

@CName("Java_io_github_qingshu_ncnn_NcnnJni_ncnnVersionString")
fun ncnnVersionStringJni(env: CPointer<JNIEnvVar>, clazz: JClass?): JString? = jniBoundary(env, null) {
    ncnnVersionString()?.let { jniNewString(env, it) }
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_ncnnVersionNumberJni")
fun ncnnVersionNumberJni(env: CPointer<JNIEnvVar>, clazz: JClass?): Int = jniBoundary(env, 0) {
    ncnnVersionNumber()
}
