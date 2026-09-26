package com.example.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "known_words",
    indices = [Index(value = ["word", "language"], unique = true)]
)
data class KnownWord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val status: Int = 2,
    val language: String = "de"
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val author: String,
    @ColumnInfo(name = "file_path") val filePath: String?,
    @ColumnInfo(name = "total_words") val totalWords: Int,
    @ColumnInfo(name = "unique_words_count") val uniqueWordsCount: Int,
    @ColumnInfo(name = "current_chapter_index") val currentChapterIndex: Int = 0,
    @ColumnInfo(name = "current_scroll_position") val currentScrollPosition: Int = 0,
    @ColumnInfo(name = "last_opened", defaultValue = "0") val lastOpened: Long = 0L,
    @ColumnInfo(name = "language", defaultValue = "'de'") val language: String = "de",
    @ColumnInfo(name = "total_chapters", defaultValue = "1") val totalChapters: Int = 1,
    @ColumnInfo(name = "total_pages", defaultValue = "1") val totalPages: Int = 1,
    @ColumnInfo(name = "learned_words_count", defaultValue = "0") val learnedWordsCount: Int = 0,
    @ColumnInfo(name = "learning_words_count", defaultValue = "0") val learningWordsCount: Int = 0,
    @ColumnInfo(name = "new_words_count", defaultValue = "0") val newWordsCount: Int = 0,
    @ColumnInfo(name = "comprehension_percent", defaultValue = "0.0") val comprehensionPercent: Double = 0.0,
    @ColumnInfo(name = "complexity_percent", defaultValue = "0.0") val complexityPercent: Double = 0.0
)

@Entity(
    tableName = "chapters",
    indices = [Index(value = ["bookId"])]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "bookId") val bookId: Int,
    val title: String,
    val content: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int
)

@Entity(
    tableName = "study_words",
    indices = [Index(value = ["word", "language"], unique = true)]
)
data class StudyWord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val translation: String,
    val language: String = "de",
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "correct_count", defaultValue = "0") val correctCount: Int = 0
)

@Entity(
    tableName = "word_activity_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["language", "timestamp"]),
        Index(value = ["word", "language"])
    ]
)
data class WordActivityEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val status: Int, // 1 = на изучение, 2 = выучено
    val language: String = "de",
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
