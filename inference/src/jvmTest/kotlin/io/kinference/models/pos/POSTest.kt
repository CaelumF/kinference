package io.kinference.models.pos

import io.kinference.runners.PerformanceRunner
import io.kinference.runners.AccuracyRunner
import io.kinference.utils.TestRunner
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class POSTest {
    @Test
    fun `test POS-tagger`()  = TestRunner.runTest {
        AccuracyRunner.runFromResources("/pos_tagger/")
    }

    @Test
    @Tag("heavy")
    fun `test POS-tagger performance`()  = TestRunner.runTest {
        PerformanceRunner.runFromResources("/pos_tagger/")
    }
}
