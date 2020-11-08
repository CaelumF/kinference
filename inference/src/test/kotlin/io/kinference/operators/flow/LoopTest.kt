package io.kinference.operators.flow

import io.kinference.runners.TestRunner
import org.junit.jupiter.api.Test

class LoopTest {
    private fun getTargetPath(dirName: String) = "/loop/$dirName/"

    @Test
    fun `test loop`() {
        TestRunner.runFromResources(getTargetPath("test_loop"))
    }
}
