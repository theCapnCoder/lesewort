package com.example

import com.example.data.parser.BookParser
import com.example.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class StoryGenerationLogicTest {

    @Test
    fun testLevelRecommendation() {
        assertEquals(CefrLevel.A1, calculateRecommendedLevel(50))
        assertEquals(CefrLevel.A1, calculateRecommendedLevel(150))
        assertEquals(CefrLevel.A2, calculateRecommendedLevel(151))
        assertEquals(CefrLevel.A2, calculateRecommendedLevel(500))
        assertEquals(CefrLevel.B1, calculateRecommendedLevel(501))
        assertEquals(CefrLevel.B1, calculateRecommendedLevel(1500))
        assertEquals(CefrLevel.B2, calculateRecommendedLevel(1501))
        assertEquals(CefrLevel.B2, calculateRecommendedLevel(3500))
        assertEquals(CefrLevel.C1, calculateRecommendedLevel(3501))
        assertEquals(CefrLevel.C1, calculateRecommendedLevel(7000))
        assertEquals(CefrLevel.C2, calculateRecommendedLevel(7001))
    }

    @Test
    fun testPromptGenerationContainsRulesAndWords() {
        val studyWords = listOf("Hund", "Katze", "Baum", "laufen")
        val knownWords = listOf("Haus", "Auto", "Straße", "sehen")

        val prompt = buildDefaultPrompt(
            languageCode = "de",
            level = CefrLevel.A2,
            genre = StoryGenre.DETECTIVE,
            storyLength = StoryLength.NOVELLA,
            unknownWordsRatio = UnknownWordsRatio.PERCENT_7_5,
            learningWords = studyWords,
            knownWords = knownWords,
            customUserNotes = "Включи тайную записку в старой книге",
            customTargetWords = "die Sehnsucht, entdecken, plötzlich"
        )

        assertTrue(prompt.contains("German") || prompt.contains("Немецкий"))
        assertTrue(prompt.contains("A2"))
        assertTrue(prompt.contains("AI*.A2."))
        assertTrue(prompt.contains("Детектив"))
        assertTrue(prompt.contains("1500") || prompt.contains("Chapters"))
        assertTrue(prompt.contains("7.5%"))
        assertTrue(prompt.contains("Hund, Katze, Baum, laufen"))
        assertTrue(prompt.contains("Haus, Auto, Straße, sehen"))
        assertTrue(prompt.contains("die Sehnsucht, entdecken, plötzlich"))
        assertTrue(prompt.contains("MANDATORY TARGET WORDS TO LEARN & REPEAT AS OFTEN AS POSSIBLE"))
        assertTrue(prompt.contains("Включи тайную записку в старой книге"))
    }

    @Test
    fun testStoryGenerationLanguagesExcludeRuEsIt() {
        val codes = STORY_GENERATION_LANGUAGES.map { it.code }
        assertEquals(listOf("de", "en", "fr"), codes)
        assertFalse(codes.contains("ru"))
        assertFalse(codes.contains("es"))
        assertFalse(codes.contains("it"))
    }

    @Test
    fun testUnknownWordsRatioValues() {
        val percentages = UnknownWordsRatio.values().map { it.percentage }
        assertEquals(listOf(2.5, 5.0, 7.5, 10.0), percentages)
        val labels = UnknownWordsRatio.values().map { it.label }
        assertEquals(listOf("2.5%", "5%", "7.5%", "10%"), labels)
    }

    @Test
    fun testFormatAiStoryTitle() {
        assertEquals("AI*.A1. Der kleine Hund", formatAiStoryTitle("# AI*.A1. Der kleine Hund", "A1"))
        assertEquals("AI*.A2. Das Geheimnis im Wald", formatAiStoryTitle("# Das Geheimnis im Wald", "A2"))
        assertEquals("AI*.B1. Ein Tag am Meer", formatAiStoryTitle("AI*. B1: Ein Tag am Meer", "B1"))
        assertEquals("AI*.C1. Детектив", formatAiStoryTitle("", "C1", fallbackGenre = "Детектив"))
    }

    @Test
    fun testPromptWhenKnownWordsDisabled() {
        val studyWords = listOf("Hund", "Katze", "Baum", "laufen")
        val knownWords = listOf("Haus", "Auto", "Straße", "sehen")

        val prompt = buildDefaultPrompt(
            languageCode = "de",
            level = CefrLevel.A1,
            genre = StoryGenre.FANTASY,
            includeKnownWordsInPrompt = false,
            learningWords = studyWords,
            knownWords = knownWords
        )

        assertFalse(prompt.contains("Target Ratio of New/Unknown Words"))
        assertFalse(prompt.contains("KNOWN VOCABULARY BASE"))
        assertFalse(prompt.contains("WORDS TO PRACTICE"))
        assertTrue(prompt.contains("VOCABULARY & GRAMMAR ADAPTATION FOR CEFR LEVEL A1"))
        assertTrue(prompt.contains("Strictly write using vocabulary, idioms, sentence lengths, and grammatical patterns appropriate for CEFR A1"))
    }

    @Test
    fun testCustomPercentageInPrompt() {
        val prompt = buildDefaultPrompt(
            languageCode = "en",
            level = CefrLevel.B1,
            genre = StoryGenre.SCI_FI,
            includeKnownWordsInPrompt = true,
            customUnknownPercentage = 12.5,
            learningWords = listOf("spaceship", "nebula"),
            knownWords = listOf("star", "planet")
        )

        assertTrue(prompt.contains("12.5%"))
        assertTrue(prompt.contains("Target Ratio of New/Unknown Words: 12.5% (12.5%)"))
        assertTrue(prompt.contains("spaceship, nebula"))
        assertTrue(prompt.contains("star, planet"))
    }

    @Test
    fun testFormatPercentageHelpers() {
        assertEquals("5%", formatPercentageLabel(5.0))
        assertEquals("7.5%", formatPercentageLabel(7.5))
        assertEquals("12%", formatPercentageLabel(12.0))
        assertEquals("3.5%", formatPercentageLabel(3.5))

        assertTrue(formatPercentageDescription(2.5).contains("Очень легкое чтение"))
        assertTrue(formatPercentageDescription(5.0).contains("Комфортный баланс"))
        assertTrue(formatPercentageDescription(7.5).contains("Умеренная сложность"))
        assertTrue(formatPercentageDescription(10.0).contains("Интенсивное обучение"))
        assertTrue(formatPercentageDescription(15.0).contains("Высокая сложность"))
    }

    @Test
    fun testContinuationPromptPreservesVocabularyAndLevelConstraints() {
        val studyWords = listOf("Hund", "Katze", "Baum")
        val knownWords = listOf("Haus", "Auto", "Straße")

        val continuationPrompt = buildContinuationPrompt(
            languageCode = "de",
            level = CefrLevel.A1,
            genre = StoryGenre.FANTASY,
            storyLength = StoryLength.NOVELLA,
            fromChapter = 2,
            toChapter = 5,
            previousStoryEnding = "Anna sah das kleine Haus im Wald. Die Tür war offen.",
            unknownWordsRatio = UnknownWordsRatio.PERCENT_5_0,
            learningWords = studyWords,
            knownWords = knownWords,
            customTargetWords = "der Schlüssel, öffnen",
            includeKnownWordsInPrompt = true,
            customUnknownPercentage = 5.0
        )

        // Must strictly preserve CEFR A1 simplicity
        assertTrue(continuationPrompt.contains("CEFR Level: A1"))
        assertTrue(continuationPrompt.contains("ABSOLUTE VOCABULARY & SIMPLICITY CONSISTENCY REQUIRED"))
        assertTrue(continuationPrompt.contains("Chapters 2 to 5 MUST be just as easy and accessible to read as Chapter 1"))
        assertTrue(continuationPrompt.contains("ABSOLUTELY NO VOCABULARY OVERLOAD"))
        assertTrue(continuationPrompt.contains("It must NEVER be that every second word is unknown"))

        // Must contain vocabulary constraints
        assertTrue(continuationPrompt.contains("Target Ratio of New/Unknown Words: 5% (5.0%)"))
        assertTrue(continuationPrompt.contains("KNOWN VOCABULARY BASE"))
        assertTrue(continuationPrompt.contains("Haus, Auto, Straße"))
        assertTrue(continuationPrompt.contains("der Schlüssel, öffnen"))
        assertTrue(continuationPrompt.contains("Hund, Katze, Baum"))

        // Must specify chapter headers
        assertTrue(continuationPrompt.contains("## Kapitel 2:"))
        assertTrue(continuationPrompt.contains("## Kapitel 5:"))
        assertTrue(continuationPrompt.contains("Anna sah das kleine Haus im Wald"))
    }

    @Test
    fun testContinuationPromptWhenKnownWordsDisabled() {
        val continuationPrompt = buildContinuationPrompt(
            languageCode = "de",
            level = CefrLevel.A2,
            genre = StoryGenre.DETECTIVE,
            storyLength = StoryLength.NOVELLA,
            fromChapter = 3,
            toChapter = 5,
            previousStoryEnding = "Der Kommissar fand eine Spur im Schnee.",
            includeKnownWordsInPrompt = false
        )

        assertTrue(continuationPrompt.contains("CEFR Level: A2"))
        assertTrue(continuationPrompt.contains("VOCABULARY & GRAMMAR ADAPTATION FOR CEFR LEVEL A2"))
        assertTrue(continuationPrompt.contains("Strictly write using simple vocabulary, short sentences"))
        assertFalse(continuationPrompt.contains("KNOWN VOCABULARY BASE"))
        assertTrue(continuationPrompt.contains("## Kapitel 3:"))
    }

    @Test
    fun testChapterHeaderRegexDetection() {
        val regex = Regex("(?i)##\\s*(?:[a-zа-яёí]+\\s*)?(\\d+)", RegexOption.IGNORE_CASE)
        val testText = """
            # AI*.A1. Die Geschichte
            ## Kapitel 1: Der Anfang
            Text text text...
            ## Chapter 2: The Middle
            More text...
            ## 3: Das Ende
            Final text.
        """.trimIndent()

        val chapters = regex.findAll(testText)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toList()

        assertEquals(listOf(1, 2, 3), chapters)
    }

    @Test
    fun testParseStoryCreation() {
        val storyText = """
            # Das Geheimnis im Wald
            
            Es war ein ruhiger Tag. Ein Mann ging durch den Wald und sah ein altes Haus.
            
            In dem Haus lag ein altes Buch. Auf dem Tisch stand eine kleine Tasse.
        """.trimIndent()

        val parsed = BookParser.parseStory(
            title = "Das Geheimnis im Wald",
            author = "Детектив (A2)",
            text = storyText
        )

        assertEquals("Das Geheimnis im Wald", parsed.title)
        assertEquals("Детектив (A2)", parsed.author)
        assertTrue(parsed.chapters.isNotEmpty())
        assertTrue(parsed.chapters.first().content.contains("Es war ein ruhiger Tag"))
    }
}
