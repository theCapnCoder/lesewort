package com.example

import com.example.data.engine.LinguisticEngine
import org.junit.Assert.*
import org.junit.Test

class BookStatisticsPersistenceTest {

    @Test
    fun `test statistics calculation logic for books`() {
        val chapterContent = "Hund Katze Maus Hund Vogel"
        val tokens = LinguisticEngine.tokenizeText(chapterContent)
        val uniqueTokens = tokens.toSet()

        val knownWords = setOf("hund") // status 2 = learned
        val learningWords = setOf("katze") // status 1 = learning

        val stats = LinguisticEngine.calculateStatistics(tokens, knownWords)

        val learnedCount = uniqueTokens.count { knownWords.contains(it) }
        val learningCount = uniqueTokens.count { learningWords.contains(it) }
        val newCount = uniqueTokens.size - learnedCount - learningCount

        assertEquals("Learned count should be 1", 1, learnedCount)
        assertEquals("Learning count should be 1", 1, learningCount)
        assertEquals("New count should be 2", 2, newCount)

        // Tokens: hund (2), katze (1), maus (1), vogel (1) = 5
        // Known tokens = 2 / 5 = 40.0%
        assertEquals(40.0, stats.comprehensibleVolumePercent, 0.1)
    }

    @Test
    fun `test dynamic statistics update when known words change`() {
        val chapterContent = "Apfel Banane Zitrone"
        val tokens = LinguisticEngine.tokenizeText(chapterContent)
        val uniqueTokens = tokens.toSet()

        var knownWords = emptySet<String>()
        var learningWords = emptySet<String>()

        var learnedCount = uniqueTokens.count { knownWords.contains(it) }
        var learningCount = uniqueTokens.count { learningWords.contains(it) }
        var newCount = uniqueTokens.size - learnedCount - learningCount

        assertEquals(0, learnedCount)
        assertEquals(0, learningCount)
        assertEquals(3, newCount)

        // User marks "Apfel" as learned
        knownWords = setOf("apfel")
        learnedCount = uniqueTokens.count { knownWords.contains(it) }
        learningCount = uniqueTokens.count { learningWords.contains(it) }
        newCount = uniqueTokens.size - learnedCount - learningCount

        assertEquals(1, learnedCount)
        assertEquals(0, learningCount)
        assertEquals(2, newCount)

        // User marks "Banane" as learning
        learningWords = setOf("banane")
        learnedCount = uniqueTokens.count { knownWords.contains(it) }
        learningCount = uniqueTokens.count { learningWords.contains(it) }
        newCount = uniqueTokens.size - learnedCount - learningCount

        assertEquals(1, learnedCount)
        assertEquals(1, learningCount)
        assertEquals(1, newCount)

        // User removes "Apfel"
        knownWords = emptySet()
        learnedCount = uniqueTokens.count { knownWords.contains(it) }
        learningCount = uniqueTokens.count { learningWords.contains(it) }
        newCount = uniqueTokens.size - learnedCount - learningCount

        assertEquals(0, learnedCount)
        assertEquals(1, learningCount)
        assertEquals(2, newCount)
    }

    @Test
    fun `test book entity persistent stats mapping`() {
        val book = com.example.data.db.BookEntity(
            id = 1,
            title = "Test Book",
            author = "Test Author",
            filePath = null,
            totalWords = 100,
            uniqueWordsCount = 40,
            currentChapterIndex = 0,
            currentScrollPosition = 0,
            totalChapters = 5,
            totalPages = 12,
            learnedWordsCount = 10,
            learningWordsCount = 5,
            newWordsCount = 25,
            comprehensionPercent = 35.0,
            complexityPercent = 65.0
        )

        assertEquals(10, book.learnedWordsCount)
        assertEquals(5, book.learningWordsCount)
        assertEquals(25, book.newWordsCount)
        assertEquals(35.0, book.comprehensionPercent, 0.001)
        assertEquals(65.0, book.complexityPercent, 0.001)
        assertEquals(5, book.totalChapters)
        assertEquals(12, book.totalPages)
    }
}
