package io.kinference.algorithms.completion.suggest.ranking

import io.kinference.algorithms.completion.CompletionModel
import io.kinference.algorithms.completion.suggest.feature.Features

/**
 * Naive version of [RankingModel] that performs reordering based on probability of completion
 * and match rank between it and prefix
 */
class ProbRankingModel : RankingModel {
    override fun rank(context: String, prefix: String, completions: List<CompletionModel.CompletionResult>): List<CompletionModel.CompletionResult> {
        val ranked = completions.map { completion ->
            completion to Features.stepProfit(completion.info)
        }.toMap()

        return ranked.entries.sortedByDescending { it.value }.map { it.key }
    }
}
