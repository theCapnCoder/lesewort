package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences

data class LemmatizedVocabEntry(
    val words: List<String> = emptyList(),
    val rawCount: Int = 0,
    val processedRawWordsCount: Int = 0,
    val timestamp: Long = 0L
)

object LemmatizedVocabRepository {
    private const val PREFS_NAME = "lesewort_lemmatized_vocab"
    private const val KEY_WORDS_PREFIX = "lemmatized_words_"
    private const val KEY_RAW_COUNT_PREFIX = "lemmatized_raw_count_"
    private const val KEY_PROCESSED_WORDS_PREFIX = "lemmatized_processed_raw_"
    private const val KEY_TIMESTAMP_PREFIX = "lemmatized_ts_"

    const val TYPE_KNOWN = "known"
    const val TYPE_STUDY = "study"

    // Default portion size for manual step-by-step processing (1 click = 1 single AI request)
    // 200 words is the sweet spot for fast, reliable LLM lemmatization without hitting token/reasoning limits
    const val DEFAULT_BATCH_PORTION = 200

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLemmatizedWords(context: Context, language: String, type: String): LemmatizedVocabEntry {
        val lang = language.lowercase()
        val prefs = getPrefs(context)
        val raw = prefs.getString("${KEY_WORDS_PREFIX}${type}_$lang", null) ?: return LemmatizedVocabEntry()
        val rawCount = prefs.getInt("${KEY_RAW_COUNT_PREFIX}${type}_$lang", 0)
        val ts = prefs.getLong("${KEY_TIMESTAMP_PREFIX}${type}_$lang", 0L)
        val processedRawSet = getProcessedRawWords(context, language, type)
        val list = raw.split("|||")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return LemmatizedVocabEntry(
            words = list,
            rawCount = rawCount,
            processedRawWordsCount = processedRawSet.size,
            timestamp = ts
        )
    }

    fun getProcessedRawWords(context: Context, language: String, type: String): Set<String> {
        val lang = language.lowercase()
        val prefs = getPrefs(context)
        return prefs.getStringSet("${KEY_PROCESSED_WORDS_PREFIX}${type}_$lang", emptySet()) ?: emptySet()
    }

    /**
     * Calculates the incremental delta: raw words from the dictionary that have NOT yet been lemmatized.
     */
    fun getUnprocessedWords(
        context: Context,
        language: String,
        type: String,
        allCurrentRawWords: List<String>
    ): List<String> {
        val processedSet = getProcessedRawWords(context, language, type)
            .map { it.lowercase().trim() }
            .toSet()

        return allCurrentRawWords.filter { word ->
            val clean = word.trim()
            clean.length > 1 && clean.lowercase() !in processedSet
        }
    }

    /**
     * Appends newly lemmatized base words and records the processed batch of raw words.
     * Merges with any existing lemmas and saves the new state.
     */
    fun appendLemmatizedWords(
        context: Context,
        language: String,
        type: String,
        newLemmas: List<String>,
        processedBatchRawWords: List<String>,
        totalDictionaryRawCount: Int
    ) {
        val lang = language.lowercase()
        val prefs = getPrefs(context)

        // Existing lemmas
        val existingEntry = getLemmatizedWords(context, language, type)
        val mergedLemmas = (existingEntry.words + newLemmas)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()

        // Existing processed raw words
        val existingProcessedRaw = getProcessedRawWords(context, language, type)
        val updatedProcessedRaw = (existingProcessedRaw + processedBatchRawWords.map { it.trim().lowercase() })
            .toSet()

        prefs.edit()
            .putString("${KEY_WORDS_PREFIX}${type}_$lang", mergedLemmas.joinToString("|||"))
            .putStringSet("${KEY_PROCESSED_WORDS_PREFIX}${type}_$lang", updatedProcessedRaw)
            .putInt("${KEY_RAW_COUNT_PREFIX}${type}_$lang", totalDictionaryRawCount)
            .putLong("${KEY_TIMESTAMP_PREFIX}${type}_$lang", System.currentTimeMillis())
            .apply()
    }

    /**
     * Legacy/full save for backward compatibility.
     */
    fun saveLemmatizedWords(
        context: Context,
        language: String,
        type: String,
        words: List<String>,
        rawCount: Int
    ) {
        val lang = language.lowercase()
        val serialized = words.joinToString("|||")
        getPrefs(context).edit()
            .putString("${KEY_WORDS_PREFIX}${type}_$lang", serialized)
            .putInt("${KEY_RAW_COUNT_PREFIX}${type}_$lang", rawCount)
            .putLong("${KEY_TIMESTAMP_PREFIX}${type}_$lang", System.currentTimeMillis())
            .apply()
    }

    /**
     * Clears all saved lemmas and the history of processed raw words.
     */
    fun clearLemmatizedWords(context: Context, language: String, type: String) {
        val lang = language.lowercase()
        getPrefs(context).edit()
            .remove("${KEY_WORDS_PREFIX}${type}_$lang")
            .remove("${KEY_PROCESSED_WORDS_PREFIX}${type}_$lang")
            .remove("${KEY_RAW_COUNT_PREFIX}${type}_$lang")
            .remove("${KEY_TIMESTAMP_PREFIX}${type}_$lang")
            .apply()
    }
}
