@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("UNUSED_PARAMETER")

package io.github.qingshu.ncnn_android_jni

import io.github.qingshu.ncnn.binding.NcnnBindingException
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.android.JNINativeInterface
import platform.android.jbyteArray
import platform.android.jclass
import platform.android.jfloatArray
import platform.android.jstring
import platform.android.jvalue
import kotlin.experimental.ExperimentalNativeApi

internal typealias JNIEnvVar = platform.android.JNIEnvVar
internal typealias JniTable = JNINativeInterface
internal typealias JClass = jclass
internal typealias JString = jstring
internal typealias JByteArray = jbyteArray
internal typealias JFloatArray = jfloatArray
internal typealias JValue = jvalue

internal fun CPointer<JNIEnvVar>.jniTable(): CPointer<JniTable>? = pointed.value

internal fun jniString(env: CPointer<JNIEnvVar>, value: JString?): String? {
    if (value == null) return null
    val table = env.jniTable() ?: return null
    val chars = table.pointed.GetStringChars!!(env, value, null) ?: return null
    return try {
        chars.toKString()
    } finally {
        table.pointed.ReleaseStringChars!!(env, value, chars)
    }
}

internal fun jniNewString(env: CPointer<JNIEnvVar>, value: String): JString? {
    val table = env.jniTable() ?: return null
    return memScoped { table.pointed.NewStringUTF!!(env, value.cstr.ptr) }
}

internal fun jniNewString(env: CPointer<JNIEnvVar>, value: CPointer<ByteVar>): JString? = env.jniTable()?.pointed?.NewStringUTF!!(env, value)

internal fun jniFloatArray(env: CPointer<JNIEnvVar>, array: JFloatArray?): FloatArray? {
    if (array == null) return null
    val table = env.jniTable() ?: return null
    val length = table.pointed.GetArrayLength!!(env, array.reinterpret())
    if (length <= 0) return FloatArray(0)
    val values = FloatArray(length)
    values.usePinned { table.pointed.GetFloatArrayRegion!!(env, array, 0, length, it.addressOf(0)) }
    return values
}

internal fun jniByteArray(env: CPointer<JNIEnvVar>, array: JByteArray?): ByteArray? {
    if (array == null) return null
    val table = env.jniTable() ?: return null
    val length = table.pointed.GetArrayLength!!(env, array.reinterpret())
    if (length <= 0) return ByteArray(0)
    val values = ByteArray(length)
    values.usePinned { table.pointed.GetByteArrayRegion!!(env, array, 0, length, it.addressOf(0)) }
    return values
}

internal fun newJFloatArray(env: CPointer<JNIEnvVar>, values: FloatArray): JFloatArray? {
    val table = env.jniTable() ?: return null
    val result = table.pointed.NewFloatArray!!(env, values.size) ?: return null
    if (values.isNotEmpty()) {
        values.usePinned { table.pointed.SetFloatArrayRegion!!(env, result, 0, values.size, it.addressOf(0)) }
    }
    return result
}

internal fun newJByteArray(env: CPointer<JNIEnvVar>, bytes: ByteArray): JByteArray? {
    val table = env.jniTable() ?: return null
    val result = table.pointed.NewByteArray!!(env, bytes.size) ?: return null
    if (bytes.isNotEmpty()) {
        bytes.usePinned { table.pointed.SetByteArrayRegion!!(env, result, 0, bytes.size, it.addressOf(0)) }
    }
    return result
}

private fun CPointer<JniTable>.hasPendingException(env: CPointer<JNIEnvVar>): Boolean = pointed.ExceptionCheck!!(env) != 0.toUByte()

private fun throwJvm(env: CPointer<JNIEnvVar>, className: String, message: String) {
    val table = env.jniTable() ?: return
    if (table.hasPendingException(env)) return
    val cls = memScoped { table.pointed.FindClass!!(env, className.cstr.ptr) } ?: return
    memScoped { table.pointed.ThrowNew!!(env, cls, message.cstr.ptr) }
}

private fun throwNcnn(env: CPointer<JNIEnvVar>, error: NcnnBindingException) {
    val table = env.jniTable() ?: return
    if (table.hasPendingException(env)) return
    val cls = memScoped {
        table.pointed.FindClass!!(env, "io/github/qingshu/ncnn_core/NcnnException".cstr.ptr)
    } ?: return
    val ctor = memScoped {
        table.pointed.GetMethodID!!(env, cls, "<init>".cstr.ptr, "(Ljava/lang/String;I)V".cstr.ptr)
    } ?: return
    memScoped {
        val message = table.pointed.NewStringUTF!!(env, (error.message ?: "ncnn error").cstr.ptr)
            ?: return@memScoped
        val args = allocArray<jvalue>(2)
        args[0].l = message
        args[1].i = error.code
        val exception = table.pointed.NewObjectA!!(env, cls, ctor, args) ?: return@memScoped
        table.pointed.Throw!!(env, exception)
    }
}

internal inline fun <T> jniBoundary(env: CPointer<JNIEnvVar>, fallback: T, block: () -> T): T = try {
    block()
} catch (error: NcnnBindingException) {
    throwNcnn(env, error)
    fallback
} catch (error: OutOfMemoryError) {
    throwJvm(env, "java/lang/OutOfMemoryError", error.message ?: "out of memory")
    fallback
} catch (error: IllegalArgumentException) {
    throwJvm(env, "java/lang/IllegalArgumentException", error.message ?: "invalid argument")
    fallback
} catch (error: IllegalStateException) {
    throwJvm(env, "java/lang/IllegalStateException", error.message ?: "invalid state")
    fallback
} catch (error: Throwable) {
    throwJvm(env, "java/lang/RuntimeException", error.toString())
    fallback
}
