package io.kinference.operators.activations

import io.kinference.Utils
import org.junit.jupiter.api.Test

class IdentityTest {
    private fun getTargetPath(dirName: String) = "/identity/$dirName/"

    @Test
    fun `test identity`() {
        Utils.tensorTestRunner(getTargetPath("test_identity"))
    }
}
