package io.kinference.data

import io.kinference.BackendInfo

interface ONNXData<T, B : BackendInfo> {
    val backend: B

    val name: String?
    val type: ONNXDataType
    val data: T

    fun rename(name: String): ONNXData<T, B>
}

abstract class ONNXTensor<T, B : BackendInfo>(override val name: String?, override val data: T) : ONNXData<T, B> {
    override val type: ONNXDataType = ONNXDataType.ONNX_TENSOR
}

abstract class ONNXSequence<T, B : BackendInfo>(override val name: String?, override val data: T) : ONNXData<T, B> {
    override val type: ONNXDataType = ONNXDataType.ONNX_SEQUENCE
}

abstract class ONNXMap<T, B : BackendInfo>(override val name: String?, override val data: T) : ONNXData<T, B> {
    override val type: ONNXDataType = ONNXDataType.ONNX_MAP
}
