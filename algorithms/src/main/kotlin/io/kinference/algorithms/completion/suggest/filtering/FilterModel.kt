package io.kinference.algorithms.completion.suggest.filtering

import io.kinference.algorithms.completion.CompletionConfig
import io.kinference.algorithms.completion.CompletionModel

/**
 * Interface for filtering models that perform filtering of completion results based on information from
 * completion model, context, prefix and specified filtering config
 */
interface FilterModel {
    /**
     * Performs filtering of [completions] based on information from completion model, [context], [prefix] and [config].
     * Note: this method should retain order of entries in [completions] list
     */
    fun filter(
        context: String,
        prefix: String,
        completions: List<CompletionModel.CompletionResult>,
        config: CompletionConfig.Filter
    ): List<CompletionModel.CompletionResult>
}
