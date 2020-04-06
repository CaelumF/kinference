package org.jetbrains.research.kotlin.mpp.inference.operators.layer.reccurent.lstm

import org.jetbrains.research.kotlin.mpp.inference.operators.activations.Activation
import org.jetbrains.research.kotlin.mpp.inference.operators.layer.reccurent.RecurrentLayer
import org.jetbrains.research.kotlin.mpp.inference.space.*
import org.jetbrains.research.kotlin.mpp.inference.tensors.Tensor
import org.jetbrains.research.kotlin.mpp.inference.types.resolveKClass
import scientifik.kmath.structures.BufferNDStructure
import scientifik.kmath.structures.asBuffer
import scientifik.kmath.structures.asIterable

class LSTMLayer<T : Number> : RecurrentLayer<T>() {
    @Suppress("UNCHECKED_CAST")
    override fun apply(inputs: Collection<Tensor<T>>): Collection<Tensor<T>> {
        require(inputs.size in 3..4) { "Applicable only for three or four arguments" }

        val inputList = inputs.toList()

        val inputTensor = inputList[0]
        val weights = inputList[1].as2DCollection().first()
        val recWeights = inputList[2].as2DCollection().first()

        val hiddenSize = recWeights.data.shape[1]
        val batchSize = inputTensor.data.shape[1]

        val bias = if (inputList.size >= 4) inputList[3] else null

        val stateSpace = resolveSpaceWithKClass(inputTensor.type!!.resolveKClass(), intArrayOf(batchSize, hiddenSize))
        stateSpace as TensorRing<T>
        var currentState = State.create(stateSpace, inputTensor.type)
        val resAnsList = mutableListOf<Tensor<T>>()

        for (inputMatrix in inputTensor.as2DCollection()) {
            var gates = weights.dot(inputMatrix.transpose()) + recWeights.dot(currentState.ans.transpose())

            gates = addBiases(gates, bias, hiddenSize)
            val gatesData = GatesData.create(gates, batchSize.toLong(), hiddenSize.toLong())
            gatesData.activate()

            val newCellGate = gatesData.forgetGate.multiply(currentState.cellGate) + gatesData.inputGate.multiply(gatesData.cellGate)
            val newAns = gatesData.outputGate.multiply(Activation.Tanh<T>().apply(listOf(newCellGate)).first())

            currentState = State(newAns, newCellGate)
            resAnsList.add(newAns.transpose())
        }

        return listOf(concatOutput(resAnsList))
    }

    private fun addBiases(gates: Tensor<T>, biases: Tensor<T>?, hiddenSize: Int) = when(biases){
        null -> gates
        else -> gates.mapIndexed { index, t -> t + biases.data[intArrayOf(0, index[0])] + biases.data[intArrayOf(0, index[0] + 4 * hiddenSize)] }
    }

    @Suppress("UNCHECKED_CAST")
    private fun concatOutput(outputs: Collection<Tensor<T>>) : Tensor<T> {
        val newShape = intArrayOf(outputs.size, 1, outputs.first().data.shape[0], outputs.first().data.shape[1])
        val newData = mutableListOf<T>()
        for (output in outputs) {
            newData.addAll(output.data.buffer.asIterable())
        }
        val newBuffer = BufferNDStructure(SpaceStrides(newShape), newData.asBuffer())
        val newSpace = resolveSpaceWithKClass(outputs.first().type!!.resolveKClass(), newShape)
        newSpace as TensorRing<T>
        return Tensor("Y", newBuffer, outputs.first().type, newSpace)
    }

    data class GatesData<T : Number>(var inputGate: Tensor<T>,
                                     var outputGate: Tensor<T>,
                                     var forgetGate: Tensor<T>,
                                     var cellGate: Tensor<T>) {
        fun activate() {
            inputGate = Activation.Sigmoid<T>().apply(listOf(inputGate)).first()
            outputGate = Activation.Sigmoid<T>().apply(listOf(outputGate)).first()
            forgetGate = Activation.Sigmoid<T>().apply(listOf(forgetGate)).first()
            cellGate = Activation.Tanh<T>().apply(listOf(cellGate)).first()
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun <T : Number> create(gates: Tensor<T>, batchSize: Long, hiddenSize: Long) : GatesData<T> {
                val chunkedBuffer = gates.data.buffer.asIterable().chunked((batchSize * hiddenSize).toInt())

                val shape = listOf(hiddenSize, batchSize)
                val gateSpace = resolveSpaceWithKClass(gates.type!!.resolveKClass(), shape.toIntArray())
                gateSpace as TensorRing<T>
                val res = List(chunkedBuffer.size) { Tensor(shape, chunkedBuffer[it], gates.type, null, gateSpace) }
                return GatesData(res[0], res[1], res[2], res[3])
            }
        }
    }

    data class State<T : Number>(val ans: Tensor<T>, val cellGate: Tensor<T>){
        companion object {
            fun <T : Number> create(stateSpace: TensorRing<T>, type: TensorProto.DataType) =
                State(Tensor(null, stateSpace.zero, type, stateSpace), Tensor(null, stateSpace.zero, type, stateSpace))
        }
    }
}
