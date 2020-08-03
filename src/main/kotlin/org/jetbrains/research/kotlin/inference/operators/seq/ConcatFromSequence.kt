package org.jetbrains.research.kotlin.inference.operators.seq

import org.jetbrains.research.kotlin.inference.attributes.Attribute
import org.jetbrains.research.kotlin.inference.data.ONNXDataType
import org.jetbrains.research.kotlin.inference.data.seq.TensorSeq
import org.jetbrains.research.kotlin.inference.data.tensors.Tensor
import org.jetbrains.research.kotlin.inference.extensions.tensor.concatenate
import org.jetbrains.research.kotlin.inference.extensions.tensor.stack
import org.jetbrains.research.kotlin.inference.onnx.AttributeProto
import org.jetbrains.research.kotlin.inference.operators.AttributeInfo
import org.jetbrains.research.kotlin.inference.operators.IOInfo
import org.jetbrains.research.kotlin.inference.operators.Operator
import org.jetbrains.research.kotlin.inference.operators.OperatorInfo

class ConcatFromSequence(attributes: Map<String, Attribute<Any>>, usedOutputsNum: Int)
    : Operator<TensorSeq, Tensor>(INFO, usedOutputsNum, attributes) {
    companion object {
        private val TYPE_CONSTRAINTS = ALL_DATA_TYPES

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), true),
            AttributeInfo("new_axis", setOf(AttributeProto.AttributeType.INT), false, default = 0L)
        )

        private val INPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "input_sequence", optional = false, onnxDataType = ONNXDataType.ONNX_SEQUENCE))

        private val OUTPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "concat_result", optional = false))

        private val INFO = OperatorInfo("ConcatFromSequence", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)
    }

    override fun apply(inputs: List<TensorSeq?>): List<Tensor?> {
        val axis = getAttributeValue("axis") as Long
        val newAxis = getAttributeValue("new_axis") as Long

        val srcTensors = inputs.first()!!.data
        val tensor = if (newAxis == 1L) srcTensors.stack(axis.toInt()) else srcTensors.concatenate(axis.toInt())
        return listOf(tensor)
    }
}
