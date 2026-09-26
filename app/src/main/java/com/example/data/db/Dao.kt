package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownWordDao {
    @Query("SELECT * FROM known_words ORDER BY word ASC")
    fun getAllKnownWords(): Flow<List<KnownWord>>

    @Query("SELECT * FROM known_words")
    suspend fun getAllKnownWordsList(): List<KnownWord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnownWord(knownWord: KnownWord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnownWords(knownWords: List<KnownWord>): List<Long>

    @Query("DELETE FROM known_words WHERE id = :id")
    suspend fun deleteKnownWord(id: Int)

    @Query("DELETE FROM known_words WHERE LOWER(word) = LOWER(:word) AND language = :language")
    suspend fun deleteKnownWordByWordAndLanguage(word: String, language: String)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY id DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY id DESC")
    suspend fun getAllBooksList(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Int): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("""
        UPDATE books 
        SET total_chapters = :totalChapters,
            total_pages = :totalPages,
            learned_words_count = :learnedWordsCount,
            learning_words_count = :learningWordsCount,
            new_words_count = :newWordsCount,
            comprehension_percent = :comprehensionPercent,
            complexity_percent = :complexityPercent
        WHERE id = :bookId
    """)
    suspend fun updateBookStats(
        bookId: Int,
        totalChapters: Int,
        totalPages: Int,
        learnedWordsCount: Int,
        learningWordsCount: Int,
        newWordsCount: Int,
        comprehensionPercent: Double,
        complexityPercent: Double
    )

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Int)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapter_index ASC")
    fun getChaptersForBook(bookId: Int): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapter_index ASC")
    suspend fun getChaptersForBookList(bookId: Int): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: Int)
}

@Dao
interface StudyWordDao {
    @Query("SELECT * FROM study_words ORDER BY added_at DESC")
    fun getAllStudyWords(): Flow<List<StudyWord>>

    @Query("SELECT * FROM study_words")
    suspend fun getAllStudyWordsList(): List<StudyWord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyWord(studyWord: StudyWord): Long

    @Query("DELETE FROM study_words WHERE id = :id")
    suspend fun deleteStudyWord(id: Int)

    @Query("DELETE FROM study_words")
    suspend fun deleteAllStudyWords()

    @Query("UPDATE study_words SET correct_count = :correctCount WHERE id = :id")
    suspend fun updateCorrectCount(id: Int, correctCount: Int)

    @Query("DELETE FROM study_words WHERE LOWER(word) = LOWER(:word) AND language = :language")
    suspend fun deleteStudyWordByWordAndLanguage(word: String, language: String)

    @Query("SELECT * FROM study_words WHERE LOWER(word) = LOWER(:word) AND language = :language LIMIT 1")
    suspend fun getStudyWord(word: String, language: String): StudyWord?
}

@Dao
interface WordActivityDao {
    @Query("SELECT * FROM word_activity_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<WordActivityEvent>>

    @Query("SELECT * FROM word_activity_events WHERE language = :language ORDER BY timestamp DESC")
    fun getEventsByLanguageFlow(language: String): Flow<List<WordActivityEvent>>

    @Query("SELECT * FROM word_activity_events ORDER BY timestamp DESC")
    suspend fun getAllEventsList(): List<WordActivityEvent>

    @Query("SELECT COUNT(*) FROM word_activity_events")
    suspend fun getEventsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: WordActivityEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<WordActivityEvent>): List<Long>

    @Query("DELETE FROM word_activity_events WHERE id = :id")
    suspend fun deleteEvent(id: Int)

    @Query("DELETE FROM word_activity_events WHERE LOWER(word) = LOWER(:word) AND language = :language")
    suspend fun deleteEventsByWordAndLanguage(word: String, language: String)

    @Query("DELETE FROM word_activity_events")
    suspend fun deleteAllEvents()
}
