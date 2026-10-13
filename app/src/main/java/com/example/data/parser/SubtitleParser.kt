package com.example.data.parser

/**
 * Parser for SRT/VTT subtitle format books.
 * Handles extraction of metadata and chapters from subtitle files.
 */
class SubtitleParser(
    private val textProcessor: TextProcessor = TextProcessor(),
    private val chapterManager: ChapterManager = ChapterManager(),
    private val timestampUtils: TimestampUtils = TimestampUtils()
) {

    /**
     * Parses SRT/VTT subtitle content and returns a ParsedBook.
     *
     * @param fileName The name of the file (used for book title)
     * @param text The subtitle content
     * @return ParsedBook containing metadata and chapters
     */
    fun parseSrt(fileName: String, text: String): ParsedBook {
        val bookTitle = fileName.substringBeforeLast(".")
        val cues = timestampUtils.extractSubtitleCues(text)

        if (cues.isEmpty()) {
            val cleanText = timestampUtils.extractCleanTextFromSrt(text)
            return TxtParser().parseTxt(fileName, cleanText).copy(author = "Субтитры")
        }

        val showHours = cues.last().endMs >= 3600_000L || true
        val chapters = mutableListOf<ParsedChapter>()
        val currentChapterCues = mutableListOf<SubtitleCue>()
        var currentChapterWordCount = 0
        val targetChapterWords = 8000
        val hardMaxChapterWords = 10000
        val targetChapterDurationMs = 60 * 60 * 1000L // 60 minutes (1 hour)
        val sentenceEndRegex = Regex("""[.!?]["'»”]?$""")
        val clauseEndRegex = Regex("""[,;:]["'»”]?$""")

        fun flushChapter(partNumber: Int) {
            if (currentChapterCues.isEmpty()) return

            val startMs = currentChapterCues.first().startMs
            val endMs = currentChapterCues.last().endMs
            val startStr = timestampUtils.formatTimestamp(startMs, showHours)
            val endStr = timestampUtils.formatTimestamp(endMs, showHours)
            val chapterTitle = "Часть $partNumber ($startStr - $endStr)"

            // Group subtitle cues into paragraphs of 3-4 sentences
            val paragraphList = mutableListOf<String>()
            var currentPara = StringBuilder()
            var sentenceCountInPara = 0

            for (cue in currentChapterCues) {
                if (currentPara.isNotEmpty()) {
                    currentPara.append(" ")
                }
                currentPara.append(cue.text)

                val endsSentence = sentenceEndRegex.containsMatchIn(cue.text)
                if (endsSentence) {
                    sentenceCountInPara++
                    if (sentenceCountInPara >= 3 || currentPara.length >= 250) {
                        paragraphList.add(currentPara.toString().trim())
                        currentPara = StringBuilder()
                        sentenceCountInPara = 0
                    }
                } else if (currentPara.length >= 350) {
                    paragraphList.add(currentPara.toString().trim())
                    currentPara = StringBuilder()
                    sentenceCountInPara = 0
                }
            }

            if (currentPara.isNotEmpty()) {
                paragraphList.add(currentPara.toString().trim())
            }

            val chapterContent = paragraphList.joinToString("\n\n")
            if (chapterContent.isNotEmpty()) {
                chapters.add(ParsedChapter(chapterTitle, chapterContent))
            }

            currentChapterCues.clear()
            currentChapterWordCount = 0
        }

        var partNumber = 1
        var chapterStartMs = cues.first().startMs

        for (cue in cues) {
            val cueWords = textProcessor.fastCountWords(cue.text)
            currentChapterCues.add(cue)
            currentChapterWordCount += cueWords

            val currentChapterDuration = cue.endMs - chapterStartMs
            val isSentenceBoundary = sentenceEndRegex.containsMatchIn(cue.text)
            val isClauseBoundary = isSentenceBoundary || clauseEndRegex.containsMatchIn(cue.text)

            val shouldFlush = when {
                currentChapterDuration >= 55 * 60 * 1000L && isSentenceBoundary -> true
                currentChapterDuration >= 60 * 60 * 1000L -> true
                currentChapterWordCount >= hardMaxChapterWords -> true
                currentChapterWordCount >= targetChapterWords && isSentenceBoundary -> true
                else -> false
            }

            if (shouldFlush) {
                flushChapter(partNumber)
                partNumber++
                chapterStartMs = cue.endMs
            }
        }
        flushChapter(partNumber)

        val manageable = if (chapters.isNotEmpty()) {
            chapterManager.splitLargeChapters(bookTitle, chapters, targetWords = 8000, maxWords = 10000)
        } else {
            val cleanText = timestampUtils.extractCleanTextFromSrt(text)
            val parts = chapterManager.splitContentIntoParts(cleanText, "Часть 1", targetWords = 8000)
            parts
        }

        return ParsedBook(bookTitle, "Субтитры", manageable)
    }
}