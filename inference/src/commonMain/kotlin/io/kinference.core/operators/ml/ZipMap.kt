package io.kinference.core.operators.ml

import io.kinference.core.attributes.Attribute
import io.kinference.core.data.KIONNXData
import io.kinference.core.data.map.KIONNXMap
import io.kinference.core.data.seq.KIONNXSequence
import io.kinference.core.data.tensors.KITensor
import io.kinference.core.data.tensors.asTensor
import io.kinference.core.graph.Context
import io.kinference.core.graph.ProfilingContext
import io.kinference.ndarray.arrays.FloatNDArray
import io.kinference.ndarray.arrays.pointers.FloatPointer
import io.kinference.core.operators.*
import io.kinference.protobuf.message.AttributeProto
import io.kinference.protobuf.message.TensorProto
import io.kinference.core.types.*
import kotlin.collections.HashMap
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ZipMap(attributes: Map<String, Attribute<Any>>, inputs: List<String>, outputs: List<String>) : Operator<KITensor, KIONNXSequence>(INFO, attributes, inputs, outputs) {
    companion object {
        private val OUT_TYPE_CONSTRAINTS = setOf(TensorProto.DataType.INT64, TensorProto.DataType.STRING, TensorProto.DataType.FLOAT)

        private val INPUTS_INFO = listOf(
            IOInfo(0, setOf(TensorProto.DataType.FLOAT), "X", optional = false)
        )

        private val OUTPUTS_INFO = listOf(
            IOInfo(0, OUT_TYPE_CONSTRAINTS, "Z", optional = false)
        )

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("classlabels_int64s", setOf(AttributeProto.AttributeType.INTS), required = false),
            AttributeInfo("classlabels_strings", setOf(AttributeProto.AttributeType.STRINGS), required = false)
        )

        private val INFO = OperatorInfo("ZipMap", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)

        private fun <T : Any> FloatNDArray.asSeqWithLabels(labels: Labels<T>, mapInfo: ValueTypeInfo.MapTypeInfo): KIONNXSequence {
            val seqInfo = ValueInfo(ValueTypeInfo.SequenceTypeInfo(mapInfo), name = "Z")
            val mapValueInfo = ValueInfo(mapInfo)
            val rows = if (rank == 1) 1 else shape[0]
            val columns = shape.last()

            val inputPointer = FloatPointer(array)
            return KIONNXSequence(seqInfo, rows) {
                val map = HashMap<T, KIONNXData<*>>(columns)
                repeat(columns) {
                    val value = inputPointer.getAndIncrement()
                    val tensor = FloatNDArray.scalar(value).asTensor()
                    map[labels[it]] = tensor
                }
                KIONNXMap(map as Map<Any, KIONNXData<*>>, mapValueInfo)
            }
        }
    }

    sealed class Labels<T> {
        abstract operator fun get(i: Int): T

        class StringLabels(private val labels: List<String>): Labels<String>() {
            override fun get(i: Int): String = labels[i]
        }

        class LongLabels(private val labels: LongArray): Labels<Long>() {
            override fun get(i: Int): Long = labels[i]
        }
    }

    private val classLabelsLong: Labels.LongLabels? by attributeOrNull("classlabels_int64s") { labels: LongArray? -> Labels.LongLabels(labels!!) }
    private val classLabelsString: Labels.StringLabels? by attributeOrNull("classlabels_strings") { labels: List<String>? -> Labels.StringLabels(labels!!) }

    private val outputMapInfo: ValueTypeInfo.MapTypeInfo
        get() {
            val mapKeyType = if (classLabelsLong != null) TensorProto.DataType.INT64 else TensorProto.DataType.STRING
            val mapValueInfo = ValueTypeInfo.TensorTypeInfo(TensorShape.empty(), TensorProto.DataType.FLOAT)
            return ValueTypeInfo.MapTypeInfo(mapKeyType, mapValueInfo)
        }

    override fun apply(context: Context, inputs: List<KITensor?>, profilingContext: ProfilingContext?): List<KIONNXSequence?> {
        val labels = classLabelsLong ?: classLabelsString
        requireNotNull(labels) { "Class labels should be specified" }

        val input = inputs[0]!!.data as FloatNDArray
        require(input.rank == 2)

        return listOf(input.asSeqWithLabels(labels, outputMapInfo))
    }
}
