package io.github.qingshu.ncnn

import kotlinx.cinterop.ExperimentalForeignApi
import ncnn.ncnn_net_t

@OptIn(ExperimentalForeignApi::class)
internal expect fun netLoadParamPath(net: ncnn_net_t, path: String)

@OptIn(ExperimentalForeignApi::class)
internal expect fun netLoadModelPath(net: ncnn_net_t, path: String)
