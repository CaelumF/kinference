package org.jetbrains.research.kotlin.inference.operators.tensor

import org.jetbrains.research.kotlin.inference.attributes.Attribute
import org.jetbrains.research.kotlin.inference.data.tensors.Tensor
import org.jetbrains.research.kotlin.inference.extensions.tensor.concatenate
import org.jetbrains.research.kotlin.inference.graph.Context
import org.jetbrains.research.kotlin.inference.onnx.AttributeProto
import org.jetbrains.research.kotlin.inference.operators.*

class Concat(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>)
    : Operator<Tensor, Tensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = ALL_DATA_TYPES

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), true)
        )

        private val INPUTS_INFO = listOf(VariadicIOInfo(0, TYPE_CONSTRAINTS, "inputs", minimumArity = 1, differentiable = true, heterogeneous = false))

        private val OUTPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "concat_result", optional = false, differentiable = true))

        private val INFO = OperatorInfo("Concat", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)
    }

    override fun apply(context: Context, inputs: List<Tensor?>): List<Tensor?> {
        val axis = getAttributeValue("axis") as Number
        inputs as List<Tensor>
        return listOf(inputs.concatenate(axis.toInt()))
    }
}
