package io.kinference.core.operators.seq

import io.kinference.attribute.Attribute
import io.kinference.data.ONNXDataType
import io.kinference.core.data.seq.KIONNXSequence
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.splitWithAxis
import io.kinference.core.graph.KIContext
import io.kinference.profiler.ProfilingContext
import io.kinference.operator.*
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto
import io.kinference.types.ValueTypeInfo.SequenceTypeInfo
import io.kinference.data.ONNXData
import io.kinference.graph.Context
import kotlin.time.ExperimentalTime

sealed class SplitToSequence(info: OperatorInfo, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<KITensor, KIONNXSequence>(info, attributes, inputs, outputs) {
    companion object {
        private val DEFAULT_VERSION = VersionInfo(sinceVersion = 11)

        operator fun invoke(version: Int?, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) = when (version ?: DEFAULT_VERSION.sinceVersion) {
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


    override fun <D : ONNXData<*, *>> apply(context: Context<D>, inputs: List<KITensor?>, profilingContext: ProfilingContext?, checkCancelled: () -> Unit): List<KIONNXSequence?> {
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
