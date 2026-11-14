package io.github.qingshu.ncnn_core

internal object TestModel {
    /**
     * A tiny network: Input -> Convolution (1x1, 1 in/out channel, bias) -> ReLU.
     * With weight 2.0 and bias 0.0, output = relu(2 * input).
     */
    const val PARAM: String = "7767517\n" +
        "3 3\n" +
        "Input in0 0 1 data\n" +
        "Convolution conv0 1 1 data conv 0=1 1=1 5=1 6=1\n" +
        "ReLU relu0 1 1 conv output\n"

    /**
     * ModelBin layout used by Convolution::load_model:
     * weights are loaded with type 0 (int32 tag 0 + float32 per value),
     * bias is loaded with type 1 (raw float32 values).
     */
    fun modelBytes(): ByteArray = byteArrayOf(
        0, 0, 0, 0, // weight tag: float32 follows
        0, 0, 0, 0x40, // 2.0f
        0, 0, 0, 0, // bias: raw float 0.0f
    )
}
