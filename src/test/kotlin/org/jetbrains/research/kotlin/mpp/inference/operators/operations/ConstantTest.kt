package org.jetbrains.research.kotlin.mpp.inference.operators.operations

import org.jetbrains.research.kotlin.mpp.inference.Utils
import org.junit.jupiter.api.Test

class ConstantTest {
    private fun getTargetPath(dirName: String) = "/constant/$dirName/"

    @Test
    fun test_constant() {
        Utils.tensorTestRunner(getTargetPath("test_constant"))
    }
}
