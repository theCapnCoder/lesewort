package com.example.data.repository

import android.content.Context
import com.example.data.db.*
import com.example.data.engine.LinguisticEngine
import com.example.data.parser.ParsedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BookRepository(
    private val knownWordDao: KnownWordDao,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val studyWordDao: StudyWordDao,
    private val wordActivityDao: WordActivityDao? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) {
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooks()
    val allKnownWords: Flow<List<KnownWord>> = knownWordDao.getAllKnownWords()
    val allStudyWords: Flow<List<StudyWord>> = studyWordDao.getAllStudyWords()
    val allWordActivityEvents: Flow<List<WordActivityEvent>> = 
        wordActivityDao?.getAllEventsFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun clearAllActivityEvents() = withContext(ioDispatcher) {
        wordActivityDao?.deleteAllEvents()
    }

    suspend fun clearSeededEventsOnceIfNeeded(context: Context) = withContext(ioDispatcher) {
        try {
            val prefs = context.getSharedPreferences("lesewort_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("word_activity_seeded_cleaned_v2", false)) {
                wordActivityDao?.deleteAllEvents()
                prefs.edit().putBoolean("word_activity_seeded_cleaned_v2", true).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("BookRepository", "Error clearing seeded activity events", e)
        }
    }

    suspend fun getAllBooksList(): List<BookEntity> = withContext(ioDispatcher) {
        bookDao.getAllBooksList()
    }

    suspend fun getAllStudyWordsList(): List<StudyWord> = withContext(ioDispatcher) {
        studyWordDao.getAllStudyWordsList()
    }

    suspend fun insertStudyWord(word: String, translation: String, language: String): Boolean = withContext(ioDispatcher) {
        val clean = LinguisticEngine.tokenizeWord(word)
        if (clean.isNotEmpty()) {
            studyWordDao.insertStudyWord(
                StudyWord(
                    word = clean,
                    translation = translation,
                    language = language
                )
            )
            true
        } else {
            false
        }
    }

    suspend fun deleteStudyWord(id: Int) = withContext(ioDispatcher) {
        studyWordDao.deleteStudyWord(id)
    }

    suspend fun deleteAllStudyWords() = withContext(ioDispatcher) {
        studyWordDao.deleteAllStudyWords()
    }

    suspend fun updateStudyWordCorrectCount(id: Int, correctCount: Int) = withContext(ioDispatcher) {
        studyWordDao.updateCorrectCount(id, correctCount)
    }

    suspend fun deleteStudyWordByWordAndLanguage(word: String, language: String) = withContext(ioDispatcher) {
        studyWordDao.deleteStudyWordByWordAndLanguage(word, language)
    }

    suspend fun getStudyWord(word: String, language: String): StudyWord? = withContext(ioDispatcher) {
        studyWordDao.getStudyWord(word, language)
    }

    suspend fun getAllKnownWordsList(): List<KnownWord> = withContext(ioDispatcher) {
        knownWordDao.getAllKnownWordsList()
    }

    suspend fun getKnownWordsSet(): Set<String> = withContext(ioDispatcher) {
        knownWordDao.getAllKnownWordsList().map { it.word.lowercase() }.toSet()
    }

    suspend fun insertKnownWord(rawWord: String, status: Int = 2, language: String = "de"): Boolean = withContext(ioDispatcher) {
        val clean = LinguisticEngine.tokenizeWord(rawWord)
        if (clean.isNotEmpty()) {
            knownWordDao.insertKnownWord(KnownWord(word = clean, status = status, language = language))
            wordActivityDao?.insertEvent(
                WordActivityEvent(
                    word = clean,
                    status = status,
                    language = language,
                    timestamp = System.currentTimeMillis()
                )
            )
            true
        } else {
            false
        }
    }

    suspend fun insertKnownWords(rawWords: List<String>, status: Int = 2, language: String = "de"): Int = withContext(ioDispatcher) {
        val cleanWords = rawWords.map { LinguisticEngine.tokenizeWord(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        
        val entities = cleanWords.map { KnownWord(word = it, status = status, language = language) }
        knownWordDao.insertKnownWords(entities)
        val now = System.currentTimeMillis()
        val eventEntities = cleanWords.map {
            WordActivityEvent(
                word = it,
                status = status,
                language = language,
                timestamp = now
            )
        }
        wordActivityDao?.insertEvents(eventEntities)
        cleanWords.size
    }

    suspend fun deleteKnownWord(id: Int) = withContext(ioDispatcher) {
        val wordEntity = knownWordDao.getAllKnownWordsList().find { it.id == id }
        knownWordDao.deleteKnownWord(id)
        if (wordEntity != null) {
            wordActivityDao?.deleteEventsByWordAndLanguage(wordEntity.word.trim().lowercase(), wordEntity.language)
        }
    }

    suspend fun deleteKnownWordByWord(word: String, language: String) = withContext(ioDispatcher) {
        val clean = word.trim().lowercase()
        knownWordDao.deleteKnownWordByWordAndLanguage(clean, language)
        wordActivityDao?.deleteEventsByWordAndLanguage(clean, language)
    }

    suspend fun getBookById(id: Int): BookEntity? = withContext(ioDispatcher) {
        bookDao.getBookById(id)
    }

    fun getBookChaptersFlow(bookId: Int): Flow<List<ChapterEntity>> {
        return chapterDao.getChaptersForBook(bookId)
    }

    suspend fun getBookChaptersList(bookId: Int): List<ChapterEntity> = withContext(ioDispatcher) {
        chapterDao.getChaptersForBookList(bookId)
    }

    suspend fun updateBookProgress(bookId: Int, chapterIndex: Int, scrollPosition: Int) = withContext(ioDispatcher) {
        val book = bookDao.getBookById(bookId)
        if (book != null) {
            val updated = book.copy(
                currentChapterIndex = chapterIndex,
                currentScrollPosition = scrollPosition
            )
            bookDao.updateBook(updated)
        }
    }

    suspend fun updateBookLastOpened(bookId: Int, lastOpened: Long) = withContext(ioDispatcher) {
        val book = bookDao.getBookById(bookId)
        if (book != null) {
            val updated = book.copy(
                lastOpened = lastOpened
            )
            bookDao.updateBook(updated)
        }
    }

    suspend fun updateBookStats(
        bookId: Int,
        totalChapters: Int,
        totalPages: Int,
        learnedWordsCount: Int,
        learningWordsCount: Int,
        newWordsCount: Int,
        comprehensionPercent: Double,
        complexityPercent: Double
    ) = withContext(ioDispatcher) {
        bookDao.updateBookStats(
            bookId = bookId,
            totalChapters = totalChapters,
            totalPages = totalPages,
            learnedWordsCount = learnedWordsCount,
            learningWordsCount = learningWordsCount,
            newWordsCount = newWordsCount,
            comprehensionPercent = comprehensionPercent,
            complexityPercent = complexityPercent
        )
    }

    suspend fun deleteBook(bookId: Int) = withContext(ioDispatcher) {
        chapterDao.deleteChaptersForBook(bookId)
        bookDao.deleteBookById(bookId)
    }

    suspend fun insertBookWithChapters(parsedBook: ParsedBook, language: String, filePath: String? = null): Long = withContext(ioDispatcher) {
        // 1. Tokenize entire text to extract statistics
        val fullContentText = parsedBook.chapters.joinToString("\n") { it.content }
        val tokens = LinguisticEngine.tokenizeText(fullContentText)
        val totalWordsCount = tokens.size
        val uniqueTokens = tokens.toSet()
        val uniqueWordsCount = uniqueTokens.size

        val knownWords = knownWordDao.getAllKnownWordsList().filter { it.language == language }
        val knownSet = knownWords.filter { it.status == 2 }.map { it.word.lowercase() }.toSet()
        val learningSet = knownWords.filter { it.status == 1 }.map { it.word.lowercase() }.toSet()

        val stats = LinguisticEngine.calculateStatistics(tokens, knownSet)
        val learnedCount = uniqueTokens.count { knownSet.contains(it) }
        val learningCount = uniqueTokens.count { learningSet.contains(it) }
        val newCount = uniqueTokens.size - learnedCount - learningCount

        val chapterPages = parsedBook.chapters.map { ch ->
            Math.max(1, (ch.content.length + 1199) / 1200)
        }
        val totalPages = chapterPages.sum().coerceAtLeast(1)

        // 2. Insert book with full precalculated and persistent stats
        val bookEntity = BookEntity(
            title = parsedBook.title,
            author = parsedBook.author,
            filePath = filePath,
            totalWords = totalWordsCount,
            uniqueWordsCount = uniqueWordsCount,
            currentChapterIndex = 0,
            currentScrollPosition = 0,
            language = language,
            totalChapters = parsedBook.chapters.size,
            totalPages = totalPages,
            learnedWordsCount = learnedCount,
            learningWordsCount = learningCount,
            newWordsCount = newCount,
            comprehensionPercent = stats.comprehensibleVolumePercent,
            complexityPercent = stats.complexityVolumePercent
        )
        val bookId = bookDao.insertBook(bookEntity).toInt()

        // 3. Insert chapter records
        val chapters = parsedBook.chapters.mapIndexed { index, chapter ->
            ChapterEntity(
                bookId = bookId,
                title = chapter.title,
                content = chapter.content,
                chapterIndex = index
            )
        }
        // Chunk inserts to stay safely below SQLite's limit of 999 parameters
        chapters.chunked(100).forEach { batch ->
            chapterDao.insertChapters(batch)
        }

        bookId.toLong()
    }
}
