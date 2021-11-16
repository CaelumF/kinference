package io.kinference.core.operators.seq

import io.kinference.core.attributes.Attribute
import io.kinference.data.ONNXDataType
import io.kinference.core.data.seq.KIONNXSequence
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.splitWithAxis
import io.kinference.core.graph.Context
import io.kinference.profiler.ProfilingContext
import io.kinference.core.operators.*
import io.kinference.core.operators.VersionInfo.Companion.asRange
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto
import io.kinference.core.types.ValueTypeInfo.SequenceTypeInfo
import kotlin.time.ExperimentalTime

sealed class SplitToSequence(info: OperatorInfo, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<KITensor, KIONNXSequence>(info, attributes, inputs, outputs) {
    companion object {
        operator fun invoke(version: Int, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) = when (version) {
            in SplitToSequenceVer11.VERSION.asRange() -> SplitToSequenceVer11(attributes, inputs, outputs)
            else -> error("Unsupported version of SplitToSequence operator: $version")
        }
    }
}

@ExperimentalTime
class SplitToSequenceVer11(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : SplitToSequence(INFO, attributes, inputs, outputs) {
    companion object {
        private const val DEFAULT_SPLIT_LENGTH = 1
        private val TYPE_CONSTRAINTS = ALL_DATA_TYPES

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), false, default = 0L),
            AttributeInfo("keepdims", setOf(AttributeProto.AttributeType.INT), false, default = 1L)
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "input", optional = false),
            IOInfo(1, setOf(TensorProto.DataType.INT64, TensorProto.DataType.INT32), "split", optional = true)
        )

        private val OUTPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "output_sequence", optional = false, onnxDataType = ONNXDataType.ONNX_SEQUENCE))

        internal val VERSION = VersionInfo(sinceVersion = 11)
        private val INFO = OperatorInfo("SplitToSequence", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO, VERSION, OperatorInfo.DEFAULT_DOMAIN)
    }

    private val axis: Int by attribute { it: Number -> it.toInt() }
    private val keepDims: Boolean by attribute("keepdims") { it: Number -> it.toInt() == 1 }


    override fun apply(context: Context, inputs: List<KITensor?>, profilingContext: ProfilingContext?): List<KIONNXSequence?> {
        val parts = inputs.elementAtOrNull(1)

        val input = inputs[0]!!
        val tensors = if (parts == null) {
            input.splitWithAxis(input.data.shape[axis], axis, keepDims)
        } else {
            input.splitWithAxis(parts, axis)
        }

        return listOf(KIONNXSequence("output_sequence", tensors, SequenceTypeInfo(tensors[0].info)))
    }
}
