@file:GenerateWithPrimitives

package io.kinference.ndarray

import io.kinference.ndarray.extensions.*
import io.kinference.primitives.annotations.GenerateWithPrimitives
import io.kinference.primitives.annotations.PrimitiveClass
import io.kinference.primitives.types.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import kotlin.math.*

@PrimitiveClass
class PrimitiveTiledArray {
    val size: Int
    val blockSize: Int
    val blocksNum: Int
    val blocks: Array<PrimitiveArray>

    companion object {
        const val MIN_BLOCK_SIZE = 64
        val logger: Logger = LoggerFactory.getLogger(PrimitiveTiledArray::class.java)

        operator fun invoke(strides: Strides): PrimitiveTiledArray {
            return when {
                strides.linearSize == 0 -> PrimitiveTiledArray(0, 0)
                strides.shape.isEmpty() -> PrimitiveTiledArray(1, 1)
                else -> {
                    val rowSize = strides.shape.last()
                    val blockSize = if (rowSize < MIN_BLOCK_SIZE) rowSize else {
                        var num = rowSize / MIN_BLOCK_SIZE
                        while (rowSize % num != 0) num--
                        rowSize / num
                    }

                    PrimitiveTiledArray(strides.linearSize, blockSize)
                }
            }
        }

        operator fun invoke(array: PrimitiveArray, strides: Strides): PrimitiveTiledArray {
            require(strides.linearSize == array.size)

            val tiledArray = PrimitiveTiledArray(strides)

            var startIndex = 0
            var endIndex = tiledArray.blockSize
            for (block in tiledArray.blocks) {
                array.copyInto(block, 0, startIndex, endIndex)
                startIndex = endIndex
                endIndex += tiledArray.blockSize
            }

            return tiledArray
        }

        operator fun invoke(strides: Strides, init: (Int) -> PrimitiveType): PrimitiveTiledArray {
            val tiledArray = PrimitiveTiledArray(strides)

            var count = 0
            for (block in tiledArray.blocks) {
                for (idx in 0 until tiledArray.blockSize) {
                    block[idx] = init(count++)
                }
            }

            return tiledArray
        }

        operator fun invoke(shape: IntArray) = invoke(Strides(shape))

        operator fun invoke(array: PrimitiveArray, shape: IntArray) = invoke(array, Strides(shape))

        operator fun invoke(shape: IntArray, init: (Int) -> PrimitiveType) = invoke(Strides(shape), init)
    }

    data class BlockWithOffset(val block: PrimitiveArray, val offset: Int)
    abstract class BaseIterator {
        val array: PrimitiveTiledArray

        protected var blockNum: Int
        protected var indexInBlock: Int

        protected var currentBlock: PrimitiveArray
        protected var isInitialized: Boolean = false

        constructor(array: PrimitiveTiledArray, startIndex: Int) {
            require(startIndex >= 0 && startIndex < array.size) { "Start index of Iterator must be >= 0 and < array size" }
            this.array = array
            this.blockNum = startIndex / array.blockSize
            this.indexInBlock = startIndex % array.blockSize
            this.currentBlock = array.blocks[blockNum]
        }

        constructor(other: BaseIterator) {
            this.array = other.array
            this.blockNum = other.blockNum
            this.indexInBlock = other.indexInBlock
            this.currentBlock = other.currentBlock
        }

        var linearIndex: Int
            get() = blockNum * array.blockSize + indexInBlock
            set(value) {
                require(value >= 0 && value < array.size) { "Linear index of Iterator must be >= 0 and < array size" }
                this.isInitialized = false
                this.blockNum = value / array.blockSize
                this.indexInBlock = value % array.blockSize
                this.currentBlock = array.blocks[blockNum]
            }
    }

    class Iterator: BaseIterator {
        constructor(array: PrimitiveTiledArray, startIndex: Int = 0): super(array, startIndex)
        constructor(other: BaseIterator): super(other)

        fun set(value: PrimitiveType) {
            require(isInitialized) { "Iterator not initialized" }
            currentBlock[indexInBlock] = value
        }

        fun get(): PrimitiveType {
            require(isInitialized) { "Iterator not initialized" }
            return currentBlock[indexInBlock]
        }

