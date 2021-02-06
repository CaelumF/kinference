package io.kinference.operators.operations

import io.kinference.runners.TestRunner
import org.junit.jupiter.api.Test

class SqueezeTest {
    private fun getTargetPath(dirName: String) = "/squeeze/$dirName/"

    @Test
    fun `test squeeze`() {
        TestRunner.runFromResources(getTargetPath("test_squeeze"))
    }

    @Test
    fun `test squeeze with negative axes`() {
        TestRunner.runFromResources(getTargetPath("test_squeeze_negative_axes"))
    }
}
