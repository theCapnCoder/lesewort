package com.example

import com.example.data.api.GeminiStoryService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LemmatizationPromptAndParserTest {

    @Test
    fun testBuildLemmatizationPromptContainsHardCeilingAndNoExpansionRule() {
        val words = listOf("Häuser", "ging", "schöner", "Bäume", "fliegen")
        val promptDe = GeminiStoryService.buildLemmatizationPrompt(words, "de")

        assertTrue(promptDe.contains("5"))
        assertTrue(promptDe.contains("НЕ БОЛЕЕ 5 лемм"))
        assertTrue(promptDe.contains("СЖАТИЕ СПИСКА, А НЕ УВЕЛИЧЕНИЕ"))
        assertTrue(promptDe.contains("СТРОЖАЙШЕ ЗАПРЕЩЕНО перечислять формы одного слова"))
        assertTrue(promptDe.contains("JSON-объект"))
        assertTrue(promptDe.contains("\"lemmas\":"))

        val wordsEn = listOf("houses", "went", "brighter", "running", "dogs")
        val promptEn = GeminiStoryService.buildLemmatizationPrompt(wordsEn, "en")
        assertTrue(promptEn.contains("5"))
        assertTrue(promptEn.contains("НЕ БОЛЕЕ 5 лемм"))
        assertTrue(promptEn.contains("СЖАТИЕ СПИСКА, А НЕ УВЕЛИЧЕНИЕ"))
    }

    @Test
    fun testParseLemmatizedResponseWithValidJsonObject() {
        val json = """
            {
              "lemmas": [
                "das Haus",
                "gehen",
                "schön",
                "der Baum",
                "fliegen"
              ]
            }
        """.trimIndent()

        val parsed = GeminiStoryService.parseLemmatizedResponse(json)
        assertEquals(listOf("das Haus", "gehen", "schön", "der Baum", "fliegen"), parsed)
    }

    @Test
    fun testParseLemmatizedResponseWithMarkdownJsonBlock() {
        val raw = """
            Here is the normalized lemma list:
            ```json
            {
              "lemmas": [
                "das Buch",
                "der Tisch",
                "schreiben"
              ]
            }
            ```
            Hope this helps!
        """.trimIndent()

        val parsed = GeminiStoryService.parseLemmatizedResponse(raw)
        assertEquals(listOf("das Buch", "der Tisch", "schreiben"), parsed)
    }

    @Test
    fun testParseLemmatizedResponseWithParenthesesStripsExtraForms() {
        // If an LLM returns forms in parentheses like "das Haus (die Häuser), gehen (ging, gegangen)"
        // It must NOT turn into 4 separate tokens, but strip the parentheses and keep the base lemma!
        val raw = """
            das Haus (die Häuser, dem Haus), gehen (ging, gegangen), schön (schöner)
        """.trimIndent()

        val parsed = GeminiStoryService.parseLemmatizedResponse(raw)
        assertEquals(listOf("das Haus", "gehen", "schön"), parsed)
    }

    @Test
    fun testParseLemmatizedResponseFiltersMetaCommentaryAndLongSentences() {
        val raw = """
            1. das Buch
            2. der Tisch
            существительное: дом
            Вот список слов для изучения
            This is an explanation sentence with way too many words inside it
            3. laufen
        """.trimIndent()

        val parsed = GeminiStoryService.parseLemmatizedResponse(raw)
        assertEquals(listOf("das Buch", "der Tisch", "laufen"), parsed)
    }

    @Test
    fun testParseLemmatizedResponseDirectArray() {
        val raw = """["man", "go", "bright", "apple"]"""
        val parsed = GeminiStoryService.parseLemmatizedResponse(raw)
        assertEquals(listOf("man", "go", "bright", "apple"), parsed)
    }

    @Test
    fun testExtractJsonSubstring() {
        val textWithCodeBlock = "```json\n{\"lemmas\": [\"a\", \"b\"]}\n```"
        assertEquals("{\"lemmas\": [\"a\", \"b\"]}", GeminiStoryService.extractJsonSubstring(textWithCodeBlock))

        val textWithBraces = "Some preamble {\"lemmas\": [\"test\"]} some trailing text"
        assertEquals("{\"lemmas\": [\"test\"]}", GeminiStoryService.extractJsonSubstring(textWithBraces))

        val textWithArray = "Text [\"one\", \"two\"] text"
        assertEquals("[\"one\", \"two\"]", GeminiStoryService.extractJsonSubstring(textWithArray))
    }
}
