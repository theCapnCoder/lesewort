package com.example.data.engine

import java.text.BreakIterator
import java.util.Locale

data class WordSpan(
    val word: String,
    val cleanWord: String,
    val startIndex: Int,
    val endIndex: Int
)

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

    fun isJapanese(text: String): Boolean {
        return text.any {
            it in '\u3040'..'\u309F' || // Hiragana
            it in '\u30A0'..'\u30FF' || // Katakana
            it in '\u4E00'..'\u9FAF'    // Kanji
        }
    }

    fun extractWordSpans(text: String, isJapaneseLanguage: Boolean = false): List<WordSpan> {
        if (text.isEmpty()) return emptyList()
        val isJa = isJapaneseLanguage || isJapanese(text)
        val result = mutableListOf<WordSpan>()
        if (isJa) {
            val boundary = BreakIterator.getWordInstance(Locale.JAPANESE)
            boundary.setText(text)
            var start = boundary.first()
            var end = boundary.next()
            while (end != BreakIterator.DONE) {
                val token = text.substring(start, end)
                val cleaned = tokenizeWord(token)
                if (cleaned.isNotEmpty() && token.any { it.isLetter() }) {
                    result.add(WordSpan(word = token, cleanWord = cleaned, startIndex = start, endIndex = end))
                }
                start = end
                end = boundary.next()
            }
        } else {
            val wordRegex = Regex("""(\p{L}+(?:[-'’`]\p{L}+)*)""")
            wordRegex.findAll(text).forEach { match ->
                val clean = tokenizeWord(match.value)
                if (clean.isNotEmpty()) {
                    result.add(
                        WordSpan(
                            word = match.value,
                            cleanWord = clean,
                            startIndex = match.range.first,
                            endIndex = match.range.last + 1
                        )
                    )
                }
            }
        }
        return result
    }

    fun extractWordsFromText(text: String): List<String> {
        return if (isJapanese(text)) {
            val result = mutableListOf<String>()
            val boundary = BreakIterator.getWordInstance(Locale.JAPANESE)
            boundary.setText(text)
            var start = boundary.first()
            var end = boundary.next()
            while (end != BreakIterator.DONE) {
                val token = text.substring(start, end).trim()
                val cleaned = tokenizeWord(token)
                if (cleaned.isNotEmpty()) {
                    result.add(cleaned)
                }
                start = end
                end = boundary.next()
            }
            result
        } else {
            val wordRegex = Regex("""(\p{L}+(?:[-'’`]\p{L}+)*)""")
            wordRegex.findAll(text).map { tokenizeWord(it.value) }.filter { it.isNotEmpty() }.toList()
        }
    }

    fun tokenizeText(text: String): List<String> {
        return if (isJapanese(text)) {
            extractWordsFromText(text)
        } else {
            text.split(' ', '\n', '\t', '\r')
                .map { tokenizeWord(it) }
                .filter { it.isNotEmpty() }
        }
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
