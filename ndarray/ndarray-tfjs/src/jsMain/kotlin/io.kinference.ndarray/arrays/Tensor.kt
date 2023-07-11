@file:JsModule("@tensorflow/tfjs-core")
@file:JsNonModule
package io.kinference.ndarray.arrays

import org.khronos.webgl.*
import kotlin.js.Promise

@JsName("TensorBuffer")
internal external class MutableBuffer {
    val size: Int
    val shape: Array<Int>
    val strides: Array<Int>

    fun get(vararg locs: Int): dynamic
    fun set(value: dynamic, vararg locs: Int)

    fun toTensor(): ArrayTFJS
}

@JsName("Tensor")
internal external class ArrayTFJS {
    val shape: Array<Int>
    val size: Int
    val dtype: String /* "float32" | "int32" | "bool" | "complex64" | "string" */
    val rank: Int

    internal fun data(): Promise<Any>
    internal fun dataSync(): dynamic
    fun buffer(): Promise<MutableBuffer>
    fun bufferSync(): MutableBuffer

    fun print(verbose: Boolean = definedExternally)
    fun dispose()
}


internal external fun tensor(values: Float32Array, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Int32Array, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Uint8Array, shape: Array<Int>, dtype: String): ArrayTFJS

internal external fun tensor(values: Array<Float>, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Array<Double>, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Array<Byte>, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Array<Short>, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Array<Int>, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Array<Boolean>, shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun tensor(values: Array<String>, shape: Array<Int>, dtype: String): ArrayTFJS

internal external fun range(start: Number, stop: Number, step: Number?, dtype: String?): ArrayTFJS

internal external fun fill(shape: Array<Int>, value: Number, dtype: String): ArrayTFJS
internal external fun fill(shape: Array<Int>, value: String, dtype: String): ArrayTFJS

internal external fun scalar(value: Number, dtype: String): ArrayTFJS
internal external fun scalar(value: Boolean, dtype: String): ArrayTFJS
internal external fun scalar(value: String, dtype: String): ArrayTFJS

internal external fun zeros(shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun zerosLike(x: ArrayTFJS): ArrayTFJS

internal external fun ones(shape: Array<Int>, dtype: String): ArrayTFJS
internal external fun onesLike(x: ArrayTFJS): ArrayTFJS

internal external fun oneHot(indices: ArrayTFJS, depth: Int, onValue: Number?, offValue: Number?, dtype: String?): ArrayTFJS
