package com.example.data.parser

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Facade for book parsing operations.
 * Provides a unified interface for parsing different book formats (EPUB, TXT, SRT/VTT)
 * by delegating to specialized parser classes.
 *
 * This class follows the Facade pattern and maintains backward compatibility
 * with the original BookParser object.
 */
object BookParser {

    private val epubParser = EpubParser()
    private val txtParser = TxtParser()
    private val subtitleParser = SubtitleParser()

    /**
     * Parses an EPUB file.
     *
     * @param file The EPUB file to parse
     * @return ParsedBook containing metadata and chapters, or null if parsing failed
     */
    fun parseEpubFile(file: File): ParsedBook? {
        return epubParser.parseEpub(file)
    }

    /**
     * Parses content from a URI (handles EPUB, TXT, SRT/VTT based on file extension).
     *
     * @param context The Android context
     * @param uri The URI pointing to the content
     * @return ParsedBook containing metadata and chapters, or null if parsing failed
     */
    fun parseUri(context: Context, uri: Uri): ParsedBook? {
        val contentResolver = context.contentResolver
        val name = getFileName(context, uri) ?: "imported_book"
        val extension = name.substringAfterLast(".").lowercase()

        // Copy uri to temp file to handle with standard APIs
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}_$name")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val parsedBook = try {
            when (extension) {
                "epub" -> epubParser.parseEpub(tempFile)
                "srt", "vtt" -> {
                    val text = textProcessor.readFileContent(tempFile)
                    subtitleParser.parseSrt(name, text)
                }
                "txt" -> {
                    val text = textProcessor.readFileContent(tempFile)
                    txtParser.parseTxt(name, text)
                }
                else -> {
                    val text = textProcessor.readFileContent(tempFile)
                    if (timestampUtils.isSrtFormat(text)) {
                        subtitleParser.parseSrt(name, text)
                    } else if (text.isNotEmpty()) {
                        txtParser.parseTxt(name, text)
                    } else {
                        null
                    }
                }
            }
        } finally {
            tempFile.delete()
        }

        return parsedBook
    }

    private fun readFileContent(file: File): String {
        return TextProcessor().readFileContent(file)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    // Public methods for backward compatibility
    fun parseTxt(fileName: String, text: String): ParsedBook {
        return txtParser.parseTxt(fileName, text)
    }

    fun parseSrt(fileName: String, text: String): ParsedBook {
        return subtitleParser.parseSrt(fileName, text)
    }

    fun parseStory(title: String, author: String, text: String): ParsedBook {
        // This was in the original BookParser but delegates to TxtParser logic
        return txtParser.parseTxt(title, text).copy(author = author)
    }

    fun splitLargeChapters(
        bookTitle: String,
        chapters: List<ParsedChapter>,
        targetWords: Int = 8000,
        maxWords: Int = 11000
    ): List<ParsedChapter> {
        return chapterManager.splitLargeChapters(bookTitle, chapters, targetWords, maxWords)
    }

    fun splitContentIntoParts(
        content: String,
        baseTitle: String,
        targetWords: Int = 8000
    ): List<ParsedChapter> {
        return chapterManager.splitContentIntoParts(content, baseTitle, targetWords)
    }

    fun fastCountWords(str: String): Int {
        return TextProcessor.fastCountWords(str)
    }

    fun readFileContent(file: File): String {
        return TextProcessor().readFileContent(file)
    }

    fun isSrtFormat(text: String): Boolean {
        return timestampUtils.isSrtFormat(text)
    }

    fun extractHeading(content: String): String? {
        return chapterManager.extractHeading(content)
    }

    fun cleanHtml(html: String): String {
        return TextProcessor.cleanHtml(html)
    }

    // Private instances of utility classes to avoid recreating them
    private val textProcessor = TextProcessor()
    private val timestampUtils = TimestampUtils()
}