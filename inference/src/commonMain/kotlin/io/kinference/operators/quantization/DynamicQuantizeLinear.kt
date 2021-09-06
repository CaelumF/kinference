package io.kinference.operators.quantization

import io.kinference.attributes.Attribute
import io.kinference.data.tensors.Tensor
import io.kinference.data.tensors.asTensor
import io.kinference.graph.Context
import io.kinference.graph.ProfilingContext
import io.kinference.ndarray.arrays.*
import io.kinference.ndarray.extensions.allocateNDArray
import io.kinference.ndarray.extensions.createScalarNDArray
import io.kinference.operators.*
import io.kinference.primitives.types.DataType
import io.kinference.protobuf.message.TensorProto
import kotlin.math.*
import kotlin.time.ExperimentalTime

@ExperimentalTime
class DynamicQuantizeLinear(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<Tensor, Tensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val ATTRIBUTES_INFO = emptyList<AttributeInfo>()

        private val INPUTS_INFO = listOf(
            IOInfo(0, setOf(TensorProto.DataType.FLOAT), "x", optional = false)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, setOf(TensorProto.DataType.UINT8), "y", optional = false),
            IOInfo(1, setOf(TensorProto.DataType.FLOAT), "y_scale", optional = false),
            IOInfo(2, setOf(TensorProto.DataType.UINT8), "y_zero_point", optional = false)
        )

        private val INFO = OperatorInfo("DynamicQuantizeLinear", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)

        private fun clip(x: Float, min: Float, max: Float) = when {
            x < min -> min
            x > max -> max
            else -> x
        }


        private fun Float.toUByte() = this.toUInt().toUByte()

        internal fun FloatNDArray.dynamicQuantize(): Triple<UByteNDArray, FloatNDArray, UByteNDArray> {
            val inputMin = min(0f, this.min())
            val inputMax = max(0f, this.max())

            val outputScale = (inputMax - inputMin) / 255f
            val outputScaleScalar = createScalarNDArray(DataType.FLOAT, outputScale)

            val outputZeroPoint = clip(round((-inputMin) / outputScale), 0f, 255f)
            val outputZeroPointScalar = createScalarNDArray(DataType.UBYTE, outputZeroPoint.toUByte())

            val output = allocateNDArray(DataType.UBYTE, this.strides) as MutableUByteNDArray

            for (i in 0 until this.linearSize) {
                output.array[i] = clip((round(this.array[i] / outputScale) + outputZeroPoint), 0f, 255f).toUByte()
            }

            return Triple(
                output as UByteNDArray,
                outputScaleScalar as FloatNDArray,
                outputZeroPointScalar as UByteNDArray
            )
        }
    }


    override fun apply(context: Context, inputs: List<Tensor?>, profilingContext: ProfilingContext?): List<Tensor?> {
        val input = inputs.first()!!.data as FloatNDArray

        val (output, outputScaleScalar, outputZeroPointScalar) = input.dynamicQuantize()

        return listOf(
            output.asTensor("y"),
            outputScaleScalar.asTensor("y_scale"),
            outputZeroPointScalar.asTensor("y_zero_point")
        )
    }
}
