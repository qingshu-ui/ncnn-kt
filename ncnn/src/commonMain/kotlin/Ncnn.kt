package io.github.qingshu.ncnn

/**
 * Thrown when ncnn reports a failure or a native handle cannot be used.
 *
 * @property code ncnn status code, or `-1` when the failure was detected in Kotlin.
 */
class NcnnException(message: String, val code: Int = -1) : RuntimeException(message)

/** Global ncnn information. */
object Ncnn {
    /** Human readable ncnn version, for example `1.0.20261020`. */
    val version: String get() = ncnnVersion()

    /** Numeric ncnn version, for example `20261020`. */
    val versionNumber: Int get() = ncnnVersionNumber()
}

internal expect fun ncnnVersion(): String

internal expect fun ncnnVersionNumber(): Int
