package com.example.data.parser

import java.util.regex.Regex

/**
 * Utility class for timestamp parsing and formatting operations.
 * Handles conversion between timestamp strings and milliseconds.
 */
object TimestampUtils {

    private val srtTimestampRegex = Regex("""\d{1,2}:\d{2}:\d{2}[.,]\d{3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[.,]\d{3}""")
    private val vttTimestampRegex = Regex("""\d{1,2}:\d{2}[.,]\d{3}\s*-->\s*\d{1,2}:\d{2}[.,]\d{3}""")
    private val timestampRegex = Regex("""(\d{1,2}:\d{2}(?::\d{2})?[.,]\d{2,3})\s*-->\s*(\d{1,2}:\d{2}(?::\d{2})?[.,]\d{2,3})""", RegexOption.IGNORE_CASE)
    private val numberOnlyRegex = Regex("""^\d+$""")

    /**
     * Checks if text is in SRT or VTT subtitle format.
     *
     * @param text The text to check
     * @return True if the text appears to be subtitle format
     */
    fun isSrtFormat(text: String): Boolean {
        return srtTimestampRegex.containsMatchIn(text) ||
               vttTimestampRegex.containsMatchIn(text) ||
               text.contains("-->")
    }

    /**
     * Parses a timestamp string to milliseconds.
     *
     * @param timeStr The timestamp string (e.g., "00:01:23,456" or "01:23.456")
     * @return The timestamp in milliseconds or null if parsing failed
     */
    fun parseTimestampToMs(timeStr: String): Long? {
        val clean = timeStr.trim().replace(',', '.')
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (hours * 3600 + minutes * 60 + seconds) * 1000L + millis
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    (minutes * 60 + seconds) * 1000L + millis
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formats milliseconds as a timestamp string.
     *
     * @param ms The timestamp in milliseconds
     * @param showHours Whether to always show hours component
     * @return Formatted timestamp string
     */
    fun formatTimestamp(ms: Long, showHours: Boolean): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (showHours || hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Extracts subtitle cues from raw subtitle text.
     *
     * @param rawText The raw subtitle text (SRT or VTT format)
     * @return List of subtitle cues
     */
    fun extractSubtitleCues(rawText: String): List<SubtitleCue> {
        val tagRegex = Regex("""<[^>]*>""")
        val srtCodeRegex = Regex("""\{[^}]*\}""")

        val lines = rawText.lines()
        val cues = mutableListOf<SubtitleCue>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || line.startsWith("WEBVTT", ignoreCase = true) || line.startsWith("NOTE", ignoreCase = true)) {
                i++
                continue
            }

            val tsMatch = timestampRegex.find(line)
            if (tsMatch != null) {
                val startMs = parseTimestampToMs(tsMatch.groupValues[1]) ?: 0L
                val endMs = parseTimestampToMs(tsMatch.groupValues[2]) ?: startMs

                i++
                val textBuf = mutableListOf<String>()
                while (i < lines.size) {
                    val subLine = lines[i].trim()
                    if (subLine.isEmpty()) break
                    if (timestampRegex.containsMatchIn(subLine)) break
                    if (numberOnlyRegex.matches(subLine) && i + 1 < lines.size && timestampRegex.containsMatchIn(lines[i + 1])) {
                        break
                    }

                    val cleaned = subLine
                        .replace(tagRegex, "")
                        .replace(srtCodeRegex, "")
                        .replace("&nbsp;", " ")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&#160;", " ")
                        .trim()

                    if (cleaned.isNotEmpty()) {
                        textBuf.add(cleaned)
                    }
                    i++
                }

                if (textBuf.isNotEmpty()) {
                    cues.add(SubtitleCue(startMs, endMs, textBuf.joinToString(" ")))
                }
            } else {
                i++
            }
        }

        return cues
    }

    /**
     * Extracts clean text from SRT/VTT subtitle content.
     *
     * @param rawText The raw subtitle text
     * @return Clean text suitable for processing as a book
     */
    fun extractCleanTextFromSrt(rawText: String): String {
        val tagRegex = Regex("""<[^>]*>""")
        val srtCodeRegex = Regex("""\{[^}]*\}""")
        val timestampRegex = Regex("""\d{1,2}:\d{2}:\d{2}[.,]\d{3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[.,]\d{3}.*""", RegexOption.IGNORE_CASE)
        val numberOnlyRegex = Regex("""^\d+$""")

        val lines = rawText.lines()
        val cleanSubtitleLines = mutableListOf<String>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.isEmpty() || line.startsWith("WEBVTT", ignoreCase = true) || line.startsWith("NOTE", ignoreCase = true)) {
                i++
                continue
            }

            if (timestampRegex.matches(line) || line.contains("-->")) {
                i++
                val textBuf = mutableListOf<String>()
                while (i < lines.size) {
                    val subLine = lines[i].trim()
                    if (subLine.isEmpty()) {
                        break
                    }
                    if (timestampRegex.matches(subLine) || subLine.contains("-->")) {
                        break
                    }
                    if (numberOnlyRegex.matches(subLine) && i + 1 < lines.size && (lines[i + 1].contains("-->") || timestampRegex.matches(lines[i + 1].trim()))) {
                        break
                    }

                    val cleaned = subLine
                        .replace(tagRegex, "")
                        .replace(srtCodeRegex, "")
                        .replace("&nbsp;", " ")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .trim()

                    if (cleaned.isNotEmpty()) {
                        textBuf.add(cleaned)
                    }
                    i++
                }
                if (textBuf.isNotEmpty()) {
                    cleanSubtitleLines.add(textBuf.joinToString(" "))
                }
            } else if (numberOnlyRegex.matches(line) && i + 1 < lines.size && (lines[i + 1].contains("-->") || timestampRegex.matches(lines[i + 1].trim()))) {
                i++
            } else {
                val cleaned = line
                    .replace(tagRegex, "")
                    .replace(srtCodeRegex, "")
                    .replace("&nbsp;", " ")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
                if (cleaned.isNotEmpty() && !numberOnlyRegex.matches(cleaned)) {
                    cleanSubtitleLines.add(cleaned)
                }
                i++
            }
        }

        val paragraphList = mutableListOf<String>()
        var currentPara = StringBuilder()
        var sentenceCountInPara = 0
        val sentenceEndRegex = Regex("""[.!?]["'»”]?$""")

        for (sub in cleanSubtitleLines) {
            if (currentPara.isNotEmpty()) {
                currentPara.append(" ")
            }
            currentPara.append(sub)

            val endsWithSentencePunctuation = sentenceEndRegex.containsMatchIn(sub)
            if (endsWithSentencePunctuation) {
                sentenceCountInPara++
                if (sentenceCountInPara >= 3 || currentPara.length >= 200) {
                    paragraphList.add(currentPara.toString().trim())
                    currentPara = StringBuilder()
                    sentenceCountInPara = 0
                }
            }
        }

        if (currentPara.isNotEmpty()) {
            paragraphList.add(currentPara.toString().trim())
        }

        return paragraphList.joinToString("\n\n")
    }
}