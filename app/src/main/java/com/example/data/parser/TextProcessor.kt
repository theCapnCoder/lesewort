package com.example.data.parser

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for text processing operations.
 * Handles HTML cleaning, word counting, file reading, and other text-related utilities.
 */
object TextProcessor {

    /**
     * Reads file content with proper encoding detection.
     *
     * @param file The file to read
     * @return The file content as a string
     */
    fun readFileContent(file: File): String {
        val bytes = try { file.readBytes() } catch (e: Exception) { return "" }
        if (bytes.isEmpty()) return ""

        // Check BOMs
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16LE"))
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charset.forName("UTF-16BE"))
        }

        // Try UTF-8 first
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            try {
                String(bytes, Charset.forName("windows-1251"))
            } catch (e2: Exception) {
                String(bytes, Charset.defaultCharset())
            }
        }
    }

    /**
     * Cleans HTML content by removing tags and normalizing whitespace.
     *
     * @param html The HTML content to clean
     * @return Cleaned text content
     */
    fun cleanHtml(html: String): String {
        var text = html
        // Extract out unreadable head & script styles
        text = text.replace(Regex("""<head[^>]*>.*?</head>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("""<script[^>]*>.*?</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        text = text.replace(Regex("""<style[^>]*>.*?</style>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")

        // Treat spacing elements
        text = text.replace(Regex("""<p\b[^>]*>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""</p>""", RegexOption.IGNORE_CASE), "\n\n")
        text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("""<div\b[^>]*>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""</div>""", RegexOption.IGNORE_CASE), "\n")

        // Strip other tags
        text = text.replace(Regex("""<[^>]*>""", RegexOption.DOT_MATCHES_ALL), "")

        // Standard conversions
        text = text.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#160;", " ")

        text = text.replace(Regex("\n{3,}"), "\n\n")
        return text.trim()
    }

    /**
     * Counts words in a string efficiently.
     *
     * @param str The string to count words in
     * @return The number of words
     */
    fun fastCountWords(str: String): Int {
        if (str.isEmpty()) return 0
        var count = 0
        var inWord = false
        for (i in 0 until str.length) {
            val c = str[i]
            if (!c.isWhitespace()) {
                if (!inWord) {
                    count++
                    inWord = true
                }
            } else {
                inWord = false
            }
        }
        return count
    }
}