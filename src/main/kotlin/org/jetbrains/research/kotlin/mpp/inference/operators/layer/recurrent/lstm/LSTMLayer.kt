package org.jetbrains.research.kotlin.mpp.inference.operators.layer.recurrent.lstm

import TensorProto
import org.jetbrains.research.kotlin.mpp.inference.createInferredTypeBuffer
import org.jetbrains.research.kotlin.mpp.inference.data.tensors.*
import org.jetbrains.research.kotlin.mpp.inference.operators.activations.Sigmoid
import org.jetbrains.research.kotlin.mpp.inference.operators.activations.Tanh
import scientifik.kmath.structures.Buffer
import scientifik.kmath.structures.BufferNDStructure
import scientifik.kmath.structures.VirtualBuffer
import scientifik.kmath.structures.get

open class LSTMLayer<T : Number> {
    open fun apply(inputs: List<Tensor>): List<Tensor> {
        require(inputs.size in 3..4) { "Applicable only for three or four arguments" }

        val inputList = inputs.toList()

        val inputTensor = inputList[0]
        val weights = inputList[1].squeeze(0)
        val recWeights = inputList[2].squeeze(0)
        val bias = inputList.getOrNull(3)

        val batchSize = inputTensor.data.shape[1]
        val hiddenSize = recWeights.data.shape[1]

        val (mainOutput, currentState) = activate(inputTensor.as2DList(), weights, recWeights, bias)
        val shapeForOutput = intArrayOf(1, batchSize, hiddenSize)
        return listOf(mainOutput.toOutput(), currentState.output.reshape(shapeForOutput), currentState.cellGate.reshape(shapeForOutput))
    }

    protected fun activate(inputMatrices: Collection<Tensor>, weights: Tensor, recWeights: Tensor, bias: Tensor?): Pair<List<Tensor>, State> {
        val hiddenSize = recWeights.data.shape[1]
        val batchSize = inputMatrices.first().data.shape[0]

        var currentState = State.initialize<T>(batchSize, hiddenSize, inputMatrices.first().info.type)
        val biasesData = if (bias != null) BiasesData.create(bias, hiddenSize, batchSize) else null
        val weightsTranspose = weights.transpose()
        val recWeightsTranspose = recWeights.transpose()

        val mainOutput = inputMatrices.map { inputMatrix ->
            val gatesData = GatesData.create(inputMatrix, weightsTranspose, recWeightsTranspose, currentState)

            val activatedGatesData = if (biasesData != null) gatesData.addBiases(biasesData.first, biasesData.second, true) else gatesData.activate()
//            val activatedGatesData = gatesDataWithBiases.activate()

            currentState = State.create(activatedGatesData, currentState)

            currentState.output
        }

        return Pair(mainOutput, currentState)
    }

    data class GatesData(
        val inputGate: Tensor,
        val outputGate: Tensor,
        val forgetGate: Tensor,
        val cellGate: Tensor
    ) {
        fun activate(): GatesData {
            val activatedInputGate = Sigmoid().apply(inputGate).first()
            val activatedOutputGate = Sigmoid().apply(outputGate).first()
            val activatedForgetGate = Sigmoid().apply(forgetGate).first()
            val activatedCellGate = Tanh().apply(cellGate).first()
            return GatesData(activatedInputGate, activatedOutputGate, activatedForgetGate, activatedCellGate)
        }

        private fun calcGates(tensor1: Tensor, tensor2: Tensor, tensor3: Tensor, activate: ((Number) -> Number)? = null): Tensor {
            val (buffer, type) = if (activate == null) {
                createInferredTypeBuffer(tensor1.info.type, tensor1.info.type, tensor1.data.strides.linearSize) {
                    add(tensor1.data.buffer[it] as Number, tensor2.data.buffer[it] as Number, tensor3.data.buffer[it] as Number)
                }
            } else {
                createInferredTypeBuffer(tensor1.info.type, tensor1.info.type, tensor1.data.strides.linearSize) {
                    activate(add(tensor1.data.buffer[it] as Number, tensor2.data.buffer[it] as Number, tensor3.data.buffer[it] as Number))
                }
            }

            return Tensor(null, BufferNDStructure(tensor1.data.strides, buffer as Buffer<Any>), type)
        }

        fun addBiases(weightsBiasesData: BiasesData, recursiveWeightsBiasesData: BiasesData, activation: Boolean = false): GatesData {
            val inputGateWithBiases = calcGates(inputGate, weightsBiasesData.inputGateBiases, recursiveWeightsBiasesData.inputGateBiases, if (activation) (Sigmoid)::activate else null)
            val outputGateWithBiases = calcGates(outputGate, weightsBiasesData.outputGateBiases, recursiveWeightsBiasesData.outputGateBiases, if (activation) (Sigmoid)::activate else null)
            val forgetGateWithBiases = calcGates(forgetGate, weightsBiasesData.forgetGateBiases, recursiveWeightsBiasesData.forgetGateBiases, if (activation) (Sigmoid)::activate else null)
            val cellGateWithBiases = calcGates(cellGate, weightsBiasesData.cellGateBiases, recursiveWeightsBiasesData.cellGateBiases, if (activation) (Tanh)::activate else null)

            return GatesData(inputGateWithBiases, outputGateWithBiases, forgetGateWithBiases, cellGateWithBiases)
        }

        companion object {
            fun create(inputMatrix: Tensor, weights: Tensor, recWeights: Tensor, prevState: State): GatesData {
                val gates = (inputMatrix.matmul(weights) + prevState.output.matmul(recWeights)) as Tensor
                val gatesList = gates.splitWithAxis(4, 1)
                return GatesData(gatesList[0], gatesList[1], gatesList[2], gatesList[3])
            }
        }
    }

