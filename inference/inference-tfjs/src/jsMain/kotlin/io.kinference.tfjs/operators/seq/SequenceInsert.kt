package io.kinference.tfjs.operators.seq

import io.kinference.attribute.Attribute
import io.kinference.data.ONNXData
import io.kinference.data.ONNXDataType
import io.kinference.graph.Contexts
import io.kinference.ndarray.arrays.NumberNDArrayTFJS
import io.kinference.operator.*
import io.kinference.protobuf.message.TensorProto
import io.kinference.tfjs.TFJSData
import io.kinference.tfjs.data.seq.TFJSSequence
import io.kinference.tfjs.data.tensors.TFJSTensor
import io.kinference.types.TensorShape
import io.kinference.types.ValueTypeInfo

sealed class SequenceInsert(
    name: String,
    info: OperatorInfo,
    attributes: Map<String, Attribute<Any>>,
    inputs: List<String>,
    outputs: List<String>
) : Operator<TFJSData<*>, TFJSSequence>(name, info, attributes, inputs, outputs) {
    companion object {
        private val DEFAULT_VERSION = VersionInfo(sinceVersion = 11)

        operator fun invoke(name: String, version: Int?, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>): SequenceInsert {
            return when (version ?: DEFAULT_VERSION.sinceVersion) {
                in SequenceInsertVer11.VERSION.asRange() -> SequenceInsertVer11(name, attributes, inputs, outputs)
                else -> error("Unsupported version of SequenceInsert operator: $version")
            }
        }
    }
}


class SequenceInsertVer11 internal constructor(
    name: String,
    attributes: Map<String, Attribute<Any>>,
    inputs: List<String>,
    outputs: List<String>
) : SequenceInsert(name, INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = ALL_DATA_TYPES

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "input_sequence", optional = false, onnxDataType = ONNXDataType.ONNX_SEQUENCE),
            IOInfo(1, TYPE_CONSTRAINTS, "tensor", optional = false),
            IOInfo(2, setOf(TensorProto.DataType.INT64, TensorProto.DataType.INT32), "position", optional = true)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "output_sequence", optional = false, onnxDataType = ONNXDataType.ONNX_SEQUENCE)
        )

        internal val VERSION = VersionInfo(sinceVersion = 11)
        private val INFO = OperatorInfo("SequenceInsert", emptyMap(), INPUTS_INFO, OUTPUTS_INFO, VERSION, OperatorInfo.DEFAULT_DOMAIN)
    }

    override suspend fun <D : ONNXData<*, *>> apply(contexts: Contexts<D>, inputs: List<TFJSData<*>?>): List<TFJSSequence?> {
        val seqInput = (inputs[0]!! as TFJSSequence).clone().data
        val seq = ArrayList(seqInput as List<TFJSTensor>)
        val tensor = (inputs[1]!! as TFJSTensor).clone()
        val positionTensor = inputs.getOrNull(2)?.data as? NumberNDArrayTFJS

        val position = positionTensor?.singleValue()?.toInt() ?: seq.size
        val actualPosition = if (position >= 0) position else seq.size + position

        require(actualPosition >= 0 && actualPosition <= seq.size) { "Index $position is out of range [-${seq.size}, ${seq.size}]" }

        seq.add(actualPosition, tensor)
        val outputSeq = TFJSSequence(
            name = "output_sequence",
            data = seq,
            info = ValueTypeInfo.SequenceTypeInfo(
                elementType = ValueTypeInfo.TensorTypeInfo(
                    shape = TensorShape.unknown(),
                    type = tensor.info.type
                )
            )
        )
        return listOf(outputSeq)
    }
}
