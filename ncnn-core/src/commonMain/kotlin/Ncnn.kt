package io.github.qingshu.ncnn_core

/** Thrown when ncnn reports a failure or a native handle cannot be used. */
class NcnnException(message: String, val code: Int = -1) : RuntimeException(message)

/** Global ncnn information. */
object Ncnn {
    /** Human readable ncnn version, for example `1.0.20261020`. */
    val version: String get() = ncnnVersionString()

    /** Numeric ncnn version, for example `20261020`. */
    val versionNumber: Int get() = ncnnVersionNumber()
}
