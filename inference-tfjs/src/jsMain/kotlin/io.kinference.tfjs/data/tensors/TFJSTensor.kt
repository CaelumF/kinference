package io.kinference.tfjs.data.tensors

import io.kinference.data.ONNXDataType
import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.*
import io.kinference.ndarray.arrays.pointers.forEach
import io.kinference.ndarray.arrays.tiled.*
import io.kinference.protobuf.message.TensorProto
import io.kinference.protobuf.message.TensorProto.DataType
import io.kinference.protobuf.resolveProtoDataType
import io.kinference.tfjs.data.TFJSData
import io.kinference.tfjs.externals.core.NDArrayTFJS
import io.kinference.tfjs.externals.core.tensor
import io.kinference.tfjs.externals.extensions.*
import io.kinference.tfjs.types.ValueInfo

class TFJSTensor(data: NDArrayTFJS, info: ValueInfo) : TFJSData<NDArrayTFJS>(data, info) {
    override val type: ONNXDataType = ONNXDataType.ONNX_TENSOR

    override fun rename(name: String): TFJSTensor {
        return TFJSTensor(data, ValueInfo(info.typeInfo, name))
    }

    fun toNDArray(): NDArray {
        val shapeIntArray = data.shape.toIntArray()
        val strides = Strides(shapeIntArray)
        return when(data.dtype) {
            "float32" -> {
                val array = FloatTiledArray(strides, data.dataFloat())
                FloatNDArray(array, Strides(shapeIntArray))
            }

            "int32" -> {
                val array = IntTiledArray(strides, data.dataInt())
                IntNDArray(array, strides)
            }

            else -> error("Unsupported type")
        }
    }

    companion object {
        //TODO: complex, uint32/64 tensors
        @Suppress("UNCHECKED_CAST")
        fun create(proto: TensorProto): TFJSTensor {
            val type = proto.dataType ?: DataType.UNDEFINED
            val array = parseArray(proto)
            requireNotNull(array) { "Array value should be initialized" }

            return TFJSTensor(array, type, proto.dims, proto.name)
        }

        operator fun invoke(value: NDArray, name: String? = ""): TFJSTensor {
            return when (val resolvedType = value.type.resolveProtoDataType()) {
                DataType.FLOAT -> invoke((value as FloatNDArray).array, resolvedType, value.shape, name)
                DataType.INT32 -> invoke((value as IntNDArray).array, resolvedType, value.shape, name)
                DataType.UINT8 -> invoke((value as UByteNDArray).array, resolvedType, value.shape, name)
                DataType.INT64 -> invoke((value as LongNDArray).array, resolvedType, value.shape, name)
                else -> error("Unsupported type")
            }
        }

        private operator fun invoke(value: Any, type: DataType, dims: IntArray, name: String? = ""): TFJSTensor {
            val nameNotNull = name.orEmpty()
            val typedDims = dims.toTypedArray()
            return when (type) {
                DataType.FLOAT -> tensor((value as FloatTiledArray).toArray(), typedDims, "float32").asTensor(nameNotNull)
                DataType.INT32 -> tensor((value as IntTiledArray).toArray(), typedDims, "int32").asTensor(nameNotNull)
                DataType.UINT8 -> tensor((value as UByteTiledArray).toArray().toTypedArray(), typedDims, "int32").asTensor(nameNotNull)
                DataType.INT8  -> tensor((value as ByteTiledArray).toArray().toTypedArray(), typedDims, "int32").asTensor(nameNotNull)
                DataType.INT64 -> {
                    value as LongTiledArray
                    val outputIntArray = IntArray(value.size)
                    var count = 0
                    value.pointer().forEach(value.size) {
                        outputIntArray[count++] = it.toInt()
                    }
                    tensor(outputIntArray, typedDims, dtype = "int32").asTensor(nameNotNull)
                }
                else -> error("Unsupported type")
            }
        }

        private fun parseArray(proto: TensorProto) = when {
            proto.isTiled() -> proto.tiledData
            proto.isString() -> proto.stringData
            else -> error("Unsupported data type ${proto.dataType}")
        }
    }
}