        fun block(): BlockWithOffset {
            require(isInitialized) { "Iterator not initialized" }
            return BlockWithOffset(currentBlock, indexInBlock)
        }

        fun next(): PrimitiveType {
            when {
                !isInitialized -> isInitialized = true
                indexInBlock < array.blockSize - 1 -> indexInBlock++
                blockNum < array.blocksNum - 1 -> {
                    blockNum++
                    indexInBlock = 0
                    currentBlock = array.blocks[blockNum]
                }
                else -> throw IllegalStateException("No more elements in array")
            }

            return currentBlock[indexInBlock]
        }

        fun hasNext(): Boolean = blockNum < array.blocksNum - 1 || indexInBlock < array.blockSize - 1
    }

    class BlockIterator: BaseIterator {
        constructor(array: PrimitiveTiledArray, startIndex: Int = 0): super(array, startIndex)
        constructor(other: BaseIterator): super(other)

        fun get(): BlockWithOffset {
            require(isInitialized) { "Iterator not initialized" }
            return BlockWithOffset(currentBlock, indexInBlock)
        }

        fun next(): BlockWithOffset {
            when {
                !isInitialized -> isInitialized = true
                blockNum < array.blocksNum - 1 -> {
                    blockNum++
                    indexInBlock = 0
                    currentBlock = array.blocks[blockNum]
                }
                else -> throw IllegalStateException("No more blocks in array")
            }

            return BlockWithOffset(currentBlock, indexInBlock)
        }

        fun hasNext(): Boolean = blockNum < array.blocksNum - 1

        fun isCompatibleWith(other: BlockIterator, requestedSize: Int): Boolean {
            return this.indexInBlock == other.indexInBlock &&
                this.array.blockSize == other.array.blockSize &&
                this.array.size - this.linearIndex >= requestedSize &&
                other.array.size - other.linearIndex >= requestedSize
        }

        inline fun accept(other: BlockIterator, count: Int, action: (dst: PrimitiveType, src: PrimitiveType) -> PrimitiveType) {
            var end = count
            if (this.isCompatibleWith(other, count)) {
                while (end > 0) {
                    val dst = this.next()
                    val src = other.next()

                    for (index in dst.offset until min(dst.block.size, dst.offset + end)) {
                        dst.block[index] = action(dst.block[index], src.block[index])
                    }

                    end -= dst.block.size
                }
            } else {
                logger.warn("BlockIterators not compatible: rollback to Iterator")
                val dstIt = Iterator(this)
                val srcIt = Iterator(other)
                while (end > 0) {
                    dstIt.set(action(dstIt.next(), srcIt.next()))
                    end--
                }
            }
        }

        inline fun combine(other: BlockIterator, count: Int, action: (first: PrimitiveType, second: PrimitiveType) -> Unit) {
            var end = count
            if (this.isCompatibleWith(other, count)) {
                while (end > 0) {
                    val fst = this.next()
                    val snd = other.next()

                    for (index in fst.offset until min(fst.block.size, fst.offset + end)) {
                        action(fst.block[index], snd.block[index])
                    }

                    end -= fst.block.size
                }
            } else {
                logger.warn("BlockIterators not compatible: rollback to Iterator")
                val fstIt = Iterator(this)
                val sndIt = Iterator(other)
                while (end > 0) {
                    action(fstIt.next(), sndIt.next())
                    end--
                }
            }
        }
    }

    constructor(size: Int, blockSize: Int) {
        if (blockSize != 0)
            require(size % blockSize == 0) { "Size must divide blockSize" }

        this.blocksNum = if (blockSize == 0) 0 else size / blockSize
        this.blocks = Array(blocksNum) { PrimitiveArray(blockSize) }
        this.blockSize = blockSize
        this.size = size
    }

    constructor(blocks: Array<PrimitiveArray>) {
        this.blocks = blocks
        this.blockSize = if (blocks.isEmpty()) 0 else blocks.first().size
        this.blocksNum = blocks.size
        this.size = this.blocksNum * this.blockSize
    }

