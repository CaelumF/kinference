package io.kinference.algorithms.completion.generation

import io.kinference.algorithms.completion.CompletionConfig
import io.kinference.algorithms.completion.CompletionModels
import io.kinference.algorithms.completion.generation.model.GPT2ModelWrapper
import io.kinference.algorithms.completion.tokenizer.BPETokenizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class FairSeqGenerationTest {
    @Test
    @Tag("heavy")
    fun testExecutable() {
        val model = GPT2ModelWrapper(CompletionModels.v4.loader, CompletionModels.v4.model)
        val tokenizer = BPETokenizer(CompletionModels.v4.loader)
        val generator = FairSeqGeneration(model, tokenizer)

        val text = "hello"
        val prefix = " wo"
        val contextIds = tokenizer.encode(text)
        val result = generator.generate(contextIds, prefix, CompletionConfig.Generation.default)
        val variants = result.map { it.map { info -> tokenizer.decode(info.ids) } }

        assertEquals(variants[0].toSet(), setOf(" would", " work", " world", " working", " won"))

//        assertTrue(" would" in variants[0])
//        assertTrue(" world" in variants[0])
//        assertTrue(" won" in variants[0])

        assertTrue(" would be" in variants[1])
        assertTrue(" would you" in variants[1])
        assertTrue(" would not" in variants[1])
        assertTrue(" would have" in variants[1])

        assertTrue(" would be a" in variants[2])
        assertTrue(" would be the" in variants[2])
        assertTrue(" would not be" in variants[2])
        assertTrue(" would have been" in variants[2])
    }
}
