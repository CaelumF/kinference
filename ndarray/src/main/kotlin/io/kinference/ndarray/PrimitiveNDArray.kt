@file:GenerateWithPrimitives

package io.kinference.ndarray

import io.kinference.ndarray.extensions.*
import io.kinference.primitives.annotations.GenerateWithPrimitives
import io.kinference.primitives.annotations.PrimitiveClass
import io.kinference.primitives.types.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

@PrimitiveClass
class PrimitiveTiledArray(val strides: Strides) {
    companion object {
        const val MIN_BLOCK_SIZE = 1024
    }

    val size: Int = strides.linearSize
    val colSize: Int

    val blockSize: Int
    val blocksNum: Int
    val blocksInRow: Int
    val blocks: Array<PrimitiveArray>

    init {
        when {
            strides.linearSize == 0 -> {
                blocks = emptyArray()
                blockSize = 0
                blocksNum = 0
                blocksInRow = 0
                colSize = 0
            }
            strides.shape.isEmpty() -> {
                blockSize = 1
                blocksNum = 1
                blocksInRow = 1
                colSize = 1
                blocks = Array(1) { PrimitiveArray(1) }
            }
            else -> {
                val rowSize = strides.shape.last()
                blockSize = if (rowSize < MIN_BLOCK_SIZE) rowSize else {
                    var num = rowSize / MIN_BLOCK_SIZE
                    while (rowSize % num != 0) num--
                    rowSize / num
                }

                blocksInRow = rowSize / blockSize
                blocksNum = size / blockSize
                blocks = Array(blocksNum) { PrimitiveArray(blockSize) }
                colSize = strides.shape.getOrElse(strides.shape.lastIndex - 1) { 1 }
            }
        }
    }

    constructor(array: PrimitiveArray, strides: Strides) : this(strides) {
        require(strides.linearSize == array.size)

        if (strides.shape.isEmpty()) {
            blocks[0][0] = array[0]
            return
        }

        var startIndex = 0
        var endIndex = blockSize
        for (block in blocks) {
            array.copyInto(block, 0, startIndex, endIndex)
            startIndex = endIndex
            endIndex += blockSize
        }
    }

    constructor(strides: Strides, init: (Int) -> PrimitiveType) : this(strides) {
        if (strides.shape.isEmpty()) {
            blocks[0][0] = init(0)
            return
        }

        var counter = 0

        for (block in blocks) {
            for (idx in 0 until blockSize) {
                block[idx] = init(counter)
                counter++
            }
        }
    }

    fun toArray(): PrimitiveArray {
        if (size == 0) {
            return PrimitiveArray(0)
        }

        if (strides.shape.isEmpty()) {
            val output = PrimitiveArray(1)
            output[0] = blocks[0][0]
            return output
        }

        val array = PrimitiveArray(strides.linearSize)
        var offset = 0

        for (block in blocks) {
            block.copyInto(array, offset)
            offset += blockSize
        }

        return array
    }

    private fun indexFor(i: Int): Pair<Int, Int> {
        val blockIdx = i / blockSize
        val blockOff = i % blockSize
        return blockIdx to blockOff
    }

    operator fun get(i: Int): PrimitiveType {
        val (blockIdx, blockOff) = indexFor(i)
        return blocks[blockIdx][blockOff]
    }

    operator fun set(i: Int, value: PrimitiveType) {
        val (blockIdx, blockOff) = indexFor(i)
        blocks[blockIdx][blockOff] = value
    }

    fun copyOf(): PrimitiveTiledArray {
        val copyArray = PrimitiveTiledArray(strides)

        for (blockNum in 0 until blocksNum) {
            val thisBlock = this.blocks[blockNum]
            val destBlock = copyArray.blocks[blockNum]

            thisBlock.copyInto(destBlock)
        }

        return copyArray
    }

