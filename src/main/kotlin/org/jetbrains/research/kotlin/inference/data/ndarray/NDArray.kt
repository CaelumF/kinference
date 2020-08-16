package org.jetbrains.research.kotlin.inference.data.ndarray

import org.jetbrains.research.kotlin.inference.data.tensors.*
import org.jetbrains.research.kotlin.inference.extensions.ndarray.*
import org.jetbrains.research.kotlin.inference.extensions.primitives.*
import org.jetbrains.research.kotlin.inference.onnx.TensorProto
import org.jetbrains.research.kotlin.inference.onnx.TensorProto.DataType
import org.jetbrains.research.kotlin.inference.types.TensorInfo
import org.jetbrains.research.kotlin.inference.types.TensorShape
import kotlin.math.abs

abstract class NDArray<T> protected constructor(override val array: T, strides: Strides, override val type: DataType) : TypedNDArray<T> {
    final override var strides: Strides = strides
        protected set

    override val rank: Int
        get() = strides.shape.size

    override val linearSize: Int
        get() = strides.linearSize

    override val shape: IntArray
        get() = strides.shape

    override infix fun matmul(other: TypedNDArray<T>): TypedNDArray<T> {
        require(!this.isScalar() && !other.isScalar()) { "Matmul operation is not available for scalar tensors" }
        if (rank <= 2 && other.rank <= 2) {
            val actualThis = if (rank == 1) this.toMutable().reshape(1.concat(shape)) else this.toMutable()
            val actualOther = if (other.rank == 1) this.toMutable().reshape(other.shape.concat(1)) else other.toMutable()
            return actualThis.matrixDot(actualOther)
        }

        val (fstShape, sndShape) = broadcastMatrixElementsShape(shape, other.shape)
        val thisMatrices = this.broadcast(fstShape, asMatrixStack = true).as2DList()
        val otherMatrices = other.broadcast(sndShape, asMatrixStack = true).as2DList()

        val resMatrices = thisMatrices.mapIndexed { i, tensor ->
            tensor.matrixDot(otherMatrices[i])
        }

        val lastDims = resMatrices.first().shape

        val shape = shape.copyOf(rank - 2) + lastDims
        return resMatrices.concatenate(0).toMutable().reshape(shape)
    }

    override fun row(row: Int): MutableTypedNDArray<T> {
        val rowLength: Int = linearSize / shape[0]
        val start = row * rowLength
        val dims = shape.copyOfRange(1, rank)

        return createMutableNDArray(type, slice(rowLength, start), Strides(dims)) as MutableTypedNDArray<T>
    }

    override fun slice(starts: IntArray, ends: IntArray, steps: IntArray): NDArray<T> {
        val newShape = IntArray(shape.size) {
            val length = abs(ends[it] - starts[it])
            val rest = length % abs(steps[it])
            (length / abs(steps[it])) + if (rest != 0) 1 else 0
        }

        val newStrides = Strides(newShape)
        val newArray = createLateInitArray(type, newStrides)

        slice(newArray, 0, 0, shape, starts, ends, steps)

        return createNDArrayFromLateInitArray(type, newArray, newStrides) as NDArray<T>
    }

    private fun slice(dest: LateInitArray, offset: Int, axis: Int, shape: IntArray, starts: IntArray, ends: IntArray, steps: IntArray) {
        val start = starts[axis]
        val end = ends[axis]
        val step = steps[axis]

        val range = if (step > 0) (start until end step step) else (start downTo end + 1 step -step)

        if (axis == shape.size - 1) {
            appendToLateInitArray(dest, range, offset)
        } else {
            var dim = 1
            for (ind in (axis + 1) until shape.size) dim *= shape[ind]

            for (index in range) {
                slice(dest, offset + index * dim, axis + 1, shape, starts, ends, steps)
            }
        }
    }

    // TODO: better equals
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NDArray<T>

        if (array != other.array) return false

        return true
    }

    override fun hashCode(): Int {
        return array.hashCode()
    }

    fun asTensor(name: String? = null) = Tensor(this as NDArray<Any>, TensorInfo(name ?: "", type, TensorShape(this.shape)))

    companion object {
        //TODO: complex, uint32/64 tensors
        fun create(proto: TensorProto): TypedNDArray<out Any> {
            if (proto.dims.isNullOrEmpty()) return createScalar(proto)

            return when (val type = DataType.fromValue(proto.data_type ?: 0)) {
                DataType.DOUBLE -> DoubleNDArray(proto.double_data.toDoubleArray(), Strides(proto.dims.toIntArray()))
                DataType.FLOAT -> FloatNDArray(proto.float_data.toFloatArray(), Strides(proto.dims.toIntArray()))
                DataType.INT64 -> LongNDArray(proto.int64_data.toLongArray(), Strides(proto.dims.toIntArray()))
                DataType.INT32 -> IntNDArray(proto.int32_data.toIntArray(), Strides(proto.dims.toIntArray()))
                //DataType.STRING -> Tensor(proto.string_data.map { it.utf8() }, type, proto.dims.toIntArray(), proto.name)
                else -> error("Unsupported data type $type")
            }
        }

        operator fun <T> invoke(dims: List<Long>, value: List<*>, type: DataType, mutable: Boolean = false): TypedNDArray<T> {
            val data = createArray(type, value.size) { i -> value[i]!! }
            return NDArray(type, data, dims.toIntArray(), mutable) as TypedNDArray<T>
        }


        operator fun <T : Any> invoke(type: DataType, value: T, dims: IntArray = IntArray(0), mutable: Boolean = false): TypedNDArray<T> {
            return NDArray(type, value, Strides(dims), mutable)
        }

        operator fun <T : Any> invoke(type: DataType, value: T, strides: Strides, mutable: Boolean = false): TypedNDArray<T> {
            return if (mutable) {
                createMutableNDArray<Any>(type, value, strides)
            } else {
                createNDArray<Any>(type, value, strides)
            } as TypedNDArray<T>
        }

        operator fun invoke(value: List<*>, type: DataType, mutable: Boolean = false): TypedNDArray<Any> {
            val dims = intArrayOf(value.size)
            val data = createArray(type, value.size) { i -> value[i] }
            return NDArray(type, data, dims, mutable)
        }

        private fun createScalar(proto: TensorProto): TypedNDArray<out Any> {
            val type = DataType.fromValue(proto.data_type ?: 0)
            val array = when (type) {
                DataType.DOUBLE -> proto.double_data
                DataType.FLOAT -> proto.float_data
                DataType.INT64 -> proto.int64_data
                DataType.INT32 -> proto.int32_data
                DataType.BOOL -> proto.int32_data.map { it != 0 }
                else -> error("Unsupported data type")
            }

            return if (array.isEmpty()) {
                when (type) {
                    DataType.DOUBLE -> DoubleNDArray(doubleArrayOf(proto.raw_data!!.asByteBuffer().double))
                    DataType.FLOAT -> FloatNDArray(floatArrayOf(proto.raw_data!!.asByteBuffer().float))
                    DataType.INT64 -> LongNDArray(longArrayOf(proto.raw_data!!.asByteBuffer().long))
                    DataType.INT32 -> IntNDArray(intArrayOf(proto.raw_data!!.asByteBuffer().int))
                    DataType.BOOL -> BooleanNDArray(booleanArrayOf(proto.raw_data!!.asByteBuffer().int != 0))
                    else -> error("Unsupported data type")
                }
            } else NDArray<Any>(type, array, IntArray(0))
        }
    }
}
