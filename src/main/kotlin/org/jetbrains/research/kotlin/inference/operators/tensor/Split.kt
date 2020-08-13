package org.jetbrains.research.kotlin.inference.operators.tensor

import org.jetbrains.research.kotlin.inference.attributes.Attribute
import org.jetbrains.research.kotlin.inference.data.tensors.Tensor
import org.jetbrains.research.kotlin.inference.extensions.primitives.toIntArray
import org.jetbrains.research.kotlin.inference.extensions.tensor.splitWithAxis
import org.jetbrains.research.kotlin.inference.graph.Context
import org.jetbrains.research.kotlin.inference.onnx.AttributeProto
import org.jetbrains.research.kotlin.inference.operators.*

class Split(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<Tensor, Tensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = ALL_DATA_TYPES

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), false, default = 0L),
            AttributeInfo("split", setOf(AttributeProto.AttributeType.INTS), false)
        )

        private val INPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "input", optional = false, differentiable = true))

        private val OUTPUTS_INFO = listOf(VariadicIOInfo(0, TYPE_CONSTRAINTS, "outputs", minimumArity = 1, differentiable = true))

        private val INFO = OperatorInfo("Split", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)
    }

    override fun apply(context: Context, inputs: List<Tensor?>): List<Tensor?> {
        val axis = getAttributeValue("axis") as Number

        val input = inputs.first()!!
        return when (val parts = getAttributeValueOrNull("split")) {
            null -> input.splitWithAxis(outputs.size, axis.toInt())
            is Number -> input.splitWithAxis(parts.toInt(), axis.toInt())
            is List<*> -> input.splitWithAxis((parts as List<Number>).toIntArray(), axis.toInt())
            else -> error("Unsupported value type")
        }
    }
}