    fun copyInto(dest: PrimitiveTiledArray, destOffset: Int = 0, srcStart: Int = 0, srcEnd: Int = size) {
        if (srcStart == srcEnd)
            return

        var (leftBlock, leftOffset) = indexFor(srcStart)
        var (rightBlock, rightOffset) = dest.indexFor(destOffset)

        var tempLeftBlock = blocks[leftBlock]
        var tempRightBlock = dest.blocks[rightBlock]

        if (this.blockSize == dest.blockSize && leftOffset == rightOffset) {
            val (endLeftBlock, endLeftOffset) = indexFor(srcEnd)

            if (leftBlock == endLeftBlock) {
                for (idx in leftOffset until endLeftOffset) {
                    tempRightBlock[idx] = tempLeftBlock[idx]
                }
                return
            }

            for (idx in leftOffset until blockSize) {
                tempRightBlock[idx] = tempLeftBlock[idx]
            }
            leftBlock++
            rightBlock++

            for (i in 0 until endLeftBlock - leftBlock) {
                tempLeftBlock = this.blocks[leftBlock]
                tempRightBlock = dest.blocks[rightBlock]

                tempLeftBlock.copyInto(tempRightBlock)
                leftBlock++
                rightBlock++
            }

            if (leftBlock >= blocksNum || rightBlock >= dest.blocksNum)
                return

            tempLeftBlock = this.blocks[leftBlock]
            tempRightBlock = dest.blocks[rightBlock]

            for (idx in 0 until endLeftOffset) {
                tempRightBlock[idx] = tempLeftBlock[idx]
            }

            return
        }

        for (i in srcStart until srcEnd) {
            tempRightBlock[rightOffset++] = tempLeftBlock[leftOffset++]

            if (leftOffset == this.blockSize) {
                leftOffset = 0

                if (++leftBlock < blocksNum)
                    tempLeftBlock = blocks[leftBlock]
            }

            if (rightOffset == dest.blockSize) {
                rightOffset = 0

                if (++rightBlock < dest.blocksNum)
                    tempRightBlock = dest.blocks[rightBlock]
            }
        }
    }

    fun fill(value: PrimitiveType, from: Int, to: Int) {
        for (i in from until to) this[i] = value
    }
}

@PrimitiveClass
@ExperimentalUnsignedTypes
//TODO: var at array to val
open class PrimitiveNDArray(var array: PrimitiveTiledArray, strides: Strides = Strides.empty()) : NumberNDArray {
    constructor(array: PrimitiveArray, strides: Strides = Strides.empty()) : this(PrimitiveTiledArray(array, strides), strides)

    override val type = DataType.UNKNOWN

    final override var strides: Strides = strides
        protected set

    override fun get(index: Int): PrimitiveType = array[index]
    override fun get(indices: IntArray): PrimitiveType = array[strides.offset(indices)]

    override fun allocateNDArray(strides: Strides): MutableNumberNDArray = MutablePrimitiveNDArray(PrimitiveTiledArray(strides), strides)

    override fun reshapeView(newShape: IntArray): NDArray {
        val newStrides = Strides(newShape)

        require(newStrides.linearSize == linearSize)

        return PrimitiveNDArray(array, newStrides)
    }

    override fun toMutable(newStrides: Strides): MutableNumberNDArray = MutablePrimitiveNDArray(array.copyOf(), newStrides)

    override fun map(function: PrimitiveToPrimitiveFunction): MutableNumberNDArray {
        function as PrimitiveMap
        val destination = allocateNDArray(strides) as MutablePrimitiveNDArray

        for (blockNum in 0 until array.blocksNum) {
            val thisBlock = this.array.blocks[blockNum]
            val destBlock = destination.array.blocks[blockNum]

            for (idx in thisBlock.indices) {
                destBlock[idx] = function.apply(thisBlock[idx])
            }
        }
        return destination
    }

    override fun erfFor(value: Any): PrimitiveType {
        value as PrimitiveType
        val sign = value.toDouble().sign
        val doubleValue = abs(value.toDouble())
        val t = 1 / (1 + ERF_P_VALUE * doubleValue)

        val sum = t * (ERF_COEF[0] + t * (ERF_COEF[1] + t * (ERF_COEF[2] + t * (ERF_COEF[3] + t * ERF_COEF[4]))))

        return (sign * (1.0 - sum * exp(-doubleValue * doubleValue))).toPrimitive()
    }

