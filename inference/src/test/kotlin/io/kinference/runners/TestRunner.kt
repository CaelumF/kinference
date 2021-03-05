package io.kinference.runners

import io.kinference.data.ONNXData
import io.kinference.loaders.S3Client
import io.kinference.model.Model
import io.kinference.protobuf.message.TensorProto
import io.kinference.utils.Assertions
import io.kinference.utils.DataLoader
import java.io.File
import kotlin.math.pow

object TestRunner {
    private val testData = File("../build/test-data")

    private val delta = (10.0).pow(-3)

    data class ONNXTestData(val actual: List<ONNXData>, val expected: List<ONNXData>)

    private fun runTestsFromS3(testPath: String, prefix: String, testRunner: (File) -> List<ONNXTestData>): List<ONNXTestData> {
        val toFolder = File(testData, testPath)
        S3Client.copyObjects(prefix, toFolder)
        return testRunner(toFolder)
    }

    private fun runTestsFromResources(testPath: String): List<ONNXTestData> {
        val path = javaClass.getResource(testPath)!!.path
        return runTestsFromFolder(File(path))
    }

    private fun runTestsFromFolder(path: File): List<ONNXTestData> {
        val model = Model.load(File(path, "model.onnx").absolutePath)

        return path.list()!!.filter { "test" in it }.map {
            val inputFiles = File("$path/$it").walk().filter { file -> "input" in file.name }
            val outputFiles = File("$path/$it").walk().filter { file -> "output" in file.name }

            val inputTensors = inputFiles.map { model.graph.prepareInput(TensorProto.decode(it.readBytes())) }.toList()
            val expectedOutputTensors = outputFiles.map { DataLoader.getTensor(it) }.toList()
            val actualOutputTensors = model.predict(inputTensors)
            ONNXTestData(expectedOutputTensors, actualOutputTensors)
        }
    }

    fun runFromS3(path: String, prefix: String, testRunner: (File) -> List<ONNXTestData> = this::runTestsFromFolder, delta: Double = TestRunner.delta) {
        check(runTestsFromS3(path, prefix, testRunner), delta)
    }

    fun runFromResources(path: String, delta: Double = TestRunner.delta) {
        check(runTestsFromResources(path), delta)
    }

    private fun check(datasets: List<ONNXTestData>, delta: Double = TestRunner.delta) {
        for (dataSet in datasets) {
            val (expectedOutputTensors, actualOutputTensors) = dataSet

            val mappedActualOutputTensors = actualOutputTensors.associateBy { it.info.name }

            for (expectedOutputTensor in expectedOutputTensors) {
                val actualOutputTensor = mappedActualOutputTensors[expectedOutputTensor.info.name] ?: error("Required tensor not found")
                Assertions.assertEquals(expectedOutputTensor, actualOutputTensor, delta)
            }
        }
    }
}