    constructor(size: Int, blockSize: Int, init: (Int) -> PrimitiveType) : this(size, blockSize) {
        var count = 0
        for (block in blocks) {
            for (idx in 0 until blockSize) {
                block[idx] = init(count++)
            }
        }
    }

    fun iterator(startIndex: Int = 0) = Iterator(this, startIndex)
    fun blockIterator(startIndex: Int = 0) = BlockIterator(this, startIndex)

    fun toArray(): PrimitiveArray {
        if (size == 0) {
            return PrimitiveArray(0)
        }

        val array = PrimitiveArray(size)
        var offset = 0

        for (block in blocks) {
            block.copyInto(array, offset)
            offset += blockSize
        }

        return array
    }

    fun indexFor(i: Int): Pair<Int, Int> {
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
        val copyArray = PrimitiveTiledArray(size, blockSize)

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

        val thisIter = BlockIterator(this, srcStart)
        val destIter = BlockIterator(dest, destOffset)

        destIter.accept(thisIter, srcEnd - srcStart) { dst, src -> src }
    }

    fun copyOfRange(fromIndex: Int, toIndex: Int): PrimitiveArray {
        val array = PrimitiveArray(toIndex - fromIndex)
        val thisIterator = Iterator(this, fromIndex)

        for (i in array.indices) {
            array[i] = thisIterator.next()
        }

        return array
    }

    fun fill(value: PrimitiveType, from: Int = 0, to: Int = size) {
        if (from == to)
            return

        val blockIterator = BlockIterator(this, from)

        var count = to - from

        while (count > 0) {
            val blockWithOffset = blockIterator.next()
            blockWithOffset.block.fill(value, blockWithOffset.offset, min(blockSize, count))

            count -= blockSize
        }
    }
}

@PrimitiveClass
@ExperimentalUnsignedTypes
//TODO: var at array to val
open class PrimitiveNDArray(var array: PrimitiveTiledArray, strides: Strides = Strides.empty()) : NumberNDArray {
    constructor(array: PrimitiveArray, strides: Strides = Strides.empty()) : this(PrimitiveTiledArray(array, strides), strides)

    protected val blocksInRow: Int
        get() = when {
            strides.linearSize == 0 -> 0
            strides.shape.isEmpty() -> 1
            else -> strides.shape.last() / array.blockSize
        }

    override fun view(vararg axes: Int): PrimitiveNDArray {
        val offset = axes.foldIndexed(0) { index, acc, i -> acc + i * strides.strides[index] }
        val offsetBlocks = offset / array.blockSize

        val newShape = shape.copyOfRange(axes.size, shape.size)
        val newStrides = Strides(newShape)

        val countBlocks = newStrides.linearSize / array.blockSize

        val copyBlocks = array.blocks.copyOfRange(offsetBlocks, offsetBlocks + countBlocks)
        val newArray = PrimitiveTiledArray(copyBlocks)

        return PrimitiveNDArray(newArray, newStrides)
    }

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