    override fun withZeroPoint(zeroPoint: NumberNDArray): IntNDArray {
        zeroPoint as PrimitiveNDArray

        return if (zeroPoint.linearSize == 1) {
            val zero = zeroPoint.array.blocks[0][0].toInt()
            val arr = IntTiledArray(this.strides)
            for (i in 0 until array.blocksNum) {
                val currentBlock = array.blocks[i]
                for (j in currentBlock.indices) arr.blocks[i][j] = currentBlock[j].toInt() - zero
            }
            IntNDArray(arr, strides)
        } else {
            val blocks = zeroPoint.linearSize
            val blockSize = this.linearSize / blocks
            val arr = IntArray(this.linearSize) { i ->
                this.array[i].toInt() - zeroPoint.array[i % blockSize].toInt()
            }
            IntNDArray(IntTiledArray(arr, strides), strides)
        }
    }

    override fun dequantize(zeroPoint: NDArray?, scale: NDArray, axis: Int?): NDArray {
        scale as FloatNDArray
        val zeros = (zeroPoint as? PrimitiveNDArray)?.array
        val output = MutableFloatNDArray(FloatTiledArray(this.strides), this.strides)

        when {
            canDequantizePerTensor(zeroPoint, scale) -> {
                val zero = zeros?.get(0)?.toFloat() ?: 0f
                for (i in 0 until array.blocksNum) {
                    val currentBlock = array.blocks[i]
                    for (j in currentBlock.indices) output.array.blocks[i][j] = (currentBlock[j].toFloat() - zero) * scale[0]
                }
            }
            canDequantizePerAxis(axis!!, zeroPoint, scale) -> {
                val actualAxis = indexAxis(axis)
                val blockCount = computeBlockSize(toDim = actualAxis)
                val blockSize = computeBlockSize(fromDim = actualAxis + 1)
                var outOffset = 0
                repeat(blockCount) {
                    for (i in 0 until shape[actualAxis]) {
                        val zero = zeros?.get(i)?.toFloat() ?: 0f
                        for (j in 0 until blockSize) output.array[j + outOffset] = (this.array[j + outOffset].toFloat() - zero) * scale[i]
                        outOffset += blockSize
                    }
                }
            }
            else -> error("Cannot perform dequantization. Scale and zero point tensors should be either scalars or 1D tensors containing ${shape[axis]} elements")
        }
        return output
    }

    override fun row(row: Int): MutableNumberNDArray {
        val rowLength: Int = linearSize / shape[0]
        val start = row * rowLength
        val dims = shape.copyOfRange(1, rank)

        return MutablePrimitiveNDArray(PrimitiveTiledArray(Strides(dims)) { array[start + it] }, Strides(dims))
    }

    // TODO check if step == 1 and use Arrays.copy
    override fun appendToLateInitArray(array: LateInitArray, range: IntProgression, additionalOffset: Int) {
        array as LateInitPrimitiveArray

        for (index in range) {
            array.putNext(this.array[additionalOffset + index])
        }
    }

    override fun slice(starts: IntArray, ends: IntArray, steps: IntArray): MutableNumberNDArray {
        val newShape = IntArray(shape.size) {
            val length = abs(ends[it] - starts[it])
            val rest = length % abs(steps[it])
            (length / abs(steps[it])) + if (rest != 0) 1 else 0
        }

        val newStrides = Strides(newShape)
        val newArray = LateInitPrimitiveArray(newStrides.linearSize)

        slice(newArray, 0, 0, shape, starts, ends, steps)

        return MutablePrimitiveNDArray(PrimitiveTiledArray(newArray.getArray(), newStrides), newStrides)
    }

    override fun min(): PrimitiveType {
        var min = PrimitiveType.MAX_VALUE
        for (block in array.blocks) {
            for (idx in block.indices) {
                val tmp = block[idx]
                if (tmp < min) min = tmp
            }
        }
        return min
    }

