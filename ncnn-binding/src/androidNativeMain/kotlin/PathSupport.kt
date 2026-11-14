@file:OptIn(ExperimentalForeignApi::class)

package io.github.qingshu.ncnn.binding

import kotlinx.cinterop.ExperimentalForeignApi
import ncnn.ncnn_net_t

internal actual fun loadParamPath(net: ncnn_net_t, path: String) {
    checkStatus(ncnn.ncnn_net_load_param(net, path), "Net.loadParam(path)")
}

internal actual fun loadModelPath(net: ncnn_net_t, path: String) {
    checkStatus(ncnn.ncnn_net_load_model(net, path), "Net.loadModel(path)")
}
