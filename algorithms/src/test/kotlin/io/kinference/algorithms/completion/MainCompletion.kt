package io.kinference.algorithms.completion

import io.kinference.algorithms.completion.suggest.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.lang.System.currentTimeMillis

class MainCompletion {
    @Test
    @Tag("heavy")
    fun test() {
        val config = config

        val completionsCollector = FairseqCompletionsCollector(config)
        val rankingModel = FirstProbRankingModel()
        val filterModel = ProbFilterModel()
        val postFilterModel: FilterModel? = null

        val completionModel = CompletionModel(completionsCollector, rankingModel, filterModel, postFilterModel)

//            interaction(completionModel)
            speedTest(completionModel, 30, 20)
        }

    private fun interaction(completionModel: CompletionModel) {
        println("Write something")
        var input = "hello wo"
        while (true) {
            val sepIndex = input.lastIndexOf(' ')
            val context = input.substring(0, sepIndex)
            val prefix = input.substring(sepIndex)
            println("$context:$prefix")

            val completions = completionModel.complete(context, prefix, config)
            println(completions)
            println()

            input = readLine().toString()
        }
    }

    private fun speedTest(completionModel: CompletionModel, len: Int = 1, itersNum: Int = 100) {
//            1 - 0.97258
        println("Warm up")
        val input = (0 until len).map { "hello " }.joinToString { "" } + "wo"
//            val input = "hello wo"
        for (i in 0 until 10) {
            val sepIndex = input.lastIndexOf(' ')
            val context = input.substring(0, sepIndex)
            val prefix = input.substring(sepIndex)

            val completions = completionModel.complete(context, prefix, config)
        }

        println("Start")
        val startTime = currentTimeMillis()
        for (i in 0 until itersNum) {
            val sepIndex = input.lastIndexOf(' ')
            val context = input.substring(0, sepIndex)
            val prefix = input.substring(sepIndex)

            val completions = completionModel.complete(context, prefix, config)
        }
        val duration = (currentTimeMillis() - startTime) / 1000.0 / itersNum
        println()
        println(duration)
    }
}