    override fun max(): PrimitiveType {
        var max = PrimitiveType.MIN_VALUE
        for (block in array.blocks) {
            for (idx in block.indices) {
                val tmp = block[idx]
                if (tmp > max) max = tmp
            }
        }

        return max
    }

    override fun sum(): PrimitiveType {
        var sum = (0).toPrimitive()

        for (block in array.blocks) {
            for (idx in block.indices) {
                sum = (sum + block[idx]).toPrimitive()
            }
        }
        return sum
    }

    override fun cumulativeSum(axis: Int, exclusive: Boolean, reverse: Boolean): MutableNumberNDArray {
        val output = MutablePrimitiveNDArray(PrimitiveTiledArray(strides), strides)

        val actualAxis = indexAxis(axis)

        val blockSize = computeBlockSize(fromDim = actualAxis + 1)
        val batchSize = computeBlockSize(fromDim = actualAxis)
        val numBatches = computeBlockSize(toDim = actualAxis)
        val numBlocks = batchSize / blockSize
        repeat(numBatches) { batchIdx ->
            val dstOff = if (!reverse) batchIdx * batchSize else (numBatches - batchIdx) * batchSize - 1
            if (!exclusive) {
                if (!reverse)
                    output.copyFrom(dstOff, this, dstOff, dstOff + blockSize)
                else
                    output.copyFrom(dstOff - blockSize + 1, this, dstOff - blockSize + 1, dstOff + 1)
            }

            if (!reverse) {
                for (i in 1 until numBlocks) {
                    for (j in 0 until blockSize) {
                        val currentOff = dstOff + i * blockSize + j
                        val thisOff = if (!exclusive) currentOff else currentOff - blockSize
                        output.array[currentOff] = (output.array[currentOff - blockSize] + array[thisOff]).toPrimitive()
                    }
                }
            } else {
                for (i in 1 until numBlocks) {
                    for (j in blockSize - 1 downTo 0) {
                        val currentOff = dstOff - i * blockSize - j
                        val thisOff = if (!exclusive) currentOff else currentOff + blockSize
                        output.array[currentOff] = (output.array[currentOff + blockSize] + array[thisOff]).toPrimitive()
                    }
                }
            }
        }

        return output
    }

    override fun plus(other: NumberNDArray): MutableNumberNDArray = plus(other, MutablePrimitiveNDArray(PrimitiveTiledArray(strides), strides))

    private fun plusScalar(array: PrimitiveTiledArray, scalar: PrimitiveType, destination: PrimitiveTiledArray) {
        require(array.strides.shape.contentEquals(destination.strides.shape))

        for (blockNum in 0 until array.blocksNum) {
            val arrayBlock = array.blocks[blockNum]
            val destBlock = destination.blocks[blockNum]

            for (idx in arrayBlock.indices) {
                destBlock[idx] = (arrayBlock[idx] + scalar).toPrimitive()
            }
        }
    }

    override fun plus(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArray {
        require(other is PrimitiveNDArray && destination is MutablePrimitiveNDArray) { "Operands must have the same types" }

        when {
            this.isScalar() && other.isScalar() -> destination.array.blocks[0][0] = (array.blocks[0][0] + other.array.blocks[0][0]).toPrimitive()
            this.isScalar() -> plusScalar(other.array, this.array.blocks[0][0], destination.array)
            other.isScalar() -> plusScalar(this.array, other.array.blocks[0][0], destination.array)
            else -> this.applyWithBroadcast(other, destination, false) { left, right, dest ->
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray
                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] + rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }

        return destination
    }

    override fun minus(other: NumberNDArray): MutableNumberNDArray = minus(other, MutablePrimitiveNDArray(PrimitiveTiledArray(strides), strides))

    override fun minus(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArray {
        require(other is PrimitiveNDArray && destination is MutablePrimitiveNDArray) { "Operands must have the same types" }

        when {
            this.isScalar() && other.isScalar() -> destination.array.blocks[0][0] = (this.array.blocks[0][0] - other.array.blocks[0][0]).toPrimitive()
            other.isScalar() -> {
                require(shape.contentEquals(destination.shape))

                val scalar = other.array.blocks[0][0]

                for (blockNum in 0 until array.blocksNum) {
                    val leftBlock = this.array.blocks[blockNum]
                    val destBlock = destination.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] - scalar).toPrimitive()
                    }
                }
            }
            this.isScalar() -> error("Subtraction of a matrix from a scalar is prohibited")
            else -> this.applyWithBroadcast(other, destination, true) { left, right, dest ->
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray

                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] - rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }

