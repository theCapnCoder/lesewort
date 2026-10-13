package com.example.data.parser

/**
 * Data classes used across book parsing operations.
 */

/**
 * Represents a parsed chapter with title and content.
 */
data class ParsedChapter(
    val title: String,
    val content: String
)

/**
 * Represents a parsed book with metadata and chapters.
 */
data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<ParsedChapter>,
    val coverBytes: ByteArray? = null
)

/**
 * Represents an item in the EPUB manifest.
 */
data class ManifestItem(
    val id: String,
    val href: String,
    val properties: String,
    val mediaType: String
)

/**
 * Represents a subtitle cue with timing and text.
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)