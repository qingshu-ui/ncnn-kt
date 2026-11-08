package io.github.qingshu.ncnn

import kotlinx.cinterop.ExperimentalForeignApi
import ncnn.ncnn_net_t

@OptIn(ExperimentalForeignApi::class)
internal actual fun netLoadParamPath(net: ncnn_net_t, path: String) {
    checkStatus(ncnn.ncnn_net_load_param(net, path), "Net.loadParam(path)")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun netLoadModelPath(net: ncnn_net_t, path: String) {
    checkStatus(ncnn.ncnn_net_load_model(net, path), "Net.loadModel(path)")
}
