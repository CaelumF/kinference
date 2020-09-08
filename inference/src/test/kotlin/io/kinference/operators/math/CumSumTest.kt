package io.kinference.operators.math

import io.kinference.Utils
import org.junit.jupiter.api.Test

class CumSumTest {
    private fun getTargetPath(dirName: String) = "/cumsum/$dirName/"

    @Test
    fun `test cumulative sum for 1d data`() {
        Utils.tensorTestRunner(getTargetPath("test_cumsum_1d"))
    }

    @Test
    fun `test exclusive cumulative sum for 1d data`() {
        Utils.tensorTestRunner(getTargetPath("test_cumsum_1d_exclusive"))
    }

    @Test
    fun `test reverse exclusive cumulative sum for 1d data`() {
        Utils.tensorTestRunner(getTargetPath("test_cumsum_1d_reverse_exclusive"))
    }

    @Test
    fun `test cumulative sum along axis=0 for 2d data`() {
        Utils.tensorTestRunner(getTargetPath("test_cumsum_2d_axis_0"))
    }

    @Test
    fun `test cumulative sum along axis=1 for 2d data`() {
        Utils.tensorTestRunner(getTargetPath("test_cumsum_2d_axis_1"))
    }

    @Test
    fun `test cumulative sum along negative for 2d data`() {
        Utils.tensorTestRunner(getTargetPath("test_cumsum_2d_negative_axis"))
    }
}
