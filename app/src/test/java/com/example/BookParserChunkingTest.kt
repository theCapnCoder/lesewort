package com.example

import com.example.data.parser.BookParser
import com.example.data.parser.ParsedChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookParserChunkingTest {

    @Test
    fun testParseSrt_chunksLargeSubtitleFileByHour() {
        // Create synthetic SRT representing a 4-hour audiobook
        val srtBuilder = StringBuilder()
        var currentMs = 0L
        val stepMs = 5000L // 5 seconds per line (~15 words)
        var cueIndex = 1

        // Generate ~3000 cues (about 4.1 hours and ~35,000 words)
        for (i in 1..3000) {
            val startSec = currentMs
            val endSec = currentMs + stepMs
            val startStr = "%02d:%02d:%02d,000".format(startSec / 3600000, (startSec % 3600000) / 60000, (startSec % 60000) / 1000)
            val endStr = "%02d:%02d:%02d,000".format(endSec / 3600000, (endSec % 3600000) / 60000, (endSec % 60000) / 1000)

            srtBuilder.append("$cueIndex\n")
            srtBuilder.append("$startStr --> $endStr\n")
            srtBuilder.append("Dies ist ein deutscher Satz für das Hörbuch und das Lernen der Sprache Nummer $i.\n\n")

            currentMs = endSec
            cueIndex++
        }

        val parsed = BookParser.parseSrt("Audiobook.srt", srtBuilder.toString())

        assertEquals("Audiobook", parsed.title)
        assertEquals("Субтитры", parsed.author)
        // 4.1 hours should produce approximately 4-5 chapters (1 hour each)
        assertTrue("Chapters size should be 4 or 5 for 4-hour audiobook, got ${parsed.chapters.size}", parsed.chapters.size in 4..6)

        // Verify chapter title format with 1-hour time range
        val firstChapter = parsed.chapters[0]
        assertTrue(firstChapter.title.startsWith("Часть 1 ("))
        assertTrue(firstChapter.title.contains(" - "))
        assertTrue(firstChapter.content.contains("\n\n"))
    }

    @Test
    fun testParseSrt_withoutPunctuation_stillChunksReliablyByHour() {
        // Subtitles from automated speech recognition with 3.5 hours of audio
        val srtBuilder = StringBuilder()
        var currentMs = 0L
        val stepMs = 4000L
        var cueIndex = 1

        // 3.5 hours = ~3150 cues
        for (i in 1..3150) {
            val startSec = currentMs
            val endSec = currentMs + stepMs
            val startStr = "%02d:%02d:%02d,000".format(startSec / 3600000, (startSec % 3600000) / 60000, (startSec % 60000) / 1000)
            val endStr = "%02d:%02d:%02d,000".format(endSec / 3600000, (endSec % 3600000) / 60000, (endSec % 60000) / 1000)

            srtBuilder.append("$cueIndex\n")
            srtBuilder.append("$startStr --> $endStr\n")
            srtBuilder.append("und hier sprechen wir weiter ohne jeden punkt oder fragezeichen wort $i\n\n")

            currentMs = endSec
            cueIndex++
        }

        val parsed = BookParser.parseSrt("AudiobookNoPunctuation.srt", srtBuilder.toString())

        assertTrue("Should split into multiple chapters, got ${parsed.chapters.size}", parsed.chapters.size >= 3)
    }

    @Test
    fun testParseTxt_chunksMonolithicLargeTextFile() {
        val txtBuilder = StringBuilder()
        // Generate a large text with 50 paragraphs
        for (p in 1..50) {
            txtBuilder.append("Absatz $p. Dies ist ein sehr interessanter Text für das Deutschlernen mit vielen Wörtern und Sätzen, die analysiert werden müssen. ")
            txtBuilder.append("Jeder Absatz hat mehrere Sätze und Wörter, damit der Text realistisch aussieht und natürlich klingt.\n\n")
        }

        val parsedDirect = BookParser.splitLargeChapters(
            bookTitle = "Story",
            chapters = listOf(ParsedChapter("Story", txtBuilder.toString())),
            targetWords = 300,
            maxWords = 500
        )

        assertTrue("Should be split into multiple chapters, got ${parsedDirect.size}", parsedDirect.size > 1)
        assertTrue(parsedDirect[0].title.contains("Часть 1"))
    }
}
