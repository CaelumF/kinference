package io.kinference.data.tensors


import io.kinference.data.ONNXData
import io.kinference.data.ONNXDataType
import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.*
import io.kinference.ndarray.arrays.tiled.*
import io.kinference.ndarray.extensions.*
import io.kinference.ndarray.toIntArray
import io.kinference.onnx.TensorProto
import io.kinference.onnx.TensorProto.DataType
import io.kinference.types.TensorInfo
import io.kinference.types.TensorShape
import okio.ByteString
import java.nio.ByteBuffer
import java.nio.ByteOrder

//TODO: support segments
//TODO: support external and raw data
class Tensor(val data: NDArray, info: TensorInfo) : ONNXData(ONNXDataType.ONNX_TENSOR, info) {
    override fun rename(name: String): ONNXData {
        return Tensor(data, TensorInfo(name, info.type, TensorShape(data.shape)))
    }

    operator fun plus(other: Tensor): Tensor {
        require(this.data is NumberNDArray && other.data is NumberNDArray)
        return (this.data + other.data).asTensor()
    }

    operator fun minus(other: Tensor): Tensor {
        require(this.data is NumberNDArray && other.data is NumberNDArray)
        return (this.data - other.data).asTensor()
    }

    operator fun times(other: Tensor): Tensor {
        require(this.data is NumberNDArray && other.data is NumberNDArray)
        return (this.data * other.data).asTensor()
    }

    infix fun matmul(other: Tensor): Tensor {
        require(this.data is NumberNDArray && other.data is NumberNDArray)
        return (this.data matmul other.data).asTensor()
    }

