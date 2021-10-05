package io.kinference.operators

import io.kinference.attributes.Attribute
import io.kinference.data.ONNXData
import io.kinference.operators.layer.attention.Attention
import io.kinference.operators.layer.normalization.EmbedLayerNormalization
import io.kinference.operators.layer.normalization.SkipLayerNormalization
import io.kinference.operators.math.*
import io.kinference.operators.tensor.*
import io.kinference.protobuf.message.NodeProto
import kotlin.time.ExperimentalTime

@ExperimentalTime
object OperatorFactory {
    @Suppress("UNCHECKED_CAST")
    fun create(opType: String?, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) = when (opType) {
        "Attention" -> Attention(attributes, inputs, outputs)
        "Add" -> Add(attributes, inputs, outputs)
        "Shape" -> Shape(attributes, inputs, outputs)
        "ConstantOfShape" -> ConstantOfShape(attributes, inputs, outputs)
        "Cast" -> Cast(attributes, inputs, outputs)
        "EmbedLayerNormalization" -> EmbedLayerNormalization(attributes, inputs, outputs)
        "MatMul" -> MatMul(attributes, inputs, outputs)
        "SkipLayerNormalization" -> SkipLayerNormalization(attributes, inputs, outputs)
        "BiasGelu" -> BiasGelu(attributes, inputs, outputs)
        else -> error("Unsupported operator: $opType")
    } as Operator<ONNXData, ONNXData>

    fun create(proto: NodeProto) = create(proto.opType, proto.attribute.map { Attribute.create(it) }.associateBy(Attribute<Any>::name), proto.input, proto.output)
}
