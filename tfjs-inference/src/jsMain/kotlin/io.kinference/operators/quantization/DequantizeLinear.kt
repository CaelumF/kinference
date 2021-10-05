package io.kinference.operators.quantization

import io.kinference.attributes.Attribute
import io.kinference.custom_externals.core.*
import io.kinference.custom_externals.extensions.*
import io.kinference.data.tensors.Tensor
import io.kinference.data.tensors.asTensor
import io.kinference.graph.Context
import io.kinference.operators.*
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto

class DequantizeLinear(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>)
    : Operator<Tensor, Tensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val IN_TYPE_CONSTRAINTS = setOf(
            TensorProto.DataType.INT8,
            TensorProto.DataType.UINT8
        )

        private val OUT_TYPE_CONSTRAINTS = setOf(
            TensorProto.DataType.FLOAT,
            TensorProto.DataType.FLOAT16
        )

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), required = false, default = 1)
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, IN_TYPE_CONSTRAINTS, "x", optional = false),
            IOInfo(0, OUT_TYPE_CONSTRAINTS, "x_scale", optional = false),
            IOInfo(0, IN_TYPE_CONSTRAINTS, "x_zero_point", optional = true)
        )

        private val OUTPUTS_INFO = listOf(IOInfo(0, OUT_TYPE_CONSTRAINTS, "y", optional = false))

        private val INFO = OperatorInfo("DequantizeLinear", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)

        private fun canDequantizePerTensor(zeroPoint: TensorTFJS?, scale: TensorTFJS): Boolean {
            return scale.size == 1 && (zeroPoint == null || zeroPoint.size == 1)
        }

        private fun TensorTFJS.canDequantizePerAxis(axis: Int, zeroPoint: TensorTFJS?, scale: TensorTFJS): Boolean {
            return scale.rank == 1 && scale.size == shape[axis] && (zeroPoint == null || zeroPoint.rank == 1 && zeroPoint.size == shape[axis])
        }
    }

    private val axis: Int by attribute { it: Number -> it.toInt() }


    override fun apply(context: Context, inputs: List<Tensor?>): List<Tensor?> {
        val outputs = tidy {
            val input = inputs[0]!!.data
            val scale = inputs[1]!!.data
            val zeroPoint = inputs.getOrNull(2)?.data
            val actualAxis = input.indexAxis(axis)

            require(zeroPoint == null || scale.shape.contentEquals(zeroPoint.shape)) { "Zero point and scale tensors should have the same dims" }

            val output = when {
                canDequantizePerTensor(zeroPoint, scale) -> {
                    val zero = zeroPoint ?: scalar(0, "int32")

                    (input - zero) * scale
                }

                input.canDequantizePerAxis(actualAxis, zeroPoint, scale) -> {
                    val blockCount = input.computeBlockSize(toDim = actualAxis)
                    val blockSize = input.computeBlockSize(fromDim = actualAxis + 1)
                    val dim = input.shape[actualAxis]
                    val preparedInput = input.reshape(arrayOf(blockCount, dim, blockSize))
                    val preparedZP = zeroPoint?.reshape(arrayOf(1, dim, 1)) ?: fill(arrayOf(1, dim, 1), 0, "int32")
                    val preparedScale = scale.reshape(arrayOf(1, dim, 1))

                    val rawOutput = (preparedInput - preparedZP) * preparedScale
                    rawOutput.reshape(input.shape)
                }

                else -> error("Cannot perform dequantization. Scale and zero point tensors should be either scalars or 1D tensors")
            }

            return@tidy arrayOf(output)
        }

        return listOf(outputs.first().asTensor("y"))
    }
}

