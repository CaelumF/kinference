package org.jetbrains.research.kotlin.inference.operators.flow

import org.jetbrains.research.kotlin.inference.attributes.Attribute
import org.jetbrains.research.kotlin.inference.data.tensors.Tensor
import org.jetbrains.research.kotlin.inference.extensions.tensor.stack
import org.jetbrains.research.kotlin.inference.graph.Graph
import org.jetbrains.research.kotlin.inference.onnx.AttributeProto
import org.jetbrains.research.kotlin.inference.onnx.TensorProto
import org.jetbrains.research.kotlin.inference.operators.*

class Loop(attributes: Map<String, Attribute<Any>>, usedOutputsNum: Int) : Operator<Tensor, Tensor>(INFO, usedOutputsNum, attributes) {
    companion object {
        private val TYPE_CONSTRAINTS = ALL_DATA_TYPES

        private val ATTRIBUTES_INFO = listOf(
            AttributeInfo("body", setOf(AttributeProto.AttributeType.GRAPH), required = true)
        )

        private val INPUTS_INFO = listOf(
            IOInfo(0, setOf(TensorProto.DataType.INT64), "M", optional = true, scalar = true),
            IOInfo(1, setOf(TensorProto.DataType.BOOL), "cond", optional = true, scalar = true),
            VariadicIOInfo(2, TYPE_CONSTRAINTS, "v_initial")
        )

        private val OUTPUTS_INFO = listOf(VariadicIOInfo(0, TYPE_CONSTRAINTS, "v_final_and_scan_outputs", minimumArity = 1))

        private val INFO = OperatorInfo("Loop", ATTRIBUTES_INFO, INPUTS_INFO, OUTPUTS_INFO)
    }

    private fun inner(body: Graph, counter: Long, condition: Boolean, modified: MutableList<Tensor?>, scans: List<MutableList<Tensor>>): Boolean {
        body.setInput(Tensor(counter, TensorProto.DataType.INT64, name = body.inputs[0].name))
        body.setInput(Tensor(condition, TensorProto.DataType.BOOL, name = body.inputs[1].name))

        val outputs = body.execute()
        val iterationOutputs = outputs.drop(body.inputs.size - 1)

        modified.clear()
        body.inputs.drop(2).zip(outputs.drop(1)) { input, value ->
            body.setInput(value.rename(input.name))
            modified.add(value as Tensor)
        }

        require(iterationOutputs.size == scans.size) { "Loop subgraph didn't provide expected output count" }
        scans.zip(iterationOutputs) { buffer, output ->
            buffer.add(output as Tensor)
        }

        return (outputs[0] as Tensor).data[0] as Boolean
    }

    override fun apply(inputs: List<Tensor?>): List<Tensor?> {
        val maxTripCount = inputs[0]?.data?.get(0) as Long?
        val keepgoing = inputs[1]?.data?.get(0) as Boolean?

        val body = getAttributeValue("body") as Graph
        require(body.inputs.size == inputs.size) { "Not enough inputs for Loop subgraph\nPresent: ${inputs.size}, Expected: ${body.inputs.size}" }
        body.inputs.drop(2).zip(inputs.drop(2)) { input, value ->
            require(value != null) { "Graph inputs must not be null" }
            body.setInput(value.rename(input.name))
        }

        // NOTE: works as ONNX Runtime (counter and condition are ignored and not returned to results of Loop)
        val modifiedCount = body.inputs.size - 2
        val modified = inputs.drop(2).toMutableList()

        val scansCount = body.outputs.size - 1 - modifiedCount
        val scans = (0 until scansCount).map { ArrayList<Tensor>() }

        var counter = 0L
        var condition = keepgoing ?: true
        when {
            maxTripCount == null && keepgoing == null -> {
                while (true) {
                    condition = inner(body, counter, condition, modified, scans)
                    counter += 1
                }
            }
            maxTripCount == null && keepgoing != null -> {
                while (condition) {
                    condition = inner(body, counter, condition, modified, scans)
                    counter += 1
                }
            }
            maxTripCount != null && keepgoing == null -> {
                for (counter in 0 until maxTripCount) {
                    condition = inner(body, counter, condition, modified, scans)
                }
            }
            maxTripCount != null && keepgoing != null -> {
                for (counter in 0 until maxTripCount) {
                    if (!condition) break
                    condition = inner(body, counter, condition, modified, scans)
                }
            }
        }

        return modified + scans.map { it.stack(0) }
    }
}