    override fun copyOfRange(start: Int, end: Int): PrimitiveArray {
        return array.copyOfRange(start, end)
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
        require(array.blocksNum == destination.blocksNum && array.blockSize == destination.blockSize)

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
        require(array.blocksNum == destination.blocksNum && array.blockSize == destination.blockSize)

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

        val resortedLeft = resortBlocks(this.array.blocks, this.shape[0], this.blocksInRow)
        val resortedRight = resortBlocks(other.array.blocks, other.shape[0], other.blocksInRow)
        val resortedDest = resortBlocks(destination.array.blocks, destination.shape[0], destination.blocksInRow)

        val rdBlockSize = destination.array.blockSize
        for (rdCol in 0 until other.blocksInRow) {
            val rightIdx = rdCol * t
            val destIdx = rdCol * n

            for (i in 0 until n) {
                val destBlock = resortedDest[destIdx + i]

                for (lCol in 0 until this.blocksInRow) {
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
                    val cIt = c.array.iterator(t * ldc + cOffset)
                    for (j in 0 until n) {
                        val bIdx = j * ldb + bOffset
                        val aIt = array.blockIterator(aIdx)
                        val bIt = b.array.blockIterator(bIdx)

                        cIt.next()
                        aIt.combine(bIt, k) { elementInA, elementInB ->
                            cIt.set((alphaPrimitive * elementInA * elementInB + cIt.get()).toPrimitive())
                        }
                    }
                }
            }
            else -> {
                for (t in 0 until m) {
                    val cIdx = t * ldc + cOffset
                    val aIdx = t * lda + aOffset
                    val aIt = array.iterator(aIdx)
                    for (i in 0 until k) {
                        val temp = (alphaPrimitive * aIt.next()).toPrimitive()
                        val bIdx = i * ldb + bOffset

                        val bIt = b.array.blockIterator(bIdx)
                        val cIt = c.array.blockIterator(cIdx)

                        cIt.accept(bIt, n) { elementInC, elementInB ->
                            (temp * elementInB + elementInC).toPrimitive()
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

    override fun viewMutable(vararg axes: Int): MutablePrimitiveNDArray {
        val offset = axes.foldIndexed(0) { index, acc, i -> acc + i * strides.strides[index] }
        val offsetBlocks = offset / array.blockSize

        val newShape = shape.copyOfRange(axes.size, shape.size)
        val newStrides = Strides(newShape)

        val countBlocks = newStrides.linearSize / array.blockSize

        val copyBlocks = array.blocks.copyOfRange(offsetBlocks, offsetBlocks + countBlocks)
        val newArray = PrimitiveTiledArray(copyBlocks)

        return MutablePrimitiveNDArray(newArray, newStrides)
    }

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
    private fun transposeRec(prevArray: PrimitiveTiledArray, newArray: PrimitiveTiledArray, prevStrides: Strides, newStrides: Strides, index: Int, prevOffset: Int, newOffset: Int, permutation: IntArray) {
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
                var (newArrayBlock, newArrayOffset) = newArray.indexFor(newOffset)
                var (prevArrayBlock, prevArrayOffset) = prevArray.indexFor(prevOffset)

                val (deltaBlock, deltaOffset) = prevArray.indexFor(temp)

                var tempNewBlock = newArray.blocks[newArrayBlock]
                var tempPrevBlock = prevArray.blocks[prevArrayBlock]

                for (i in 0 until newStrides.shape[index]) {
                    tempNewBlock[newArrayOffset++] = tempPrevBlock[prevArrayOffset]

                    prevArrayBlock += deltaBlock
                    prevArrayOffset += deltaOffset

                    if (prevArrayOffset >= prevArray.blockSize) {
                        prevArrayBlock += prevArrayOffset / prevArray.blockSize
                        prevArrayOffset %= prevArray.blockSize
                    }

                    if (prevArrayBlock < prevArray.blocksNum)
                        tempPrevBlock = prevArray.blocks[prevArrayBlock]

                    if (newArrayOffset >= newArray.blockSize) {
                        newArrayOffset = 0

                        if (++newArrayBlock < newArray.blocksNum)
                            tempNewBlock = newArray.blocks[newArrayBlock]
                    }
                }
            }
        }
    }

    override fun transpose(permutations: IntArray): MutableNumberNDArray {
        val newStrides = strides.transpose(permutations)
        val newArray = PrimitiveTiledArray(newStrides)
        array.copyInto(newArray)

        transposeRec(array, newArray, strides, newStrides, 0, 0, 0, permutations)

        this.strides = newStrides
        this.array = newArray
        return this
    }

    override fun transpose2D(): MutableNDArray {
        require(rank == 2)

        val newShape = shape.reversedArray()
        val newStrides = Strides(newShape)
        val newArray = PrimitiveTiledArray(newStrides)

        val newBlocksInRow = newShape[1] / newArray.blockSize

        var blockNum = 0

        for (row in 0 until newShape[0]) {
            val (blockOffset, offset) = array.indexFor(row)
            var col = 0
            for (i in 0 until newBlocksInRow) {
                val block = newArray.blocks[blockNum++]
                for (idx in 0 until newArray.blockSize) {
                    block[idx] = this.array.blocks[blockOffset + col * this.blocksInRow][offset]
                    col++
                }
            }
        }

        this.array = newArray
        this.strides = newStrides

        return this
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
