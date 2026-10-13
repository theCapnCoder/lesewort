package com.example.data.parser

import java.io.File

/**
 * Parser for TXT format books.
 * Handles extraction of metadata and chapters from plain text files.
 */
class TxtParser(
    private val textProcessor: TextProcessor = TextProcessor(),
    private val chapterManager: ChapterManager = ChapterManager()
) {

    /**
     * Parses a TXT file and returns a ParsedBook.
     *
     * @param fileName The name of the file (used for book title if not detected)
     * @param text The text content of the file
     * @return ParsedBook containing metadata and chapters
     */
    fun parseTxt(fileName: String, text: String): ParsedBook {
        val bookTitle = fileName.substringBeforeLast(".")
        val author = "Текст"

        val trimmedText = text.removePrefix("﻿").trim()
        if (trimmedText.isEmpty()) {
            return ParsedBook(bookTitle, author, listOf(ParsedChapter(bookTitle, "")))
        }

        // 1. Try to detect natural chapter headings in the text
        val rawChapters = chapterManager.splitTxtByDetectedChapters(bookTitle, trimmedText)

        // 2. Ensure that no individual chapter is huge (> 2800 words), splitting at paragraph boundaries
        val manageableChapters = chapterManager.splitLargeChapters(bookTitle, rawChapters, targetWords = 1800, maxWords = 2800)
        val finalChapters = chapterManager.postProcessChapterTitles(bookTitle, manageableChapters)

        return ParsedBook(bookTitle, author, finalChapters)
    }
}