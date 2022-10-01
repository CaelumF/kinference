package io.kinference.ort

import ai.onnxruntime.*
import io.kinference.BackendInfo
import io.kinference.OptimizableEngine
import io.kinference.data.ONNXData
import io.kinference.data.ONNXDataType
import io.kinference.ort.data.tensor.ORTTensor
import io.kinference.ort.model.ORTModel
import io.kinference.protobuf.*
import io.kinference.utils.CommonDataLoader
import okio.Buffer
import okio.Path

typealias ORTData<T> = ONNXData<T, ORTBackend>

object ORTBackend : BackendInfo(name = "ONNXRuntime")

object ORTEngine : OptimizableEngine<ORTData<*>> {
    override val info: BackendInfo = ORTBackend

    private val ORT_READER_CONFIG = ProtobufReader.ReaderConfig(tensorDecoder = FlatTensorDecoder)
    fun protoReader(bytes: ByteArray) = ProtobufReader(Buffer().write(bytes), ORT_READER_CONFIG)

    override fun loadModel(bytes: ByteArray): ORTModel {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)

        val session = env.createSession(bytes, options)
        return ORTModel(session)
    }

    fun loadModel(bytes: ByteArray, options: OrtSession.SessionOptions): ORTModel {
        val session = OrtEnvironment.getEnvironment().createSession(bytes, options)
        return ORTModel(session)
    }

    fun loadModel(bytes: ByteArray, optimize: Boolean, logLevel: OrtLoggingLevel = OrtLoggingLevel.ORT_LOGGING_LEVEL_INFO): ORTModel {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        if (optimize)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        else
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)

        options.setSessionLogLevel(logLevel)
        val session = env.createSession(bytes, options)

        return ORTModel(session)
    }

    override suspend fun loadModel(path: Path): ORTModel {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        val session = env.createSession(path.toString(), options)
        return ORTModel(session)
    }

    override fun loadModel(bytes: ByteArray, optimize: Boolean): ORTModel {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        if (optimize)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        else
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        val session = env.createSession(bytes, options)
        return ORTModel(session)
    }

    override suspend fun loadModel(path: Path, optimize: Boolean): ORTModel {
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        if (optimize)
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        else
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        val session = env.createSession(path.toString(), options)
        return ORTModel(session)
    }

    override suspend fun loadData(path: Path, type: ONNXDataType) = loadData(CommonDataLoader.bytes(path), type)

    override fun loadData(bytes: ByteArray, type: ONNXDataType) = when (type) {
        ONNXDataType.ONNX_TENSOR -> ORTTensor.create(protoReader(bytes).readTensor())
        else -> error("$type construction is not supported in OnnxRuntime Java API")
    }
}
