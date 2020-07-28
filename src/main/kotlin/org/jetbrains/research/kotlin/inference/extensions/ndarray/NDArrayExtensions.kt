package org.jetbrains.research.kotlin.inference.extensions.ndarray

import org.jetbrains.research.kotlin.inference.data.ndarray.NDArray
import org.jetbrains.research.kotlin.inference.data.tensors.Strides
import org.jetbrains.research.kotlin.inference.data.tensors.applyWithBroadcast
import org.jetbrains.research.kotlin.inference.extensions.primitives.scalarOp
import org.jetbrains.research.kotlin.inference.extensions.primitives.toIntArray

fun NDArray.splitWithAxis(parts: Int, axis: Int = 0, keepDims: Boolean = true): List<NDArray> {
    require(axis in shape.indices) { "Index $axis out of shape bound: (0, ${rank - 1}" }

    val elementsByIndex = shape[axis]
    val mainSplit = elementsByIndex / parts
    val split = MutableList(parts - 1) { mainSplit }

    val tail = elementsByIndex - mainSplit * (parts - 1)
    split.add(tail)

    return this.splitWithAxis(split.toIntArray(), axis, keepDims).toList()
}

fun NDArray.splitWithAxis(splitTensor: NDArray, axis: Int = 0, keepDims: Boolean = true): List<NDArray> {
    return if (splitTensor.linearSize == 1) {
        splitWithAxis((splitTensor[0] as Number).toInt(), axis, keepDims)
    } else {
        this.splitWithAxis((splitTensor.array as List<Long>).toIntArray(), axis, keepDims).toList()
    }
}

fun NDArray.wrapOneDim(): NDArray {
    val newStrides = Strides(intArrayOf(1, *this.shape))
    return this.clone(newStrides)
}

//if axis not 0
private fun NDArray.mergeOnAxis(other: NDArray, axis: Int): NDArray {
    val dim = this.shape
    val rows = this.rows.zip(other.rows).map { (fst, snd) -> fst.concatenate(snd, axis - 1) }.toMutableList()
    var result = rows[0]

    if (dim[0] > 1) {
        result = rows.apply { set(0, rows[0].wrapOneDim()) }.reduce { acc, tensor -> acc.concatenate(tensor.wrapOneDim()) }
    }
    if (dim[0] == 1 && axis > 0) result = result.wrapOneDim()

    return result
}

fun NDArray.concatenate(other: NDArray, axis: Int = 0): NDArray {
    val actualAxis = this.indexAxis(axis)
    if (actualAxis != 0) return this.mergeOnAxis(other, actualAxis)

    val fstDim: IntArray = this.shape
    var sndDim: IntArray = other.shape
    if (fstDim.size > 1 && sndDim.size == 1) sndDim = intArrayOf(1, sndDim[0])

    val newShape: IntArray = if (fstDim.size == 1) {
        intArrayOf(fstDim[0] + sndDim[0])
    } else {
        fstDim.copyOf(fstDim.size).apply { set(0, fstDim[0] + sndDim[0]) }
    }
    return allocateNDArray(type, Strides(newShape)).apply {
        placeAll(0, this@concatenate.array)
        placeAll(this@concatenate.linearSize, other.array)
    }
}

fun Collection<NDArray>.concatenate(axis: Int): NDArray {
    return this.reduce { acc, tensor -> acc.concatenate(tensor, axis) }
}

fun Array<NDArray>.stack(axis: Int): NDArray {
    return this.map {
        val newShape = this.first().shape.toMutableList()
        newShape.add(axis, 1)
        it.reshape(newShape.toIntArray())
    }.concatenate(axis)
}

fun NDArray.as2DList(): List<NDArray> {
    if (this.rank == 2) return listOf(this)
    if (this.rank == 1) return listOf(this.wrapOneDim())

    val matrixShape = intArrayOf(shape[indexAxis(-2)], shape[indexAxis(-1)])
    val matrixStrides = Strides(matrixShape)

    return List(strides.linearSize / matrixStrides.linearSize) { index ->
        createNDArray(type, matrixStrides) {
            this[it + index * matrixStrides.linearSize]
        }
    }
    //return this.rows().map { it.as2DList() }.flatten()
}

fun NDArray.reshape(tensorShape: NDArray): NDArray {
    val requestedShape = tensorShape.array as LongArray
    val newShape = IntArray(requestedShape.size) { i -> requestedShape[i].toInt() }
    require(newShape.count { it == -1 } <= 1) { "At most one dimension of the new shape can be -1" }

    for ((i, axisShape) in newShape.withIndex()) {
        if (axisShape == 0) newShape[i] = shape[i]
    }

    val negativeIdx = requestedShape.indexOf(-1)
    if (negativeIdx != -1) {
        val elementsCount = newShape.filter { it != -1 }.reduce(Int::times)
        newShape[negativeIdx] = strides.linearSize / elementsCount
    }

    return reshape(newShape)
}

fun NDArray.combineWith(other: NDArray, transform: (Any, Any) -> Any): NDArray {
    if (this.isScalar()) {
        return other.scalarOp(this[0], transform)
    } else if (other.isScalar()) {
        return this.scalarOp(other[0], transform)
    }

    if (!shape.contentEquals(other.shape)) {
        return applyWithBroadcast(other, transform)
    }

    val sum = transform(array, other.array)
    return NDArray(sum, type, strides)
}

fun NDArray.gather(indices: NDArray, axis: Int = 0): NDArray {
    val addedShape = shape.toMutableList().also { it.removeAt(axis) }
    val newShape = addedShape.toMutableList().also { it.addAll(axis, indices.shape.toList()) }
    val newStrides = Strides(newShape.toIntArray())

    val newArray = createArray(type, newStrides.linearSize) { i ->
        val current = newStrides.index(i)
        val indicesIndices = current.sliceArray(axis until indices.rank + axis)
        val gatherIndices = (current.take(axis) + current.takeLast(rank - 1 - axis)).toMutableList().also { it.add(axis, (indices[indicesIndices] as Long).toInt()) }
        val positiveGatherIndices = gatherIndices.zip(shape.toList()) { index, shape ->
            if (index < 0) shape + index else index
        }.toIntArray()
        val linear = strides.offset(positiveGatherIndices)
        this[linear]
    }

    return NDArray(newArray, type, newStrides.shape)
}
