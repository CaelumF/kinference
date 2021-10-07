package io.kinference.core.operators.tensor

import io.kinference.core.attributes.Attribute
import io.kinference.core.data.tensors.KITensor
import io.kinference.core.data.tensors.asTensor
import io.kinference.core.graph.Context
import io.kinference.core.graph.ProfilingContext
import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.FloatNDArray
import io.kinference.ndarray.arrays.LongNDArray
import io.kinference.core.operators.*
import kotlin.time.ExperimentalTime
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto

@ExperimentalTime
class ConstantOfShape(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>)
    : Operator<KITensor, KITensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = PRIMITIVE_DATA_TYPES

        private val DEFAULT_TENSOR = FloatNDArray.scalar(0f).asTensor("value")
        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("value", setOf(AttributeProto.AttributeType.TENSOR), default = DEFAULT_TENSOR, required = false)
        )

        private val INPUTS_INFO = listOf(IOInfo(0, setOf(TensorProto.DataType.INT64), "input", optional = false))

        private val OUTPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "output", optional = false))

        private val INFO = OperatorInfo("ConstantOfShape", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)
    }

    private val value: KITensor by attribute()


    override fun apply(context: Context, inputs: List<KITensor?>, profilingContext: ProfilingContext?): List<KITensor?> {
        val array = inputs[0]!!.data as LongNDArray
        val pointer = array.array.pointer()
        val shape = IntArray(array.linearSize) { pointer.getAndIncrement().toInt() }
        val result = value.data.allocateNDArray(Strides(shape)).apply { fill(value.data.singleValue()) }
        return listOf(result.asTensor("output"))
    }
}
