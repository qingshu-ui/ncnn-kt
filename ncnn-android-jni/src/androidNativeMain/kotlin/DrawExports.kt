@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@file:Suppress("UNUSED_PARAMETER")

package io.github.qingshu.ncnn_android_jni

import io.github.qingshu.ncnn.binding.drawCircle
import io.github.qingshu.ncnn.binding.drawLine
import io.github.qingshu.ncnn.binding.drawRectangle
import io.github.qingshu.ncnn.binding.drawText
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

@CName("Java_io_github_qingshu_ncnn_NcnnJni_drawRectangle")
fun drawRectangleJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    pixels: JByteArray?,
    width: Int,
    height: Int,
    channels: Int,
    x: Int,
    y: Int,
    rectWidth: Int,
    rectHeight: Int,
    color: Int,
    thickness: Int,
): Unit = jniBoundary(env, Unit) {
    val data = jniByteArray(env, pixels) ?: return@jniBoundary
    drawRectangle(data, width, height, channels, x, y, rectWidth, rectHeight, color, thickness)
    updateJByteArray(env, pixels, data)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_drawCircle")
fun drawCircleJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    pixels: JByteArray?,
    width: Int,
    height: Int,
    channels: Int,
    centerX: Int,
    centerY: Int,
    radius: Int,
    color: Int,
    thickness: Int,
): Unit = jniBoundary(env, Unit) {
    val data = jniByteArray(env, pixels) ?: return@jniBoundary
    drawCircle(data, width, height, channels, centerX, centerY, radius, color, thickness)
    updateJByteArray(env, pixels, data)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_drawLine")
fun drawLineJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    pixels: JByteArray?,
    width: Int,
    height: Int,
    channels: Int,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int,
    color: Int,
    thickness: Int,
): Unit = jniBoundary(env, Unit) {
    val data = jniByteArray(env, pixels) ?: return@jniBoundary
    drawLine(data, width, height, channels, x0, y0, x1, y1, color, thickness)
    updateJByteArray(env, pixels, data)
}

@CName("Java_io_github_qingshu_ncnn_NcnnJni_drawText")
fun drawTextJni(
    env: CPointer<JNIEnvVar>,
    clazz: JClass?,
    pixels: JByteArray?,
    width: Int,
    height: Int,
    channels: Int,
    text: JString?,
    x: Int,
    y: Int,
    fontPixelSize: Int,
    color: Int,
): Unit = jniBoundary(env, Unit) {
    val data = jniByteArray(env, pixels) ?: return@jniBoundary
    val content = jniString(env, text) ?: return@jniBoundary
    drawText(data, width, height, channels, content, x, y, fontPixelSize, color)
    updateJByteArray(env, pixels, data)
}
