package org.jetbrains.research.kotlin.inference.operators.activations

import org.jetbrains.research.kotlin.inference.attributes.Attribute
import org.jetbrains.research.kotlin.inference.data.ndarray.NDArray
import org.jetbrains.research.kotlin.inference.extensions.ndarray.allocateNDArray
import org.jetbrains.research.kotlin.inference.extensions.ndarray.createScalarNDArray
import org.jetbrains.research.kotlin.inference.extensions.primitives.exp
import org.jetbrains.research.kotlin.inference.extensions.primitives.max
import org.jetbrains.research.kotlin.inference.extensions.primitives.sum
import org.jetbrains.research.kotlin.inference.onnx.AttributeProto
import org.jetbrains.research.kotlin.inference.operators.AttributeInfo
import org.jetbrains.research.kotlin.inference.operators.IOInfo
import org.jetbrains.research.kotlin.inference.operators.OperatorInfo

//only for float and double types
class Softmax(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Activation(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = FLOAT_DATA_TYPES

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), false, default = 1L)
        )

        private val INFO = OperatorInfo("Softmax", ATTRIBUTES_INFO,
            listOf(IOInfo(0, TYPE_CONSTRAINTS, "input", optional = false)),
            listOf(IOInfo(0, TYPE_CONSTRAINTS, "output", optional = false))
        )

        private fun resolveDims(dims: IntArray?): Int {
            return if (dims == null || dims.isEmpty()) 1 else dims.reduce(Int::times)
        }

        private fun expMatrixRows(input: NDArray<Any>, axis: Int): Array<NDArray<Any>> {
            val actualAxis = input.indexAxis(axis)
            val shape = input.shape
            val (rowIdx, columnIdx) = (shape.indices).partition { it < actualAxis }

            val rows = resolveDims(shape.sliceArray(rowIdx))
            val columns = resolveDims(shape.sliceArray(columnIdx))

            val matrixRows = input.reshape(intArrayOf(rows, columns)).rows

            return Array(matrixRows.size) { i ->
                val max = matrixRows[i].max()!!
                matrixRows[i].minus(createScalarNDArray(input.type, max), false).exp()
            }
        }

        fun softmax(input: NDArray<Any>, axis: Int = 0): NDArray<Any> {
            val matrixRows = expMatrixRows(input, axis)

            val step = matrixRows[0].linearSize
            val array = allocateNDArray(input.type, input.strides)
            repeat(matrixRows.size) { i ->
                val sum = matrixRows[i].sum()
                val row = matrixRows[i].div(createScalarNDArray(input.type, sum), false)
                array.placeAll(i * step, row.array)
            }
            return array
        }
    }

    private val axis: Int by attribute { it: Number -> it.toInt() }

    override fun activate(input: NDArray<Any>): NDArray<Any> {
        return softmax(input, axis)
    }
}
