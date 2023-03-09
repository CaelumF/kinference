@file:GeneratePrimitives(DataType.DOUBLE, DataType.FLOAT)
package io.kinference.ndarray.extensions.biasGelu

import io.kinference.ndarray.*
import io.kinference.ndarray.arrays.*
import io.kinference.ndarray.extensions.*
import io.kinference.primitives.annotations.GenerateNameFromPrimitives
import io.kinference.primitives.annotations.GeneratePrimitives
import io.kinference.primitives.types.*
import kotlin.math.*

private val SQRT_2 = sqrt(2.0).toPrimitive()
private val HALF = (0.5).toPrimitive()
private val ONE = (1.0).toPrimitive()
private val ERF_P_VALUE = (0.3275911).toPrimitive()
private val ERF_COEF_1 = (0.254829592).toPrimitive()
private val ERF_COEF_2 = (-0.284496736).toPrimitive()
private val ERF_COEF_3 = (1.421413741).toPrimitive()
private val ERF_COEF_4 = (-1.453152027).toPrimitive()
private val ERF_COEF_5 = (1.061405429).toPrimitive()

@GenerateNameFromPrimitives
internal suspend fun computeGeluPrimitive(input: PrimitiveNDArray, bias: PrimitiveNDArray): MutablePrimitiveNDArray {
    val output = MutablePrimitiveNDArray(input.strides)

    val inputBlocks = input.array.blocks
    val biasBlocks = bias.array.blocks
    val outputBlocks = output.array.blocks

    val blockSize = input.array.blockSize

    parallelizeByBlocks(blockSize, inputBlocks.size, 1024) { blockStart, blockEnd ->
        val temporaryBlock = PrimitiveArray(blockSize)
        val temporaryBlockAbs = PrimitiveArray(blockSize)
        for (blockIdx in blockStart until blockEnd) {
            val outputBlock = outputBlocks[blockIdx]
            val block = inputBlocks[blockIdx]
            val biasBlock = biasBlocks[blockIdx % biasBlocks.size]

            for (j in temporaryBlock.indices) {
                temporaryBlock[j] = block[j] + biasBlock[j]
            }

            for (j in temporaryBlockAbs.indices) {
                temporaryBlockAbs[j] = temporaryBlock[j] / SQRT_2
            }

            for (j in temporaryBlock.indices) {
                temporaryBlock[j] = temporaryBlock[j] * HALF
            }

            for (j in temporaryBlockAbs.indices) {
                temporaryBlockAbs[j] = temporaryBlockAbs[j].absoluteValue
            }

            for (j in outputBlock.indices) {
                outputBlock[j] = ONE / (temporaryBlockAbs[j] * ERF_P_VALUE + ONE)
            }

            for (j in temporaryBlockAbs.indices) {
                temporaryBlockAbs[j] = exp(-(temporaryBlockAbs[j].pow(2)))
            }

            for (j in outputBlock.indices) {
                outputBlock[j] = outputBlock[j] * (ERF_COEF_1 + outputBlock[j] * (ERF_COEF_2 + outputBlock[j] * (ERF_COEF_3 + outputBlock[j] * (ERF_COEF_4 + outputBlock[j] * ERF_COEF_5))))
            }

            for (j in outputBlock.indices) {
                outputBlock[j] = (ONE - temporaryBlockAbs[j] * outputBlock[j]).withSign(temporaryBlock[j].sign)
            }

            for (j in outputBlock.indices) {
                outputBlock[j] = (ONE + outputBlock[j]) * temporaryBlock[j]
            }
        }
    }

    return output
}