    data class State(val output: Tensor, val cellGate: Tensor) {
        companion object {
            @Suppress("UNCHECKED_CAST")
            fun <T : Number> initialize(batchSize: Int, hiddenSize: Int, type: TensorProto.DataType): State {
                val newShape = intArrayOf(batchSize, hiddenSize)
                val zeros = BufferNDStructure(TensorStrides(newShape), VirtualBuffer(batchSize * hiddenSize) { 0.0 as T }) as BufferNDStructure<Any>
                return State(Tensor(null, zeros, type), Tensor(null, zeros, type))
            }

            fun create(gatesData: GatesData, prevState: State): State {
                val (cellGateBuffer, cellGateType) = createInferredTypeBuffer(gatesData.forgetGate.info.type, gatesData.cellGate.info.type, gatesData.forgetGate.data.strides.linearSize) {
                    add(
                        times(gatesData.forgetGate.data.buffer[it] as Number, prevState.cellGate.data.buffer[it] as Number),
                        times(gatesData.inputGate.data.buffer[it] as Number, gatesData.cellGate.data.buffer[it] as Number)
                    )
                }

                val (outputBuffer, outputType) = createInferredTypeBuffer(gatesData.outputGate.info.type, cellGateType, gatesData.outputGate.data.strides.linearSize) {
                    times(
                        gatesData.outputGate.data.buffer[it] as Number,
                        Tanh.activate(cellGateBuffer[it])
                    )
                }

                val newOutput = Tensor(null, BufferNDStructure(gatesData.outputGate.data.strides, outputBuffer as Buffer<Any>), outputType)
                val newCellGate = Tensor(null, BufferNDStructure(gatesData.forgetGate.data.strides, cellGateBuffer as Buffer<Any>), cellGateType)
                return State(newOutput, newCellGate)
            }
        }
    }

    data class BiasesData(
        val inputGateBiases: Tensor,
        val outputGateBiases: Tensor,
        val forgetGateBiases: Tensor,
        val cellGateBiases: Tensor
    ) {
        companion object {
            fun create(biases: Tensor, hiddenSize: Int, batchSize: Int): Pair<BiasesData, BiasesData> {
                val shape = intArrayOf(batchSize, hiddenSize)
                val blockSize = hiddenSize * batchSize
                val newStrides = TensorStrides(shape)

                @Suppress("UNCHECKED_CAST")
                val parsedBiases = List(8) { index ->
                    val (buffer, _) = createInferredTypeBuffer(biases.info.type, biases.info.type, newStrides.linearSize) { i ->
                        val indices = newStrides.index(i)
                        val colNum = indices[1]
                        biases.data.buffer[hiddenSize * index + colNum]
                    }
                    val newStructure = BufferNDStructure(newStrides, buffer)
                    Tensor(null, newStructure, biases.info.type)
                }
                val weightsBiasesData = BiasesData(parsedBiases[0], parsedBiases[1], parsedBiases[2], parsedBiases[3])
                val recursiveWeightsBiasesData = BiasesData(parsedBiases[4], parsedBiases[5], parsedBiases[6], parsedBiases[7])
                return Pair(weightsBiasesData, recursiveWeightsBiasesData)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun List<Tensor>.toOutput(): Tensor {
        val newShape = intArrayOf(this.size, 1, this.first().data.shape[0], this.first().data.shape[1])
        val newStrides = TensorStrides(newShape)

        val type = this.first().info.type
        val (buffer, _) = createInferredTypeBuffer(type, type, newStrides.linearSize) { i ->
            val indices = newStrides.index(i)
            val (inputNum, _, rowNum, colNum) = indices
            this[inputNum].data[rowNum, colNum]
        }

        val newBuffer = BufferNDStructure(newStrides, buffer)
        return Tensor(null, newBuffer, type)
    }
}
