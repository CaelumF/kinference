package io.kinference.operators.math

import io.kinference.attributes.Attribute
import io.kinference.data.tensors.Tensor
import io.kinference.graph.Context
import io.kinference.onnx.TensorProto
import io.kinference.operators.IOInfo
import io.kinference.operators.Operator
import io.kinference.operators.OperatorInfo

class Sub(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<Tensor, Tensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = setOf(
            TensorProto.DataType.UINT32,
            TensorProto.DataType.UINT64,
            TensorProto.DataType.INT32,
            TensorProto.DataType.INT64,
            TensorProto.DataType.FLOAT16,
            TensorProto.DataType.FLOAT,
            TensorProto.DataType.DOUBLE,
            TensorProto.DataType.BFLOAT16
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "A", optional = false),
            IOInfo(1, TYPE_CONSTRAINTS, "B", optional = false)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "C", optional = false)
        )

        private val INFO = OperatorInfo("Add", emptyMap(), INPUTS_INFO, OUTPUTS_INFO)
    }

    override fun apply(context: Context, inputs: List<Tensor?>): List<Tensor?> {
        val result = inputs[0]!! - inputs[1]!!
        return listOf(result.rename("C") as Tensor)
    }
}
