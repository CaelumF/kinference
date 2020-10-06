package io.kinference.completion.generating

import java.lang.IllegalArgumentException
import java.lang.Integer.min
import kotlin.math.ln

fun logSoftmax(scores: List<MutableList<Double>>): List<MutableList<Double>> {
    val expScores = scores.map { it.map { e -> kotlin.math.exp(e) }.toMutableList() }
    val sumLastScores = expScores.map { it.sum() }
    return expScores.mapIndexed { i, list -> list.map { ln(it / sumLastScores[i]) }.toMutableList() }
}

fun topk1d(data: List<Double>, size: Int): List<Int> {
    val pairedData = ArrayList<Pair<Double, Int>>(data.size)
    for (i in data.indices) {
        pairedData.add(Pair(data[i], i))
    }
    pairedData.sortBy { -it.first }
    return pairedData.map { it.second }.subList(0, size)
}

fun topk2d(data: List<List<Double>>, size: Int, dim: Int = 0): List<List<Int>> {
    if (data.isEmpty()) {
        return listOf()
    }

    when (dim) {
        0 -> {
            val listSize = min(data.size, size)
            val result = List(listSize) { ArrayList<Int>(data[0].size) }
            for (j in data[0].indices) {
                val slice = data.indices.map { data[it][j] }
                val topColumn = topk1d(slice, size)
                topColumn.forEachIndexed { i, e -> result[i].add(e) }
            }
            return result
        }
        1 -> {
            val result: MutableList<List<Int>> = ArrayList(data.size)
            for (row in data) {
                result.add(topk1d(row, size))
            }
            return result
        }
        else -> {
            throw IllegalArgumentException("Index should be 0 or 1")
        }
    }
}
