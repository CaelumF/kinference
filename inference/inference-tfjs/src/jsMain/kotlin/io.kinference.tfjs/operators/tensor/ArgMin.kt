package io.kinference.tfjs.operators.tensor

import io.kinference.attribute.Attribute
import io.kinference.data.ONNXData
import io.kinference.graph.Contexts
import io.kinference.ndarray.arrays.NumberNDArrayTFJS
import io.kinference.operator.*
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto
import io.kinference.tfjs.data.tensors.TFJSTensor
import io.kinference.tfjs.data.tensors.asTensor

sealed class ArgMin(name: String, info: OperatorInfo, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) :
    Operator<TFJSTensor, TFJSTensor>(name, info, attributes, inputs, outputs) {
    companion object {
        private val DEFAULT_VERSION = VersionInfo(sinceVersion = 1)

        operator fun invoke(name: String, version: Int?, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) =
            when (version ?: DEFAULT_VERSION.sinceVersion) {
                in ArgMinVer12.VERSION.asRange() -> ArgMinVer12(name, attributes, inputs, outputs)
                else -> error("Unsupported version of ArgMin operator: $version")
            }
    }
}

class ArgMinVer12(name: String, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) :
    ArgMin(name, INFO, attributes, inputs, outputs) {
    companion object {
        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), required = false, default = 0),
            AttributeInfo("keepdims", setOf(AttributeProto.AttributeType.INT), required = false, default = 1),
            AttributeInfo("select_last_index", setOf(AttributeProto.AttributeType.INT), required = false, default = 0),
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, PRIMITIVE_DATA_TYPES + TensorProto.DataType.BFLOAT16, "data", differentiable = false)
        )

        private val OUTPUTS_INFO = listOf(IOInfo(0, setOf(TensorProto.DataType.INT64), "reduced", optional = false))

        internal val VERSION = VersionInfo(sinceVersion = 1)
        private val INFO = OperatorInfo("ArgMin", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO, VERSION, OperatorInfo.DEFAULT_DOMAIN)
    }

    private val axis: Int by attribute { it: Number -> it.toInt() }
    private val keepDims: Boolean by attribute("keepdims") { it: Number -> it.toInt() != 0 }
    private val selectLastIndex: Boolean by attribute("select_last_index") { it: Number -> it.toInt() != 0 }


    override suspend fun <D : ONNXData<*, *>> apply(contexts: Contexts<D>, inputs: List<TFJSTensor?>): List<TFJSTensor?> {
        val input = inputs[0]!!.data as NumberNDArrayTFJS

        return listOf(input.argmin(axis, keepDims, selectLastIndex).asTensor("reduced"))
    }
}
