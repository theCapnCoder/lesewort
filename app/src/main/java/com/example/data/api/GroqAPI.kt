package com.example.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WordDataComplete(
    val wordTranslation: String,
    val sentenceTranslation: String,
    val explanation: String
)

object GroqAPI {
    private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "openai/gpt-oss-20b"
    private val client = OkHttpClient()

    fun cleanModelId(model: String): String {
        return model.substringBefore(" (").trim()
    }

    suspend fun getWordDataComplete(
        word: String,
        sentence: String,
        apiKey: String,
        language: String, // "de", "en", or "fr"
        model: String = "openai/gpt-oss-20b",
        customGermanTemplate: String? = null,
        customEnglishTemplate: String? = null,
        customFrenchTemplate: String? = null
    ): WordDataComplete = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API ключ Groq не настроен. Перейдите в настройки для его добавления.")
        }

        val langName = when (language) {
            "de" -> "немецкий"
            "fr" -> "французский"
            else -> "английский"
        }

        val prompt = when (language) {
            "de" -> {
                if (customGermanTemplate != null) {
                    customGermanTemplate
                        .replace("{word}", word)
                        .replace("{sentence}", sentence)
                } else {
                    """
                        Дай полную информацию о немецком слове "$word" из предложения: "$sentence".
                        Учти грамматические особенности немецкого слова (род, артикль der/die/das, если применимо, форму множественного числа, или управление глагола).
        
                        Отвечай на русском языке строго в следующем формате по разделам:
        
                        ===ПЕРЕВОД СЛОВА===
                        [СТРОГО только сам перевод немецкого слова "$word" на русский язык, одно слово или несколько вариантов через запятую (например: "эссенция" или "эссенция, экстракт"). Категорически запрещено добавлять любые вводные слова или фразы вроде "переводится как", "в данном контексте это значит", "существительное" или "артикль". Пиши ТОЛЬКО чистый перевод и ничего кроме него!]
        
                        ===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
                        [Точный и естественный перевод немецкого предложения "$sentence" на русский язык]
        
                        ===ОБЪЯСНЕНИЕ===
                        [Краткое грамматическое или лексическое объяснение слова "$word" в контексте предложения: 2-3 предложения на русском языке]
                    """.trimIndent()
                }
            }
            "fr" -> {
                if (customFrenchTemplate != null) {
                    customFrenchTemplate
                        .replace("{word}", word)
                        .replace("{sentence}", sentence)
                } else {
                    """
                        Дай полную информацию о французском слове "$word" из предложения: "$sentence".
                        Учти грамматические особенности французского слова (род, артикль le/la/l'/les, если применимо, форму множественного числа, спряжение глагола или управление).
        
                        Отвечай на русском языке строго в следующем формате по разделам:
        
                        ===ПЕРЕВОД СЛОВА===
                        [СТРОГО только сам перевод французского слова "$word" на русский язык, одно слово или несколько вариантов через запятую. Категорически запрещено добавлять любые вводные слова или фразы. Пиши ТОЛЬКО чистый перевод и ничего кроме него!]
        
                        ===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
                        [Точный и естественный перевод французского предложения "$sentence" на русский язык]
        
                        ===ОБЪЯСНЕНИЕ===
                        [Краткое грамматическое или лексическое объяснение слова "$word" в контексте предложения: 2-3 предложения на русском языке]
                    """.trimIndent()
                }
            }
            else -> {
                if (customEnglishTemplate != null) {
                    customEnglishTemplate
                        .replace("{word}", word)
                        .replace("{sentence}", sentence)
                } else {
                    """
                        Provide complete dictionary information for the English word "$word" from the sentence: "$sentence".
                        Analyze the English grammatical features (part of speech, verb forms like v1/v2/v3, pleural, or key collocations).
        
                        Отвечай на русском языке строго в следующем формате по разделам:
        
                        ===ПЕРЕВОД СЛОВА===
                        [СТРОГО только сам перевод английского слова "$word" на русский язык, одно слово или несколько вариантов через запятую (например: "эссенция" или "эссенция, экстракт"). Категорически запрещено добавлять любые вводные слова или фразы вроде "переводится как", "в данном контексте это значит", "глагол" или "существительное". Пиши ТОЛЬКО чистый перевод и ничего кроме него!]
        
                        ===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
                        [Точный и естественный перевод английского предложения "$sentence" на русский язык]
        
                        ===ОБЪЯСНЕНИЕ===
                        [Краткое грамматическое или лексическое объяснение слова "$word" в контексте предложения: 2-3 предложения на русском языке]
                    """.trimIndent()
                }
            }
        }

        val json = JSONObject().apply {
            put("model", cleanModelId(model))
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка API: ${response.code} ${response.message}")
            }
            val responseBody = response.body?.string() ?: throw Exception("Пустой ответ от сервера")
            val responseJson = JSONObject(responseBody)
            val choices = responseJson.getJSONArray("choices")
            if (choices.length() == 0) {
                throw Exception("Перевод недоступен - пустой список вариантов")
            }
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content") ?: ""

            var wordTranslation = ""
            var sentenceTranslation = ""
            var explanation = ""

            val lines = content.lines()
            var currentSection = ""
            val wordLines = mutableListOf<String>()
            val sentenceLines = mutableListOf<String>()
            val expLines = mutableListOf<String>()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.contains("ПЕРЕВОД СЛОВА", ignoreCase = true)) {
                    currentSection = "WORD"
                    continue
                } else if (trimmed.contains("ПЕРЕВОД ПРЕДЛОЖЕНИЯ", ignoreCase = true)) {
                    currentSection = "SENTENCE"
                    continue
                } else if (trimmed.contains("ОБЪЯСНЕНИЕ", ignoreCase = true)) {
                    currentSection = "EXPLANATION"
                    continue
                }
                
                if (trimmed.isNotEmpty() && !trimmed.startsWith("===")) {
                    when (currentSection) {
                        "WORD" -> wordLines.add(trimmed)
                        "SENTENCE" -> sentenceLines.add(trimmed)
                        "EXPLANATION" -> expLines.add(trimmed)
                    }
                }
            }

            wordTranslation = wordLines.joinToString("\n")
            sentenceTranslation = sentenceLines.joinToString("\n")
            explanation = expLines.joinToString("\n")

            fun clean(text: String): String {
                return text.replace(Regex("^\\["), "")
                    .replace(Regex("\\]$"), "")
                    .replace(Regex("^['\"]"), "")
                    .replace(Regex("['\"]$"), "")
                    .trim()
            }

            WordDataComplete(
                wordTranslation = if (wordTranslation.isNotEmpty()) clean(wordTranslation) else "Перевод недоступен",
                sentenceTranslation = if (sentenceTranslation.isNotEmpty()) clean(sentenceTranslation) else "Перевод недоступен",
                explanation = if (explanation.isNotEmpty()) clean(explanation) else "Объяснение недоступно"
            )
        }
    }

    suspend fun getWordTranslationVariants(
        word: String,
        sentence: String = "",
        apiKey: String,
        language: String, // "de" or "en"
        model: String = "openai/gpt-oss-20b"
    ): List<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API ключ Groq не настроен.")
        }

        val langName = when (language) {
            "de" -> "немецкий"
            "fr" -> "французский"
            else -> "английский"
        }

        val prompt = if (sentence.trim().isNotEmpty()) {
            """
                Дай точный и контекстуальный перевод для слова "$word" (язык: $langName) на русский язык, основываясь исключительно на его значении в следующем предложении:
                "$sentence"

                Верни СТРОГО только подходящие по смыслу варианты перевода на русский язык через запятую (например: "управлять" или "управлять, бежать").
                Категорически запрещено добавлять какие-либо объяснения, примеры, вводные слова, нумерацию или знаки форматирования. Твой ответ должен состоять только из вариантов перевода через запятую.
            """.trimIndent()
        } else {
            """
                Дай варианты перевода для слова "$word" (язык: $langName).
                Верни СТРОГО только список переведенных на русский язык слов через запятую.
                Категорически запрещено добавлять объяснения, примеры, вводные слова, нумерацию или знаки форматирования.
                Например, для немецкого слова "Himmel" верни строго: "небо, небеса"
                Например, для английского слова "run" верни строго: "бежать, бег, управлять"
            """.trimIndent()
        }

        val json = JSONObject().apply {
            put("model", cleanModelId(model))
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка API: ${response.code} ${response.message}")
            }
            val responseBody = response.body?.string() ?: throw Exception("Пустой ответ от сервера")
            val responseJson = JSONObject(responseBody)
            val choices = responseJson.getJSONArray("choices")
            if (choices.length() == 0) {
                throw Exception("Варианты перевода недоступны")
            }
            val content = choices.getJSONObject(0).getJSONObject("message").getString("content") ?: ""
            
            content.split(",")
                .map { it.replace(Regex("[^\\p{L}\\s\\-]"), "").trim() }
                .filter { it.isNotEmpty() }
        }
    }

    suspend fun translateParagraph(
        paragraph: String,
        apiKey: String,
        language: String, // "de", "en", or "fr"
        model: String = "openai/gpt-oss-20b"
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API ключ Groq не настроен.")
        }

        val langName = when (language) {
            "de" -> "немецкий"
            "fr" -> "французский"
            else -> "английский"
        }

        val prompt = """
            Переведи следующий текст с языка $langName на естественный, литературный русский язык.
            Верни СТРОГО только сам перевод и ничего больше. Не пиши никаких дополнительных фраз, пояснений, комментариев или кавычек. Только перевод текста.
            
            Текст для перевода:
            "$paragraph"
        """.trimIndent()

        val json = JSONObject().apply {
            put("model", cleanModelId(model))
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка API: ${response.code} ${response.message}")
            }
            val responseBody = response.body?.string() ?: throw Exception("Пустой ответ от сервера")
            val responseJson = JSONObject(responseBody)
            val choices = responseJson.getJSONArray("choices")
            if (choices.length() == 0) {
                throw Exception("Перевод недоступен")
            }
            choices.getJSONObject(0).getJSONObject("message").getString("content") ?: ""
        }
    }

    suspend fun translateParagraphUnknownWords(
        paragraph: String,
        unknownWords: List<String>,
        apiKey: String,
        language: String, // "de" or "en"
        model: String = "openai/gpt-oss-20b"
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API ключ Groq не настроен.")
        }
        if (unknownWords.isEmpty()) {
            return@withContext emptyMap()
        }

        val wordsListStr = unknownWords.joinToString(", ")
        val langName = when (language) {
            "de" -> "немецкий"
            "fr" -> "французский"
            else -> "английский"
        }

        val prompt = """
            Текст на языке ($langName):
            "$paragraph"

            Список неизвестных слов из текста: [$wordsListStr].

            Дай точный контекстуальный перевод на русский язык для каждого из этих слов, исходя ИСКЛЮЧИТЕЛЬНО из того значения, в котором слово употреблено в данном тексте.
            Перевод каждого слова должен быть краткий (1-2 слова, без пояснений).

            Верни ответ СТРОГО в формате JSON объекта вида:
            {
              "слово1": "перевод1",
              "слово2": "перевод2"
            }
            Не добавляй никакой разметки markdown, никаких пояснений до или после JSON. Верни ТОЛЬКО валидный JSON.
        """.trimIndent()

        val json = JSONObject().apply {
            put("model", cleanModelId(model))
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Ошибка API: ${response.code} ${response.message}")
            }
            val responseBody = response.body?.string() ?: throw Exception("Пустой ответ от сервера")
            val responseJson = JSONObject(responseBody)
            val choices = responseJson.getJSONArray("choices")
            if (choices.length() == 0) {
                throw Exception("Перевод недоступен")
            }
            var rawContent = choices.getJSONObject(0).getJSONObject("message").getString("content") ?: ""

            rawContent = rawContent.trim()
            if (rawContent.startsWith("```")) {
                rawContent = rawContent.substringAfter("\n")
                if (rawContent.endsWith("```")) {
                    rawContent = rawContent.substringBeforeLast("```").trim()
                }
            }

            val resultMap = mutableMapOf<String, String>()
            try {
                val parsedJson = JSONObject(rawContent)
                val keys = parsedJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val valStr = parsedJson.optString(key, "")
                    if (valStr.isNotEmpty()) {
                        val parsedKey = com.example.data.engine.LinguisticEngine.tokenizeWord(key)
                        if (parsedKey.isNotEmpty()) {
                            resultMap[parsedKey] = valStr.trim()
                        }
                        resultMap[key.lowercase().trim()] = valStr.trim()
                    }
                }
            } catch (e: Exception) {
                Regex(""""([^"]+)"\s*:\s*"([^"]+)"""").findAll(rawContent).forEach { match ->
                    val k = match.groupValues[1].lowercase().trim()
                    val parsedK = com.example.data.engine.LinguisticEngine.tokenizeWord(k)
                    val v = match.groupValues[2].trim()
                    if (v.isNotEmpty()) {
                        if (parsedK.isNotEmpty()) resultMap[parsedK] = v
                        resultMap[k] = v
                    }
                }
            }
            resultMap
        }
    }
}
