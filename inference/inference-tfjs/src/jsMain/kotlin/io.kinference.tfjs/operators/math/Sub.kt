package io.kinference.tfjs.operators.math

import io.kinference.attribute.Attribute
import io.kinference.data.ONNXData
import io.kinference.graph.Contexts
import io.kinference.ndarray.arrays.NumberNDArrayTFJS
import io.kinference.operator.*
import io.kinference.tfjs.data.tensors.TFJSTensor
import io.kinference.tfjs.data.tensors.asTensor

sealed class Sub(
    name: String,
    info: OperatorInfo,
    attributes: Map<String, Attribute<Any>>,
    inputs: List<String>,
    outputs: List<String>
) : Operator<TFJSTensor, TFJSTensor>(name, info, attributes, inputs, outputs) {
    companion object {
        private val DEFAULT_VERSION = VersionInfo(sinceVersion = 7)

        operator fun invoke(name: String, version: Int?, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>): Sub {
            return when (version ?: DEFAULT_VERSION.sinceVersion) {
                in SubVer7.VERSION.asRange() -> SubVer7(name, attributes, inputs, outputs)
                else -> error("Unsupported version of Sub operator: $version")
            }
        }
    }
}


class SubVer7(
    name: String,
    attributes: Map<String, Attribute<Any>>,
    inputs: List<String>,
    outputs: List<String>
) : Sub(name, INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = PRIMITIVE_DATA_TYPES

        private val INPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "A", optional = false),
            IOInfo(1, TYPE_CONSTRAINTS, "B", optional = false)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "C", optional = false)
        )

        internal val VERSION = VersionInfo(sinceVersion = 7)
        private val INFO = OperatorInfo("Sub", emptyMap(), INPUTS_INFO, OUTPUTS_INFO, VERSION, OperatorInfo.DEFAULT_DOMAIN)
    }

    override suspend fun <D : ONNXData<*, *>> apply(contexts: Contexts<D>, inputs: List<TFJSTensor?>): List<TFJSTensor?> {
        val first = inputs[0]!!.data as NumberNDArrayTFJS
        val second = inputs[1]!!.data as NumberNDArrayTFJS
        return listOf((first - second).asTensor("C"))
    }
}
