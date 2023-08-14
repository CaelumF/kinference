package io.kinference.tfjs.data.tensors

import io.kinference.ndarray.arrays.NDArrayTFJS
//import io.kinference.ndarray.extensions.toNDArray
import io.kinference.protobuf.message.TensorProto
import io.kinference.protobuf.resolveProtoDataType
import io.kinference.types.*

fun <T : NDArrayTFJS> T.asTensor(name: String? = null) =
    TFJSTensor(this, ValueInfo(ValueTypeInfo.TensorTypeInfo(TensorShape(shape), type.resolveProtoDataType()), name ?: ""))

fun <T : NDArrayTFJS> T.asTensor(type: TensorProto.DataType,name: String? = null) =
    TFJSTensor(this, ValueInfo(ValueTypeInfo.TensorTypeInfo(TensorShape(shape), type), name ?: ""))

fun String.tfTypeResolve(): TensorProto.DataType {
    return when (this) {
        "float32" -> TensorProto.DataType.FLOAT
        "int32" -> TensorProto.DataType.INT32
        "bool" -> TensorProto.DataType.BOOL
        else -> error("Unsupported type $this")
    }
}

fun <T : NDArrayTFJS> Array<T>.asNamedOutputs(names: List<String>): List<TFJSTensor> {
    return List(this.size) {
        this[it].asTensor(names.getOrNull(it))
    }
}

fun <T : NDArrayTFJS> List<T>.asNamedOutputs(names: List<String>): List<TFJSTensor> {
    return List(this.size) {
        this[it].asTensor(names.getOrNull(it))
    }
}
