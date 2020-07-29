package org.jetbrains.research.kotlin.inference.data.ndarray

import TensorProto
import org.jetbrains.research.kotlin.inference.data.tensors.Strides

class BooleanNDArray(array: BooleanArray, strides: Strides = Strides.empty()) : NDArray(array, strides, TensorProto.DataType.BOOL) {
    override fun clone(newStrides: Strides): BooleanNDArray {
        return BooleanNDArray((array as BooleanArray).copyOf(), newStrides)
    }

    override fun get(i: Int): Boolean {
        return (array as BooleanArray)[i]
    }

    override fun get(indices: IntArray): Boolean {
        return (array as BooleanArray)[strides.offset(indices)]
    }

    override fun plus(other: NDArray): NDArray {
        TODO("Not yet implemented")
    }

    override fun times(other: NDArray): NDArray {
        TODO("Not yet implemented")
    }

    override fun div(other: NDArray): NDArray {
        TODO("Not yet implemented")
    }

    override fun minus(other: NDArray): NDArray {
        TODO("Not yet implemented")
    }

    override fun placeAll(startOffset: Int, block: Any) {
        array as BooleanArray; block as BooleanArray
        block.copyInto(array, startOffset)
    }
}