        return destination
    }

    override fun times(other: NumberNDArray): MutableNumberNDArray = times(other, MutablePrimitiveNDArray(PrimitiveTiledArray(strides), strides))

    private fun timesScalar(array: PrimitiveTiledArray, scalar: PrimitiveType, destination: PrimitiveTiledArray) {
        require(array.strides.shape.contentEquals(destination.strides.shape))

        for (blockNum in 0 until array.blocksNum) {
            val arrayBlock = array.blocks[blockNum]
            val destBlock = destination.blocks[blockNum]

            for (idx in arrayBlock.indices) {
                destBlock[idx] = (arrayBlock[idx] * scalar).toPrimitive()
            }
        }
    }

    override fun times(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArray {
        require(other is PrimitiveNDArray && destination is MutablePrimitiveNDArray) { "Operands must have the same types" }

        when {
            this.isScalar() && other.isScalar() -> destination.array.blocks[0][0] = (this.array.blocks[0][0] * other.array.blocks[0][0]).toPrimitive()
            this.isScalar() -> timesScalar(other.array, this.array.blocks[0][0], destination.array)
            other.isScalar() -> timesScalar(this.array, other.array.blocks[0][0], destination.array)
            else -> this.applyWithBroadcast(other, destination, false) { left, right, dest ->
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray

                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] * rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }

        return destination
    }

    override fun div(other: NumberNDArray): MutableNumberNDArray = div(other, MutablePrimitiveNDArray(PrimitiveTiledArray(strides), strides))

    override fun div(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArray {
        require(other is PrimitiveNDArray && destination is MutablePrimitiveNDArray) { "Operands must have the same types" }

        when {
            this.isScalar() && other.isScalar() -> destination.array.blocks[0][0] = (this.array.blocks[0][0] / other.array.blocks[0][0]).toPrimitive()
            other.isScalar() -> {
                require(shape.contentEquals(destination.shape))

                val scalar = other.array.blocks[0][0]

                for (blockNum in 0 until array.blocksNum) {
                    val leftBlock = this.array.blocks[blockNum]
                    val destBlock = destination.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] / scalar).toPrimitive()
                    }
                }
            }
            this.isScalar() -> error("Division of a scalar into a matrix is prohibited")
            else -> this.applyWithBroadcast(other, destination, true) { left, right, dest ->
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray

                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] / rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }

        return destination
    }

    private fun resortBlocks(blocks: Array<PrimitiveArray>, colSize: Int, blocksInRow: Int): Array<PrimitiveArray> {
        val result = blocks.copyOf()

        for (i in 0 until blocksInRow) {
            for (j in 0 until colSize) {
                result[i * colSize + j] = blocks[j * blocksInRow + i]
            }
        }

        return result
    }

    override fun dot(other: NumberNDArray, destination: MutableNumberNDArray): MutableNumberNDArray {
        other as PrimitiveNDArray; destination as MutablePrimitiveNDArray
        require(shape.size == 2 && other.shape.size == 2)
        require(shape[1] == other.shape[0])

        val n = this.shape[0]
        val m = other.shape[1]
        val t = this.shape[1]

        val resortedLeft = resortBlocks(this.array.blocks, this.array.colSize, this.array.blocksInRow)
        val resortedRight = resortBlocks(other.array.blocks, other.array.colSize, other.array.blocksInRow)
        val resortedDest = resortBlocks(destination.array.blocks, destination.array.colSize, destination.array.blocksInRow)

        val rdBlockSize = destination.array.blockSize
        for (rdCol in 0 until other.array.blocksInRow) {
            val rightIdx = rdCol * t
            val destIdx = rdCol * n

            for (i in 0 until n) {
                val destBlock = resortedDest[destIdx + i]

                for (lCol in 0 until this.array.blocksInRow) {
                    val leftBlock = resortedLeft[i + lCol * n]
                    val rightIdxOffset = rightIdx + this.array.blockSize * lCol

                    for (k in 0 until this.array.blockSize) {
                        val temp = leftBlock[k]
                        val rightBlock = resortedRight[rightIdxOffset + k]

                        for (j in 0 until rdBlockSize) {
                            destBlock[j] = (destBlock[j] + temp * rightBlock[j]).toPrimitive()
                        }
                    }
                }
            }
        }

        return destination
    }

    override fun gemm(m: Int, n: Int, k: Int, alpha: Double, lda: Int, b: NDArray, ldb: Int, beta: Double, c: MutableNDArray, ldc: Int, aOffset: Int, bOffset: Int, cOffset: Int, transposeA: Boolean, transposeB: Boolean): MutableNDArray {
        b as PrimitiveNDArray; c as MutablePrimitiveNDArray
        val betaPrimitive = beta.toPrimitive()

        if (beta != 1.0) {
            for (i in 0 until m) {
                val cIdx = i * ldc + cOffset
                for (j in 0 until n) {
                    c.array[cIdx + j] = (betaPrimitive * c.array[cIdx + j]).toPrimitive()
                }
            }
        }

        val alphaPrimitive = alpha.toPrimitive()
        when {
            transposeA && transposeB -> {
                for (t in 0 until m) {
                    for (j in 0 until n) {
                        val cIdx = t * ldc + j + cOffset
                        for (i in 0 until k) {
                            val aIdx = i * lda + t + aOffset
                            val bIdx = j * ldb + i + bOffset
                            c.array[cIdx] = (alphaPrimitive * array[aIdx] * b.array[bIdx] + c.array[cIdx]).toPrimitive()
                        }
                    }
                }
            }
            transposeA -> {
                for (t in 0 until m) {
                    for (j in 0 until n) {
                        val cIdx = t * ldc + j + cOffset
                        for (i in 0 until k) {
                            val aIdx = i * lda + t + aOffset
                            val bIdx = i * ldb + j + bOffset
                            c.array[cIdx] = (alphaPrimitive * array[aIdx] * b.array[bIdx] + c.array[cIdx]).toPrimitive()
                        }
                    }
                }
            }
            transposeB -> {
                for (t in 0 until m) {
                    val aIdx = t * lda + aOffset
                    for (j in 0 until n) {
                        val cIdx = t * ldc + j + cOffset
                        val bIdx = j * ldb + bOffset
                        for (i in 0 until k) {
                            c.array[cIdx] = (alphaPrimitive * array[aIdx + i] * b.array[bIdx + i] + c.array[cIdx]).toPrimitive()
                        }
                    }
                }
            }
            else -> {
                for (t in 0 until m) {
                    val cIdx = t * ldc + cOffset
                    val aIdx = t * lda + aOffset
                    for (i in 0 until k) {
                        val temp = (alphaPrimitive * array[aIdx + i]).toPrimitive()
                        val bIdx = i * ldb + bOffset
                        for (j in 0 until n) {
                            c.array[cIdx + j] = (temp * b.array[bIdx + j] + c.array[cIdx + j]).toPrimitive()
                        }
                    }
                }
            }
        }

        return c
    }

    override fun copyIfNotMutable(): MutableNDArray {
        return MutablePrimitiveNDArray(array.copyOf(), strides)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrimitiveNDArray) return false

        if (type != other.type) return false
        if (strides != other.strides) return false
        if (array != other.array) return false

        return true
    }

    override fun hashCode(): Int {
        var result = array.hashCode()
        result = 31 * result + strides.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

@PrimitiveClass
@ExperimentalUnsignedTypes
open class MutablePrimitiveNDArray(array: PrimitiveTiledArray, strides: Strides = Strides.empty()) : PrimitiveNDArray(array, strides), MutableNumberNDArray {
    constructor(array: PrimitiveArray, strides: Strides = Strides.empty()) : this(PrimitiveTiledArray(array, strides), strides)

    override fun set(index: Int, value: Any) {
        array[index] = value as PrimitiveType
    }

    override fun copyIfNotMutable(): MutableNDArray {
        return MutablePrimitiveNDArray(array, strides)
    }

    override fun fill(value: Any, from: Int, to: Int) {
        value as PrimitiveType
        array.fill(value, from, to)
    }

    override fun mapMutable(function: PrimitiveToPrimitiveFunction): MutableNumberNDArray {
        function as PrimitiveMap

        for (block in array.blocks) {
            for (idx in block.indices) {
                block[idx] = function.apply(block[idx])
            }
        }

        return this
    }

    override fun erf(): MutableNumberNDArray {
        return this.mapMutable(object : PrimitiveMap {
            override fun apply(value: PrimitiveType): PrimitiveType = erfFor(value)
        })
    }

    override operator fun plusAssign(other: NDArray) {
        other as PrimitiveNDArray

        when {
            this.isScalar() && other.isScalar() -> this.array.blocks[0][0] = (this.array.blocks[0][0] + other.array.blocks[0][0]).toPrimitive()
            other.isScalar() -> {
                val scalar = other.array.blocks[0][0]
                for (block in this.array.blocks) {
                    for (idx in block.indices) {
                        block[idx] = (block[idx] + scalar).toPrimitive()
                    }
                }
            }
            this.isScalar() -> error("Plus assign of a scalar into a matrix is prohibited")
            else -> this.applyWithBroadcast(other, this, true) { left, right, dest ->
                // TODO change to real plusAssign
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray

                /*val leftArray = left.array.toArray()
                val rightArray = right.array.toArray()
                val destArray = dest.array.toArray()

                for (index in 0 until left.linearSize) {
                    destArray[index] = (leftArray[index] + rightArray[index]).toPrimitive()
                }

                dest.array = PrimitiveTiledArray(destArray, dest.strides)*/

                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] + rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }
    }

    override operator fun minusAssign(other: NDArray) {
        other as PrimitiveNDArray

        when {
            this.isScalar() && other.isScalar() -> this.array.blocks[0][0] = (this.array.blocks[0][0] - other.array.blocks[0][0]).toPrimitive()
            other.isScalar() -> {
                val scalar = other.array.blocks[0][0]
                for (block in this.array.blocks) {
                    for (idx in block.indices) {
                        block[idx] = (block[idx] - scalar).toPrimitive()
                    }
                }
            }
            this.isScalar() -> error("Minus assign of a scalar into a matrix is prohibited")
            else -> this.applyWithBroadcast(other, this, true) { left, right, dest ->
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray

                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] - rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }
    }

    override operator fun timesAssign(other: NDArray) {
        other as PrimitiveNDArray

        when {
            this.isScalar() && other.isScalar() -> this.array.blocks[0][0] = (this.array.blocks[0][0] * other.array.blocks[0][0]).toPrimitive()
            other.isScalar() -> {
                val scalar = other.array.blocks[0][0]
                for (block in this.array.blocks) {
                    for (idx in block.indices) {
                        block[idx] = (block[idx] * scalar).toPrimitive()
                    }
                }
            }
            this.isScalar() -> error("Times assign of a scalar into a matrix is prohibited")
            else -> this.applyWithBroadcast(other, this, true) { left, right, dest ->
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray

                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] * rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }
    }

    override operator fun divAssign(other: NDArray) {
        other as PrimitiveNDArray

        when {
            this.isScalar() && other.isScalar() -> this.array.blocks[0][0] = (this.array.blocks[0][0] / other.array.blocks[0][0]).toPrimitive()
            other.isScalar() -> {
                val scalar = other.array.blocks[0][0]
                for (block in this.array.blocks) {
                    for (idx in block.indices) {
                        block[idx] = (block[idx] / scalar).toPrimitive()
                    }
                }
            }
            this.isScalar() -> error("Div assign of a scalar into a matrix is prohibited")
            else -> this.applyWithBroadcast(other, this, true) { left, right, dest ->
                left as PrimitiveNDArray; right as PrimitiveNDArray; dest as MutablePrimitiveNDArray

                for (blockNum in 0 until left.array.blocksNum) {
                    val leftBlock = left.array.blocks[blockNum]
                    val rightBlock = right.array.blocks[blockNum]
                    val destBlock = dest.array.blocks[blockNum]

                    for (idx in leftBlock.indices) {
                        destBlock[idx] = (leftBlock[idx] / rightBlock[idx]).toPrimitive()
                    }
                }
            }
        }
    }

    override fun copyFrom(offset: Int, other: NDArray, startInOther: Int, endInOther: Int) {
        other as PrimitiveNDArray
        other.array.copyInto(this.array, offset, startInOther, endInOther)
    }

    override fun reshape(strides: Strides): MutableNumberNDArray {
        if (strides.shape.isNotEmpty() && this.shape.isNotEmpty() && strides.shape.last() != this.shape.last()) {
            val newArray = PrimitiveTiledArray(strides)

            this.array.copyInto(newArray)
            this.array = newArray
        }

        this.strides = strides
        return this
    }

    // TODO separate from PrimitiveArray (maybe LateInitArray will help)
    private fun transposeRec(prevArray: PrimitiveArray, newArray: PrimitiveArray, prevStrides: Strides, newStrides: Strides, index: Int, prevOffset: Int, newOffset: Int, permutation: IntArray) {
        if (index != newStrides.shape.lastIndex) {
            val temp = prevStrides.strides[permutation[index]]
            val temp2 = newStrides.strides[index]
            for (i in 0 until newStrides.shape[index])
                transposeRec(prevArray, newArray, prevStrides, newStrides, index + 1, prevOffset + temp * i,
                    newOffset + temp2 * i, permutation)
        } else {
            val temp = prevStrides.strides[permutation[index]]
            if (temp == 1) {
                prevArray.copyInto(newArray, newOffset, prevOffset, prevOffset + newStrides.shape[index])
            } else {
                for (i in 0 until newStrides.shape[index]) {
                    newArray[newOffset + i] = prevArray[prevOffset + i * temp]
                }
            }
        }
    }

    override fun transpose(permutations: IntArray): MutableNumberNDArray {
        val newStrides = strides.transpose(permutations)

        val tempArray = array.toArray()

        transposeRec(tempArray.copyOf(), tempArray, strides, newStrides, 0, 0, 0, permutations)

        this.array = PrimitiveTiledArray(tempArray, newStrides)

        return this.reshape(newStrides)
    }

    override fun transpose2D(): MutableNDArray {
        require(rank == 2)

        val newShape = shape.reversedArray()
        val newStrides = Strides(newShape)

        val thisArray = this.array.toArray()

        val tmp = thisArray.copyOf()
        for (j in (0 until shape[1])) {
            val ind = j * shape[0]
            for (i in (0 until shape[0])) {
                thisArray[ind + i] = (tmp[i * shape[1] + j]).toPrimitive()
            }
        }

        this.array = PrimitiveTiledArray(thisArray, newStrides)

        return this.reshape(newStrides)
    }

    override fun clean() {
        for (block in array.blocks) {
            block.fill((0).toPrimitive())
        }
    }
}

@PrimitiveClass
class LateInitPrimitiveArray(size: Int) : LateInitArray {
    private val array = PrimitiveArray(size)
    private var index = 0

    fun putNext(value: PrimitiveType) {
        array[index] = value
        index++
    }

    fun getArray(): PrimitiveArray {
        require(index == array.size) { "LateInitArray not initialized yet" }
        return array
    }
}

@PrimitiveClass
interface PrimitiveMap : PrimitiveToPrimitiveFunction {
    fun apply(value: PrimitiveType): PrimitiveType
}
