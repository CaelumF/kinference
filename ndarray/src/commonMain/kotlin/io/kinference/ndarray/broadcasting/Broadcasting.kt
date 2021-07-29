package io.kinference.ndarray.broadcasting

import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.*
import io.kinference.ndarray.extensions.allocateNDArray
import io.kinference.primitives.types.DataType

// TODO remove to different module
fun unsqueezeFirst(shape: IntArray, newShapeSize: Int): IntArray {
    val wrapSize = newShapeSize - shape.size

    val wrappedShape = IntArray(newShapeSize)
    wrappedShape.fill(1, 0, wrapSize)
    shape.copyInto(wrappedShape, wrapSize)
    return wrappedShape
}

object Broadcasting {
    fun broadcastShape(shapes: List<IntArray>): IntArray {
        val totalShapeLength = shapes.maxOf { it.size }

        return IntArray(totalShapeLength) { i ->
            val dims = shapes.map { it.getOrNull(it.size - i - 1) ?: 1 }
            val maxDim = dims.find { it != 1 } ?: 1

            if (dims.all { it != 1 && it != maxDim }) error("Cannot broadcast shapes")

            maxDim
        }.reversedArray()
    }

    fun broadcastShapeForMatmul(leftShape: IntArray, rightShape: IntArray): IntArray {
        val outputMatrixShape = intArrayOf(leftShape[leftShape.lastIndex - 1], rightShape.last())
        val broadcastShape = broadcastShape(listOf(leftShape.copyOfRange(0, leftShape.size - 2), rightShape.copyOfRange(0, rightShape.size - 2)))

        val outputShape = IntArray(broadcastShape.size + 2)
        broadcastShape.copyInto(outputShape)
        outputMatrixShape.copyInto(outputShape, broadcastShape.size)

        return outputShape
    }

    fun applyWithBroadcast(inputs: List<NDArray>, destination: MutableNDArray, op: (List<NDArray>, MutableNDArray) -> Unit): MutableNDArray {
        val newShape = broadcastShape(inputs.map { it.shape })

        require(destination.shape.contentEquals(newShape))

        val wrappedInputs = inputs.map { it.reshapeView(unsqueezeFirst(it.shape, newShape.size)) }

        broadcast(wrappedInputs, destination, op)
        return destination
    }

    fun applyWithBroadcast(inputs: List<NDArray>, destType: DataType, op: (List<NDArray>, MutableNDArray) -> Unit): MutableNDArray {
        val newShape = broadcastShape(inputs.map { it.shape })
        val destination = allocateNDArray(destType, newShape)

        val wrappedInputs = inputs.map { it.reshapeView(unsqueezeFirst(it.shape, newShape.size)) }

        broadcast(wrappedInputs, destination, op)
        return destination
    }

    fun matmulWithBroadcast(
        left: NDArray,
        right: NDArray,
        destination: MutableNDArray,
        dotFunc: NumberNDArray.(NumberNDArray, MutableNumberNDArray) -> MutableNumberNDArray
    ) {
        require(broadcastShapeForMatmul(left.shape, right.shape).contentEquals(destination.shape))

        val wrappedLeft = left.reshapeView(unsqueezeFirst(left.shape, destination.shape.size))
        val wrappedRight = right.reshapeView(unsqueezeFirst(right.shape, destination.shape.size))

        matmulBroadcast(wrappedLeft, wrappedRight, destination, dotFunc)
    }

    private fun broadcast(
        inputs: List<NDArray>,
        destination: MutableNDArray,
        op: (List<NDArray>, MutableNDArray) -> Unit
    ) {
        if (inputs.slice(1..inputs.lastIndex).all { it.shape.contentEquals(inputs.first().shape) }) {
            op(inputs, destination)
        } else {
            innerBroadcast(inputs, destination) { inputs, dest -> broadcast(inputs, dest, op) }
        }
    }

    private fun matmulBroadcast(
        left: NDArray,
        right: NDArray,
        destination: MutableNDArray,
        dotFunc: NumberNDArray.(NumberNDArray, MutableNumberNDArray) -> MutableNumberNDArray
    ) {
        if (left.rank == 2) {

            (left as NumberNDArray).dotFunc(right as NumberNDArray, destination as MutableNumberNDArray)

        } else {
            innerBroadcast(listOf(left, right), destination) { inputs, dest -> matmulBroadcast(inputs[0], inputs[1], dest, dotFunc) }
        }
    }

    private fun innerBroadcast(
        inputs: List<NDArray>,
        destination: MutableNDArray,
        recurrentBack: (List<NDArray>, MutableNDArray) -> Unit
    ) {
        val indexedInputs = inputs.withIndex()
        val (arraysWithOne, arraysWithoutOne) = indexedInputs.partition { it.value.shape[0] == 1 }

        if (destination.shape.size == 1) {
            val broadcastSize = destination.shape.last()
            val broadcastArraysWithOne = arraysWithOne.map { it.copy(value = it.value.allocateNDArray(Strides(intArrayOf(broadcastSize)))
                .apply { fill(it.value.singleValue()) }) }
            val mergedInputs = broadcastArraysWithOne.plus(arraysWithoutOne).sortedBy { it.index }.map { it.value }

            return recurrentBack(mergedInputs, destination)
        }

        val viewedArraysWithOne = arraysWithOne.map { it.copy(value = it.value.view(0)) }

        for (i in 0 until destination.shape[0]) {
            val viewedArraysWithoutOne = arraysWithoutOne.map { it.copy(value = it.value.view(i)) }
            val viewedDestination = destination.viewMutable(i)

            val mergedViewedInputs = viewedArraysWithOne.plus(viewedArraysWithoutOne).sortedBy { it.index }.map { it.value }

            recurrentBack(mergedViewedInputs, viewedDestination)
        }
    }
}
