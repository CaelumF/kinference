package io.kinference.benchmark

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import io.kinference.Utils
import io.kinference.data.tensors.Tensor
import io.kinference.model.Model
import io.kinference.ndarray.*
import io.kinference.onnx.TensorProto
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.stream.Collectors

object BenchmarkUtils {
    val ortOptions
        get() = OrtSession.SessionOptions().apply {
            this.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            this.setInterOpNumThreads(1)
            this.setIntraOpNumThreads(1)
            this.disableProfiling()
            this.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }

    fun Tensor.toOnnxTensor(env: OrtEnvironment) = when (this.data.type) {
        TensorProto.DataType.FLOAT -> OnnxTensor.createTensor(env, FloatBuffer.wrap((data as FloatNDArray).array), data.shape.toLongArray())
        TensorProto.DataType.DOUBLE -> OnnxTensor.createTensor(env, DoubleBuffer.wrap((data as DoubleNDArray).array), data.shape.toLongArray())
        TensorProto.DataType.INT32 -> OnnxTensor.createTensor(env, IntBuffer.wrap((data as IntNDArray).array), data.shape.toLongArray())
        TensorProto.DataType.INT64 -> OnnxTensor.createTensor(env, LongBuffer.wrap((data as LongNDArray).array), data.shape.toLongArray())
        else -> throw UnsupportedOperationException()
    }

    fun IntArray.toLongArray() = LongArray(size) { this[it].toLong() }

    fun modelWithInputs(path: String): Pair<ByteArray, List<Tensor>> {
        val (mainPath, testName, dataSet) = path.split('.')

        lateinit var modelBytes: ByteArray
        lateinit var inputs: List<Tensor>

        val testDir = "/$mainPath/test_$testName/"
        val path = javaClass.getResource(testDir).toURI()

        FileSystems.newFileSystem(path, emptyMap<String, Any>()).use { fileSystem ->
            val testPath = fileSystem.getPath(testDir)

            val modelPath = fileSystem.getPath("$testDir/model.onnx")
            modelBytes = Files.readAllBytes(modelPath)

            val dataSetPath = Files.list(testPath).collect(Collectors.toList()).find { "test_data_set_$dataSet" in it.fileName.toString() }
            val inputFiles = Files.list(dataSetPath).collect(Collectors.toList()).filter { "input" in it.fileName.toString() }
            inputs = inputFiles.map { Utils.getTensor(Files.readAllBytes(it)) }

        }

        return modelBytes to inputs
    }

    data class OrtState(val session: OrtSession, val inputs: Map<String, OnnxTensor>) {
        companion object {
            fun create(path: String): OrtState {
                val (modelBytes, inputs) = modelWithInputs(path)

                val env = OrtEnvironment.getEnvironment()
                val session = env.createSession(modelBytes, ortOptions)
                val ortInputs = inputs.map { it.info.name to it.toOnnxTensor(env) }.toMap()

                return OrtState(session, ortInputs)
            }
        }
    }

    data class KIState(val model: Model, val inputs: List<Tensor>) {
        companion object {
            fun create(path: String): KIState {
                val (modelBytes, inputs) = modelWithInputs(path)
                val model = Model.load(modelBytes)
                return KIState(model, inputs)
            }
        }
    }
}
