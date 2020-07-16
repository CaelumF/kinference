package org.jetbrains.research.kotlin.mpp.inference.data.tensors

import scientifik.kmath.structures.Strides
import java.util.concurrent.ConcurrentHashMap

class TensorStrides private constructor(override val shape: IntArray) : Strides {
    override val strides = ArrayList<Int>(shape.size)

    init {
        shape.foldRight(1) { i, acc ->
            strides.add(acc)
            acc * i
        }
        strides.reverse()
    }

    override fun offset(index: IntArray): Int {
        return index.mapIndexed { i, value ->
            require(value in 0 until shape[i]) { "Index $value out of shape bound: (0, ${shape[i]})" }

            value * strides[i]
        }.sum()
    }

    override fun index(offset: Int): IntArray {
        require(offset in 0 until linearSize) { "Offset $offset out of buffer bounds: (0, ${linearSize - 1})" }

        val res = IntArray(shape.size)
        var current = offset

        for ((index, stride) in strides.withIndex()) {
            res[index] = current / stride
            current %= stride
        }

        return res
    }

    override val linearSize = if (shape.isEmpty()) 1 else strides[0] * shape[0]


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TensorStrides) return false

        return shape.contentEquals(other.shape)
    }

    override fun hashCode() = shape.contentHashCode()

    companion object {
        private val spaceStridesCache = ConcurrentHashMap<IntArray, TensorStrides>()

        operator fun invoke(shape: IntArray): TensorStrides = spaceStridesCache.getOrPut(shape) { TensorStrides(shape) }
    }
}
