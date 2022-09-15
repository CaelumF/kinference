package io.kinference.ndarray.arrays

import io.kinference.ndarray.extensions.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface NDArrayCore : NDArray {
    override fun close() = Unit

    fun map(function: PrimitiveToPrimitiveFunction, destination: MutableNDArray): MutableNDArrayCore
    fun map(function: PrimitiveToPrimitiveFunction): MutableNDArrayCore

    fun view(vararg axes: Int): NDArrayCore
    fun row(i: Int): MutableNDArray

    override fun reshape(strides: Strides): NDArrayCore
    override fun reshape(shape: IntArray): NDArrayCore = reshape(Strides(shape))

    override fun toMutable(newStrides: Strides): MutableNDArrayCore

    override fun transpose(permutations: IntArray): NDArrayCore

    override fun squeeze(vararg axes: Int): NDArrayCore = squeeze(this, *axes)
    override fun unsqueeze(vararg axes: Int): NDArray = unsqueeze(this, *axes)

    override fun stack(others: List<NDArray>, axis: Int): MutableNDArrayCore = (listOf(this) + others as List<NDArrayCore>).stack(axis)
    override fun concat(others: List<NDArray>, axis: Int): MutableNDArrayCore

    override fun gather(indices: NDArray, axis: Int, batchDims: Int): NDArrayCore = gather(this, indices as NDArrayCore, axis)
    fun gather(indices: NDArray, axis: Int, dst: MutableNDArrayCore): NDArrayCore = gather(this, indices as NDArrayCore, axis, dst)
}

interface MutableNDArrayCore : NDArrayCore, MutableNDArray {
    fun mapMutable(function: PrimitiveToPrimitiveFunction): MutableNDArrayCore
    fun viewMutable(vararg axes: Int): MutableNDArrayCore
}

interface NumberNDArrayCore : NDArrayCore, NumberNDArray {
    fun gemm(
        m: Int,
        n: Int,
        k: Int,
        alpha: Double,
        lda: Int,
        b: NDArray,
        ldb: Int,
        beta: Double,
        c: MutableNDArray,
        ldc: Int,
        aOffset: Int,
        bOffset: Int,
        cOffset: Int,
        transposeA: Boolean,
        transposeB: Boolean
    ): MutableNDArrayCore

    override fun toMutable(newStrides: Strides): MutableNumberNDArrayCore

    override fun reshape(strides: Strides): NumberNDArrayCore
    override fun reshape(shape: IntArray): NumberNDArrayCore = reshape(Strides(shape))

    override fun view(vararg axes: Int): NumberNDArrayCore
    override fun transpose(permutations: IntArray): NumberNDArrayCore

    fun plus(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArrayCore
    fun minus(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArrayCore
    fun times(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArrayCore
    fun div(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArrayCore

    override operator fun plus(other: NumberNDArray): MutableNumberNDArrayCore
    override operator fun minus(other: NumberNDArray): MutableNumberNDArrayCore
    override operator fun times(other: NumberNDArray): MutableNumberNDArrayCore
    override operator fun div(other: NumberNDArray): MutableNumberNDArrayCore

    fun dot(other: NumberNDArray, destination: MutableNumberNDArray, coroutineContext: CoroutineContext = EmptyCoroutineContext): MutableNumberNDArrayCore
    fun matmul(other: NumberNDArray, destination: MutableNumberNDArrayCore, coroutineContext: CoroutineContext): MutableNumberNDArrayCore
}

interface MutableNumberNDArrayCore : NumberNDArrayCore, MutableNDArrayCore, MutableNumberNDArray {
    override fun viewMutable(vararg axes: Int): MutableNumberNDArrayCore
}