    companion object {
        //TODO: complex, uint32/64 tensors
        @Suppress("UNCHECKED_CAST")

        fun create(proto: TensorProto, divider: Int = 1): Tensor {
            if (proto.dims.isNullOrEmpty()) return parseScalar(proto)

            val shape = proto.dims.toIntArray()
            val type = DataType.fromValue(proto.data_type ?: 0)
            val array = when (type) {
                DataType.DOUBLE -> proto.double_data
                DataType.FLOAT -> proto.float_data
                DataType.INT64 -> proto.int64_data
                DataType.INT32 -> proto.int32_data
                DataType.BOOL -> proto.int32_data.map { it != 0 }
                DataType.STRING -> proto.string_data.map { it.utf8() }
                DataType.UINT8 -> proto.int32_data.map { it.toUByte() }
                DataType.INT8 -> proto.int32_data.map { it.toByte() }
                else -> error("Unsupported data type $type")
            }

            return if (array.isEmpty()) {
//                require(proto.raw_data != null) { "Tensor without data" }
                val rawData = proto.raw_data ?: ByteString.EMPTY

                val buffer = ByteBuffer.wrap(rawData.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
                val sizeInBytes = rawData.size

                when (type) {
                    DataType.DOUBLE -> {
                        val array = buffer.asDoubleBuffer()
                        DoubleNDArray(shape, divider) { array[it] }.asTensor(proto.name)
                    }
                    DataType.FLOAT -> {
                        val array = buffer.asFloatBuffer()
                        FloatNDArray(shape, divider) { array[it] }.asTensor(proto.name)
                    }
                    DataType.INT64 -> {
                        val array = buffer.asLongBuffer()
                        LongNDArray(shape, divider) { array[it] }.asTensor(proto.name)
                    }
                    DataType.INT32 -> {
                        val array = buffer.asIntBuffer()
                        IntNDArray(shape, divider) { array[it] }.asTensor(proto.name)
                    }
                    DataType.INT8 -> {
                        ByteNDArray(shape, divider) { buffer[it] }.asTensor(proto.name)
                    }
                    DataType.UINT8 -> {
                        UByteNDArray(shape, divider) { buffer[it].toUByte() }.asTensor(proto.name)
                    }
                    DataType.BOOL -> {
                        BooleanNDArray(shape, divider) { buffer[it] != 0.toByte() }.asTensor(proto.name)
                    }
                    DataType.STRING -> error("String data MUST not be present in raw_data field")
                    else -> error("Unsupported data type $type")
                }
            } else when (type) {
                DataType.DOUBLE -> Tensor(array as List<Double>, type, shape, proto.name, divider)
                DataType.FLOAT -> Tensor(array as List<Float>, type, shape, proto.name, divider)
                DataType.INT64 -> Tensor(array as List<Long>, type, shape, proto.name, divider)
                DataType.INT32 -> Tensor(array as List<Int>, type, shape, proto.name, divider)
                DataType.INT8 -> Tensor(array as List<Byte>, type, shape, proto.name, divider)
                DataType.UINT8 -> Tensor(array as List<UByte>, type, shape, proto.name, divider)
                DataType.BOOL -> Tensor(array as List<Boolean>, type, shape, proto.name, divider)
                DataType.STRING -> Tensor(array as List<String>, type, shape, proto.name, divider)
                else -> error("Unsupported data type $type")
            }
        }

        operator fun invoke(dims: List<Long>, value: List<Any?>, type: DataType, name: String?, divider: Int = 1): Tensor {
            val shape = dims.toIntArray()

            val data = createArray(type.resolveLocalDataType(), shape, divider) { i -> value[i]!! }
            return Tensor(data, type, shape, name)
        }

        operator fun invoke(value: Any, type: DataType, dims: IntArray = IntArray(0), name: String? = "", divider: Int = 1): Tensor {
            val name = name ?: ""
            if (dims.isEmpty()) return createScalarNDArray(type.resolveLocalDataType(), value).asTensor(name)

            value as List<Any?>
            return when (type) {
                DataType.DOUBLE -> DoubleNDArray(dims, divider) { value[it] as Double }.asTensor(name)
                DataType.FLOAT -> FloatNDArray(dims, divider) { value[it] as Float }.asTensor(name)
                DataType.INT32 -> IntNDArray(dims, divider) { value[it] as Int }.asTensor(name)
                DataType.INT8 -> ByteNDArray(dims, divider) { value[it] as Byte }.asTensor(name)
                DataType.UINT8 -> UByteNDArray(dims, divider) { value[it] as UByte }.asTensor(name)
                DataType.INT64 -> LongNDArray(dims, divider) { value[it] as Long }.asTensor(name)
                DataType.INT16 -> ShortNDArray(dims, divider) { value[it] as Short }.asTensor(name)
                DataType.BOOL -> BooleanNDArray(dims, divider) { value[it] as Boolean }.asTensor(name)
                else -> error("Unsupported data type $type")
            }
        }

        operator fun invoke(value: List<Any>, type: DataType): Tensor {
            val dims = intArrayOf(value.size)
            val data = createArray(type.resolveLocalDataType(), dims) { i -> value[i] }
            return Tensor(data, type, dims)
        }

        private fun parseScalar(proto: TensorProto): Tensor {
            val type = DataType.fromValue(proto.data_type ?: 0)
            val array = when (type) {
                DataType.DOUBLE -> proto.double_data
                DataType.FLOAT -> proto.float_data
                DataType.INT64 -> proto.int64_data
                DataType.INT32 -> proto.int32_data
                DataType.INT8 -> if (!proto.int32_data.isNullOrEmpty()) proto.int32_data.map { it.toByte() } else emptyList()
                DataType.UINT8 -> if (!proto.int32_data.isNullOrEmpty()) proto.int32_data.map { it.toUByte() } else emptyList()
                DataType.BOOL -> proto.int32_data.map { it != 0 }
                else -> error("Unsupported data type $type")
            }
            return if (array.isEmpty()) {
                val buffer = proto.raw_data!!.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN)
                when (type) {
                    DataType.DOUBLE -> DoubleNDArray.scalar(buffer.double)
                    DataType.FLOAT -> FloatNDArray.scalar(buffer.float)
                    DataType.INT64 -> LongNDArray.scalar(buffer.long)
                    DataType.INT32 -> IntNDArray.scalar(buffer.int)
                    DataType.INT16 -> ShortNDArray.scalar(buffer.short)
                    DataType.INT8 -> ByteNDArray.scalar(buffer.get())
                    DataType.UINT8 -> UByteNDArray.scalar(buffer.get().toUByte())
                    DataType.BOOL -> BooleanNDArray.scalar(buffer.int != 0)
                    else -> error("Unsupported data type $type")
                }.asTensor(proto.name)
            } else createScalarNDArray(type.resolveLocalDataType(), array[0]).asTensor(proto.name)
        }
    }
}
