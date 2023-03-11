@file:GeneratePrimitives(DataType.DOUBLE, DataType.FLOAT)
@file:Suppress("UnusedImport")

package io.kinference.ndarray.extensions.gelu

import io.kinference.ndarray.arrays.MutablePrimitiveNDArray
import io.kinference.ndarray.arrays.PrimitiveNDArray
import io.kinference.ndarray.parallelizeByBlocks
import io.kinference.primitives.types.*
import io.kinference.ndarray.extensions.*
import io.kinference.primitives.annotations.GenerateNameFromPrimitives
import io.kinference.primitives.annotations.GeneratePrimitives
import kotlin.math.*

private val HALF by lazy { (0.5).toPrimitive() }
private val ONE by lazy { (1.0).toPrimitive() }
private val TWO by lazy { (2.0).toPrimitive() }
private val FGELU_COEF_1 by lazy { (0.035677408136300125).toPrimitive() }
private val FGELU_COEF_2 by lazy { (0.7978845608028654).toPrimitive() }

@GenerateNameFromPrimitives
internal suspend fun fastGeluPrimitive(input: PrimitiveNDArray, bias: PrimitiveNDArray?): MutablePrimitiveNDArray {
    val output = MutablePrimitiveNDArray(input.strides)

    val inputBlocks = input.array.blocks
    val outputBlocks = output.array.blocks

    val blockSize = input.array.blockSize

    // TODO: (cupertank) Remove constants
    parallelizeByBlocks(blockSize, inputBlocks.size, 1024) { blockStart, blockEnd ->
        val temporaryBlockExp = PrimitiveArray(blockSize)
        for (blockIdx in blockStart until blockEnd) {
            val outputBlock = outputBlocks[blockIdx]
            val block = inputBlocks[blockIdx]

            if (bias != null) {
                val biasBlocks = bias.array.blocks
                val biasBlock = biasBlocks[blockIdx % biasBlocks.size]
                for (j in outputBlock.indices) {
                    outputBlock[j] = block[j] + biasBlock[j]
                }
            } else {
                for (j in outputBlock.indices) {
                    outputBlock[j] = block[j]
                }
            }

            for (j in temporaryBlockExp.indices) {
                val temp = outputBlock[j]
                temporaryBlockExp[j] = exp(TWO * temp * (FGELU_COEF_1 * temp * temp + FGELU_COEF_2))
            }

            for (j in temporaryBlockExp.indices) {
                temporaryBlockExp[j] = min(temporaryBlockExp[j], PrimitiveType.MAX_VALUE)
            }

            for (j in outputBlock.indices) {
                outputBlock[j] = outputBlock[j] * (HALF + HALF * (temporaryBlockExp[j] - ONE) / (temporaryBlockExp[j] + ONE))
            }
        }
    }

    return output
}
