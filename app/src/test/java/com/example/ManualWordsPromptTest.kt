package com.example

import com.example.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class ManualWordsPromptTest {

    @Test
    fun testParseWordsInputFormats() {
        // Comma separated
        val commaInput = "der Tisch, die Lampe, das Haus"
        assertEquals(listOf("der Tisch", "die Lampe", "das Haus"), parseWordsInput(commaInput))

        // Semicolon separated
        val semicolonInput = "laufen; springen; schwimmen"
        assertEquals(listOf("laufen", "springen", "schwimmen"), parseWordsInput(semicolonInput))

        // Newline separated with numbered list
        val numberedInput = "1. der Hund\n2. die Katze\n3) die Maus"
        assertEquals(listOf("der Hund", "die Katze", "die Maus"), parseWordsInput(numberedInput))

        // Bulleted lists and trailing punctuation
        val bulletInput = "- Apfel\n• Banane\n* Orange"
        assertEquals(listOf("Apfel", "Banane", "Orange"), parseWordsInput(bulletInput))

        // Mixed with empty elements, whitespace, quotes and duplicates
        val messyInput = "  \"der Tisch\", 'die Lampe', , der Tisch; \n- das Haus. \n  "
        assertEquals(listOf("der Tisch", "die Lampe", "das Haus"), parseWordsInput(messyInput))

        // Blank or empty input
        assertTrue(parseWordsInput("").isEmpty())
        assertTrue(parseWordsInput("   \n\t  ").isEmpty())
    }

    @Test
    fun testManualWordsExclusivelyUsedInPrompt() {
        val standardBookStudyWords = listOf("BuchWort1", "BuchWort2")
        val standardBookKnownWords = listOf("BuchBekannt1", "BuchBekannt2")

        val manualStudyWordsInput = "das Geheimnis, entdecken, plötzlich"
        val manualKnownWordsInput = "der Tisch, das Haus, die Lampe, schnell"

        val parsedManualStudy = parseWordsInput(manualStudyWordsInput)
        val parsedManualKnown = parseWordsInput(manualKnownWordsInput)

        // When manual mode is active, only parsedManualStudy and parsedManualKnown are passed
        val prompt = buildDefaultPrompt(
            languageCode = "de",
            level = CefrLevel.A2,
            genre = StoryGenre.DETECTIVE,
            storyLength = StoryLength.NOVELLA,
            unknownWordsRatio = UnknownWordsRatio.PERCENT_5_0,
            learningWords = parsedManualStudy,
            knownWords = parsedManualKnown,
            includeKnownWordsInPrompt = true
        )

        // Verify manual words ARE in the prompt
        assertTrue(prompt.contains("das Geheimnis"))
        assertTrue(prompt.contains("entdecken"))
        assertTrue(prompt.contains("plötzlich"))
        assertTrue(prompt.contains("der Tisch"))
        assertTrue(prompt.contains("das Haus"))
        assertTrue(prompt.contains("die Lampe"))
        assertTrue(prompt.contains("schnell"))

        // Verify standard book words ARE NOT in the prompt
        assertFalse(prompt.contains("BuchWort1"))
        assertFalse(prompt.contains("BuchWort2"))
        assertFalse(prompt.contains("BuchBekannt1"))
        assertFalse(prompt.contains("BuchBekannt2"))
    }

    @Test
    fun testManualWordsOnlyVocabularyCounters() {
        val manualKnown = parseWordsInput("der Tisch, das Haus, die Frau, laufen, schnell")
        val manualStudy = parseWordsInput("das Geheimnis, entdecken")

        val totalVocabCount = manualKnown.size + manualStudy.size
        assertEquals(5, manualKnown.size)
        assertEquals(2, manualStudy.size)
        assertEquals(7, totalVocabCount)

        val recommended = calculateRecommendedLevel(totalVocabCount)
        assertEquals(CefrLevel.A1, recommended)
    }
}
