package io.kinference.core.operators.activations

import io.kinference.core.attributes.Attribute
import io.kinference.core.operators.*
import io.kinference.ndarray.arrays.*
import io.kinference.primitives.types.DataType
import io.kinference.protobuf.message.AttributeProto
import kotlin.time.ExperimentalTime

sealed class LeakyRelu(info: OperatorInfo, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Activation(info, attributes, inputs, outputs) {
    companion object {
        operator fun invoke(version: Int, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) = when (version) {
            in LeakyReluVer6.VERSION.asRange() -> LeakyReluVer6(attributes, inputs, outputs)
            else -> error("Unsupported version of LeakyRelu operator: $version")
        }
    }
}

@ExperimentalTime
class LeakyReluVer6(attributes: Map<String, Attribute<Any>> = emptyMap(), inputs: List<String>, outputs: List<String>) : LeakyRelu(INFO, attributes, inputs, outputs) {
    companion object {
        private val TYPE_CONSTRAINTS = FLOAT_DATA_TYPES

        private val ATTRIBUTE_INFO = listOf(AttributeInfo("alpha", setOf(AttributeProto.AttributeType.FLOAT), default = 0.01f))

        private val INPUT_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "X", optional = false))
        private val OUTPUT_INFO = listOf(IOInfo(0, TYPE_CONSTRAINTS, "Y", optional = false))

        internal val VERSION = VersionInfo(sinceVersion = 6)
        private val INFO = OperatorInfo("LeakyRelu", ATTRIBUTE_INFO, INPUT_INFO, OUTPUT_INFO, VERSION, OperatorInfo.DEFAULT_DOMAIN)
    }

    val alpha: Float by attribute()

    private val activateFloat: FloatMap = object : FloatMap {
        override fun apply(value: Float): Float = if (value < 0) value * alpha else value
    }

    private val activateDouble: DoubleMap = object : DoubleMap {
        override fun apply(value: Double): Double = if (value < 0) value * alpha else value
    }

    override fun activate(input: NDArray): NDArray = when (val type = input.type) {
        DataType.FLOAT -> input.map(activateFloat)
        DataType.DOUBLE -> input.map(activateDouble)
        else -> error("Unsupported data type for this operation: $type")
    }
}
