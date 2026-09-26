package com.example.data.api

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiStoryService {

    private const val GEMINI_MODEL = "gemini-3.5-flash"
    private const val GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateStory(
        prompt: String,
        userCustomGroqApiKey: String? = null,
        userCustomGeminiApiKey: String? = null,
        groqModel: String? = null
    ): String = withContext(Dispatchers.IO) {
        val geminiApiKey = userCustomGeminiApiKey?.takeIf { it.isNotBlank() }
            ?: try { BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" } } catch (e: Throwable) { null }

        var geminiException: Exception? = null

        // 1. Try Gemini API first if key is available
        if (!geminiApiKey.isNullOrBlank()) {
            try {
                return@withContext callGeminiApi(
                    apiKey = geminiApiKey,
                    prompt = prompt,
                    systemInstruction = "You are an expert novelist and foreign language educator. Your mission is to write very long, rich, multi-chapter immersive stories and novellas (at least 1500 to 2500+ words). You NEVER write short summaries or brief essays. You write complete, detailed scenes, deep dialogues, character reflections, and atmospheric descriptions divided into multiple named chapters.",
                    maxOutputTokens = 8192
                )
            } catch (e: Exception) {
                geminiException = e
            }
        }

        // 2. Fallback to Groq if Groq key is provided
        val groqKey = userCustomGroqApiKey?.takeIf { it.isNotBlank() }
        if (!groqKey.isNullOrBlank()) {
            try {
                return@withContext callGroqApi(
                    apiKey = groqKey,
                    prompt = prompt,
                    requestedModel = groqModel,
                    systemInstruction = "You are an expert novelist and foreign language educator. Your mission is to write very long, rich, multi-chapter immersive stories and novellas (at least 1500 to 2500+ words). You NEVER write short summaries or brief essays. You write complete, detailed scenes, deep dialogues, character reflections, and atmospheric descriptions divided into multiple named chapters.",
                    maxTokens = 3500
                )
            } catch (e: Exception) {
                throw Exception("Ошибка генерации (Gemini: ${geminiException?.message ?: "ключ не задан"}, Groq: ${e.message})")
            }
        }

        // If neither worked or key missing
        if (geminiException != null) {
            throw geminiException
        }

        throw IllegalArgumentException(
            "API ключ для генерации не найден. Пожалуйста, настройте Gemini API ключ в панели Secrets AI Studio или Groq API ключ в настройках."
        )
    }

    suspend fun executeAiPrompt(
        prompt: String,
        systemInstruction: String = "You are an expert linguist and assistant.",
        userCustomGroqApiKey: String? = null,
        userCustomGeminiApiKey: String? = null,
        groqModel: String? = null,
        maxTokens: Int = 4096,
        temperature: Double = 0.75,
        responseJsonFormat: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val geminiApiKey = userCustomGeminiApiKey?.takeIf { it.isNotBlank() }
            ?: try { BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" } } catch (e: Throwable) { null }

        var geminiException: Exception? = null

        // 1. Try Gemini API first if key is available (with fallback between supported models on quota 429)
        if (!geminiApiKey.isNullOrBlank()) {
            try {
                return@withContext callGeminiWithFallback(
                    apiKey = geminiApiKey,
                    prompt = prompt,
                    systemInstruction = systemInstruction,
                    maxOutputTokens = maxTokens,
                    temperature = temperature,
                    responseJsonFormat = responseJsonFormat
                )
            } catch (e: Exception) {
                geminiException = e
            }
        }

        // 2. Fallback to Groq if Groq key is provided
        val groqKey = userCustomGroqApiKey?.takeIf { it.isNotBlank() }
        if (!groqKey.isNullOrBlank()) {
            try {
                return@withContext callGroqApi(
                    apiKey = groqKey,
                    prompt = prompt,
                    requestedModel = groqModel,
                    systemInstruction = systemInstruction,
                    maxTokens = maxTokens.coerceIn(1024, 8192),
                    temperature = temperature,
                    responseJsonFormat = responseJsonFormat
                )
            } catch (e: Exception) {
                if (responseJsonFormat && (e.message?.contains("response_format", ignoreCase = true) == true || e.message?.contains("400") == true)) {
                    // Retry Groq without response_format flag in case custom model doesn't support json_object mode
                    return@withContext callGroqApi(
                        apiKey = groqKey,
                        prompt = prompt,
                        requestedModel = groqModel,
                        systemInstruction = systemInstruction,
                        maxTokens = maxTokens.coerceIn(1024, 8192),
                        temperature = temperature,
                        responseJsonFormat = false
                    )
                }
                throw Exception("Ошибка ИИ (Gemini: ${geminiException?.message ?: "ключ не задан"}, Groq: ${e.message})")
            }
        }

        if (geminiException != null) {
            throw geminiException
        }

        throw IllegalArgumentException(
            "API ключ не найден. Пожалуйста, настройте Gemini API ключ в панели Secrets AI Studio или Groq API ключ в настройках."
        )
    }

    suspend fun lemmatizeWords(
        words: List<String>,
        languageCode: String,
        userCustomGroqApiKey: String? = null,
        userCustomGeminiApiKey: String? = null,
        groqModel: String? = null,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanedInput = words
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
            .distinct()

        if (cleanedInput.isEmpty()) return@withContext emptyList()

        onProgress?.invoke(1, 1)

        val prompt = buildLemmatizationPrompt(cleanedInput, languageCode)
        val systemPrompt = "Ты — строгий эксперт-лингвист. Твоя единственная цель — сжать входящий список из ${cleanedInput.size} слов в их начальные словарные формы (леммы). Итоговых лемм ДОЛЖНО БЫТЬ НЕ БОЛЕЕ ${cleanedInput.size}. Запрещено придумывать новые слова, синонимы, переводы или перечислять формы слов. Ответ СТРОГО в JSON: {\"lemmas\": [\"...\"]}."

        val rawResponse = executeAiPrompt(
            prompt = prompt,
            systemInstruction = systemPrompt,
            userCustomGroqApiKey = userCustomGroqApiKey,
            userCustomGeminiApiKey = userCustomGeminiApiKey,
            groqModel = groqModel,
            maxTokens = 4096,
            temperature = 0.1,
            responseJsonFormat = true
        )

        val parsed = parseLemmatizedResponse(rawResponse)
        val distinctParsed = parsed.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        // HARD SAFETY CONSTRAINT:
        // Lemmatization reduces or preserves vocabulary size (duplicates and inflected forms collapse into 1 lemma).
        // It can NEVER produce more lemmas than the input word count!
        val maxAllowedLemmas = cleanedInput.size
        val constrainedResult = if (distinctParsed.size > maxAllowedLemmas) {
            distinctParsed.take(maxAllowedLemmas)
        } else {
            distinctParsed
        }

        constrainedResult.sorted()
    }

    fun buildLemmatizationPrompt(words: List<String>, languageCode: String): String {
        val inputCount = words.size
        val wordsText = words.joinToString(", ")
        return when (languageCode.lowercase()) {
            "de" -> """
Ты — строгий эксперт-лингвист немецкого языка.

ВХОДНЫЕ ДАННЫЕ:
Список из $inputCount немецких слов и словоформ:
$wordsText

ТВОЯ ЗАДАЧА:
Сжать и нормализовать этот список, приведя каждое слово к его ЕДИНСТВЕННОЙ начальной словарной форме (лемме).

ЖЁСТКИЕ ПРАВИЛА И ОГРАНИЧЕНИЯ (КАТЕГОРИЧЕСКИ ОБЯЗАТЕЛЬНЫ К ИСПОЛНЕНИЮ):
1. СЖАТИЕ СПИСКА, А НЕ УВЕЛИЧЕНИЕ:
   Во входном списке ровно $inputCount слов. В ответе должно получиться НЕ БОЛЕЕ $inputCount лемм (обычно от ${maxOf(1, inputCount / 2)} до $inputCount, так как разные падежи, числа и формы глаголов схлопываются в одну лемму).
   Количество лемм в ответе НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ не может превышать $inputCount!

2. РОВНО 1 НАЧАЛЬНАЯ ФОРМА НА СЛОВО (БЕЗ ПЕРЕЧИСЛЕНИЙ):
   Каждому слову соответствует ровно ОДНА словарная лемма.
   СТРОЖАЙШЕ ЗАПРЕЩЕНО перечислять формы одного слова!
   - НЕЛЬЗЯ: "das Buch, die Bücher", "gehen, ging, gegangen", "schön, schöner".
   - МОЖНО ТОЛЬКО ОДНО СЛОВО: "das Buch", "gehen", "schön".

3. СТРОГИЕ СЛОВАРНЫЕ ПРАВИЛА ДЛЯ НЕМЕЦКОГО ЯЗЫКА:
   - Существительные: ТОЛЬКО единственное число с определенным артиклем (der, die или das). Никаких окончаний множественного числа в скобках! Пример: "der Tisch", "die Frau", "das Kind".
   - Глаголы: ТОЛЬКО инфинитив. Пример: "sehen", "machen", "können".
   - Прилагательные / наречия: ТОЛЬКО начальная форма (положительная степень, без окончаний). Пример: "schnell", "groß", "gut".

4. КАТЕГОРИЧЕСКИЙ ЗАПРЕТ НА ВЫДУМЫВАНИЕ И СИНОНИМЫ:
   Обрабатывай ИСКЛЮЧИТЕЛЬНО слова из входного списка! Категорически ЗАПРЕЩЕНО придумывать новые слова, синонимы, примеры использования, переводы на русский или ассоциации.

5. БЕЗ ПОЯСНЕНИЙ И СКОБОК:
   Категорически запрещены любые скобки, части речи, комментарии, нумерация или вступительные фразы.

6. ФОРМАТ ВЫДАЧИ:
   Верни ответ СТРОГО как валидный JSON-объект следующей структуры:
{
  "lemmas": [
    "лемма 1",
    "лемма 2"
  ]
}
""".trimIndent()

            "en" -> """
Ты — строгий эксперт-лингвист английского языка.

ВХОДНЫЕ ДАННЫЕ:
Список из $inputCount английских слов и словоформ:
$wordsText

ТВОЯ ЗАДАЧА:
Сжать и нормализовать этот список, приведя каждое слово к его ЕДИНСТВЕННОЙ начальной словарной форме (лемме).

ЖЁСТКИЕ ПРАВИЛА И ОГРАНИЧЕНИЯ (КАТЕГОРИЧЕСКИ ОБЯЗАТЕЛЬНЫ К ИСПОЛНЕНИЮ):
1. СЖАТИЕ СПИСКА, А НЕ УВЕЛИЧЕНИЕ:
   Во входном списке ровно $inputCount слов. В ответе должно получиться НЕ БОЛЕЕ $inputCount лемм.
   Разные формы одного слова объединяются в одну лемму. Количество лемм в ответе НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ не может превышать $inputCount!

2. РОВНО 1 НАЧАЛЬНАЯ ФОРМА НА СЛОВО:
   Каждому слову соответствует ровно ОДНА словарная лемма.
   СТРОЖАЙШЕ ЗАПРЕЩЕНО перечислять формы одного слова (например: НЕЛЬЗЯ "go, went, gone", МОЖНО ТОЛЬКО "go").

3. СЛОВАРНЫЕ ПРАВИЛА:
   - Существительные: единственное число (например, "car", "child", "apple").
   - Глаголы: инфинитив / начальная форма без "to" (например, "take", "run", "read").
   - Прилагательные: положительная степень (например, "fast", "bright").

4. КАТЕГОРИЧЕСКИЙ ЗАПРЕТ НА ВЫДУМЫВАНИЕ И СИНОНИМЫ:
   Обрабатывай ИСКЛЮЧИТЕЛЬНО слова из входного списка! Категорически ЗАПРЕЩЕНО добавлять синонимы, родственные слова, переводы или примеры.

5. БЕЗ ПОЯСНЕНИЙ И СКОБОК:
   Никаких скобок, пометок частей речи, транскрипций или вступительных фраз.

6. ФОРМАТ ВЫДАЧИ:
   Верни ответ СТРОГО как валидный JSON-объект следующей структуры:
{
  "lemmas": [
    "lemma 1",
    "lemma 2"
  ]
}
""".trimIndent()

            "fr" -> """
Ты — строгий эксперт-лингвист французского языка.

ВХОДНЫЕ ДАННЫЕ:
Список из $inputCount французских слов и словоформ:
$wordsText

ТВОЯ ЗАДАЧА:
Сжать и нормализовать этот список, приведя каждое слово к его ЕДИНСТВЕННОЙ начальной словарной форме (лемме).

ЖЁСТКИЕ ПРАВИЛА И ОГРАНИЧЕНИЯ (КАТЕГОРИЧЕСКИ ОБЯЗАТЕЛЬНЫ К ИСПОЛНЕНИЮ):
1. СЖАТИЕ СПИСКА, А НЕ УВЕЛИЧЕНИЕ:
   Во входном списке ровно $inputCount слов. В ответе должно получиться НЕ БОЛЕЕ $inputCount лемм.
   Количество лемм в ответе НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ не может превышать $inputCount!

2. РОВНО 1 НАЧАЛЬНАЯ ФОРМА НА СЛОВО:
   Каждому слову соответствует ровно ОДНА словарная лемма.
   СТРОЖАЙШЕ ЗАПРЕЩЕНО перечислять формы одного слова (например: НЕЛЬЗЯ "aller, vais, allé", МОЖНО ТОЛЬКО "aller").

3. СЛОВАРНЫЕ ПРАВИЛА:
   - Существительные: единственное число с определенным артиклем ("le livre", "la table", "l'arbre").
   - Глаголы: инфинитив ("faire", "voir", "parler").
   - Прилагательные: мужской род, единственное число ("grand", "bon").

4. КАТЕГОРИЧЕСКИЙ ЗАПРЕТ НА ВЫДУМЫВАНИЕ И СИНОНИМЫ:
   Обрабатывай ТОЛЬКО слова из входного списка! Никаких новых слов, синонимов, переводов или примеров.

5. БЕЗ ПОЯСНЕНИЙ И СКОБОК:
   Никаких скобок, грамматических пометок или вступительных фраз.

6. ФОРМАТ ВЫДАЧИ:
   Верни ответ СТРОГО как валидный JSON-объект:
{
  "lemmas": [
    "lemme 1",
    "lemme 2"
  ]
}
""".trimIndent()

            else -> """
Ты — строгий эксперт-лингвист.

ВХОДНЫЕ ДАННЫЕ:
Список из $inputCount слов (язык: '$languageCode'):
$wordsText

ТВОЯ ЗАДАЧА:
Привести каждое слово к его ЕДИНСТВЕННОЙ начальной словарной форме (лемме).

ЖЁСТКИЕ ПРАВИЛА И ОГРАНИЧЕНИЯ:
1. СЖАТИЕ СПИСКА, А НЕ УВЕЛИЧЕНИЕ: В ответе должно получиться НЕ БОЛЕЕ $inputCount лемм.
2. РОВНО 1 НАЧАЛЬНАЯ ФОРМА: Каждому входному слову соответствует ровно одна словарная форма. Запрещено перечислять формы слова.
3. ЗАПРЕТ НА НОВЫЕ СЛОВА: Не добавляй синонимы, переводы или примеры.
4. БЕЗ ПОЯСНЕНИЙ И СКОБОК.
5. ФОРМАТ ВЫДАЧИ: СТРОГО JSON-объект:
{
  "lemmas": [
    "лемма 1",
    "лемма 2"
  ]
}
""".trimIndent()
        }
    }

    private fun cleanAndAddLemma(raw: String, target: MutableList<String>) {
        if (raw.isBlank()) return
        // 1. Remove parenthetical notes like (die Häuser), (ging), [noun], etc.
        var w = raw.replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()

        // 2. Strip leading numbers or bullets like "1. ", "- ", "* ", "• "
        w = w.replace(Regex("^[0-9]+[.)]\\s*"), "")
            .replace(Regex("^[-*•]\\s*"), "")
            .trim('"', '\'', '.', ',', ';', ':', '`', '*', '-', '•', ' ', '\t', '\n')

        if (w.length < 2) return

        // 3. Reject grammatical annotations / meta comments
        val lower = w.lowercase()
        val metaWords = listOf(
            "существительное", "глагол", "прилагательное", "инфинитив", "ед.ч.", "мн.ч.",
            "начальная форма", "словарная форма", "перевод", "пример", "синоним", "падеж",
            "род:", "часть речи", "noun", "verb", "adjective", "plural", "singular"
        )
        if (metaWords.any { lower.contains(it) }) return

        // 4. Reject sentence-length hallucinated text (more than 4 words)
        val wordTokens = w.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (wordTokens.size > 4) return

        target.add(w)
    }

    fun extractJsonSubstring(text: String): String? {
        val trimmed = text.trim()
        val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val codeBlockMatch = codeBlockRegex.find(trimmed)
        if (codeBlockMatch != null) {
            val inner = codeBlockMatch.groupValues[1].trim()
            if ((inner.startsWith("{") && inner.endsWith("}")) || (inner.startsWith("[") && inner.endsWith("]"))) {
                return inner
            }
        }

        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1)
        }

        val firstBracket = trimmed.indexOf('[')
        val lastBracket = trimmed.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket > firstBracket) {
            return trimmed.substring(firstBracket, lastBracket + 1)
        }

        return null
    }

    fun parseLemmatizedResponse(rawResponse: String): List<String> {
        val trimmed = rawResponse.trim()
        val results = mutableListOf<String>()

        // 1. Try to extract and parse JSON
        val jsonString = extractJsonSubstring(trimmed)
        if (jsonString != null) {
            try {
                if (jsonString.startsWith("{")) {
                    val jsonObj = JSONObject(jsonString)
                    val array = jsonObj.optJSONArray("lemmas")
                        ?: jsonObj.optJSONArray("words")
                        ?: jsonObj.optJSONArray("result")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val item = array.optString(i, "")
                            cleanAndAddLemma(item, results)
                        }
                    }
                } else if (jsonString.startsWith("[")) {
                    val array = JSONArray(jsonString)
                    for (i in 0 until array.length()) {
                        val item = array.optString(i, "")
                        cleanAndAddLemma(item, results)
                    }
                }
            } catch (e: Throwable) {
                // If org.json fails (e.g. trailing commas or unit test mock stub), use regex extraction
            }

            // If org.json didn't yield results (e.g. trailing comma or JVM stub), extract quoted tokens directly
            if (results.isEmpty()) {
                val quotedTokens = Regex("\"([^\"]{2,})\"").findAll(jsonString)
                    .map { it.groupValues[1] }
                    .filterNot { it.equals("lemmas", ignoreCase = true) || it.equals("words", ignoreCase = true) || it.equals("result", ignoreCase = true) }
                for (token in quotedTokens) {
                    cleanAndAddLemma(token, results)
                }
            }
        }

        // 2. Fallback text parsing if JSON didn't produce results
        if (results.isEmpty()) {
            var text = trimmed
            if (text.startsWith("```")) {
                text = text.replace(Regex("^```[a-zA-Z]*\\n?"), "").replace(Regex("\\n?```$"), "").trim()
            }

            val lines = text.lines()
            val cleanedLines = lines.filterNot { line ->
                val l = line.trim().lowercase()
                l.startsWith("вот ") || l.startsWith("итоговый ") || l.startsWith("список ") ||
                        l.startsWith("базовые ") || l.startsWith("hier ist") || l.startsWith("here is") ||
                        l.startsWith("правило") || l.startsWith("ограничение") || l.startsWith("note:")
            }
            // Strip parentheses before splitting by comma, to avoid splitting (form1, form2) into separate tokens!
            val strippedParenText = cleanedLines.joinToString("\n")
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\[.*?\\]"), "")

            strippedParenText.split(Regex("[,;\n•]+")).forEach { token ->
                cleanAndAddLemma(token, results)
            }
        }

        return results.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    private fun callGeminiWithFallback(
        apiKey: String,
        prompt: String,
        systemInstruction: String = "You are an expert novelist and foreign language educator. Your mission is to write very long, rich, multi-chapter immersive stories and novellas (at least 1500 to 2500+ words). You NEVER write short summaries or brief essays. You write complete, detailed scenes, deep dialogues, character reflections, and atmospheric descriptions divided into multiple named chapters.",
        maxOutputTokens: Int = 8192,
        temperature: Double = 0.75,
        responseJsonFormat: Boolean = false
    ): String {
        try {
            return callGeminiApi(
                apiKey = apiKey,
                prompt = prompt,
                systemInstruction = systemInstruction,
                maxOutputTokens = maxOutputTokens,
                modelName = GEMINI_MODEL,
                temperature = temperature,
                responseJsonFormat = responseJsonFormat
            )
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val isQuotaOrRateLimit = msg.contains("Quota exceeded", ignoreCase = true) ||
                    msg.contains("exceeded your current quota", ignoreCase = true) ||
                    msg.contains("limit: 20", ignoreCase = true) ||
                    msg.contains("429", ignoreCase = true)

            if (isQuotaOrRateLimit) {
                // If gemini-3.5-flash quota is exhausted, attempt fallback to gemini-3.1-flash-lite-preview which has independent quota
                try {
                    return callGeminiApi(
                        apiKey = apiKey,
                        prompt = prompt,
                        systemInstruction = systemInstruction,
                        maxOutputTokens = maxOutputTokens,
                        modelName = "gemini-3.1-flash-lite-preview",
                        temperature = temperature,
                        responseJsonFormat = responseJsonFormat
                    )
                } catch (fallbackEx: Exception) {
                    // Propagate the original error with the retry duration advice
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    private fun callGeminiApi(
        apiKey: String,
        prompt: String,
        systemInstruction: String = "You are an expert novelist and foreign language educator. Your mission is to write very long, rich, multi-chapter immersive stories and novellas (at least 1500 to 2500+ words). You NEVER write short summaries or brief essays. You write complete, detailed scenes, deep dialogues, character reflections, and atmospheric descriptions divided into multiple named chapters.",
        maxOutputTokens: Int = 8192,
        modelName: String = GEMINI_MODEL,
        temperature: Double = 0.75,
        responseJsonFormat: Boolean = false
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val jsonBody = JSONObject().apply {
            val systemInstructionObj = JSONObject().apply {
                val partsArr = JSONArray().apply {
                    val p = JSONObject().apply {
                        put("text", systemInstruction)
                    }
                    put(p)
                }
                put("parts", partsArr)
            }
            put("system_instruction", systemInstructionObj)

            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        val partObj = JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)

            val generationConfig = JSONObject().apply {
                put("temperature", temperature)
                put("topP", 0.95)
                put("topK", 40)
                put("maxOutputTokens", maxOutputTokens)
                if (responseJsonFormat) {
                    put("responseMimeType", "application/json")
                }
            }
            put("generationConfig", generationConfig)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBodyStr = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errorObj = JSONObject(responseBodyStr).optJSONObject("error")
                errorObj?.optString("message") ?: "HTTP ${response.code}: $responseBodyStr"
            } catch (e: Exception) {
                "HTTP ${response.code}: $responseBodyStr"
            }
            throw Exception("Gemini API Error: $errorMsg")
        }

        val responseJson = JSONObject(responseBodyStr)
        val candidates = responseJson.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val text = parts.getJSONObject(0).optString("text", "")
                if (text.isNotBlank()) {
                    return text.trim()
                }
            }
        }

        throw Exception("Пустой ответ от Gemini API")
    }

    private fun callGroqApi(
        apiKey: String,
        prompt: String,
        requestedModel: String? = null,
        systemInstruction: String = "You are an expert novelist and foreign language educator. Your mission is to write very long, rich, multi-chapter immersive stories and novellas (at least 1500 to 2500+ words). You NEVER write short summaries or brief essays. You write complete, detailed scenes, deep dialogues, character reflections, and atmospheric descriptions divided into multiple named chapters.",
        maxTokens: Int = 4096,
        temperature: Double = 0.3,
        responseJsonFormat: Boolean = false
    ): String {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val rawModel = requestedModel?.takeIf { it.isNotBlank() } ?: "openai/gpt-oss-20b"
        val modelToUse = GroqAPI.cleanModelId(rawModel)

        val jsonBody = JSONObject().apply {
            put("model", modelToUse)
            val messagesArray = JSONArray().apply {
                if (systemInstruction.isNotBlank()) {
                    val systemObj = JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    }
                    put(systemObj)
                }

                val messageObj = JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
                put(messageObj)
            }
            put("messages", messagesArray)
            put("temperature", temperature)
            put("max_tokens", maxTokens.coerceIn(1024, 8192))
            if (responseJsonFormat) {
                val respFormat = JSONObject().apply {
                    put("type", "json_object")
                }
                put("response_format", respFormat)
            }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBodyStr = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errorObj = JSONObject(responseBodyStr).optJSONObject("error")
                errorObj?.optString("message") ?: "HTTP ${response.code}: $responseBodyStr"
            } catch (e: Exception) {
                "HTTP ${response.code}: $responseBodyStr"
            }
            throw Exception("Groq API Error (модель: $modelToUse, код: ${response.code}): $errorMsg")
        }

        val responseJson = JSONObject(responseBodyStr)
        val choices = responseJson.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            var content = message?.optString("content", "")?.trim() ?: ""

            // Reasoning models (e.g. gpt-oss, qwen, deepseek) may place text in reasoning or reasoning_content
            if (content.isBlank()) {
                val reasoning = message?.optString("reasoning", "")?.trim()
                    ?: message?.optString("reasoning_content", "")?.trim()
                    ?: ""
                if (reasoning.isNotBlank()) {
                    content = reasoning
                }
            }

            if (content.isNotBlank()) {
                return content
            }

            val finishReason = firstChoice.optString("finish_reason", "")
            if (finishReason == "length") {
                throw Exception("Модель Groq исчерпала лимит токенов при рассуждении (уменьшите размер пачки слов)")
            }
        }

        throw Exception("Пустой ответ от Groq API (модель: $modelToUse)")
    }
}
