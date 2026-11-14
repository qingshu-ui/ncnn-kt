@file:OptIn(ExperimentalForeignApi::class)

package io.github.qingshu.ncnn.binding

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.wcstr
import ncnn.ncnn_net_t

internal actual fun loadParamPath(net: ncnn_net_t, path: String) {
    val status = memScoped { ncnn.ncnn_net_load_param_w(net, path.wcstr) }
    checkStatus(status, "Net.loadParam(path)")
}

internal actual fun loadModelPath(net: ncnn_net_t, path: String) {
    val status = memScoped { ncnn.ncnn_net_load_model_w(net, path.wcstr) }
    checkStatus(status, "Net.loadModel(path)")
}
