package org.jetbrains.research.kotlin.inference.extensions.primitives

fun plus(left: FloatArray, right: FloatArray, copy: Boolean): FloatArray {
    require(left.size == right.size)
    val array = if (copy) FloatArray(left.size) else left

    for (i in left.indices) array[i] = left[i] + right[i]

    return array
}


fun plus(left: IntArray, right: IntArray, copy: Boolean): IntArray {
    require(left.size == right.size)
    val array = if (copy) IntArray(left.size) else left

    for (i in left.indices) array[i] = left[i] + right[i]

    return array
}

fun plus(left: LongArray, right: LongArray, copy: Boolean): LongArray {
    require(left.size == right.size)
    val array = if (copy) LongArray(left.size) else left

    for (i in left.indices) array[i] = left[i] + right[i]

    return array
}

fun plus(left: DoubleArray, right: DoubleArray, copy: Boolean): DoubleArray {
    require(left.size == right.size)
    val array = if (copy) DoubleArray(left.size) else left

    for (i in left.indices) array[i] = left[i] + right[i]

    return array
}

fun plus(left: ShortArray, right: ShortArray, copy: Boolean): ShortArray {
    require(left.size == right.size)
    val array = if (copy) ShortArray(left.size) else left

    for (i in left.indices) array[i] = (left[i] + right[i]).toShort()

    return array
}
