package io.kinference.tfjs.operators.layer.recurrent.lstm

import io.kinference.ndarray.arrays.*
import io.kinference.tfjs.operators.layer.recurrent.LayerDirection

abstract class LSTMLayerBase(val hiddenSize: Int, val activations: List<String>, val direction: LayerDirection) {

    abstract suspend fun apply(
        input: NumberNDArrayTFJS,
        weights: NumberNDArrayTFJS,
        recurrentWeights: NumberNDArrayTFJS,
        bias: NumberNDArrayTFJS?,
        sequenceLens: NumberNDArrayTFJS?,
        initialHiddenState: NumberNDArrayTFJS?,
        initialCellState: NumberNDArrayTFJS?,
        peepholes: NumberNDArrayTFJS?,
    ): LSTMLayerOutput

    companion object {
        fun create(hiddenSize: Int, activations: List<String>, direction: LayerDirection) =
            when(direction) {
                LayerDirection.FORWARD, LayerDirection.REVERSE -> LSTMLayer(hiddenSize, activations, direction)
                LayerDirection.BIDIRECTIONAL -> BiLSTMLayer(hiddenSize, activations)
            }
    }
}
