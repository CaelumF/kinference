package io.kinference.ndarray.arrays

import io.kinference.primitives.types.DataType
import io.kinference.utils.Closeable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

enum class PadMode {
    CONSTANT,
    EDGE,
    REFLECT
}

interface NDArray : Closeable {
    val type: DataType

    val strides: Strides

    val linearSize: Int
        get() = strides.linearSize
    val shape: IntArray
        get() = strides.shape
    val rank: Int
        get() = shape.size

    operator fun get(index: IntArray): Any
    fun getLinear(index: Int): Any

    fun singleValue(): Any

    fun reshape(strides: Strides): NDArray
    fun reshape(shape: IntArray): NDArray = reshape(Strides(shape))
    fun toMutable(): MutableNDArray

    fun squeeze(vararg axes: Int): NDArray
    fun unsqueeze(vararg axes: Int): NDArray

    fun stack(others: List<NDArray>, axis: Int): NDArray
    fun concat(others: List<NDArray>, axis: Int): NDArray

    fun split(parts: Int, axis: Int = 0): List<NDArray>
    fun split(split: IntArray, axis: Int = 0): List<NDArray>

    fun gather(indices: NDArray, axis: Int = 0, batchDims: Int = 0): NDArray

    fun copyIfNotMutable(): MutableNDArray
    fun clone(): NDArray

    fun slice(starts: IntArray, ends: IntArray, steps: IntArray): NDArray
    fun expand(shape: IntArray): MutableNDArray
    fun pad(pads: Array<Pair<Int, Int>>, mode: PadMode, constantValue: NDArray? = null): NDArray
    fun nonZero(): NumberNDArray

    fun tile(repeats: IntArray): NDArray
    fun transpose(permutations: IntArray): NDArray
    fun transpose2D(): NDArray

    fun view(vararg axes: Int): NDArray
}

interface MutableNDArray : NDArray {
    operator fun set(index: IntArray, value: Any)
    fun setLinear(index: Int, value: Any)

    fun viewMutable(vararg axes: Int): MutableNDArray

    fun copyFrom(offset: Int, other: NDArray, startInOther: Int = 0, endInOther: Int = min(other.linearSize, linearSize))
    fun fill(value: Any, from: Int = 0, to: Int = linearSize)

    fun fillByArrayValue(array: NDArray, index: Int, from: Int = 0, to: Int = linearSize)

    fun clean()
}

interface NumberNDArray : NDArray {
    override fun toMutable(): MutableNumberNDArray

    override fun slice(starts: IntArray, ends: IntArray, steps: IntArray): MutableNumberNDArray

    fun min(): Any
    fun min(axis: Int, keepDims: Boolean): NumberNDArray
    fun max(): Any
    fun max(axis: Int, keepDims: Boolean): NumberNDArray
    fun sum(): Any
    fun cumulativeSum(axis: Int, exclusive: Boolean, reverse: Boolean): MutableNumberNDArray
    fun erf(): NumberNDArray
    fun softmax(axis: Int = 0, coroutineContext: CoroutineContext? = null): NumberNDArray
    fun logSoftmax(axis: Int = 0, coroutineContext: CoroutineContext? = null): NumberNDArray

    operator fun plus(other: NumberNDArray): MutableNumberNDArray
    operator fun minus(other: NumberNDArray): MutableNumberNDArray
    operator fun times(other: NumberNDArray): MutableNumberNDArray
    operator fun div(other: NumberNDArray): MutableNumberNDArray

    fun dot(other: NumberNDArray, coroutineContext: CoroutineContext = EmptyCoroutineContext): MutableNumberNDArray
    fun matmul(other: NumberNDArray, coroutineContext: CoroutineContext = EmptyCoroutineContext): MutableNumberNDArray

    fun argmax(axis: Int = 0, keepDims: Boolean = true, selectLastIndex: Boolean = false): NumberNDArray
    fun reduceSum(axes: IntArray, keepDims: Boolean = true): NDArray
    fun topK(axis: Int, k: Int, largest: Boolean, sorted: Boolean): Pair<NumberNDArray, NumberNDArray>

    override fun reshape(strides: Strides): NumberNDArray
    override fun reshape(shape: IntArray): NumberNDArray = reshape(Strides(shape))

    override fun transpose(permutations: IntArray): NumberNDArray
    override fun pad(pads: Array<Pair<Int, Int>>, mode: PadMode, constantValue: NDArray?): NumberNDArray

    override fun view(vararg axes: Int): NumberNDArray
}

interface MutableNumberNDArray : MutableNDArray, NumberNDArray {
    operator fun plusAssign(other: NumberNDArray)
    operator fun minusAssign(other: NumberNDArray)
    operator fun timesAssign(other: NumberNDArray)
    operator fun divAssign(other: NumberNDArray)

    override fun viewMutable(vararg axes: Int): MutableNumberNDArray
}

operator fun NDArray.get(vararg index: Int) = this[index]
operator fun MutableNDArray.set(vararg index: Int, value: Any) { this[index] = value }
