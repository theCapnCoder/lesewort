package com.example.ui

data class ChapterStatsData(
    val complexity: Double,
    val newCount: Int,
    val learningCount: Int,
    val learnedCount: Int,
    val uniqueCount: Int
)

data class BookUnknownWord(
    val word: String,
    val cleanWord: String = word.lowercase(),
    val count: Int
)
