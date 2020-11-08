package io.kinference.ndarray.extensions

import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.*
import io.kinference.ndarray.arrays.tiled.*
import io.kinference.primitives.types.DataType


inline fun <reified T> createArray(type: DataType, shape: IntArray, divider: Int = 1, noinline init: (Int) -> T): Any {
    return when (type) {
        DataType.DOUBLE -> DoubleTiledArray(shape, divider) { init(it) as Double }
        DataType.FLOAT -> FloatTiledArray(shape, divider) { init(it) as Float }
        DataType.LONG -> LongTiledArray(shape, divider) { init(it) as Long }
        DataType.INT -> IntTiledArray(shape, divider) { init(it) as Int }
        DataType.SHORT -> ShortTiledArray(shape, divider) { init(it) as Short }
        DataType.BOOLEAN -> BooleanTiledArray(shape, divider) { init(it) as Boolean }
        DataType.BYTE -> ByteTiledArray(shape, divider) { init(it) as Byte }
        DataType.UBYTE -> UByteTiledArray(shape, divider) { init(it) as UByte }
        else -> Array(Strides(shape).linearSize, init)
    }
}

fun createMutableNDArray(type: DataType, value: Any, strides: Strides): MutableNDArray {
    return when (type) {
        DataType.DOUBLE -> MutableDoubleNDArray(value as DoubleTiledArray, strides)
        DataType.FLOAT -> MutableFloatNDArray(value as FloatTiledArray, strides)
        DataType.LONG -> MutableLongNDArray(value as LongTiledArray, strides)
        DataType.INT -> MutableIntNDArray(value as IntTiledArray, strides)
        DataType.SHORT -> MutableShortNDArray(value as ShortTiledArray, strides)
        DataType.BOOLEAN -> MutableBooleanNDArray(value as BooleanTiledArray, strides)
        DataType.BYTE -> MutableByteNDArray(value as ByteTiledArray, strides)
        DataType.UBYTE -> MutableUByteNDArray(value as UByteTiledArray, strides)
        //else -> Array(size, init)
        else -> error("Unsupported data type $type")
    }
}

fun createMutableNDArray(type: DataType, value: Any, shape: IntArray): MutableNDArray {
    return createMutableNDArray(type, value, Strides(shape))
}

fun createNDArray(type: DataType, value: Any, strides: Strides): NDArray {
    return when (type) {
        DataType.DOUBLE -> DoubleNDArray(value as DoubleTiledArray, strides)
        DataType.FLOAT -> FloatNDArray(value as FloatTiledArray, strides)
        DataType.LONG -> LongNDArray(value as LongTiledArray, strides)
        DataType.INT -> IntNDArray(value as IntTiledArray, strides)
        DataType.SHORT -> ShortNDArray(value as ShortTiledArray, strides)
        DataType.BOOLEAN -> BooleanNDArray(value as BooleanTiledArray, strides)
        DataType.BYTE -> ByteNDArray(value as ByteTiledArray, strides)
        DataType.UBYTE -> UByteNDArray(value as UByteTiledArray, strides)
        //else -> Array(size, init)
        else -> error("Unsupported data type $type")
    }
}


fun createNDArray(type: DataType, value: Any, shape: IntArray): NDArray {
    return createNDArray(type, value, Strides(shape))
}

fun createZerosArray(type: DataType, size: Int): Any {
    return when (type) {
        DataType.DOUBLE -> DoubleArray(size)
        DataType.FLOAT -> FloatArray(size)
        DataType.LONG -> LongArray(size)
        DataType.INT -> IntArray(size)
        DataType.SHORT -> ShortArray(size)
        DataType.BOOLEAN -> BooleanArray(size)
        else -> error("Unsupported data type $type")
    }
}


fun createScalarNDArray(type: DataType, value: Any): NDArray {
    return when (type) {
        DataType.DOUBLE -> DoubleNDArray(DoubleTiledArray(1, 1) { value as Double })
        DataType.FLOAT -> FloatNDArray(FloatTiledArray(1, 1) { value as Float })
        DataType.LONG -> LongNDArray(LongTiledArray(1, 1) { value as Long })
        DataType.INT -> IntNDArray(IntTiledArray(1, 1) { value as Int })
        DataType.SHORT -> ShortNDArray(ShortTiledArray(1, 1) { value as Short })
        DataType.BOOLEAN -> BooleanNDArray(BooleanTiledArray(1, 1) { value as Boolean })
        DataType.BYTE -> ByteNDArray(ByteTiledArray(1, 1) { value as Byte })
        DataType.UBYTE -> UByteNDArray(UByteTiledArray(1, 1) { value as UByte })
        //else -> Array(size, init)
        else -> error("Unsupported data type $type")
    }
}

fun allocateNDArray(type: DataType, strides: Strides): MutableNDArray {
    return when (type) {
        DataType.DOUBLE -> MutableDoubleNDArray(DoubleTiledArray(strides), strides)
        DataType.FLOAT -> MutableFloatNDArray(FloatTiledArray(strides), strides)
        DataType.LONG -> MutableLongNDArray(LongTiledArray(strides), strides)
        DataType.INT -> MutableIntNDArray(IntTiledArray(strides), strides)
        DataType.SHORT -> MutableShortNDArray(ShortTiledArray(strides), strides)
        DataType.BOOLEAN -> MutableBooleanNDArray(BooleanTiledArray(strides), strides)
        DataType.BYTE -> MutableByteNDArray(ByteTiledArray(strides), strides)
        DataType.UBYTE -> MutableUByteNDArray(UByteTiledArray(strides), strides)
        else -> error("Unsupported data type $type")
    }
}

fun allocateNDArray(type: DataType, shape: IntArray) = allocateNDArray(type, Strides(shape))

val SUPPORTED_TYPES = setOf(DataType.DOUBLE, DataType.FLOAT, DataType.LONG, DataType.INT, DataType.SHORT)
fun inferType(type1: DataType, type2: DataType): DataType {
    return when {
        type1 !in SUPPORTED_TYPES || type2 !in SUPPORTED_TYPES -> error("Unsupported type")
        type1 == DataType.DOUBLE || type2 == DataType.DOUBLE -> DataType.DOUBLE
        type1 == DataType.FLOAT || type2 == DataType.FLOAT -> DataType.FLOAT
        type1 == DataType.LONG || type2 == DataType.LONG -> DataType.LONG
        type1 == DataType.INT || type2 == DataType.INT -> DataType.INT
        type1 == DataType.SHORT || type2 == DataType.SHORT -> DataType.SHORT
        else -> error("Unsupported type")
    }
}
