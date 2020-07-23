package org.jetbrains.research.kotlin.inference.extensions.math

import org.jetbrains.research.kotlin.inference.extensions.buffer.FloatBuffer
import org.jetbrains.research.kotlin.inference.extensions.buffer.asBuffer
import scientifik.kmath.structures.*


fun times(left: FloatBuffer, right: FloatBuffer): FloatBuffer {
    require(left.size == right.size)

    val array = FloatArray(left.size)

    val a = left.array
    val b = right.array

    for (i in (0 until left.size)) array[i] = (a[i] * b[i])

    return array.asBuffer()
}

fun times(left: DoubleBuffer, right: DoubleBuffer): DoubleBuffer {
    require(left.size == right.size)

    val array = DoubleArray(left.size)

    val a = left.array
    val b = right.array

    for (i in (0 until left.size)) array[i] = (a[i] * b[i])

    return array.asBuffer()
}

fun times(left: IntBuffer, right: IntBuffer): IntBuffer {
    require(left.size == right.size)

    val array = IntArray(left.size)

    val a = left.array
    val b = right.array

    for (i in (0 until left.size)) array[i] = (a[i] * b[i])

    return array.asBuffer()
}

fun times(left: LongBuffer, right: LongBuffer): LongBuffer {
    require(left.size == right.size)

    val array = LongArray(left.size)

    val a = left.array
    val b = right.array

    for (i in (0 until left.size)) array[i] = (a[i] * b[i])

    return array.asBuffer()
}

fun times(left: ShortBuffer, right: ShortBuffer): ShortBuffer {
    require(left.size == right.size)

    val array = ShortArray(left.size)

    val a = left.array
    val b = right.array

    for (i in (0 until left.size)) array[i] = (a[i] * b[i]).toShort()

    return array.asBuffer()
}

fun <T : Any> NDBuffer<T>.times(other: NDBuffer<T>): NDBuffer<T> {
    require(this::class == other::class)
    require(this.shape.contentEquals(other.shape))
    return when (buffer) {
        is IntBuffer -> BufferNDStructure(strides, times(this.buffer as IntBuffer, other.buffer as IntBuffer))
        is FloatBuffer -> BufferNDStructure(strides, times(this.buffer as FloatBuffer, other.buffer as FloatBuffer))
        is ShortBuffer -> BufferNDStructure(strides, times((buffer as ShortBuffer), other.buffer as ShortBuffer))
        is DoubleBuffer -> BufferNDStructure(strides, times(this.buffer as DoubleBuffer, other.buffer as DoubleBuffer))
        is LongBuffer -> BufferNDStructure(strides, times(this.buffer as LongBuffer, other.buffer as LongBuffer))
        else -> throw UnsupportedOperationException()
    } as NDBuffer<T>
}

fun <T : Any> NDBuffer<T>.timesScalar(x: T): NDBuffer<T> {
    return when (buffer) {
        is IntBuffer -> BufferNDStructure(strides, times(this.buffer as IntBuffer, IntArray(this.buffer.size) { x as Int }.asBuffer()))
        is FloatBuffer -> BufferNDStructure(strides, times(this.buffer as FloatBuffer, FloatArray(this.buffer.size) { x as Float }.asBuffer()))
        is ShortBuffer -> BufferNDStructure(strides, times((buffer as ShortBuffer), ShortArray(this.buffer.size) { x as Short }.asBuffer()))
        is DoubleBuffer -> BufferNDStructure(strides, times(this.buffer as DoubleBuffer, DoubleArray(this.buffer.size) { x as Double }.asBuffer()))
        is LongBuffer -> BufferNDStructure(strides, times(this.buffer as LongBuffer, LongArray(this.buffer.size) { x as Long }.asBuffer()))
        else -> throw UnsupportedOperationException()
    } as NDBuffer<T>
}
