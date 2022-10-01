package io.kinference

import io.kinference.data.ONNXData
import io.kinference.model.Model
import okio.Path
import okio.Path.Companion.toPath

interface OptimizableEngine<T : ONNXData<*, *>> : InferenceEngine<T> {
    fun loadModel(bytes: ByteArray, optimize: Boolean = false): Model<T>

    suspend fun loadModel(path: Path, optimize: Boolean = false): Model<T>
    suspend fun loadModel(path: String, optimize: Boolean = false): Model<T> = loadModel(path.toPath(), optimize)
}
