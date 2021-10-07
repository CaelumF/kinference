package io.kinference.core.operators.math

import io.kinference.core.attributes.Attribute
import io.kinference.core.data.tensors.KITensor
import io.kinference.core.data.tensors.asTensor
import io.kinference.core.graph.Context
import io.kinference.core.graph.ProfilingContext
import io.kinference.ndarray.arrays.NumberNDArray
import io.kinference.core.operators.*
import kotlin.time.ExperimentalTime
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto.DataType

@ExperimentalTime
class CumSum(attributes: Map<String, Attribute<Any>> = emptyMap(), inputs: List<String>, outputs: List<String>) :
    Operator<KITensor, KITensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = setOf(DataType.UINT32, DataType.UINT64, DataType.INT32, DataType.INT64, DataType.FLOAT, DataType.DOUBLE)

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("exclusive", setOf(AttributeProto.AttributeType.INT), false, 0),
            AttributeInfo("reverse", setOf(AttributeProto.AttributeType.INT), false, 0)
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "x", optional = false),
            IOInfo(1, setOf(DataType.INT32, DataType.INT64), "axis", optional = false)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "y", optional = false)
        )

        private val INFO = OperatorInfo("CumSum", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)
    }

    private val exclusive by attribute { ex: Number -> ex.toInt() != 0 }
    private val reverse by attribute { r: Number -> r.toInt() != 0 }

    override fun apply(context: Context, inputs: List<KITensor?>, profilingContext: ProfilingContext?): List<KITensor?> {
        val input = inputs[0]!!.data as NumberNDArray
        val axis = (inputs[1]!!.data.singleValue() as Number).toInt()
        return listOf(input.cumulativeSum(axis, exclusive, reverse).asTensor("y"))
    }
}
