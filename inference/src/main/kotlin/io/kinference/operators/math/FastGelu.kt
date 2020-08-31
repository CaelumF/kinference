package io.kinference.operators.math

import io.kinference.primitives.types.DataType
import io.kinference.attributes.Attribute
import io.kinference.data.tensors.Tensor
import io.kinference.data.tensors.asTensor
import io.kinference.graph.Context
import io.kinference.ndarray.*
import io.kinference.operators.IOInfo
import io.kinference.operators.Operator
import io.kinference.operators.OperatorInfo

class FastGelu(attributes: Map<String, Attribute<Any>> = emptyMap(), inputs: List<String>, outputs: List<String>) :
    Operator<Tensor, Tensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = FLOAT_DATA_TYPES

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "X", optional = false),
            IOInfo(1, TYPE_CONSTRAINTS, "bias", optional = true)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "Y", optional = false)
        )

        private val INFO = OperatorInfo("FastGelu", emptyMap(), INPUTS_INFO, OUTPUTS_INFO)
    }

    @ExperimentalUnsignedTypes
    override fun apply(context: Context, inputs: List<Tensor?>): List<Tensor?> {
        val input = inputs.first()!!
        val bias = inputs.getOrNull(1)

        val result = when (input.data.type) {
            DataType.FLOAT -> {
                val biasData = bias?.data as? FloatNDArray
                val result = input.data.toMutable() as MutableFloatNDArray
                if (bias?.data == null) {
                    for (i in 0 until result.linearSize) result[i] = fgelu(result[i])
                } else {
                    for (i in 0 until result.linearSize) result[i] = fgelu(result[i] + biasData!![i % biasData.linearSize])
                }
                result
            }

            DataType.DOUBLE -> {
                val biasData = bias?.data as? DoubleNDArray
                val result = input.data.toMutable() as MutableDoubleNDArray
                if (bias?.data == null) {
                    for (i in 0 until result.linearSize) result[i] = fgelu(result[i])
                } else {
                    for (i in 0 until result.linearSize) result[i] = fgelu(result[i] + biasData!![i % biasData.linearSize])
                }
                result
            }

            else -> error("Unsupported operation")
        }.asTensor("Y")

        return listOf(result)
    }
}
