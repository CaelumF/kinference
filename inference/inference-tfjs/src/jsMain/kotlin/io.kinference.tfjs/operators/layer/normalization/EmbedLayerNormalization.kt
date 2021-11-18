package io.kinference.tfjs.operators.layer.normalization

import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto
import io.kinference.attribute.Attribute
import io.kinference.data.ONNXData
import io.kinference.graph.Context
import io.kinference.operator.*
import io.kinference.profiler.ProfilingContext
import io.kinference.tfjs.data.tensors.TFJSTensor
import io.kinference.tfjs.data.tensors.asTensor
import io.kinference.tfjs.externals.core.fill
import io.kinference.tfjs.externals.core.range
import io.kinference.tfjs.externals.extensions.*

sealed class EmbedLayerNormalization(info: OperatorInfo, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>)
    : Operator<TFJSTensor, TFJSTensor>(info, attributes, inputs, outputs) {
    companion object {
        private val DEFAULT_VERSION = VersionInfo(sinceVersion = 1)

        operator fun invoke(version: Int?, attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) = when (version ?: DEFAULT_VERSION.sinceVersion) {
            in EmbedLayerNormalizationVer1.VERSION.asRange() -> EmbedLayerNormalizationVer1(attributes, inputs, outputs)
            else -> error("Unsupported version of EmbedLayerNormalization operator: $version")
        }
    }
}

class EmbedLayerNormalizationVer1(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : EmbedLayerNormalization(INFO, attributes, inputs, outputs) {

    companion object {
        private val TYPE_CONSTRAINTS = setOf(
            TensorProto.DataType.FLOAT,
            TensorProto.DataType.FLOAT16
        )

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("epsilon", setOf(AttributeProto.AttributeType.FLOAT), false, 0.00001f)
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, setOf(TensorProto.DataType.INT32), "input_ids", false),
            IOInfo(1, setOf(TensorProto.DataType.INT32), "segment_ids", true),
            IOInfo(2, TYPE_CONSTRAINTS, "word_embedding", false),
            IOInfo(3, TYPE_CONSTRAINTS, "position_embedding", false),
            IOInfo(4, TYPE_CONSTRAINTS, "segment_embedding", true),
            IOInfo(5, TYPE_CONSTRAINTS, "gamma", false),
            IOInfo(6, TYPE_CONSTRAINTS, "beta", false),
            IOInfo(7, setOf(TensorProto.DataType.INT32), "mask", true)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, TYPE_CONSTRAINTS, "output", false),
            IOInfo(1, setOf(TensorProto.DataType.INT32), "mask_index", false)
        )

        internal val VERSION = VersionInfo(sinceVersion = 1)
        private val INFO = OperatorInfo("EmbedLayerNormalization", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO, VERSION, domain = "com.microsoft")
    }

    private val epsilon: Float by attribute()

    override fun <D : ONNXData<*, *>> apply(context: Context<D>, inputs: List<TFJSTensor?>, profilingContext: ProfilingContext?): List<TFJSTensor?> {
        val outputs = tidy {
            val inputIds = inputs[0]!!.data
            val segmentIds = inputs[1]?.data
            val wordWeights = inputs[2]!!.data
            val positionWeights = inputs[3]!!.data
            val segmentWeights = inputs[4]?.data
            val gamma = inputs[5]!!.data
            val beta = inputs[6]!!.data
            val mask = inputs[7]?.data

            val (batchSize, seqLen) = inputIds.shape
            val (_, hiddenSize) = wordWeights.shape

            val outputShape = arrayOf(batchSize, seqLen, hiddenSize)

            val wordEmbedding = wordWeights.gather(inputIds.flatten()).reshape(outputShape)

            val positionIds = range(0, inputIds.shape[1], 1, "int32").broadcastTo(inputIds.shape)

            val positionEmbedding = positionWeights.gather(positionIds.flatten()).reshape(outputShape)


            val segmentEmbedding =
                if (segmentIds != null && segmentWeights != null) {
                    segmentWeights.gather(segmentIds.flatten()).reshape(outputShape)
                } else {
                    null
                }

            val embedding = if (segmentEmbedding != null) {
                wordEmbedding.add(positionEmbedding, segmentEmbedding)
            } else {
                wordEmbedding.plus(positionEmbedding)
            }

            val momentsOutput = embedding.moments(-1, true)
            val mean = momentsOutput.mean
            val variance = momentsOutput.variance

            val epsilonTensor = tensor(floatArrayOf(epsilon), arrayOf(1), "float32")
            val output = (embedding - mean) / (sqrt(variance + epsilonTensor)) * gamma + beta

            val maskOutput = mask?.sum(1, false) ?: fill(arrayOf(batchSize), 0, "int32")
            return@tidy arrayOf(output, maskOutput)
        }

        return listOf(outputs[0].asTensor("output"), outputs[1].asTensor("mask_index"))
    }
}
