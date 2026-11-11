@file:OptIn(ExperimentalForeignApi::class)

package io.github.qingshu.ncnn

import jni.JNIEnvVar
import jni.JNINativeInterface_
import jni.jbyteArray
import jni.jclass
import jni.jfloatArray
import jni.jstring
import jni.jvalue
import kotlinx.cinterop.ExperimentalForeignApi

internal typealias JniEnvVar = JNIEnvVar
internal typealias JniTable = JNINativeInterface_
internal typealias JClass = jclass
internal typealias JString = jstring
internal typealias JByteArray = jbyteArray
internal typealias JFloatArray = jfloatArray
internal typealias JValue = jvalue
