package com.example.data.engine

data class BookStatistics(
    val comprehensibleVolumePercent: Double,  // Понятно по объёму (%)
    val knownUniquePercent: Double,           // Известных уникальных (%)
    val unknownUniquePercent: Double,         // Неизвестных уникальных (%)
    val complexityVolumePercent: Double,       // Сложность по объёму (%)
    val totalWords: Int,
    val uniqueWordsCount: Int,
    val knownWordsCount: Int,
    val unknownWordsCount: Int
)

object LinguisticEngine {

    fun tokenizeWord(word: String): String {
        return word.filter { it.isLetter() || it == '-' || it == '\'' || it == '’' || it == '`' }
            .trim('-', '\'', '’', '`')
            .lowercase()
    }

    fun tokenizeText(text: String): List<String> {
        return text.split(' ', '\n', '\t', '\r')
            .map { tokenizeWord(it) }
            .filter { it.isNotEmpty() }
    }

    fun calculateStatistics(allWords: List<String>, knownWordsSet: Set<String>): BookStatistics {
        val totalWordsCount = allWords.size
        if (totalWordsCount == 0) {
            return BookStatistics(0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0)
        }

        // 1. Total known words (including repetitions)
        val totalKnownWordsCount = allWords.count { knownWordsSet.contains(it) }
        val totalUnknownWordsCount = totalWordsCount - totalKnownWordsCount

        // 2. Unique words
        val uniqueWords = allWords.toSet()
        val totalUniqueCount = uniqueWords.size

        if (totalUniqueCount == 0) {
            return BookStatistics(0.0, 0.0, 0.0, 0.0, totalWordsCount, 0, 0, 0)
        }

        val uniqueKnownCount = uniqueWords.count { knownWordsSet.contains(it) }
        val uniqueUnknownCount = totalUniqueCount - uniqueKnownCount

        // Formula calculations
        val comprehensibleVolumePercent = (totalKnownWordsCount.toDouble() / totalWordsCount) * 100.0
        val knownUniquePercent = (uniqueKnownCount.toDouble() / totalUniqueCount) * 100.0
        val unknownUniquePercent = (uniqueUnknownCount.toDouble() / totalUniqueCount) * 100.0
        val complexityVolumePercent = (totalUnknownWordsCount.toDouble() / totalWordsCount) * 100.0

        return BookStatistics(
            comprehensibleVolumePercent = comprehensibleVolumePercent,
            knownUniquePercent = knownUniquePercent,
            unknownUniquePercent = unknownUniquePercent,
            complexityVolumePercent = complexityVolumePercent,
            totalWords = totalWordsCount,
            uniqueWordsCount = totalUniqueCount,
            knownWordsCount = uniqueKnownCount,
            unknownWordsCount = uniqueUnknownCount
        )
    }
}
