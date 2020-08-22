package org.jetbrains.research.kotlin.inference.math

import org.jetbrains.research.kotlin.inference.annotations.DataType
import org.jetbrains.research.kotlin.inference.data.tensors.Strides
import kotlin.math.abs

class LateInitBooleanArray(size: Int) : LateInitArray {
    private val array = BooleanArray(size)
    private var index = 0

    fun putNext(value: Boolean) {
        array[index] = value
        index++
    }

    fun getArray(): BooleanArray {
        require(index == array.size) { "LateInitArray not initialized yet" }
        return array
    }
}


interface BooleanMap : PrimitiveToPrimitiveFunction {
    fun apply(value: Boolean): Boolean
}

open class BooleanNDArray(val array: BooleanArray, strides: Strides = Strides.empty(), override val offset: Int = 0) : NDArray {
    init {
        require(array.size == strides.linearSize)
    }

    override val type: DataType = DataType.BOOLEAN

    final override var strides: Strides = strides
        protected set

    override fun get(index: Int): Boolean {
        return array[index]
    }

    override fun get(indices: IntArray): Boolean {
        return array[strides.offset(indices)]
    }

    override fun allocateNDArray(strides: Strides): MutableNDArray {
        return MutableBooleanNDArray(BooleanArray(strides.linearSize), strides)
    }

    override fun view(vararg axes: Int): NDArray {
        val (additionalOffset, newShape) = viewHelper(axes, strides)
        return BooleanNDArray(array, Strides(newShape), offset + additionalOffset)
    }

    override fun toMutable(newStrides: Strides, additionalOffset: Int): MutableNDArray {
        return MutableBooleanNDArray(array.copyOf(), strides)
    }

    override fun copyIfNotMutable(): MutableNDArray {
        return MutableBooleanNDArray(array, strides, offset)
    }

    override fun appendToLateInitArray(array: LateInitArray, range: IntProgression, additionalOffset: Int) {
        array as LateInitBooleanArray
        for (index in range) {
            array.putNext(this.array[additionalOffset + index])
        }
    }

    override fun map(function: PrimitiveToPrimitiveFunction): MutableNDArray {
        function as BooleanMap
        val destination = allocateNDArray(strides) as MutableBooleanNDArray
        for (index in 0 until destination.linearSize) {
            destination.array[index] = function.apply(this.array[offset + index])
        }

        return destination
    }

    override fun row(row: Int): MutableNDArray {
        val rowLength: Int = linearSize / shape[0]
        val start = row * rowLength
        val dims = shape.copyOfRange(1, rank)

        return MutableBooleanNDArray(array.copyOfRange(start, start + rowLength), Strides(dims))
    }

    override fun slice(starts: IntArray, ends: IntArray, steps: IntArray): MutableNDArray {
        val newShape = IntArray(shape.size) {
            val length = abs(ends[it] - starts[it])
            val rest = length % abs(steps[it])
            (length / abs(steps[it])) + if (rest != 0) 1 else 0
        }

        val newStrides = Strides(newShape)
        val newArray = LateInitBooleanArray(newStrides.linearSize)

        slice(newArray, 0, 0, shape, starts, ends, steps)

        return MutableBooleanNDArray(newArray.getArray(), newStrides)
    }
}

class MutableBooleanNDArray(array: BooleanArray, strides: Strides = Strides.empty(), offset: Int = 0): BooleanNDArray(array, strides, offset), MutableNDArray {
    override fun set(index: Int, value: Any) {
        array[index] = value as Boolean
    }

    override fun copyIfNotMutable(): MutableNDArray {
        return MutableBooleanNDArray(array, strides, offset)
    }

    override fun mapMutable(function: PrimitiveToPrimitiveFunction): MutableNDArray {
        function as BooleanMap
        for (index in 0 until linearSize) {
            array[offset + index] = function.apply(array[offset + index])
        }

        return this
    }

    override fun viewMutable(vararg axes: Int): MutableNDArray {
        val (additionalOffset, newShape) = viewHelper(axes, strides)
        return MutableBooleanNDArray(array, Strides(newShape), offset + additionalOffset)
    }

    override fun placeFrom(offset: Int, other: NDArray, startInOther: Int, endInOther: Int) {
        other as BooleanNDArray
        other.array.copyInto(this.array, offset, startInOther, endInOther)
    }

    override fun placeAllFrom(offset: Int, other: NDArray) {
        other as BooleanNDArray
        other.array.copyInto(this.array, offset)
    }
    
    override fun reshape(strides: Strides): MutableNDArray {
        this.strides = strides
        return  this
    }

    private fun transposeRec(prevArray: BooleanArray, newArray: BooleanArray, prevStrides: Strides, newStrides: Strides, index: Int, prevOffset: Int, newOffset: Int, permutation: IntArray) {
        if (index != newStrides.shape.lastIndex) {
            val temp = prevStrides.strides[permutation[index]]
            val temp2 = newStrides.strides[index]
            for (i in 0 until newStrides.shape[index])
                transposeRec(prevArray, newArray, prevStrides, newStrides, index + 1, prevOffset + temp * i,
                    newOffset + temp2 * i, permutation)
        } else {
            val temp = prevStrides.strides[permutation[index]]
            if (temp == 1) {
                prevArray.copyInto(newArray, newOffset, prevOffset, prevOffset + newStrides.shape[index])
            } else {
                for (i in 0 until newStrides.shape[index]) {
                    newArray[newOffset + i] = prevArray[prevOffset + i * temp]
                }
            }
        }
    }

    override fun transpose(permutations: IntArray): MutableNDArray {
        val newStrides = strides.transpose(permutations)
        transposeRec(array.copyOf(), array, strides, newStrides, 0, 0, 0, permutations)
        return this.reshape(newStrides)
    }

    override fun transpose2D(): MutableNDArray {
        TODO("Not yet implemented")
    }

    override fun clean() {
        array.fill(false)
    }
}
