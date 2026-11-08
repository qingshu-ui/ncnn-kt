package io.github.qingshu.ncnn

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.wcstr
import ncnn.ncnn_net_t

@OptIn(ExperimentalForeignApi::class)
internal actual fun netLoadParamPath(net: ncnn_net_t, path: String) {
    val status = memScoped { ncnn.ncnn_net_load_param_w(net, path.wcstr) }
    checkStatus(status, "Net.loadParam(path)")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun netLoadModelPath(net: ncnn_net_t, path: String) {
    val status = memScoped { ncnn.ncnn_net_load_model_w(net, path.wcstr) }
    checkStatus(status, "Net.loadModel(path)")
}
