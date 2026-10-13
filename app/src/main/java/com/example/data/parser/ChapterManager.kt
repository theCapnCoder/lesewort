package com.example.data.parser

import android.text.TextUtils
import java.util.regex.Regex
import java.util.regex.RegexOption

/**
 * Manages chapter detection, splitting, and post-processing operations.
 * Handles logic for detecting chapter boundaries, splitting large chapters,
 * and processing chapter titles.
 */
class ChapterManager {

    companion object {
        private val chapterRegex = Regex(
            """^\s*(?:глава|chapter|kapitel|chapitre|part|часть|раздел|section|episode|эпизод|книга|book|第|章)\s*[ivxldcm0-9_]*.*$""",
            RegexOption.IGNORE_CASE
        )
        private val romanNumeralRegex = Regex("""^\s*[ivxldcm]+\.?\s*$""", RegexOption.IGNORE_CASE)
        private val markdownHeaderRegex = Regex("""^#{1,3}\s+.*$""")
        private val dividerRegex = Regex("""^\s*[*-_=~]{3,}\s*$""")
        private val genericRegex = Regex(
            """^(?:глава|chapter|part|часть|раздел|section|episode|эпизод|книга|book)\s*[ivxldcm0-9_]*\.?\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val numbersRegex = Regex("""^[ivxldcm0-9]+\.?\s*$""", RegexOption.IGNORE_CASE)
        private val sentenceEndRegex = Regex("""[.!?]["'»”]?$""")
        private val sentenceBoundaries = Regex("""[.!?]\s+[A-ZА-ЯЁ]""")
    }

    /**
     * Extracts heading from HTML content.
     *
     * @param content The HTML content to extract heading from
     * @return The heading text or null if not found
     */
    fun extractHeading(content: String): String? {
        val headingRegex = Regex("""<(h[1-6])[^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val match = headingRegex.find(content)
        return match?.groupValues?.get(2)?.replace(Regex("<[^>]*>"), "")?.trim()
    }

    /**
     * Normalizes a file path by resolving . and .. segments.
     *
     * @param path The path to normalize
     * @return The normalized path
     */
    fun normalizePath(path: String): String {
        val parts = path.split("/")
        val resolvedList = mutableListOf<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) continue
            if (part == "..") {
                if (resolvedList.isNotEmpty()) resolvedList.removeAt(resolvedList.size - 1)
            } else {
                resolvedList.add(part)
            }
        }
        return resolvedList.joinToString("/")
    }

    /**
     * Splits plain text into chapters if heading markers are present,
     * or returns the entire text as a single chapter.
     *
     * @param bookTitle The title of the book
     * @param text The text to split into chapters
     * @return List of parsed chapters
     */
    fun splitTxtByDetectedChapters(bookTitle: String, text: String): List<ParsedChapter> {
        val lines = text.lines()
        val matches = mutableListOf<HeadingMatch>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.length > 80) continue

            if (chapterRegex.matches(line) || romanNumeralRegex.matches(line) || markdownHeaderRegex.matches(line)) {
                val cleanTitle = line.removePrefix("#").removePrefix("#").removePrefix("#").trim()
                if (cleanTitle.isNotEmpty() && !isSentencesProse(line)) {
                    matches.add(HeadingMatch(i, cleanTitle))
                }
            } else if (dividerRegex.matches(line) && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (nextLine.isNotEmpty() && nextLine.length <= 80 && !isSentencesProse(nextLine)) {
                    matches.add(HeadingMatch(i, nextLine))
                }
            }
        }

        // If we found at least 2 distinct chapter headings separated across the text, split by them
        if (matches.size >= 2) {
            val result = mutableListOf<ParsedChapter>()

            // Prologue / preface before the first chapter heading
            if (matches[0].lineIndex > 0) {
                val prologueContent = lines.subList(0, matches[0].lineIndex).joinToString("\n").trim()
                if (prologueContent.isNotEmpty()) {
                    result.add(ParsedChapter("Введение", prologueContent))
                }
            }

            for (mIndex in matches.indices) {
                val currentMatch = matches[mIndex]
                val startLine = currentMatch.lineIndex + 1
                val endLine = if (mIndex + 1 < matches.size) matches[mIndex + 1].lineIndex else lines.size

                val chapterContent = lines.subList(startLine, endLine).joinToString("\n").trim()
                if (chapterContent.isNotEmpty()) {
                    result.add(ParsedChapter(currentMatch.title, chapterContent))
                }
            }

            if (result.isNotEmpty()) {
                return result
            }
        }

        // Fallback: entire text in one chapter
        return listOf(ParsedChapter(bookTitle, text))
    }

    /**
     * Splits any chapter exceeding maxWords into comfortable, highly readable sub-parts.
     *
     * @param bookTitle The title of the book
     * @param chapters The chapters to potentially split
     * @param targetWords Target word count per chapter
     * @param maxWords Maximum word count before splitting
     * @return List of chapters (potentially split)
     */
    fun splitLargeChapters(
        bookTitle: String,
        chapters: List<ParsedChapter>,
        targetWords: Int = 8000,
        maxWords: Int = 11000
    ): List<ParsedChapter> {
        val result = mutableListOf<ParsedChapter>()

        for ((chIndex, chapter) in chapters.withIndex()) {
            val wordCount = TextProcessor.fastCountWords(chapter.content)
            if (wordCount <= maxWords) {
                result.add(chapter)
                continue
            }

            val baseTitle = chapter.title.ifBlank { "Глава ${chIndex + 1}" }
            val parts = splitContentIntoParts(chapter.content, baseTitle, targetWords)
            result.addAll(parts)
        }

        return result
    }

    /**
     * Splits large text into smaller parts by double newlines (\n\n), respecting sentence boundaries.
     *
     * @param content The content to split
     * @param baseTitle Base title for the parts
     * @param targetWords Target word count per part
     * @return List of parsed chapters (parts)
     */
    fun splitContentIntoParts(
        content: String,
        baseTitle: String,
        targetWords: Int = 8000
    ): List<ParsedChapter> {
        val rawParagraphs = content.split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawParagraphs.isEmpty()) {
            return listOf(ParsedChapter(baseTitle, content))
        }

        val parts = mutableListOf<ParsedChapter>()
        var currentPartParas = mutableListOf<String>()
        var currentWordCount = 0
        var partIndex = 1

        fun flushPart() {
            if (currentPartParas.isNotEmpty()) {
                val partContent = currentPartParas.joinToString("\n\n")
                val isGeneric = baseTitle.startsWith("Часть", ignoreCase = true) || baseTitle.startsWith("Глава", ignoreCase = true)
                val partTitle = if (isGeneric) {
                    "$baseTitle (Часть $partIndex)"
                } else {
                    "Часть $partIndex"
                }
                parts.add(ParsedChapter(partTitle, partContent))
                currentPartParas = mutableListOf()
                currentWordCount = 0
                partIndex++
            }
        }

        for (para in rawParagraphs) {
            val paraWords = TextProcessor.fastCountWords(para)

            // If a single paragraph is colossal (> 400 words), split it at sentence boundaries
            if (paraWords > 400) {
                val sentences = splitGiantParagraph(para)
                for (sentence in sentences) {
                    val sWords = TextProcessor.fastCountWords(sentence)
                    if (currentWordCount + sWords > targetWords && currentPartParas.isNotEmpty()) {
                        flushPart()
                    }
                    currentPartParas.add(sentence)
                    currentWordCount += sWords
                }
            } else {
                if (currentWordCount + paraWords > targetWords && currentPartParas.isNotEmpty()) {
                    flushPart()
                }
                currentPartParas.add(para)
                currentWordCount += paraWords
            }
        }
        flushPart()

        return if (parts.isNotEmpty()) parts else listOf(ParsedChapter(baseTitle, content))
    }

    private fun splitGiantParagraph(para: String): List<String> {
        val sentenceRegex = Regex("""(?<=[.!?]["'»”]?)\s+""")
        val rawSentences = para.split(sentenceRegex).map { it.trim() }.filter { it.isNotEmpty() }
        if (rawSentences.size <= 1) return listOf(para)

        val groups = mutableListOf<String>()
        var currentGroup = StringBuilder()
        var sentenceCount = 0

        for (s in rawSentences) {
            if (currentGroup.isNotEmpty()) {
                currentGroup.append(" ")
            }
            currentGroup.append(s)
            sentenceCount++

            if (sentenceCount >= 4 || currentGroup.length >= 350) {
                groups.add(currentGroup.toString().trim())
                currentGroup = StringBuilder()
                sentenceCount = 0
            }
        }

        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup.toString().trim())
        }

        return if (groups.isNotEmpty()) groups else listOf(para)
    }

    /**
     * Post-processes chapter titles to ensure they are meaningful and unique.
     *
     * @param bookTitle The title of the book
     * @param chapters The chapters to process
     * @return List of chapters with processed titles
     */
    fun postProcessChapterTitles(bookTitle: String, chapters: List<ParsedChapter>): List<ParsedChapter> {
        val bookTitleClean = bookTitle.trim().lowercase()
        val allSameTitle = chapters.distinctBy { it.title.trim().lowercase() }.size <= 1 && chapters.size > 1

        return chapters.mapIndexed { index, chapter ->
            val originalTitle = chapter.title.trim()
            val cleanTitle = originalTitle.lowercase()

            val finalTitle = when {
                originalTitle.isEmpty() -> {
                    "Глава ${index + 1}"
                }
                allSameTitle -> {
                    "Глава ${index + 1}"
                }
                cleanTitle == bookTitleClean && chapters.size > 1 -> {
                    "Часть ${index + 1}"
                }
                else -> originalTitle
            }
            chapter.copy(title = finalTitle)
        }
    }

    /**
     * Checks if a title is generic (like "Chapter 1", "Part I", etc.).
     *
     * @param title The title to check
     * @param bookTitle The book title for comparison
     * @return True if the title is generic
     */
    fun isGenericTitle(title: String, bookTitle: String): Boolean {
        val clean = title.trim().lowercase()
        if (clean.isEmpty()) return true

        val bookTitleClean = bookTitle.trim().lowercase()
        if (clean == bookTitleClean) return true

        // Pattern for generic titles: e.g. "Глава 1", "Chapter 1", "Part IV", etc.
        if (genericRegex.matches(clean)) return true

        // Just numbers: "1", "1.", "I", "II"
        if (numbersRegex.matches(clean)) return true

        return false
    }

    /**
     * Checks if a line looks like sentence prose (rather than a heading).
     *
     * @param line The line to check
     * @return True if the line looks like sentence prose
     */
    fun isSentencesProse(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false

        // If it has terminal punctuation and is relatively long, it's prose
        if ((trimmed.endsWith(".") || trimmed.endsWith("?") || trimmed.endsWith("!")) && trimmed.length > 40) {
            return true
        }

        // Multiple sentences check: contains ". " or "? " or "! " followed by an uppercase letter
        if (sentenceBoundaries.containsMatchIn(trimmed)) {
            return true
        }

        // Too many words: say, more than 10 words is unlikely to be a chapter title
        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size > 10) {
            return true
        }

        // Number only checking: if it is just a pure number/year/etc
        if (trimmed.all { it.isDigit() }) {
            return true
        }

        return false
    }

    /**
     * Extracts a title from lines of text, trying to find a meaningful heading.
     *
     * @param lines The lines of text to search
     * @param bookTitle The book title (to avoid matching it)
     * @param fallback Fallback title if nothing better is found
     * @return A suitable title or null
     */
    fun extractTitleFromLines(lines: List<String>, bookTitle: String, fallback: String): String? {
        if (lines.isEmpty()) return null

        for (i in 0 until minOf(lines.size, 4)) {
            val line = lines[i].trim()

            if (line.lowercase() == bookTitle.trim().lowercase()) {
                continue
            }

            if (line.length > 100) {
                continue
            }

            if (isGenericTitle(line, bookTitle)) {
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (nextLine.length in 2..100 && !isGenericTitle(nextLine, bookTitle) && !isSentencesProse(nextLine)) {
                        val cleanGeneric = line.trimEnd('.', ':', ' ', ',')
                        return "$cleanGeneric. $nextLine"
                    }
                }
                return line
            }

            if (line.length in 2..100 && !isSentencesProse(line)) {
                return line
            }
        }

        return if (fallback.isNotEmpty()) fallback else null
    }

    /**
     * Data class to hold heading match information during chapter detection.
     */
    private data class HeadingMatch(val lineIndex: Int, val title: String)
}