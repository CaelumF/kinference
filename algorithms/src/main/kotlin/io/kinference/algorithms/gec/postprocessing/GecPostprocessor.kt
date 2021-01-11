package io.kinference.algorithms.gec.postprocessing

import io.kinference.algorithms.gec.utils.SentenceCorrections
import io.kinference.algorithms.gec.utils.TextCorrection

fun transformSentence(incorrectSentence: String, corrections: List<TextCorrection>): String{
    var corrSentence = incorrectSentence
    var offset = 0
    for (correction in corrections){
        val startEnd = correction.errorRange
        corrSentence = corrSentence.substring(startIndex = 0, endIndex = offset + startEnd.first) + correction.replacement + corrSentence.substring(startIndex = offset + startEnd.second)
        offset += correction.replacement.length - (startEnd.second - startEnd.first)
    }
    return corrSentence
}

abstract class GecPostprocessor {
    abstract fun postprocess(sentObj: SentenceCorrections): String
}

class GecCorrectionPostprocessor(): GecPostprocessor(){

    override fun postprocess(sentObj: SentenceCorrections): String {
        val original = sentObj.sent
        val textCorrections = sentObj.toTextCorrections()

        return transformSentence(original, textCorrections)
    }
}
