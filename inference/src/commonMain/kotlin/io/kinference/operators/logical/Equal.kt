package io.kinference.operators.logical

import io.kinference.attributes.Attribute
import io.kinference.data.tensors.Tensor
import io.kinference.data.tensors.asTensor
import io.kinference.graph.Context
import io.kinference.graph.ProfilingContext
import io.kinference.ndarray.arrays.*
import io.kinference.ndarray.arrays.pointers.acceptDouble
import io.kinference.ndarray.extensions.applyWithBroadcast
import io.kinference.onnx.TensorProto
import io.kinference.operators.*
import io.kinference.primitives.types.DataType
import kotlin.time.ExperimentalTime

@ExperimentalTime
class Equal(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<Tensor, Tensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = PRIMITIVE_DATA_TYPES + TensorProto.DataType.BFLOAT16

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "A", optional = false),
            IOInfo(1, TYPE_CONSTRAINTS, "B", optional = false)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, setOf(TensorProto.DataType.BOOL), "C", optional = false)
        )

        private val INFO = OperatorInfo("Equal", emptyMap(), INPUTS_INFO, OUTPUTS_INFO)


        infix fun NDArray.equal(other: NDArray): NDArray {
            return applyWithBroadcast(other, DataType.BOOLEAN) { first, second, dest ->
                require(first.type == second.type) { "Arrays must have same types" }
                dest as MutableBooleanNDArray
                val pointer = dest.array.pointer()

                when (first.type) {
                    DataType.BYTE -> {
                        val f = (first as ByteNDArray).array.pointer()
                        val s = (second as ByteNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.SHORT -> {
                        val f = (first as ShortNDArray).array.pointer()
                        val s = (second as ShortNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.INT -> {
                        val f = (first as IntNDArray).array.pointer()
                        val s = (second as IntNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.LONG -> {
                        val f = (first as LongNDArray).array.pointer()
                        val s = (second as LongNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.UBYTE -> {
                        val f = (first as UByteNDArray).array.pointer()
                        val s = (second as UByteNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.USHORT -> {
                        val f = (first as UShortNDArray).array.pointer()
                        val s = (second as UShortNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.UINT -> {
                        val f = (first as UIntNDArray).array.pointer()
                        val s = (second as UIntNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.ULONG -> {
                        val f = (first as ULongNDArray).array.pointer()
                        val s = (second as ULongNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.FLOAT -> {
                        val f = (first as FloatNDArray).array.pointer()
                        val s = (second as FloatNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.DOUBLE -> {
                        val f = (first as DoubleNDArray).array.pointer()
                        val s = (second as DoubleNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    DataType.BOOLEAN -> {
                        val f = (first as BooleanNDArray).array.pointer()
                        val s = (second as BooleanNDArray).array.pointer()
                        pointer.acceptDouble(f, s, dest.linearSize) { _, a, b -> a == b }
                    }
                    else -> throw IllegalStateException("Unsupported type")
                }
            }
        }
    }


    override fun apply(context: Context, inputs: List<Tensor?>, profilingContext: ProfilingContext?): List<Tensor?> {
        val result = inputs[0]!!.data equal inputs[1]!!.data
        return listOf(result.asTensor("output"))
    }
}
