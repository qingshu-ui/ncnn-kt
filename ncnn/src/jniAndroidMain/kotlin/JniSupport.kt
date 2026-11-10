@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package io.github.qingshu.ncnn

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import ncnn.ncnn_extractor_t
import ncnn.ncnn_mat_t
import ncnn.ncnn_net_t
import platform.android.JNIEnvVar
import platform.android.JNINativeInterface
import platform.android.jbyteArray
import platform.android.jfloatArray
import platform.android.jstring
import platform.android.jvalue
import platform.posix.free
import platform.posix.malloc
import kotlin.experimental.ExperimentalNativeApi

internal fun CPointer<JNIEnvVar>.jniTable(): CPointer<JNINativeInterface>? = pointed.value

internal fun CPointer<*>.toHandle(): Long = rawValue.toLong()

internal fun <T : CPointed> Long.toNativePointer(): CPointer<T> = toCPointer<T>() ?: error("null native handle")

internal fun asNet(handle: Long): ncnn_net_t = handle.toNativePointer()

internal fun asMat(handle: Long): ncnn_mat_t = handle.toNativePointer()

internal fun asExtractor(handle: Long): ncnn_extractor_t = handle.toNativePointer()

internal fun jniString(
    table: CPointer<JNINativeInterface>,
    env: CPointer<JNIEnvVar>,
    value: jstring?,
): String? {
    if (value == null) return null
    val chars = table.pointed.GetStringChars!!(env, value, null) ?: return null
    return try {
        chars.toKString()
    } finally {
        table.pointed.ReleaseStringChars!!(env, value, chars)
    }
}

internal fun jniNewString(
    table: CPointer<JNINativeInterface>,
    env: CPointer<JNIEnvVar>,
    value: String?,
): jstring? {
    if (value == null) return null
    return memScoped { table.pointed.NewStringUTF!!(env, value.cstr.ptr) }
}

internal fun jniNewStringUtf8(
    table: CPointer<JNINativeInterface>,
    env: CPointer<JNIEnvVar>,
    value: CPointer<ByteVar>?,
): jstring? = value?.let { table.pointed.NewStringUTF!!(env, it) }

internal fun jniFloatArray(
    table: CPointer<JNINativeInterface>,
    env: CPointer<JNIEnvVar>,
    array: jfloatArray?,
): FloatArray? {
    if (array == null) return null
    val length = table.pointed.GetArrayLength!!(env, array.reinterpret())
    if (length <= 0) return FloatArray(0)
    val values = FloatArray(length)
    values.usePinned {
        table.pointed.GetFloatArrayRegion!!(env, array, 0, length, it.addressOf(0))
    }
    return values
}

internal fun jniByteArray(
    table: CPointer<JNINativeInterface>,
    env: CPointer<JNIEnvVar>,
    array: jbyteArray?,
): ByteArray? {
    if (array == null) return null
    val length = table.pointed.GetArrayLength!!(env, array.reinterpret())
    if (length <= 0) return ByteArray(0)
    val values = ByteArray(length)
    values.usePinned {
        table.pointed.GetByteArrayRegion!!(env, array, 0, length, it.addressOf(0))
    }
    return values
}

internal fun jniByteArrayLength(
    table: CPointer<JNINativeInterface>,
    env: CPointer<JNIEnvVar>,
    array: jbyteArray?,
): Int = if (array == null) 0 else table.pointed.GetArrayLength!!(env, array.reinterpret())

internal fun jniFloatArrayLength(
    table: CPointer<JNINativeInterface>,
    env: CPointer<JNIEnvVar>,
    array: jfloatArray?,
): Int = if (array == null) 0 else table.pointed.GetArrayLength!!(env, array.reinterpret())

private fun CPointer<JNINativeInterface>.hasPendingException(env: CPointer<JNIEnvVar>): Boolean = pointed.ExceptionCheck!!(env) != 0.toUByte()

internal fun throwJvm(env: CPointer<JNIEnvVar>, className: String, message: String) {
    val table = env.jniTable() ?: return
    if (table.hasPendingException(env)) return
    val cls = memScoped { table.pointed.FindClass!!(env, className.cstr.ptr) } ?: return
    memScoped { table.pointed.ThrowNew!!(env, cls, message.cstr.ptr) }
}

internal fun throwJvmNcnn(env: CPointer<JNIEnvVar>, error: NcnnException) {
    val table = env.jniTable() ?: return
    if (table.hasPendingException(env)) return
    val cls = memScoped {
        table.pointed.FindClass!!(env, "io/github/qingshu/ncnn/NcnnException".cstr.ptr)
    } ?: return
    val ctor = memScoped {
        table.pointed.GetMethodID!!(
            env,
            cls,
            "<init>".cstr.ptr,
            "(Ljava/lang/String;I)V".cstr.ptr,
        )
    } ?: return
    memScoped {
        val message = table.pointed.NewStringUTF!!(env, (error.message ?: "ncnn error").cstr.ptr)
            ?: return@memScoped
        val args = allocArray<jvalue>(2)
        args[0].l = message.reinterpret()
        args[1].i = error.code
        val exception = table.pointed.NewObjectA!!(env, cls, ctor, args) ?: return@memScoped
        table.pointed.Throw!!(env, exception)
    }
}

internal inline fun <T> jniBoundary(env: CPointer<JNIEnvVar>, fallback: T, block: () -> T): T = try {
    block()
} catch (e: NcnnException) {
    throwJvmNcnn(env, e)
    fallback
} catch (e: OutOfMemoryError) {
    throwJvm(env, "java/lang/OutOfMemoryError", e.message ?: "out of memory")
    fallback
} catch (e: IllegalArgumentException) {
    throwJvm(env, "java/lang/IllegalArgumentException", e.message ?: "invalid argument")
    fallback
} catch (e: IllegalStateException) {
    throwJvm(env, "java/lang/IllegalStateException", e.message ?: "invalid state")
    fallback
} catch (e: Throwable) {
    throwJvm(env, "java/lang/RuntimeException", e.toString())
    fallback
}

private val netModelBuffers: MutableMap<Long, COpaquePointer> = mutableMapOf()

internal fun storeNetBuffer(handle: Long, buffer: COpaquePointer) {
    netModelBuffers.remove(handle)?.let { free(it) }
    netModelBuffers[handle] = buffer
}

internal fun releaseNetBuffer(handle: Long) {
    netModelBuffers.remove(handle)?.let { free(it) }
}

internal fun allocate(size: Int): COpaquePointer? = malloc(size.convert())

internal fun requireMat(mat: ncnn_mat_t?, operation: String): Long {
    if (mat == null) checkStatus(-1, operation)
    return mat!!.toHandle()
}

internal fun pixelChannels(type: Int): Int = when (type) {
    1, 2 -> 3
    3 -> 1
    4, 5 -> 4
    else -> 0
}
