package io.kinference.core.operators.tensor

import io.kinference.core.attributes.Attribute
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.core.graph.Context
import io.kinference.profiler.ProfilingContext
import io.kinference.ndarray.extensions.gather
import io.kinference.ndarray.extensions.indexAxis
import io.kinference.core.operators.*
import io.kinference.core.operators.VersionInfo.Companion.asRange
import kotlin.time.ExperimentalTime
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto

sealed class Gather(info: OperatorInfo, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<KITensor, KITensor>(info, attributes, inputs, outputs) {
    companion object {
        operator fun invoke(version: Int, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) = when (version) {
            in GatherVer1.VERSION.asRange() -> GatherVer1(attributes, inputs, outputs)
            else -> error("Unsupported version of Constant operator: $version")
        }
    }
}

@ExperimentalTime
class GatherVer1(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Gather(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = ALL_DATA_TYPES

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("axis", setOf(AttributeProto.AttributeType.INT), false, 0)
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "data", optional = false, differentiable = true),
            IOInfo(1, setOf(TensorProto.DataType.INT32, TensorProto.DataType.INT64), "indices", optional = false, differentiable = false)
        )

        private val OUTPUTS_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "output", optional = false))

        internal val VERSION = VersionInfo(sinceVersion = 1)
        private val INFO = OperatorInfo("Gather", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO, VERSION, OperatorInfo.DEFAULT_DOMAIN)
    }

    private val axis: Int by attribute { it: Number -> it.toInt() }


    override fun apply(context: Context, inputs: List<KITensor?>, profilingContext: ProfilingContext?): List<KITensor?> {
        val (data, indices) = inputs
        val axis = data!!.data.indexAxis(axis)
        return listOf(data.data.gather(indices!!.data, axis).asTensor())
    }
}
