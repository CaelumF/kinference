package io.kinference.core.operators.math

import io.kinference.core.attributes.Attribute
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.core.graph.Context
import io.kinference.core.operators.*
import io.kinference.core.operators.VersionInfo.Companion.asRange
import io.kinference.ndarray.arrays.NumberNDArray
import io.kinference.ndarray.extensions.createScalarNDArray
import io.kinference.ndarray.extensions.matmul
import io.kinference.ndarray.toIntArray
import io.kinference.profiler.ProfilingContext
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto
import kotlin.time.ExperimentalTime

@ExperimentalTime
class FusedMatMul(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<KITensor, KITensor>(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = setOf(
            TensorProto.DataType.FLOAT16,
            TensorProto.DataType.FLOAT,
            TensorProto.DataType.DOUBLE,
            TensorProto.DataType.BFLOAT16
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "A", optional = false),
            IOInfo(1, TYPE_CONSTRAINTS, "B", optional = false)
        )

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("alpha", setOf(AttributeProto.AttributeType.FLOAT), required = true),
            AttributeInfo("transA", setOf(AttributeProto.AttributeType.INT), required = true),
            AttributeInfo("transB", setOf(AttributeProto.AttributeType.INT), required = true),
        )

        private val OUTPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "Y", optional = false))

        private val VERSION = VersionInfo(sinceVersion = 1)
        private val INFO = OperatorInfo("FusedMatMul", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO, VERSION, domain = "com.microsoft")

        operator fun invoke(version: Int, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) = when (version) {
            in VERSION.asRange() -> FusedMatMul(attributes, inputs, outputs)
            else -> error("Unsupported version of FusedMatMul operator: $version")
        }
    }

    private val alpha: Float by attribute()

    private val transposeLeft: Boolean by attribute("transA") { it: Long -> it == 1L }
    private val transposeRight: Boolean by attribute("transB") { it: Long -> it == 1L }

    override fun apply(context: Context, inputs: List<KITensor?>, profilingContext: ProfilingContext?): List<KITensor?> {
        val left = inputs[0]!!.data as NumberNDArray
        val right = inputs[1]!!.data as NumberNDArray

        val actualLeft = if (transposeLeft) left.toMutable().transpose(left.shape.indices.toIntArray().apply {
            this[lastIndex]--
            this[lastIndex - 1]++
        }) else left

        val actualRight = if (transposeRight) right.toMutable().transpose(right.shape.indices.toIntArray().apply {
            this[lastIndex]--
            this[lastIndex - 1]++
        }) else right

        val output = actualLeft.matmul(actualRight)
        output.timesAssign(createScalarNDArray(output.type, alpha))
        return listOf(output.asTensor("Y"))
    }
}
