package com.example

import com.example.ui.components.BookFormatType
import com.example.ui.components.detectBookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BookCoverAndStatsTest {

    @Test
    fun testDetectBookFormat_srtFiles() {
        assertEquals(BookFormatType.SRT, detectBookFormat("Friends.S01E01.srt", "Unknown Author", null))
        assertEquals(BookFormatType.SRT, detectBookFormat("Movie", "Субтитры", null))
        assertEquals(BookFormatType.SRT, detectBookFormat("Show", "English Subtitles", null))
        assertEquals(BookFormatType.SRT, detectBookFormat("Show", "", "/storage/emulated/0/sub.srt"))
        assertEquals(BookFormatType.SRT, detectBookFormat("Show.vtt", "", null))
    }

    @Test
    fun testDetectBookFormat_txtFiles() {
        assertEquals(BookFormatType.TXT, detectBookFormat("notes.txt", "Unknown Author", null))
        assertEquals(BookFormatType.TXT, detectBookFormat("Story", "Текст", null))
        assertEquals(BookFormatType.TXT, detectBookFormat("Story", "Документ", null))
        assertEquals(BookFormatType.TXT, detectBookFormat("Lecture", "", "/storage/lecture.txt"))
    }

    @Test
    fun testDetectBookFormat_standardBooks() {
        assertEquals(BookFormatType.EPUB_OR_BOOK, detectBookFormat("Harry Potter", "J.K. Rowling", "book.epub"))
        assertEquals(BookFormatType.EPUB_OR_BOOK, detectBookFormat("Faust", "Goethe", "faust.fb2"))
    }
}
