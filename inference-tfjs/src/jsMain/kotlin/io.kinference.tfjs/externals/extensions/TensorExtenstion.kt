package io.kinference.tfjs.externals.extensions

import io.kinference.tfjs.externals.core.*
import org.khronos.webgl.*

fun tensor(values: FloatArray, shape: Array<Int>, dtype: String): NDArrayTFJS = tensor(values.unsafeCast<Float32Array>(), shape, dtype)

fun tensor(values: IntArray, shape: Array<Int>, dtype: String): NDArrayTFJS = tensor(values.unsafeCast<Int32Array>(), shape, dtype)

fun tensor(values: UByteArray, shape: Array<Int>, dtype: String): NDArrayTFJS = tensor(values.unsafeCast<Uint8Array>(), shape, dtype)

fun NDArrayTFJS.dataInt() = (dataSync() as Int32Array).unsafeCast<IntArray>()

fun NDArrayTFJS.dataFloat() = (dataSync() as Float32Array).unsafeCast<FloatArray>()

operator fun NDArrayTFJS.plus(other: NDArrayTFJS) = io.kinference.tfjs.externals.core.add(this, other)

operator fun NDArrayTFJS.minus(other: NDArrayTFJS) = sub(this, other)

operator fun NDArrayTFJS.div(other: NDArrayTFJS) = div(this, other)

operator fun NDArrayTFJS.times(other: NDArrayTFJS) = mul(this, other)

fun NDArrayTFJS.broadcastTo(shape: Array<Int>) = broadcastTo(this, shape)

fun NDArrayTFJS.cast(dtype: String) = cast(this, dtype)

fun NDArrayTFJS.reshape(shape: Array<Int>) = reshape(this, shape)

fun NDArrayTFJS.gather(indices: NDArrayTFJS, axis: Int = 0, batchDims: Int = 0) = gather(this, indices, axis, batchDims)

fun NDArrayTFJS.moments(axis: Int, keepDims: Boolean = false) = moments(this, arrayOf(axis), keepDims)

fun NDArrayTFJS.moments(axes: Array<Int>, keepDims: Boolean = false) = moments(this, axes, keepDims)

fun NDArrayTFJS.sum(axis: Int, keepDims: Boolean = false) = sum(this, arrayOf(axis), keepDims)

fun NDArrayTFJS.sum(axes: Array<Int>, keepDims: Boolean = false) = sum(this, axes, keepDims)

fun NDArrayTFJS.sum(keepDims: Boolean = false) = sum(this, null, keepDims)

fun NDArrayTFJS.sqrt() = io.kinference.tfjs.externals.core.sqrt(this)

fun Array<NDArrayTFJS>.sum() = addN(this)

fun NDArrayTFJS.add(vararg tensors: NDArrayTFJS) = addN(arrayOf(this, *tensors))

fun NDArrayTFJS.transpose(permutation: Array<Int>) = transpose(this, permutation)

fun NDArrayTFJS.unstack(axis: Int = 0) = unstack(this, axis)

fun Array<NDArrayTFJS>.stack(axis: Int = 0) = stack(this, axis)

fun NDArrayTFJS.stack(vararg tensors: NDArrayTFJS, axis: Int = 0) = stack(arrayOf(this, *tensors), axis)

fun NDArrayTFJS.dot(other: NDArrayTFJS) = dot(this, other)

fun Array<NDArrayTFJS>.concat(axis: Int = 0) = concat(this, axis)

fun NDArrayTFJS.concat(vararg tensors: NDArrayTFJS, axis: Int = 0) = concat(arrayOf(this, *tensors), axis)

fun NDArrayTFJS.matMul(other: NDArrayTFJS, transposeLeft: Boolean = false, transposeRight: Boolean = false) = matMul(this, other, transposeLeft, transposeRight)

fun NDArrayTFJS.softmax(axis: Int = -1) = softmax(this, axis)

fun NDArrayTFJS.erf() = erf(this)

fun NDArrayTFJS.flatten() = reshape(this, arrayOf(this.size))

fun NDArrayTFJS.isScalar() = shape.isEmpty()

fun NDArrayTFJS.computeBlockSize(fromDim: Int = 0, toDim: Int = this.shape.size): Int {
    return this.shape.sliceArray(fromDim until toDim).fold(1, Int::times)
}

fun NDArrayTFJS.indexAxis(axis: Int) = if (axis < 0) rank + axis else axis

fun NDArrayTFJS.min(axis: Int = 0, keepDims: Boolean = false) = min(this, arrayOf(axis), keepDims)

fun NDArrayTFJS.min(axes: Array<Int>, keepDims: Boolean = false) = min(this, axes, keepDims)

fun NDArrayTFJS.min(keepDims: Boolean = false) = min(this, null, keepDims)

fun NDArrayTFJS.max(axis: Int, keepDims: Boolean = false) = max(this, arrayOf(axis), keepDims)

fun NDArrayTFJS.max(axes: Array<Int>, keepDims: Boolean = false) = max(this, axes, keepDims)

fun NDArrayTFJS.max(keepDims: Boolean = false) = max(this, null, keepDims)

fun NDArrayTFJS.round() = round(this)

fun NDArrayTFJS.clip(minValue: Number, maxValue: Number) = clipByValue(this, minValue, maxValue)

operator fun NDArrayTFJS.unaryMinus() = neg(this)

fun min(a: NDArrayTFJS, b: NDArrayTFJS) = minimum(a, b)

fun max(a: NDArrayTFJS, b: NDArrayTFJS) = maximum(a, b)

fun sqrt(value: NDArrayTFJS) = value.sqrt()

fun NDArrayTFJS.tanh() = io.kinference.tfjs.externals.core.tanh(this)

fun tanh(x: NDArrayTFJS) = x.tanh()
