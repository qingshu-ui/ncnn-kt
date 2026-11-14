@file:OptIn(ExperimentalForeignApi::class)

package io.github.qingshu.ncnn.binding

import kotlinx.cinterop.ExperimentalForeignApi
import ncnn.ncnn_net_t

internal expect fun loadParamPath(net: ncnn_net_t, path: String)

internal expect fun loadModelPath(net: ncnn_net_t, path: String)
