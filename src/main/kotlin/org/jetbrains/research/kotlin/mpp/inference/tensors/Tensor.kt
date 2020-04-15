package org.jetbrains.research.kotlin.mpp.inference.tensors

import TensorProto
import TensorProto.DataType
import org.jetbrains.research.kotlin.mpp.inference.space.*
import scientifik.kmath.linear.transpose
import scientifik.kmath.operations.Ring
import scientifik.kmath.structures.*

//TODO: support segments
//TODO: support external and raw data
//TODO: numpy-like multidirectional broadcasting
class Tensor<T : Number>(val name: String?, val data: NDBuffer<T>, val type: DataType?, private val space: TensorRing<T>?) {
    operator fun plus(other: Tensor<T>): Tensor<T> {
        require(type != DataType.STRING) { "Available only for numeric tensors" }

        val res = space!!.add(data, other.data)
        return Tensor(name, res, type, space)
    }

    infix fun dot(other: Tensor<T>): Tensor<T> {
        require(data.dimension <= 2) { "Not supported for more than 2-dimensional tensors" }

        val resMatrix = with (space!!.matrixContext) { data.as2D() dot other.data.as2D() }
        val newSpace = space.rebuild(newDims = resMatrix.shape)
        return Tensor(name, resMatrix, type, newSpace)
    }

    fun mapElements(func: (T) -> T): Tensor<T> {
        val newData = BufferNDStructure(SpaceStrides(data.shape), data.buffer.asIterable().map(func).asBuffer())
        return Tensor(name, newData, type, space)
    }

    operator fun times(other: Tensor<T>): Tensor<T> {
        require(data.shape.contentEquals(other.data.shape))

        val newData = space!!.multiply(this.data, other.data)
        return Tensor(name, newData, type, space)
    }

    fun transpose(): Tensor<T> {
        require(data.dimension <= 2) { "Not supported for more than 2-dimensional tensors" }

        val resMatrix = data.as2D().transpose()
        val newSpace = space?.rebuild(newDims = resMatrix.shape)
        return Tensor(name, resMatrix, type, newSpace)
    }

    fun as2DCollection(): Collection<Tensor<T>> {
        require(data.dimension == 3)

        val blockSize = data.shape[1] * data.shape[2]
        val newShape = intArrayOf(data.shape[1], data.shape[2])
        val newSpace = space!!.rebuild(newShape)
        val newStrides = SpaceStrides(newShape)
        //val chunkedBuffer = data.buffer.asIterable().chunked(blockSize)
        return List(data.shape[0]) {index ->
            val newBuffer = VirtualBuffer(blockSize) { i ->
                val indices = newStrides.index(i)
                val rowNum = indices[0]
                val colNum = indices[1]
                data[index, rowNum, colNum]
            }
            val newStructure = BufferNDStructure(SpaceStrides(newShape), newBuffer)
            Tensor(null, newStructure, type, newSpace)
        }
    }

    fun mapIndexed(transform: Ring<T>.(index: IntArray, T) -> T): Tensor<T>{
        val newBuffer = space!!.mapIndexed(data, transform)
        return Tensor(name, newBuffer, type, space)
    }

    companion object {
        //TODO: complex, uint32/64 tensors, strings
        fun create(proto: TensorProto): Tensor<*> = when (val type = DataType.fromValue(proto.data_type ?: 0)) {
            DataType.DOUBLE -> Tensor(proto.dims, proto.double_data, type, proto.name, resolveSpace(proto.dims))
            DataType.FLOAT -> Tensor(proto.dims, proto.float_data, type, proto.name, resolveSpace(proto.dims))
            DataType.INT64 -> Tensor(proto.dims, proto.int64_data, type, proto.name, resolveSpace(proto.dims))
            DataType.INT32 -> Tensor(proto.dims, proto.int32_data, type, proto.name, resolveSpace(proto.dims))
            else -> error("Unsupported data type")
        }

        private operator fun <T : Number> invoke(name: String?, matrix: Matrix<T>, type: DataType?, space: TensorRing<T>?): Tensor<T> {
            val buffer = matrix.elements().map { it.second }.toList().asBuffer()
            return Tensor(name, BufferNDStructure(SpaceStrides(matrix.shape), buffer as Buffer<T>), type, space)
        }

        operator fun <T : Number> invoke(dims: List<Long>, value: List<T>, type: DataType?, name: String?, space: TensorRing<T>?): Tensor<T> {
            val data = BufferNDStructure(SpaceStrides(dims.toIntArray()), value.asBuffer())
            return Tensor(name, data, type, space!!)
        }

        inline operator fun <reified T : Number> invoke(value: List<T>, type: DataType?): Tensor<T> {
            val dims = intArrayOf(value.size, 1)
            val data = BufferNDStructure(SpaceStrides(dims), value.asBuffer())
            val space = tryResolveSpace<T>(dims)
            return Tensor(null, data, type, space)
        }
    }
}

