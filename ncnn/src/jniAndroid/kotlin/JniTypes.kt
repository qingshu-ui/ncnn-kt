@file:OptIn(ExperimentalForeignApi::class)

package io.github.qingshu.ncnn

import kotlinx.cinterop.ExperimentalForeignApi
import platform.android.JNIEnvVar
import platform.android.JNINativeInterface
import platform.android.jbyteArray
import platform.android.jclass
import platform.android.jfloatArray
import platform.android.jstring
import platform.android.jvalue

internal typealias JniEnvVar = JNIEnvVar
internal typealias JniTable = JNINativeInterface
internal typealias JClass = jclass
internal typealias JString = jstring
internal typealias JByteArray = jbyteArray
internal typealias JFloatArray = jfloatArray
internal typealias JValue = jvalue
