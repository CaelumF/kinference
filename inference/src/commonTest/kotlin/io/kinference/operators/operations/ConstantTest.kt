package io.kinference.operators.operations

import io.kinference.runners.AccuracyRunner
import io.kinference.utils.TestRunner
import kotlin.test.Test

class ConstantTest {
    private fun getTargetPath(dirName: String) = "/constant/$dirName/"

    @Test
    fun test_constant()  = TestRunner.runTest {
        AccuracyRunner.runFromResources(getTargetPath("test_constant"))
    }

    @Test
    fun test_scalar_constant()  = TestRunner.runTest {
        AccuracyRunner.runFromResources(getTargetPath("test_scalar_constant"))
    }
}
