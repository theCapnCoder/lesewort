package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.BookEntity
import com.example.data.db.ChapterEntity
import com.example.data.db.KnownWord
import com.example.data.db.StudyWord
import com.example.data.db.WordActivityEvent
import com.example.data.engine.BookStatistics
import com.example.data.engine.LinguisticEngine
import com.example.data.parser.ParsedBook
import com.example.data.parser.BookParser
import com.example.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.screens.*

data class DayWordStats(
    val dateKey: String, // "yyyy-MM-dd"
    val timestamp: Long,
    val learningWords: List<String>,
    val learnedWords: List<String>
) {
    val learningCount: Int get() = learningWords.size
    val learnedCount: Int get() = learnedWords.size
    val totalCount: Int get() = learningCount + learnedCount
}

data class MonthWordStats(
    val monthKey: String, // "yyyy-MM"
    val monthLabel: String, // "Сентябрь 2026"
    val timestamp: Long,
    val days: List<DayWordStats>,
    val learningWords: List<String>,
    val learnedWords: List<String>
) {
    val learningCount: Int get() = learningWords.size
    val learnedCount: Int get() = learnedWords.size
    val totalCount: Int get() = learningCount + learnedCount
    val activeDaysCount: Int get() = days.count { it.totalCount > 0 }
}

data class YearWordStats(
    val yearKey: String, // "yyyy"
    val yearLabel: String, // "2026 год"
    val timestamp: Long,
    val months: List<MonthWordStats>,
    val learningWords: List<String>,
    val learnedWords: List<String>
) {
    val learningCount: Int get() = learningWords.size
    val learnedCount: Int get() = learnedWords.size
    val totalCount: Int get() = learningCount + learnedCount
    val activeDaysCount: Int get() = months.sumOf { it.activeDaysCount }
}

data class StoryGenerationState(
    val isGenerating: Boolean = false,
    val progressStage: String = "",
    val storyTitle: String? = null,
    val storyText: String? = null,
    val savedBookId: Int? = null,
    val errorMessage: String? = null,
    val language: String = "",
    val levelCode: String = "",
    val genreTitleRu: String = "",
    val showBanner: Boolean = false
)

data class ImportProgressState(
    val totalBooks: Int,
    val currentBookIndex: Int,
    val currentBookTitle: String,
    val stage: String,
    val successfulCount: Int,
    val failedCount: Int
)

enum class SortType(val displayName: String) {
    NEWEST("Новые вначале"),
    COMPLEXITY_ASC("Сложность: по возрастанию"),
    COMPLEXITY_DESC("Сложность: по убыванию"),
    DENSITY_ASC("Лексическая плотность: по возрастанию"),
    DENSITY_DESC("Лексическая плотность: по убыванию"),
    WORDS_ASC("Всего слов: по возрастанию"),
    WORDS_DESC("Всего слов: по убыванию")
}

data class BookUiItem(
    val book: BookEntity,
    val totalChapters: Int,
    val stats: BookStatistics?,
    val newWordsCount: Int = 0,
    val learningWordsCount: Int = 0,
    val learnedWordsCount: Int = 0,
    val totalPages: Int = 1,
    val currentPage: Int = 0
)

sealed interface TranslationUiState {
    object Idle : TranslationUiState
    object Loading : TranslationUiState
    data class Success(
        val word: String,
        val rawWord: String,
        val sentence: String,
        val wordTranslation: String,
        val sentenceTranslation: String,
        val explanation: String
    ) : TranslationUiState
    data class Error(val message: String) : TranslationUiState
}

sealed interface VariantsUiState {
    object Idle : VariantsUiState
    object Loading : VariantsUiState
    data class Success(
        val word: String,
        val variants: List<String>
    ) : VariantsUiState
    data class Error(val message: String) : VariantsUiState
}

sealed interface ParagraphTranslationUiState {
    object Loading : ParagraphTranslationUiState
    data class Success(val translation: String) : ParagraphTranslationUiState
    data class Error(val message: String) : ParagraphTranslationUiState
}

sealed interface InlineParagraphTranslationUiState {
    object Loading : InlineParagraphTranslationUiState
    data class Success(val wordTranslations: Map<String, String>) : InlineParagraphTranslationUiState
    data class Error(val message: String) : InlineParagraphTranslationUiState
}

data class ChapterBatchStudyState(
    val isProcessing: Boolean = false,
    val currentBatch: Int = 0,
    val totalBatches: Int = 0,
    val processedWords: Int = 0,
    val totalWords: Int = 0,
    val isDone: Boolean = false,
    val errorMessage: String? = null
)

data class GroqApiKeyItem(
    val id: String,
    val name: String,
    val key: String
) {
    val maskedKey: String
        get() {
            val trimmed = key.trim()
            if (trimmed.length <= 12) return "••••••••"
            val prefix = trimmed.take(8)
            val suffix = trimmed.takeLast(4)
            return "$prefix••••$suffix"
        }
}

const val DEFAULT_GROQ_API_KEY = "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"

data class CustomPrompt(
    val id: String,
    val name: String,
    val germanPromptTemplate: String,
    val englishPromptTemplate: String,
    val frenchPromptTemplate: String = DEFAULT_FRENCH_PROMPT,
    val isActive: Boolean
)

val DEFAULT_GERMAN_PROMPT = """
Дай полную информацию о немецком слове "{word}" из предложения: "{sentence}".
Учти грамматические особенности немецкого слова (род, артикль der/die/das, если применимо, форму множественного числа, или управление глагола).

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод немецкого слова "{word}" на русский язык, одно слово или несколько вариантов через запятую (например: "эссенция" или "эссенция, экстракт"). Категорически запрещено добавлять любые вводные слова или фразы вроде "переводится как", "в данном контексте это значит", "существительное" или "артикль". Пиши ТОЛЬКО чистый перевод и ничего кроме него!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод немецкого предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Краткое грамматическое или лексическое объяснение слова "{word}" в контексте предложения: 2-3 предложения на русском языке]
""".trimIndent()

val DEFAULT_ENGLISH_PROMPT = """
Provide complete dictionary information for the English word "{word}" from the sentence: "{sentence}".
Analyze the English grammatical features (part of speech, verb forms like v1/v2/v3, pleural, or key collocations).

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод английского слова "{word}" на русский язык, одно слово или несколько вариантов через запятую (например: "эссенция" или "эссенция, экстракт"). Категорически запрещено добавлять любые вводные слова или фразы вроде "переводится как", "в данном контексте это значит", "глагол" или "существительное". Пиши ТОЛЬКО чистый перевод и ничего кроме него!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод английского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Краткое грамматическое или лексическое объяснение слова "{word}" в контексте предложения: 2-3 предложения на русском языке]
""".trimIndent()

val DEFAULT_FRENCH_PROMPT = """
Дай полную информацию о французском слове "{word}" из предложения: "{sentence}".
Учти грамматические особенности французского слова (род, артикль le/la/l'/les, если применимо, форму множественного числа, спряжение глагола или управление).

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод французского слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Категорически запрещено добавлять любые вводные слова или фразы. Пиши ТОЛЬКО чистый перевод и ничего кроме него!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод французского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Краткое грамматическое или лексическое объяснение слова "{word}" в контексте предложения: 2-3 предложения на русском языке]
""".trimIndent()

val ETYMOLOGY_GERMAN_PROMPT = """
Дай полную информацию о немецком слове "{word}" из предложения: "{sentence}".
Сосредоточься на этимологии слова, его происхождении, разборе по составу (корень, суффикс, приставка, составные части для сложных слов) и исторических связях. Избегай стандартных грамматических правил вроде артиклей der/die/das и форм множественного числа, если они не несут уникального исторического интереса.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод немецкого слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод немецкого предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Напиши на русском языке историю происхождения слова "{word}", его этимологию и разбор структуры слова (из каких частей состоит). Покажи связь с родственными словами.]
""".trimIndent()

val ETYMOLOGY_ENGLISH_PROMPT = """
Provide complete dictionary information for the English word "{word}" from the sentence: "{sentence}".
Focus on the word's etymology, origin, morphological breakdown (roots, prefixes, suffixes), and historical context. Avoid simple grammatical features like part of speech or standard plurals unless they have historical significance.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод английского слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод английского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Напиши на русском языке историю происхождения слова "{word}", его этимологию и разбор структуры слова (из каких частей состоит, корни, приставки/суффиксы). Покажи связь с родственными словами.]
""".trimIndent()

val ETYMOLOGY_FRENCH_PROMPT = """
Дай полную информацию о французском слове "{word}" из предложения: "{sentence}".
Сосредоточься на этимологии слова, его латинском или романском происхождении, разборе по составу (корень, суффиксы, приставки) и исторических связях.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод французского слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод французского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Напиши на русском языке историю происхождения слова "{word}", его этимологию и морфологический разбор.]
""".trimIndent()

val MNEMONICS_GERMAN_PROMPT = """
Дай полную информацию о немецком слове "{word}" из предложения: "{sentence}".
Придумай яркую мнемоническую ассоциацию или запоминалку для русскоговорящего (звуковые сходства, забавный образ или рифма) для быстрого запоминания слова "{word}". Добавь 2-3 ключевых устойчивых выражения или колоритных словосочетания с этим словом. Избегай сухого описания артиклей и падежей.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод немецкого слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод немецкого предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Опиши яркую мнемоническую ассоциацию (образ/звучание) для запоминания слова "{word}". Приведи 2-3 классных живых выражения с ним на немецком с переводом на русский.]
""".trimIndent()

val MNEMONICS_ENGLISH_PROMPT = """
Provide complete dictionary information for the English word "{word}" from the sentence: "{sentence}".
Create a vivid mnemonic association or memory trick (verbal link, humorous mental image or phonetic resemblance) to help a Russian speaker easily memorize "{word}". Add 2-3 useful collocations or colorful idioms containing this word. Avoid standard dry grammatical notes.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод английского слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод английского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Опиши яркую мнемоническую ассоциацию (образ/звучание) для запоминания слова "{word}". Приведи 2-3 классных живых выражения/идиомы с ним на английском с переводом на русский.]
""".trimIndent()

val MNEMONICS_FRENCH_PROMPT = """
Дай полную информацию о французском слове "{word}" из предложения: "{sentence}".
Придумай яркую мнемоническую ассоциацию или запоминалку для русскоговорящего для быстрого запоминания слова "{word}". Добавь 2-3 устойчивых выражения на французском с переводом на русский.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод французского слова "{word}" на русский язык. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод французского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Опиши яркую мнемоническую ассоциацию для запоминания слова "{word}" и приведи 2-3 живых выражения с ним.]
""".trimIndent()

val NUANCES_GERMAN_PROMPT = """
Дай полную информацию о немецком слове "{word}" из предложения: "{sentence}".
Расскажи об интересных фактах, культурном контексте или тонкостях употребления этого слова современными носителями. Есть ли разница в регионах (Германия/Австрия/Швейцария), относится ли оно к сленгу, разговорной речи или устаревает. Сравни с похожими синонимами и поясни разницу. Не нужно описывать базовую грамматику.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод немецкого слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод немецкого предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Поясни тонкости употребления слова "{word}" в живой речи, интересные социокультурные детали, его сленговые или современные аспекты и разницу с синонимами.]
""".trimIndent()

val NUANCES_ENGLISH_PROMPT = """
Provide complete dictionary information for the English word "{word}" from the sentence: "{sentence}".
Explain the interesting facts, cultural context, or delicate nuances of usage for "{word}" by modern native speakers. Are there British vs American differences? Is it slang, formal, or getting outdated? Compare with close synonyms to clarify the difference. Avoid standard grammatical tables.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод английского слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод английского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Поясни тонкости употребления слова "{word}" в живой речи (например, British/American, сленг, регистр общения), интересные социокультурные детали и сравнение с похожими синонимами.]
""".trimIndent()

val NUANCES_FRENCH_PROMPT = """
Дай полную информацию о французском слове "{word}" из предложения: "{sentence}".
Расскажи об интересных фактах, социокультурном контексте или тонкостях употребления этого слова во Франции и франкоязычном мире (разговорная речь, арго, формальный стиль). Сравни с близкими синонимами.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод французского слова "{word}" на русский язык. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод французского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Поясни тонкости употребления слова "{word}" в живой речи, стилистические оттенки и сравнение с синонимами.]
""".trimIndent()

val CLASSIC_GERMAN_PROMPT = """
Дай естественное и понятное объяснение немецкому слову "{word}" из предложения: "{sentence}".
Откажись от сухих словарных списков в основном тексте. Расскажи об этом слове живым, связным текстом, концентрируясь на его смысле и контексте. Если слово состоит из нескольких частей (составное слово), обязательно разбери его на составные части и объясни, как из этих простых слов складывается общий смысл.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод немецкого слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод немецкого предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Напиши 1-2 связных абзаца обычного текста без списков. Расскажи, какой смысловой оттенок несет это слово и в каких жизненных ситуациях его уместно использовать. Если слово составное, обязательно укажи прямо в тексте, из каких самостоятельных слов оно образовано и как они формируют его значение. 
Затем, с новой строки, напиши слово "Примеры:" и ниже приведи 2-3 примера. Важно: это должны быть полные немецкие словосочетания или короткие предложения в формате "Оригинал на немецком — перевод на русский".]
""".trimIndent()

val CLASSIC_ENGLISH_PROMPT = """
Дай естественное и понятное объяснение английскому слову "{word}" из предложения: "{sentence}".
Откажись от сухих словарных списков в основном тексте. Расскажи об этом слове живым, связным текстом, концентрируясь на его смысле и контексте. Если слово состоит из нескольких частей (сложное слово, содержит приставки/суффиксы или является фразовым глаголом), обязательно разбери его на составные части и объясни, как из них складывается общий смысл.

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод английского слова "{word}" на русский язык, одно слово или несколько вариантов через запятую. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод английского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Напиши 1-2 связных абзаца обычного текста без списков. Расскажи, какой смысловой оттенок несет это слово и в каких жизненных ситуациях его уместно использовать. Если слово составное, содержит приставки/суффиксы или является фразовым глаголом, обязательно укажи прямо в тексте, из каких частей оно образовано и как они формируют его значение. 

Затем, с новой строки, напиши слово "Примеры:" и ниже приведи 2-3 примера. Важно: это должны быть полные английские словосочетания или короткие предложения в формате "Оригинал на английском — перевод на русский".]
""".trimIndent()

val CLASSIC_FRENCH_PROMPT = """
Дай естественное и понятное объяснение французскому слову "{word}" из предложения: "{sentence}".
Расскажи об этом слове живым, связным текстом, концентрируясь на его смысле и контексте. 

Отвечай на русском языке строго в следующем формате по разделам:

===ПЕРЕВОД СЛОВА===
[СТРОГО только сам перевод французского слова "{word}" на русский язык. Без лишнего текста!]

===ПЕРЕВОД ПРЕДЛОЖЕНИЯ===
[Точный и естественный перевод французского предложения "{sentence}" на русский язык]

===ОБЪЯСНЕНИЕ===
[Напиши 1-2 связных абзаца обычного текста без списков. Расскажи, какой смысловой оттенок несет это слово и в каких ситуациях его уместно использовать.
Затем, с новой строки, напиши слово "Примеры:" и ниже приведи 2-3 примера в формате "Оригинал на французском — перевод на русский".]
""".trimIndent()

val DEFAULT_PROMPTS_LIST = listOf(
    CustomPrompt(
        id = "default",
        name = "По умолчанию (Default)",
        germanPromptTemplate = DEFAULT_GERMAN_PROMPT,
        englishPromptTemplate = DEFAULT_ENGLISH_PROMPT,
        frenchPromptTemplate = DEFAULT_FRENCH_PROMPT,
        isActive = true
    ),
    CustomPrompt(
        id = "classic",
        name = "Классический (Разбор в контексте)",
        germanPromptTemplate = CLASSIC_GERMAN_PROMPT,
        englishPromptTemplate = CLASSIC_ENGLISH_PROMPT,
        frenchPromptTemplate = CLASSIC_FRENCH_PROMPT,
        isActive = false
    ),
    CustomPrompt(
        id = "etymology_breakdown",
        name = "Этимология и разбор слова",
        germanPromptTemplate = ETYMOLOGY_GERMAN_PROMPT,
        englishPromptTemplate = ETYMOLOGY_ENGLISH_PROMPT,
        frenchPromptTemplate = ETYMOLOGY_FRENCH_PROMPT,
        isActive = false
    ),
    CustomPrompt(
        id = "mnemonics_associations",
        name = "Ассоциации и мнемоника",
        germanPromptTemplate = MNEMONICS_GERMAN_PROMPT,
        englishPromptTemplate = MNEMONICS_ENGLISH_PROMPT,
        frenchPromptTemplate = MNEMONICS_FRENCH_PROMPT,
        isActive = false
    ),
    CustomPrompt(
        id = "nuances_facts",
        name = "Тонкости употребления и факты",
        germanPromptTemplate = NUANCES_GERMAN_PROMPT,
        englishPromptTemplate = NUANCES_ENGLISH_PROMPT,
        frenchPromptTemplate = NUANCES_FRENCH_PROMPT,
        isActive = false
    )
)

@kotlinx.coroutines.FlowPreview
class MainViewModel(
    private val repository: BookRepository,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
) : ViewModel() {

    // TextToSpeech lazy support
    private var tts: android.speech.tts.TextToSpeech? = null
    private var isTtsInitialized = false

    fun initTts(context: android.content.Context) {
        if (tts == null) {
            tts = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                }
            }
        }
    }

    fun speak(text: String, languageCode: String = "de") {
        if (tts == null) return
        val locale = when (languageCode.lowercase()) {
            "de" -> java.util.Locale.GERMAN
            "fr" -> java.util.Locale.FRENCH
            else -> java.util.Locale.ENGLISH
        }
        try {
            tts?.language = locale
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Settings StateFlows
    private val _groqApiKeys = MutableStateFlow<List<GroqApiKeyItem>>(emptyList())
    val groqApiKeys: StateFlow<List<GroqApiKeyItem>> = _groqApiKeys.asStateFlow()

    private val _selectedGroqKeyId = MutableStateFlow("")
    val selectedGroqKeyId: StateFlow<String> = _selectedGroqKeyId.asStateFlow()

    private val _groqApiKey = MutableStateFlow("")
    val groqApiKey: StateFlow<String> = _groqApiKey.asStateFlow()

    private val _studyWordPortionSize = MutableStateFlow(10)
    val studyWordPortionSize: StateFlow<Int> = _studyWordPortionSize.asStateFlow()

    private val _variantsTranslationState = MutableStateFlow<VariantsUiState>(VariantsUiState.Idle)
    val variantsTranslationState: StateFlow<VariantsUiState> = _variantsTranslationState.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow("openai/gpt-oss-20b")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _currentLanguage = MutableStateFlow("de")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private val _appThemeMode = MutableStateFlow("system")
    val appThemeMode: StateFlow<String> = _appThemeMode.asStateFlow()

    private val _readingFontSize = MutableStateFlow(18f)
    val readingFontSize: StateFlow<Float> = _readingFontSize.asStateFlow()

    private val _translationFontSize = MutableStateFlow(14f)
    val translationFontSize: StateFlow<Float> = _translationFontSize.asStateFlow()

    private val _learningColor = MutableStateFlow("orange")
    val learningColor: StateFlow<String> = _learningColor.asStateFlow()

    private val _learnedColor = MutableStateFlow("green")
    val learnedColor: StateFlow<String> = _learnedColor.asStateFlow()

    private val _targetCorrectCount = MutableStateFlow(8)
    val targetCorrectCount: StateFlow<Int> = _targetCorrectCount.asStateFlow()

    private val _gameRepetitionsLimit = MutableStateFlow(20)
    val gameRepetitionsLimit: StateFlow<Int> = _gameRepetitionsLimit.asStateFlow()

    private val _customPrompts = MutableStateFlow<List<CustomPrompt>>(emptyList())
    val customPrompts: StateFlow<List<CustomPrompt>> = _customPrompts.asStateFlow()

    // Interactive Translation StateFlows
    private val _translationState = MutableStateFlow<TranslationUiState>(TranslationUiState.Idle)
    val translationState: StateFlow<TranslationUiState> = _translationState.asStateFlow()

    private val _paragraphTranslations = MutableStateFlow<Map<String, ParagraphTranslationUiState>>(emptyMap())
    val paragraphTranslations: StateFlow<Map<String, ParagraphTranslationUiState>> = _paragraphTranslations.asStateFlow()

    private val _inlineParagraphTranslations = MutableStateFlow<Map<String, InlineParagraphTranslationUiState>>(emptyMap())
    val inlineParagraphTranslations: StateFlow<Map<String, InlineParagraphTranslationUiState>> = _inlineParagraphTranslations.asStateFlow()

    private val _chapterBatchStudyState = MutableStateFlow(ChapterBatchStudyState())
    val chapterBatchStudyState: StateFlow<ChapterBatchStudyState> = _chapterBatchStudyState.asStateFlow()

    // Caching tokenized book content for dynamic statistics recalculations on list screens
    data class BookCacheData(
        val tokens: List<String>,
        val totalChapters: Int,
        val totalPages: Int,
        val chapterPages: List<Int>,
        val chapterParagraphCounts: List<Int>,
        val uniqueTokens: Set<String> = tokens.toSet()
    )

    data class BookStatsData(
        val stats: BookStatistics?,
        val newCount: Int,
        val learningCount: Int,
        val learnedCount: Int
    )

    private val _bookCache = MutableStateFlow<Map<Int, BookCacheData>>(emptyMap())
    val bookCache: StateFlow<Map<Int, BookCacheData>> = _bookCache.asStateFlow()

    private val _isStatsRefreshing = MutableStateFlow(false)
    val isStatsRefreshing: StateFlow<Boolean> = _isStatsRefreshing.asStateFlow()

    private val _currentBookId = MutableStateFlow<Int?>(null)
    val currentBookId: StateFlow<Int?> = _currentBookId.asStateFlow()

    private val _activeChapterIndex = MutableStateFlow(0)
    val activeChapterIndex: StateFlow<Int> = _activeChapterIndex.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _importProgress = MutableStateFlow<ImportProgressState?>(null)
    val importProgress: StateFlow<ImportProgressState?> = _importProgress.asStateFlow()

    private val _sortBy = MutableStateFlow(SortType.NEWEST)
    val sortBy: StateFlow<SortType> = _sortBy.asStateFlow()

    private val _groupByNewWords = MutableStateFlow(false)
    val groupByNewWords: StateFlow<Boolean> = _groupByNewWords.asStateFlow()

    // Flow of all known words from the database filtered by selected language style
    val knownWords: StateFlow<List<KnownWord>> = combine(
        repository.allKnownWords,
        _currentLanguage
    ) { allWords, activeLang ->
        allWords.filter { it.language == activeLang }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val studyWords: StateFlow<List<StudyWord>> = combine(
        repository.allStudyWords,
        _currentLanguage
    ) { allWords, activeLang ->
        allWords.filter { it.language == activeLang }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Cache lookup for fast set operations (Learned words with status = 2)
    val knownWordsSet: StateFlow<Set<String>> = knownWords
        .map { list -> list.filter { it.status == 2 }.map { it.word.lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Cache lookup for fast set operations (Learning words with status = 1)
    val learningWordsSet: StateFlow<Set<String>> = knownWords
        .map { list -> list.filter { it.status == 1 }.map { it.word.lowercase() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Word Activity Statistics
    val allWordActivityEvents: Flow<List<WordActivityEvent>> = repository.allWordActivityEvents

    private val _statsFilterAllLanguages = MutableStateFlow(false)
    val statsFilterAllLanguages: StateFlow<Boolean> = _statsFilterAllLanguages.asStateFlow()

    fun setStatsFilterAllLanguages(all: Boolean) {
        _statsFilterAllLanguages.value = all
    }

    val dayWordStats: StateFlow<List<DayWordStats>> = combine(
        repository.allWordActivityEvents,
        _currentLanguage,
        _statsFilterAllLanguages
    ) { events, activeLang, allLangs ->
        val filteredEvents = if (allLangs) events else events.filter { it.language == activeLang }
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayKey = sdf.format(java.util.Date())

        val grouped = filteredEvents.groupBy { event ->
            sdf.format(java.util.Date(event.timestamp))
        }.toMutableMap()

        // Always ensure today is present so the user can immediately see today's counter (e.g. 0 words)
        if (!grouped.containsKey(todayKey)) {
            grouped[todayKey] = emptyList()
        }

        grouped.map { (dateKey, dayEvents) ->
            // For each word on this day, resolve to its latest activity event on that day
            val wordLatestEvent = dayEvents
                .sortedBy { it.timestamp }
                .groupBy { it.word.lowercase() }
                .mapValues { (_, evs) -> evs.last() }

            val learning = wordLatestEvent.values
                .filter { it.status == 1 }
                .map { it.word }
                .distinct()
                .sorted()
            val learned = wordLatestEvent.values
                .filter { it.status == 2 }
                .map { it.word }
                .distinct()
                .sorted()
            val latestTimestamp = dayEvents.maxOfOrNull { it.timestamp }
                ?: if (dateKey == todayKey) System.currentTimeMillis() else 0L

            DayWordStats(
                dateKey = dateKey,
                timestamp = latestTimestamp,
                learningWords = learning,
                learnedWords = learned
            )
        }.filter { it.dateKey == todayKey || it.totalCount > 0 }
        .sortedByDescending { it.dateKey }
    }.flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val monthWordStats: StateFlow<List<MonthWordStats>> = dayWordStats.map { days ->
        val currentMonthKey = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault()).format(java.util.Date())
        val monthFormatter = java.text.SimpleDateFormat("LLLL yyyy", java.util.Locale("ru"))
        val parseSdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())

        val grouped = days.groupBy { if (it.dateKey.length >= 7) it.dateKey.substring(0, 7) else it.dateKey }.toMutableMap()
        if (!grouped.containsKey(currentMonthKey)) {
            grouped[currentMonthKey] = emptyList()
        }

        grouped.map { (mKey, mDays) ->
            val dateObj = try {
                parseSdf.parse(mKey) ?: java.util.Date()
            } catch (e: Exception) {
                java.util.Date()
            }
            val label = monthFormatter.format(dateObj).replaceFirstChar { it.uppercase() }
            val learning = mDays.flatMap { it.learningWords }.distinct().sorted()
            val learned = mDays.flatMap { it.learnedWords }.distinct().sorted()
            val latestTs = mDays.maxOfOrNull { it.timestamp } ?: dateObj.time

            MonthWordStats(
                monthKey = mKey,
                monthLabel = label,
                timestamp = latestTs,
                days = mDays.sortedByDescending { it.dateKey },
                learningWords = learning,
                learnedWords = learned
            )
        }.filter { it.monthKey == currentMonthKey || it.totalCount > 0 }
        .sortedByDescending { it.monthKey }
    }.flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val yearWordStats: StateFlow<List<YearWordStats>> = monthWordStats.map { months ->
        val currentYearKey = java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        val grouped = months.groupBy { if (it.monthKey.length >= 4) it.monthKey.substring(0, 4) else it.monthKey }.toMutableMap()
        if (!grouped.containsKey(currentYearKey)) {
            grouped[currentYearKey] = emptyList()
        }

        grouped.map { (yKey, yMonths) ->
            val learning = yMonths.flatMap { it.learningWords }.distinct().sorted()
            val learned = yMonths.flatMap { it.learnedWords }.distinct().sorted()
            val latestTs = yMonths.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()

            YearWordStats(
                yearKey = yKey,
                yearLabel = "$yKey год",
                timestamp = latestTs,
                months = yMonths.sortedByDescending { it.monthKey },
                learningWords = learning,
                learnedWords = learned
            )
        }.filter { it.yearKey == currentYearKey || it.totalCount > 0 }
        .sortedByDescending { it.yearKey }
    }.flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearWordActivityHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllActivityEvents()
        }
    }

    // Reactive Single Source of Truth for book statistics.
    // Automatically recalculates immediately whenever book cache, known words, or learning words change!
    val bookStatsCache: StateFlow<Map<Int, BookStatsData>> = combine(
        _bookCache,
        knownWordsSet,
        learningWordsSet
    ) { cache, knownSet, learningSet ->
        val newStatsMap = HashMap<Int, BookStatsData>(cache.size)
        cache.forEach { (bookId, cacheData) ->
            val stats = LinguisticEngine.calculateStatistics(cacheData.tokens, knownSet)
            val uniqueTokens = cacheData.uniqueTokens
            val learnedCount = uniqueTokens.count { knownSet.contains(it) }
            val learningCount = uniqueTokens.count { learningSet.contains(it) }
            val newCount = uniqueTokens.size - learnedCount - learningCount
            newStatsMap[bookId] = BookStatsData(
                stats = stats,
                newCount = newCount,
                learningCount = learningCount,
                learnedCount = learnedCount
            )
        }
        newStatsMap
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun refreshBookStatistics() {
        viewModelScope.launch(defaultDispatcher) {
            _isStatsRefreshing.value = true
            try {
                val books = repository.getAllBooksList()
                val knownWords = repository.getAllKnownWordsList()
                val currentCache = _bookCache.value.toMutableMap()

                books.forEach { book ->
                    val knownSet = knownWords.filter { it.language == book.language && it.status == 2 }
                        .map { it.word.lowercase() }.toSet()
                    val learningSet = knownWords.filter { it.language == book.language && it.status == 1 }
                        .map { it.word.lowercase() }.toSet()

                    val chapters = repository.getBookChaptersList(book.id)
                    val fullText = chapters.joinToString("\n") { it.content }
                    val tokens = LinguisticEngine.tokenizeText(fullText)
                    val uniqueTokens = tokens.toSet()

                    val stats = LinguisticEngine.calculateStatistics(tokens, knownSet)
                    val learnedCount = uniqueTokens.count { knownSet.contains(it) }
                    val learningCount = uniqueTokens.count { learningSet.contains(it) }
                    val newCount = uniqueTokens.size - learnedCount - learningCount

                    val cacheData = createBookCacheData(tokens, chapters)
                    currentCache[book.id] = cacheData

                    // Persist updated stats in phone storage (Room DB)
                    repository.updateBookStats(
                        bookId = book.id,
                        totalChapters = cacheData.totalChapters,
                        totalPages = cacheData.totalPages,
                        learnedWordsCount = learnedCount,
                        learningWordsCount = learningCount,
                        newWordsCount = newCount,
                        comprehensionPercent = stats.comprehensibleVolumePercent,
                        complexityPercent = stats.complexityVolumePercent
                    )
                }
                _bookCache.value = currentCache
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isStatsRefreshing.value = false
            }
        }
    }

    private fun createBookCacheData(tokens: List<String>, chapters: List<ChapterEntity>): BookCacheData {
        val chapterPages = chapters.map { ch ->
            Math.max(1, (ch.content.length + 1199) / 1200)
        }
        val totalPages = chapterPages.sum()
        val chapterParagraphCounts = chapters.map { ch ->
            ch.content.split(Regex("\\n\\s*\\n"))
                .filter { it.trim().isNotEmpty() }
                .size
                .coerceAtLeast(1)
        }
        return BookCacheData(
            tokens = tokens,
            totalChapters = chapters.size,
            totalPages = totalPages,
            chapterPages = chapterPages,
            chapterParagraphCounts = chapterParagraphCounts,
            uniqueTokens = tokens.toSet()
        )
    }

    // Keep memory cache clean on book deletions without running heavy recalculations on app open
    init {
        viewModelScope.launch(defaultDispatcher) {
            repository.allBooks.collect { books ->
                val currentCache = _bookCache.value.toMutableMap()
                val bookIds = books.map { it.id }.toSet()
                val cachedIds = currentCache.keys.toSet()
                var changed = false
                cachedIds.forEach { id ->
                    if (!bookIds.contains(id)) {
                        currentCache.remove(id)
                        changed = true
                    }
                }
                if (changed) {
                    _bookCache.value = currentCache
                }
            }
        }
    }

    // Process and sort Library Items based on user selections
    val libraryItems: StateFlow<List<BookUiItem>> = combine(
        repository.allBooks,
        _bookCache,
        bookStatsCache,
        _sortBy,
        _currentLanguage
    ) { books, cache, statsCache, sort, activeLang ->
        val filteredBooks = books.filter { it.language == activeLang }
        val items = filteredBooks.map { book ->
            val cachedData = cache[book.id]
            val statsData = statsCache[book.id]
            
            val totalCh = cachedData?.totalChapters ?: book.totalChapters.takeIf { it > 0 } ?: 1
            val totalP = cachedData?.totalPages ?: book.totalPages.takeIf { it > 0 } ?: 1
            
            val learnedCnt = statsData?.learnedCount ?: book.learnedWordsCount
            val learningCnt = statsData?.learningCount ?: book.learningWordsCount
            val newCnt = statsData?.newCount ?: book.newWordsCount.takeIf { it > 0 || (learnedCnt == 0 && learningCnt == 0) } ?: (book.uniqueWordsCount - learnedCnt - learningCnt).coerceAtLeast(0)

            val stats = statsData?.stats ?: BookStatistics(
                comprehensibleVolumePercent = book.comprehensionPercent,
                knownUniquePercent = if (book.uniqueWordsCount > 0) (learnedCnt.toDouble() / book.uniqueWordsCount) * 100.0 else 0.0,
                unknownUniquePercent = if (book.uniqueWordsCount > 0) (((book.uniqueWordsCount - learnedCnt).coerceAtLeast(0)).toDouble() / book.uniqueWordsCount) * 100.0 else 0.0,
                complexityVolumePercent = book.complexityPercent.takeIf { it > 0.0 } ?: (100.0 - book.comprehensionPercent).coerceAtLeast(0.0),
                totalWords = book.totalWords,
                uniqueWordsCount = book.uniqueWordsCount,
                knownWordsCount = learnedCnt,
                unknownWordsCount = (book.uniqueWordsCount - learnedCnt).coerceAtLeast(0)
            )

            val curPage = if (cachedData != null) {
                val chapterPages = cachedData.chapterPages
                val paragraphCounts = cachedData.chapterParagraphCounts
                
                // Pages read in fully completed chapters before current chapter
                val pagesBefore = chapterPages.take(book.currentChapterIndex).sum()
                
                // Progress inside the current chapter
                val currentChapterPages = chapterPages.getOrNull(book.currentChapterIndex) ?: 1
                val currentChapterParagraphs = paragraphCounts.getOrNull(book.currentChapterIndex) ?: 1
                
                // Fraction of current chapter read based on currentScrollPosition
                val scrollPosPos = book.currentScrollPosition.coerceAtLeast(0)
                val fraction = (scrollPosPos.toFloat() / currentChapterParagraphs).coerceIn(0f, 1f)
                
                val pagesInCurrent = fraction * currentChapterPages
                val currentPageFloat = pagesBefore + pagesInCurrent
                Math.round(currentPageFloat).toInt().coerceIn(0, totalP)
            } else {
                if (totalCh > 0 && book.currentChapterIndex > 0) {
                    ((book.currentChapterIndex.toFloat() / totalCh) * totalP).toInt().coerceIn(0, totalP)
                } else {
                    0
                }
            }
            
            BookUiItem(
                book = book,
                totalChapters = totalCh,
                stats = stats,
                newWordsCount = newCnt,
                learningWordsCount = learningCnt,
                learnedWordsCount = learnedCnt,
                totalPages = totalP,
                currentPage = curPage
            )
        }

        when (sort) {
            SortType.NEWEST -> items.sortedByDescending { it.book.id }
            SortType.COMPLEXITY_ASC -> items.sortedBy { it.stats?.complexityVolumePercent ?: 0.0 }
            SortType.COMPLEXITY_DESC -> items.sortedByDescending { it.stats?.complexityVolumePercent ?: 0.0 }
            SortType.DENSITY_ASC -> items.sortedBy {
                if (it.book.totalWords > 0) {
                    it.book.uniqueWordsCount.toDouble() / it.book.totalWords
                } else {
                    0.0
                }
            }
            SortType.DENSITY_DESC -> items.sortedByDescending {
                if (it.book.totalWords > 0) {
                    it.book.uniqueWordsCount.toDouble() / it.book.totalWords
                } else {
                    0.0
                }
            }
            SortType.WORDS_ASC -> items.sortedBy { it.book.totalWords }
            SortType.WORDS_DESC -> items.sortedByDescending { it.book.totalWords }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active reading state
    val currentBook: StateFlow<BookEntity?> = _currentBookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.allBooks.map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentChapters: StateFlow<List<ChapterEntity>> = _currentBookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getBookChaptersFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeChapter: StateFlow<ChapterEntity?> = combine(
        currentChapters,
        _activeChapterIndex
    ) { chapters, idx ->
        if (chapters.isEmpty()) {
            null
        } else {
            val safeIdx = idx.coerceIn(0, chapters.size - 1)
            chapters[safeIdx]
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val chapterTokensCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<String>, Set<String>>>()

    val activeChapterStats: StateFlow<Map<Int, ChapterStatsData>> = combine(
        currentChapters,
        knownWordsSet,
        learningWordsSet
    ) { chapters, knownSet, learningSet ->
        chapters.mapIndexed { index, chapter ->
            val cacheKey = "${chapter.id}_${chapter.content.length}_${chapter.content.hashCode()}"
            val (tokens, uniqueWords) = chapterTokensCache.getOrPut(cacheKey) {
                val tok = LinguisticEngine.tokenizeText(chapter.content)
                Pair(tok, tok.toSet())
            }
            val totalWords = tokens.size
            val uniqueWordsCount = uniqueWords.size
            
            val knownCount = tokens.count { knownSet.contains(it) }
            val unknownCount = totalWords - knownCount
            val complexity = if (totalWords > 0) (unknownCount.toDouble() / totalWords) * 100.0 else 0.0
            
            val learnedUnique = uniqueWords.count { knownSet.contains(it) }
            val learningUnique = uniqueWords.count { learningSet.contains(it) }
            val newUnique = uniqueWordsCount - learnedUnique - learningUnique
            
            index to ChapterStatsData(complexity, newUnique, learningUnique, learnedUnique, uniqueWordsCount)
        }.toMap()
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Statistics block of current book
    val activeBookStatsData: StateFlow<BookStatsData?> = combine(
        _currentBookId,
        repository.allBooks,
        _bookCache,
        knownWordsSet,
        learningWordsSet
    ) { currentId, books, cache, knownSet, learningSet ->
        if (currentId == null) return@combine null
        val currentBook = books.firstOrNull { it.id == currentId }
        val cached = cache[currentId]
        if (cached != null) {
            val stats = LinguisticEngine.calculateStatistics(cached.tokens, knownSet)
            val uniqueTokens = cached.uniqueTokens
            val learnedCount = uniqueTokens.count { knownSet.contains(it) }
            val learningCount = uniqueTokens.count { learningSet.contains(it) }
            val newCount = uniqueTokens.size - learnedCount - learningCount
            BookStatsData(
                stats = stats,
                newCount = newCount,
                learningCount = learningCount,
                learnedCount = learnedCount
            )
        } else if (currentBook != null) {
            val learnedCnt = currentBook.learnedWordsCount
            val learningCnt = currentBook.learningWordsCount
            val newCnt = currentBook.newWordsCount.takeIf { it > 0 || (learnedCnt == 0 && learningCnt == 0) }
                ?: (currentBook.uniqueWordsCount - learnedCnt - learningCnt).coerceAtLeast(0)
            val stats = BookStatistics(
                comprehensibleVolumePercent = currentBook.comprehensionPercent,
                knownUniquePercent = if (currentBook.uniqueWordsCount > 0) (learnedCnt.toDouble() / currentBook.uniqueWordsCount) * 100.0 else 0.0,
                unknownUniquePercent = if (currentBook.uniqueWordsCount > 0) (((currentBook.uniqueWordsCount - learnedCnt).coerceAtLeast(0)).toDouble() / currentBook.uniqueWordsCount) * 100.0 else 0.0,
                complexityVolumePercent = currentBook.complexityPercent.takeIf { it > 0.0 } ?: (100.0 - currentBook.comprehensionPercent).coerceAtLeast(0.0),
                totalWords = currentBook.totalWords,
                uniqueWordsCount = currentBook.uniqueWordsCount,
                knownWordsCount = learnedCnt,
                unknownWordsCount = (currentBook.uniqueWordsCount - learnedCnt).coerceAtLeast(0)
            )
            BookStatsData(
                stats = stats,
                newCount = newCnt,
                learningCount = learningCnt,
                learnedCount = learnedCnt
            )
        } else {
            null
        }
    }
    .flowOn(defaultDispatcher)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeBookStats: StateFlow<BookStatistics?> = activeBookStatsData
        .map { it?.stats }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun changeSort(type: SortType) {
        _sortBy.value = type
    }

    fun selectBook(bookId: Int) {
        viewModelScope.launch {
            val book = repository.getBookById(bookId)
            if (book != null) {
                _currentBookId.value = bookId
                _activeChapterIndex.value = book.currentChapterIndex
                repository.updateBookLastOpened(bookId, System.currentTimeMillis())
                ensureBookInCache(bookId)
            }
        }
    }

    fun ensureBookInCache(bookId: Int) {
        if (_bookCache.value.containsKey(bookId)) return
        viewModelScope.launch(defaultDispatcher) {
            try {
                val chapters = repository.getBookChaptersList(bookId)
                if (chapters.isNotEmpty()) {
                    val fullText = chapters.joinToString("\n") { it.content }
                    val tokens = LinguisticEngine.tokenizeText(fullText)
                    val currentCache = _bookCache.value.toMutableMap()
                    currentCache[bookId] = createBookCacheData(tokens, chapters)
                    _bookCache.value = currentCache
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectChapter(index: Int, scrollPos: Int = 0) {
        val count = currentChapters.value.size
        if (count > 0) {
            val safeIdx = index.coerceIn(0, count - 1)
            _activeChapterIndex.value = safeIdx
            saveProgress(safeIdx, scrollPos)
            clearParagraphTranslations()
        }
    }

    fun saveProgress(chapterIndex: Int, scrollPos: Int) {
        val bookId = _currentBookId.value ?: return
        viewModelScope.launch {
            repository.updateBookProgress(bookId, chapterIndex, scrollPos)
        }
    }

    fun deleteBook(bookId: Int, context: android.content.Context? = null) {
        viewModelScope.launch {
            if (_currentBookId.value == bookId) {
                _currentBookId.value = null
                _activeChapterIndex.value = 0
            }
            val currentCache = _bookCache.value.toMutableMap()
            currentCache.remove(bookId)
            _bookCache.value = currentCache
            context?.let { ctx ->
                try {
                    val file = java.io.File(ctx.filesDir, "covers/cover_${bookId}.jpg")
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            repository.deleteBook(bookId)
        }
    }

    fun deleteBooks(bookIds: Set<Int>, context: android.content.Context? = null) {
        viewModelScope.launch {
            val currentCache = _bookCache.value.toMutableMap()
            bookIds.forEach { id ->
                if (_currentBookId.value == id) {
                    _currentBookId.value = null
                    _activeChapterIndex.value = 0
                }
                currentCache.remove(id)
                context?.let { ctx ->
                    try {
                        val file = java.io.File(ctx.filesDir, "covers/cover_${id}.jpg")
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                repository.deleteBook(id)
            }
            _bookCache.value = currentCache
        }
    }

    // Settings Handlers
    fun initSettings(context: android.content.Context) {
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)

        val keysJson = prefs.getString("groq_api_keys_json", null)
        val legacyKey = prefs.getString("groq_api_key", "") ?: ""
        val savedSelectedId = prefs.getString("groq_selected_key_id", "") ?: ""

        val loadedKeys = mutableListOf<GroqApiKeyItem>()
        if (!keysJson.isNullOrBlank()) {
            try {
                val array = org.json.JSONArray(keysJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                    val name = obj.optString("name", "Ключ ${i + 1}")
                    val key = obj.optString("key", "")
                    if (key.isNotBlank()) {
                        loadedKeys.add(GroqApiKeyItem(id = id, name = name, key = key))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (loadedKeys.isEmpty()) {
            val initialKey = legacyKey.ifBlank { DEFAULT_GROQ_API_KEY }
            val initialItem = GroqApiKeyItem(
                id = "default_key",
                name = "Основной ключ",
                key = initialKey
            )
            loadedKeys.add(initialItem)
            saveGroqApiKeysToPrefs(context, loadedKeys)
        }

        _groqApiKeys.value = loadedKeys
        val activeItem = loadedKeys.find { it.id == savedSelectedId } ?: loadedKeys.first()
        _selectedGroqKeyId.value = activeItem.id
        _groqApiKey.value = activeItem.key
        prefs.edit()
            .putString("groq_selected_key_id", activeItem.id)
            .putString("groq_api_key", activeItem.key)
            .apply()
        _currentLanguage.value = prefs.getString("current_language", "de") ?: "de"
        _appThemeMode.value = prefs.getString("app_theme_mode", "system") ?: "system"
        _readingFontSize.value = prefs.getFloat("reading_font_size", 18f)
        _translationFontSize.value = prefs.getFloat("translation_font_size", 14f)
        _learningColor.value = prefs.getString("learning_color", "orange") ?: "orange"
        _learnedColor.value = prefs.getString("learned_color", "green") ?: "green"
        _groupByNewWords.value = prefs.getBoolean("group_by_new_words", false)
        _studyWordPortionSize.value = prefs.getInt("study_word_portion_size", 10)
        _targetCorrectCount.value = prefs.getInt("target_correct_count", 8)
        _gameRepetitionsLimit.value = prefs.getInt("game_repetitions_limit", 20)

        val allowedModels = listOf(
            "openai/gpt-oss-20b",
            "openai/gpt-oss-120b",
            "qwen/qwen3.8-27b",
            "groq/compound-mini",
            "openai/gpt-oss-safeguard-20b"
        )
        _availableModels.value = allowedModels
        prefs.edit().putString("available_models", allowedModels.joinToString(",")).apply()

        val savedModel = prefs.getString("selected_model", "openai/gpt-oss-20b") ?: "openai/gpt-oss-20b"
        val cleanSavedModel = savedModel.substringBefore(" (").trim()
        val matchedModel = allowedModels.firstOrNull { it.substringBefore(" (").trim() == cleanSavedModel }
        _selectedModel.value = matchedModel ?: allowedModels.first()
        if (_selectedModel.value != savedModel) {
            prefs.edit().putString("selected_model", _selectedModel.value).apply()
        }

        loadCustomPrompts(context)
    }

    fun setSelectedModel(context: android.content.Context, model: String) {
        _selectedModel.value = model
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("selected_model", model).apply()
    }

    fun setStudyWordPortionSize(context: android.content.Context, size: Int) {
        _studyWordPortionSize.value = size
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("study_word_portion_size", size).apply()
    }

    fun setTargetCorrectCount(context: android.content.Context, count: Int) {
        _targetCorrectCount.value = count
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("target_correct_count", count).apply()
    }

    fun setGameRepetitionsLimit(context: android.content.Context, limit: Int) {
        _gameRepetitionsLimit.value = limit
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("game_repetitions_limit", limit).apply()
    }

    fun loadCustomPrompts(context: android.content.Context) {
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        val jsonString = prefs.getString("custom_prompts_json", null)
        if (jsonString == null) {
            _customPrompts.value = DEFAULT_PROMPTS_LIST
            saveCustomPromptsToPrefs(context, DEFAULT_PROMPTS_LIST)
        } else {
            try {
                val array = org.json.JSONArray(jsonString)
                val list = mutableListOf<CustomPrompt>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CustomPrompt(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            germanPromptTemplate = obj.getString("germanPromptTemplate"),
                            englishPromptTemplate = obj.getString("englishPromptTemplate"),
                            frenchPromptTemplate = obj.optString("frenchPromptTemplate", DEFAULT_FRENCH_PROMPT),
                            isActive = obj.getBoolean("isActive")
                        )
                    )
                }
                
                var listChanged = false
                // Auto-merge missing default prompts
                DEFAULT_PROMPTS_LIST.forEach { defaultPrompt ->
                    if (list.none { it.id == defaultPrompt.id }) {
                        list.add(defaultPrompt)
                        listChanged = true
                    }
                }

                val sortedList = list.sortedBy { item ->
                    val idx = DEFAULT_PROMPTS_LIST.indexOfFirst { it.id == item.id }
                    if (idx != -1) idx else Int.MAX_VALUE
                }
                if (sortedList != list) {
                    list.clear()
                    list.addAll(sortedList)
                    listChanged = true
                }

                if (list.none { it.isActive }) {
                    val first = list.firstOrNull()
                    if (first != null) {
                        list[0] = first.copy(isActive = true)
                        listChanged = true
                    } else {
                        list.addAll(DEFAULT_PROMPTS_LIST)
                        listChanged = true
                    }
                }
                _customPrompts.value = list
                if (listChanged) {
                    saveCustomPromptsToPrefs(context, list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _customPrompts.value = DEFAULT_PROMPTS_LIST
                saveCustomPromptsToPrefs(context, DEFAULT_PROMPTS_LIST)
            }
        }
    }

    private fun saveCustomPromptsToPrefs(context: android.content.Context, list: List<CustomPrompt>) {
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        val array = org.json.JSONArray()
        list.forEach { item ->
            val obj = org.json.JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("germanPromptTemplate", item.germanPromptTemplate)
                put("englishPromptTemplate", item.englishPromptTemplate)
                put("frenchPromptTemplate", item.frenchPromptTemplate)
                put("isActive", item.isActive)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_prompts_json", array.toString()).apply()
    }

    fun addCustomPrompt(context: android.content.Context, name: String, germanTemplate: String, englishTemplate: String, frenchTemplate: String = DEFAULT_FRENCH_PROMPT) {
        val id = java.util.UUID.randomUUID().toString()
        val newPrompt = CustomPrompt(id, name, germanTemplate, englishTemplate, frenchTemplate, false)
        val updated = _customPrompts.value + newPrompt
        _customPrompts.value = updated
        saveCustomPromptsToPrefs(context, updated)
    }

    fun updateCustomPrompt(context: android.content.Context, id: String, name: String, germanTemplate: String, englishTemplate: String, frenchTemplate: String = DEFAULT_FRENCH_PROMPT) {
        val updated = _customPrompts.value.map {
            if (it.id == id) {
                it.copy(name = name, germanPromptTemplate = germanTemplate, englishPromptTemplate = englishTemplate, frenchPromptTemplate = frenchTemplate)
            } else {
                it
            }
        }
        _customPrompts.value = updated
        saveCustomPromptsToPrefs(context, updated)
    }

    fun deleteCustomPrompt(context: android.content.Context, id: String) {
        val systemIds = listOf("default", "classic", "etymology_breakdown", "mnemonics_associations", "nuances_facts")
        if (id in systemIds) return
        val wasActive = _customPrompts.value.find { it.id == id }?.isActive ?: false
        val filtered = _customPrompts.value.filter { it.id != id }
        val updated = if (wasActive && filtered.isNotEmpty()) {
            filtered.mapIndexed { idx, item ->
                if (idx == 0) item.copy(isActive = true) else item
            }
        } else {
            filtered
        }
        _customPrompts.value = updated
        saveCustomPromptsToPrefs(context, updated)
    }

    fun activateCustomPrompt(context: android.content.Context, id: String) {
        val updated = _customPrompts.value.map {
            it.copy(isActive = (it.id == id))
        }
        _customPrompts.value = updated
        saveCustomPromptsToPrefs(context, updated)
    }

    fun fetchWordTranslationVariants(context: android.content.Context, word: String, sentence: String = "") {
        _variantsTranslationState.value = VariantsUiState.Loading
        viewModelScope.launch {
            val apiKey = _groqApiKey.value.ifBlank {
                val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
                prefs.getString("groq_api_key", "") ?: ""
            }.ifBlank { "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" }
            
            if (apiKey.isBlank()) {
                _variantsTranslationState.value = VariantsUiState.Error("API ключ Groq не настроен. Перейдите в настройки.")
                return@launch
            }
            val lang = _currentLanguage.value
            val model = _selectedModel.value

            try {
                val variants = com.example.data.api.GroqAPI.getWordTranslationVariants(
                    word = word,
                    sentence = sentence,
                    apiKey = apiKey,
                    language = lang,
                    model = model
                )
                _variantsTranslationState.value = VariantsUiState.Success(word, variants)
            } catch (e: Exception) {
                _variantsTranslationState.value = VariantsUiState.Error(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun addStudyWord(word: String, selectedVariants: List<String>) {
        viewModelScope.launch {
            val translation = selectedVariants.joinToString(", ")
            val lang = _currentLanguage.value
            repository.insertStudyWord(word = word, translation = translation, language = lang)
        }
    }

    fun incrementStudyWordCorrectCount(word: com.example.data.db.StudyWord) {
        viewModelScope.launch {
            val newCount = word.correctCount + 1
            if (newCount >= _targetCorrectCount.value) {
                repository.deleteStudyWord(word.id)
            } else {
                repository.updateStudyWordCorrectCount(word.id, newCount)
            }
        }
    }

    fun decrementStudyWordCorrectCount(word: com.example.data.db.StudyWord) {
        viewModelScope.launch {
            val newCount = word.correctCount - 1
            repository.updateStudyWordCorrectCount(word.id, newCount)
        }
    }

    fun deleteStudyWord(id: Int) {
        viewModelScope.launch {
            repository.deleteStudyWord(id)
        }
    }

    fun deleteAllStudyWords() {
        viewModelScope.launch {
            repository.deleteAllStudyWords()
        }
    }

    fun transferStudyWordsToLearned(words: List<StudyWord>) {
        viewModelScope.launch {
            words.forEach { studyWord ->
                repository.insertKnownWord(rawWord = studyWord.word, status = 2, language = studyWord.language)
                repository.deleteStudyWord(studyWord.id)
            }
        }
    }

    fun clearVariantsTranslationState() {
        _variantsTranslationState.value = VariantsUiState.Idle
    }

    fun addModel(context: android.content.Context, model: String) {
        val trimmed = model.trim()
        if (trimmed.isEmpty() || _availableModels.value.contains(trimmed)) return
        val newList = _availableModels.value + trimmed
        _availableModels.value = newList
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("available_models", newList.joinToString(",")).apply()
    }

    fun removeModel(context: android.content.Context, model: String) {
        val newList = _availableModels.value.filter { it != model }
        _availableModels.value = newList
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("available_models", newList.joinToString(",")).apply()

        if (_selectedModel.value == model) {
            val nextModel = newList.firstOrNull() ?: "openai/gpt-oss-20b"
            setSelectedModel(context, nextModel)
        }
    }

    fun setGroupByNewWords(context: android.content.Context, grouped: Boolean) {
        _groupByNewWords.value = grouped
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("group_by_new_words", grouped).apply()
    }

    private fun saveGroqApiKeysToPrefs(context: android.content.Context, list: List<GroqApiKeyItem>) {
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        val array = org.json.JSONArray()
        list.forEach { item ->
            val obj = org.json.JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("key", item.key)
            }
            array.put(obj)
        }
        prefs.edit().putString("groq_api_keys_json", array.toString()).apply()
    }

    fun selectGroqApiKey(context: android.content.Context, id: String) {
        val found = _groqApiKeys.value.find { it.id == id } ?: return
        _selectedGroqKeyId.value = id
        _groqApiKey.value = found.key
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("groq_selected_key_id", id)
            .putString("groq_api_key", found.key)
            .apply()
    }

    fun addGroqApiKey(context: android.content.Context, name: String, key: String, selectImmediately: Boolean = true) {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) return
        val trimmedName = name.trim().ifBlank { "Ключ ${_groqApiKeys.value.size + 1}" }
        val id = java.util.UUID.randomUUID().toString()
        val newItem = GroqApiKeyItem(id = id, name = trimmedName, key = trimmedKey)
        val updatedList = _groqApiKeys.value + newItem
        _groqApiKeys.value = updatedList
        saveGroqApiKeysToPrefs(context, updatedList)
        if (selectImmediately || updatedList.size == 1) {
            selectGroqApiKey(context, id)
        }
    }

    fun updateGroqApiKey(context: android.content.Context, id: String, name: String, key: String) {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) return
        val trimmedName = name.trim().ifBlank { "Ключ" }
        val updatedList = _groqApiKeys.value.map {
            if (it.id == id) it.copy(name = trimmedName, key = trimmedKey) else it
        }
        _groqApiKeys.value = updatedList
        saveGroqApiKeysToPrefs(context, updatedList)
        if (_selectedGroqKeyId.value == id) {
            _groqApiKey.value = trimmedKey
            val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("groq_api_key", trimmedKey).apply()
        }
    }

    fun deleteGroqApiKey(context: android.content.Context, id: String) {
        val currentList = _groqApiKeys.value
        val filtered = currentList.filter { it.id != id }
        if (filtered.isEmpty()) {
            val fallback = GroqApiKeyItem(id = "default_key", name = "Основной ключ", key = DEFAULT_GROQ_API_KEY)
            _groqApiKeys.value = listOf(fallback)
            saveGroqApiKeysToPrefs(context, listOf(fallback))
            selectGroqApiKey(context, fallback.id)
            return
        }
        _groqApiKeys.value = filtered
        saveGroqApiKeysToPrefs(context, filtered)
        if (_selectedGroqKeyId.value == id) {
            selectGroqApiKey(context, filtered.first().id)
        }
    }

    fun setGroqApiKey(context: android.content.Context, key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        val currentSelectedId = _selectedGroqKeyId.value
        val currentList = _groqApiKeys.value
        if (currentList.any { it.id == currentSelectedId }) {
            val curItem = currentList.first { it.id == currentSelectedId }
            updateGroqApiKey(context, currentSelectedId, curItem.name, trimmed)
        } else {
            addGroqApiKey(context, "Основной ключ", trimmed, selectImmediately = true)
        }
    }

    fun setCurrentLanguage(context: android.content.Context, lang: String) {
        _currentLanguage.value = lang
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("current_language", lang).apply()
    }

    fun setAppThemeMode(context: android.content.Context, mode: String) {
        _appThemeMode.value = mode
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme_mode", mode).apply()
    }

    fun setReadingFontSize(context: android.content.Context, size: Float) {
        _readingFontSize.value = size
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("reading_font_size", size).apply()
    }

    fun setTranslationFontSize(context: android.content.Context, size: Float) {
        val boundedSize = size.coerceIn(10f, 28f)
        _translationFontSize.value = boundedSize
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("translation_font_size", boundedSize).apply()
    }

    fun setLearningColor(context: android.content.Context, color: String) {
        _learningColor.value = color
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("learning_color", color).apply()
    }

    fun setLearnedColor(context: android.content.Context, color: String) {
        _learnedColor.value = color
        val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("learned_color", color).apply()
    }

    // Interactive AI Translators
    fun translateWordComplete(context: android.content.Context, cleanWord: String, rawWord: String, sentence: String) {
        _translationState.value = TranslationUiState.Loading
        viewModelScope.launch {
            val apiKey = _groqApiKey.value.ifBlank {
                val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
                prefs.getString("groq_api_key", "") ?: ""
            }.ifBlank { "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" }
            if (apiKey.isBlank()) {
                _translationState.value = TranslationUiState.Error("API ключ Groq не настроен. Перейдите в настройки для его добавления.")
                return@launch
            }
            val lang = _currentLanguage.value

            val activePr = _customPrompts.value.find { it.isActive }
            val customGerman = activePr?.germanPromptTemplate
            val customEnglish = activePr?.englishPromptTemplate
            val customFrench = activePr?.frenchPromptTemplate

            val modelsToTry = _availableModels.value.toMutableList()
            if (modelsToTry.isEmpty()) {
                modelsToTry.add("openai/gpt-oss-20b")
            }

            var currentTryModel = _selectedModel.value
            var startIndex = modelsToTry.indexOf(currentTryModel)
            if (startIndex == -1) {
                startIndex = 0
                currentTryModel = modelsToTry.getOrNull(0) ?: "openai/gpt-oss-20b"
            }

            var success = false
            var attempts = 0
            val maxAttempts = modelsToTry.size
            var lastErrorMsg = ""

            while (attempts < maxAttempts && !success) {
                val modelIndex = (startIndex + attempts) % modelsToTry.size
                val modelName = modelsToTry[modelIndex]

                try {
                    val data = com.example.data.api.GroqAPI.getWordDataComplete(
                        word = cleanWord,
                        sentence = sentence,
                        apiKey = apiKey,
                        language = lang,
                        model = modelName,
                        customGermanTemplate = customGerman,
                        customEnglishTemplate = customEnglish,
                        customFrenchTemplate = customFrench
                    )

                    if (modelName != _selectedModel.value) {
                        setSelectedModel(context, modelName)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Переключено на резервную модель: $modelName", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    _translationState.value = TranslationUiState.Success(
                        word = cleanWord,
                        rawWord = rawWord,
                        sentence = sentence,
                        wordTranslation = data.wordTranslation,
                        sentenceTranslation = data.sentenceTranslation,
                        explanation = data.explanation
                    )
                    success = true
                } catch (e: Exception) {
                    lastErrorMsg = e.message ?: "Неизвестная ошибка"
                    attempts++
                }
            }

            if (!success) {
                _translationState.value = TranslationUiState.Error("Все доступные модели вернули ошибку. Последняя ошибка ($currentTryModel): $lastErrorMsg")
            }
        }
    }

    fun clearTranslationState() {
        _translationState.value = TranslationUiState.Idle
    }

    fun translateParagraph(context: android.content.Context, key: String, paragraphText: String) {
        viewModelScope.launch {
            val currentMap = _paragraphTranslations.value.toMutableMap()
            currentMap[key] = ParagraphTranslationUiState.Loading
            _paragraphTranslations.value = currentMap

            val apiKey = _groqApiKey.value.ifBlank {
                val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
                prefs.getString("groq_api_key", "") ?: ""
            }.ifBlank { "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" }

            if (apiKey.isBlank()) {
                val updatedMap = _paragraphTranslations.value.toMutableMap()
                updatedMap[key] = ParagraphTranslationUiState.Error("API ключ не настроен.")
                _paragraphTranslations.value = updatedMap
                return@launch
            }

            try {
                val translation = com.example.data.api.GroqAPI.translateParagraph(
                    paragraph = paragraphText,
                    apiKey = apiKey,
                    language = _currentLanguage.value,
                    model = _selectedModel.value
                )
                val updatedMap = _paragraphTranslations.value.toMutableMap()
                updatedMap[key] = ParagraphTranslationUiState.Success(translation)
                _paragraphTranslations.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _paragraphTranslations.value.toMutableMap()
                updatedMap[key] = ParagraphTranslationUiState.Error(e.message ?: "Неизвестная ошибка")
                _paragraphTranslations.value = updatedMap
            }
        }
    }

    fun toggleParagraphTranslation(key: String) {
        val currentMap = _paragraphTranslations.value.toMutableMap()
        if (currentMap.containsKey(key)) {
            currentMap.remove(key)
        }
        _paragraphTranslations.value = currentMap
    }

    fun translateParagraphUnknownWords(
        context: android.content.Context,
        key: String,
        paragraphText: String,
        unknownWords: List<String>
    ) {
        viewModelScope.launch {
            val currentMap = _inlineParagraphTranslations.value.toMutableMap()
            currentMap[key] = InlineParagraphTranslationUiState.Loading
            _inlineParagraphTranslations.value = currentMap

            val apiKey = _groqApiKey.value.ifBlank {
                val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
                prefs.getString("groq_api_key", "") ?: ""
            }.ifBlank { "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" }

            if (apiKey.isBlank()) {
                val updatedMap = _inlineParagraphTranslations.value.toMutableMap()
                updatedMap[key] = InlineParagraphTranslationUiState.Error("API ключ не настроен.")
                _inlineParagraphTranslations.value = updatedMap
                return@launch
            }

            try {
                val translations = com.example.data.api.GroqAPI.translateParagraphUnknownWords(
                    paragraph = paragraphText,
                    unknownWords = unknownWords,
                    apiKey = apiKey,
                    language = _currentLanguage.value,
                    model = _selectedModel.value
                )
                val updatedMap = _inlineParagraphTranslations.value.toMutableMap()
                updatedMap[key] = InlineParagraphTranslationUiState.Success(translations)
                _inlineParagraphTranslations.value = updatedMap
            } catch (e: Exception) {
                val updatedMap = _inlineParagraphTranslations.value.toMutableMap()
                updatedMap[key] = InlineParagraphTranslationUiState.Error(e.message ?: "Неизвестная ошибка")
                _inlineParagraphTranslations.value = updatedMap
            }
        }
    }

    fun addParagraphUnknownWordsToStudy(
        context: android.content.Context,
        key: String,
        paragraphText: String,
        unknownWords: List<String>
    ) {
        viewModelScope.launch {
            val currentMap = _inlineParagraphTranslations.value.toMutableMap()
            currentMap[key] = InlineParagraphTranslationUiState.Loading
            _inlineParagraphTranslations.value = currentMap

            val apiKey = _groqApiKey.value.ifBlank {
                val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
                prefs.getString("groq_api_key", "") ?: ""
            }.ifBlank { "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" }

            if (apiKey.isBlank()) {
                val updatedMap = _inlineParagraphTranslations.value.toMutableMap()
                updatedMap[key] = InlineParagraphTranslationUiState.Error("API ключ не настроен.")
                _inlineParagraphTranslations.value = updatedMap
                return@launch
            }

            try {
                val translations = com.example.data.api.GroqAPI.translateParagraphUnknownWords(
                    paragraph = paragraphText,
                    unknownWords = unknownWords,
                    apiKey = apiKey,
                    language = _currentLanguage.value,
                    model = _selectedModel.value
                )

                var addedCount = 0
                val lang = _currentLanguage.value
                for (word in unknownWords) {
                    val translation = translations[word] ?: translations[word.lowercase()] ?: word
                    if (translation.isNotBlank()) {
                        val success = repository.insertStudyWord(word = word, translation = translation, language = lang)
                        if (success) addedCount++
                    }
                }

                val updatedMap = _inlineParagraphTranslations.value.toMutableMap()
                updatedMap[key] = InlineParagraphTranslationUiState.Success(translations)
                _inlineParagraphTranslations.value = updatedMap

                withContext(Dispatchers.Main) {
                    if (addedCount > 0) {
                        android.widget.Toast.makeText(context, "Добавлено слов в «Найди пару»: $addedCount", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Слова уже добавлены или не удалось перевести", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                val updatedMap = _inlineParagraphTranslations.value.toMutableMap()
                updatedMap[key] = InlineParagraphTranslationUiState.Error(e.message ?: "Неизвестная ошибка")
                _inlineParagraphTranslations.value = updatedMap
            }
        }
    }

    fun addChapterWordsToStudy(
        context: android.content.Context,
        paragraphs: List<String>,
        startIndex: Int = 0,
        paragraphCount: Int = paragraphs.size
    ) {
        if (_chapterBatchStudyState.value.isProcessing) return

        viewModelScope.launch {
            val apiKey = _groqApiKey.value.ifBlank {
                val prefs = context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE)
                prefs.getString("groq_api_key", "") ?: ""
            }.ifBlank { "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" }

            if (apiKey.isBlank()) {
                _chapterBatchStudyState.value = ChapterBatchStudyState(errorMessage = "API ключ не настроен.")
                return@launch
            }

            val endIndex = minOf(startIndex + paragraphCount, paragraphs.size)
            val selectedParagraphs = if (startIndex < paragraphs.size) paragraphs.subList(startIndex, endIndex) else emptyList()

            if (selectedParagraphs.isEmpty()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Выбран пустой отрывок.", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val knownSet = knownWordsSet.value
            val learningSet = learningWordsSet.value
            val studySet = studyWords.value.map { LinguisticEngine.tokenizeWord(it.word) }.toSet()

            val wordRegex = Regex("""(\p{L}+(?:[-'’`]\p{L}+)*)""")
            
            // Map unknown words to paragraph context
            val uniqueUnknownWordsMap = mutableMapOf<String, String>()
            
            selectedParagraphs.forEach { para ->
                wordRegex.findAll(para).forEach { match ->
                    val clean = LinguisticEngine.tokenizeWord(match.value)
                    if (clean.length > 1 &&
                        !knownSet.contains(clean) &&
                        !learningSet.contains(clean) &&
                        !studySet.contains(clean) &&
                        !uniqueUnknownWordsMap.containsKey(clean)
                    ) {
                        uniqueUnknownWordsMap[clean] = para.take(1500)
                    }
                }
            }

            val unknownWordsList = uniqueUnknownWordsMap.keys.toList()

            if (unknownWordsList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "В выбранном отрывке нет незнакомых слов!", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Chunk in batches of 30 words
            val chunkSize = 30
            val batches = unknownWordsList.chunked(chunkSize)

            _chapterBatchStudyState.value = ChapterBatchStudyState(
                isProcessing = true,
                currentBatch = 0,
                totalBatches = batches.size,
                processedWords = 0,
                totalWords = unknownWordsList.size,
                isDone = false,
                errorMessage = null
            )

            var totalAdded = 0
            val lang = _currentLanguage.value

            try {
                batches.forEachIndexed { index, batchWords ->
                    _chapterBatchStudyState.value = _chapterBatchStudyState.value.copy(
                        currentBatch = index + 1
                    )

                    val combinedContext = batchWords.mapNotNull { uniqueUnknownWordsMap[it] }.distinct().joinToString("\n---\n").take(2500)

                    val translations = com.example.data.api.GroqAPI.translateParagraphUnknownWords(
                        paragraph = combinedContext,
                        unknownWords = batchWords,
                        apiKey = apiKey,
                        language = lang,
                        model = _selectedModel.value
                    )

                    for (word in batchWords) {
                        val translation = translations[word] ?: translations[word.lowercase()] ?: word
                        if (translation.isNotBlank()) {
                            val success = repository.insertStudyWord(word = word, translation = translation, language = lang)
                            if (success) totalAdded++
                        }
                    }

                    _chapterBatchStudyState.value = _chapterBatchStudyState.value.copy(
                        processedWords = _chapterBatchStudyState.value.processedWords + batchWords.size
                    )
                }

                _chapterBatchStudyState.value = ChapterBatchStudyState(
                    isProcessing = false,
                    isDone = true,
                    processedWords = totalAdded,
                    totalWords = unknownWordsList.size
                )

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Добавлено $totalAdded слов в «Найди пару»!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                _chapterBatchStudyState.value = ChapterBatchStudyState(
                    isProcessing = false,
                    errorMessage = e.message ?: "Ошибка при обработке слов"
                )
            }
        }
    }

    fun resetChapterBatchStudyState() {
        _chapterBatchStudyState.value = ChapterBatchStudyState()
    }

    fun toggleInlineParagraphTranslation(key: String) {
        val currentMap = _inlineParagraphTranslations.value.toMutableMap()
        if (currentMap.containsKey(key)) {
            currentMap.remove(key)
        }
        _inlineParagraphTranslations.value = currentMap
    }

    fun clearInlineParagraphTranslations() {
        _inlineParagraphTranslations.value = emptyMap()
    }

    fun clearParagraphTranslations() {
        _paragraphTranslations.value = emptyMap()
        _inlineParagraphTranslations.value = emptyMap()
    }

    fun addWordToLearning(word: String) {
        viewModelScope.launch {
            repository.insertKnownWord(word, status = 1, language = _currentLanguage.value)
        }
    }

    fun addWordToLearned(word: String) {
        viewModelScope.launch {
            repository.insertKnownWord(word, status = 2, language = _currentLanguage.value)
        }
    }

    fun addKnownWord(word: String) {
        addWordToLearned(word)
    }

    fun removeKnownWord(id: Int) {
        viewModelScope.launch {
            repository.deleteKnownWord(id)
        }
    }

    fun removeKnownWordByValue(word: String) {
        viewModelScope.launch {
            repository.deleteKnownWordByWord(word, language = _currentLanguage.value)
        }
    }

    fun importDictionaryWords(wordsText: String, status: Int = 2): Int {
        val tokens = wordsText.split(Regex("\\s+"))
            .map { LinguisticEngine.tokenizeWord(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        
        viewModelScope.launch {
            repository.insertKnownWords(tokens, status = status, language = _currentLanguage.value)
        }
        return tokens.size
    }

    suspend fun importDictionaryWordsForLanguage(wordsText: String, language: String, status: Int): Int {
        val tokens = wordsText.split(Regex("\\s+"))
            .map { LinguisticEngine.tokenizeWord(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        
        repository.insertKnownWords(tokens, status = status, language = language)
        return tokens.size
    }

    suspend fun getAllKnownWordsList(): List<com.example.data.db.KnownWord> {
        return repository.getAllKnownWordsList()
    }

    suspend fun getAllStudyWordsList(): List<com.example.data.db.StudyWord> {
        return repository.getAllStudyWordsList()
    }

    fun importBook(context: android.content.Context, parsedBook: ParsedBook) {
        viewModelScope.launch(Dispatchers.Default) {
            _isAnalyzing.value = true
            val isSrtOrTxt = parsedBook.author.contains("субтитр", ignoreCase = true) ||
                    parsedBook.title.endsWith(".srt", ignoreCase = true) ||
                    parsedBook.title.endsWith(".txt", ignoreCase = true)

            val bookId = repository.insertBookWithChapters(
                parsedBook = parsedBook,
                language = _currentLanguage.value,
                filePath = parsedBook.title
            )
            
            // Save cover image ONLY for regular books with real cover bytes; delete stale file otherwise
            if (parsedBook.coverBytes != null && !isSrtOrTxt) {
                try {
                    val coversDir = java.io.File(context.filesDir, "covers")
                    if (!coversDir.exists()) {
                        coversDir.mkdirs()
                    }
                    val coverFile = java.io.File(coversDir, "cover_${bookId}.jpg")
                    coverFile.writeBytes(parsedBook.coverBytes)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    val coverFile = java.io.File(context.filesDir, "covers/cover_${bookId}.jpg")
                    if (coverFile.exists()) coverFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Auto calculate tokens for the new book immediately and cache it
            val chapters = repository.getBookChaptersList(bookId.toInt())
            val fullText = chapters.joinToString("\n") { it.content }
            val tokens = LinguisticEngine.tokenizeText(fullText)
            
            val currentCache = _bookCache.value.toMutableMap()
            currentCache[bookId.toInt()] = createBookCacheData(tokens, chapters)
            _bookCache.value = currentCache
            
            _isAnalyzing.value = false
        }
    }

    fun importMultipleBooks(context: android.content.Context, uris: List<Uri>, onCompleted: (success: Int, failed: Int) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            _isAnalyzing.value = true
            val total = uris.size
            var successCount = 0
            var failedCount = 0
            var lastSuccessfulBookId: Int? = null

            for (i in uris.indices) {
                val uri = uris[i]
                val currentIdx = i + 1
                
                val initialName = BookParser.getFileName(context, uri) ?: "Книга $currentIdx"
                
                _importProgress.value = ImportProgressState(
                    totalBooks = total,
                    currentBookIndex = currentIdx,
                    currentBookTitle = initialName,
                    stage = "Копирование и чтение файла...",
                    successfulCount = successCount,
                    failedCount = failedCount
                )

                try {
                    val parsed = BookParser.parseUri(context, uri)
                    if (parsed != null && parsed.chapters.isNotEmpty()) {
                        val bookTitle = parsed.title.ifBlank { initialName }
                        
                        _importProgress.value = ImportProgressState(
                            totalBooks = total,
                            currentBookIndex = currentIdx,
                            currentBookTitle = bookTitle,
                            stage = "Сохранение глав в базу данных...",
                            successfulCount = successCount,
                            failedCount = failedCount
                        )

                        val isSrtOrTxt = parsed.author.contains("субтитр", ignoreCase = true) ||
                                bookTitle.endsWith(".srt", ignoreCase = true) ||
                                bookTitle.endsWith(".txt", ignoreCase = true) ||
                                initialName.endsWith(".srt", ignoreCase = true) ||
                                initialName.endsWith(".txt", ignoreCase = true)

                        val bookId = repository.insertBookWithChapters(
                            parsedBook = parsed,
                            language = _currentLanguage.value,
                            filePath = initialName
                        )

                        if (parsed.coverBytes != null && !isSrtOrTxt) {
                            try {
                                val coversDir = java.io.File(context.filesDir, "covers")
                                if (!coversDir.exists()) {
                                    coversDir.mkdirs()
                                }
                                val coverFile = java.io.File(coversDir, "cover_${bookId}.jpg")
                                coverFile.writeBytes(parsed.coverBytes)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            try {
                                val coverFile = java.io.File(context.filesDir, "covers/cover_${bookId}.jpg")
                                if (coverFile.exists()) coverFile.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        _importProgress.value = ImportProgressState(
                            totalBooks = total,
                            currentBookIndex = currentIdx,
                            currentBookTitle = bookTitle,
                            stage = "Просчет лингвистической статистики...",
                            successfulCount = successCount,
                            failedCount = failedCount
                        )

                        val chapters = repository.getBookChaptersList(bookId.toInt())
                        val fullText = chapters.joinToString("\n") { it.content }
                        val tokens = LinguisticEngine.tokenizeText(fullText)

                        val currentCache = _bookCache.value.toMutableMap()
                        currentCache[bookId.toInt()] = createBookCacheData(tokens, chapters)
                        _bookCache.value = currentCache

                        successCount++
                        lastSuccessfulBookId = bookId.toInt()
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    failedCount++
                }

                _importProgress.value = ImportProgressState(
                    totalBooks = total,
                    currentBookIndex = currentIdx,
                    currentBookTitle = initialName,
                    stage = "Завершено для этой книги",
                    successfulCount = successCount,
                    failedCount = failedCount
                )
            }

            _importProgress.value = null
            _isAnalyzing.value = false

            withContext(Dispatchers.Main) {
                onCompleted(successCount, failedCount)
            }
        }
    }

    suspend fun generateAiStory(prompt: String): String = withContext(defaultDispatcher) {
        val groqKey = _groqApiKey.value
        val model = _selectedModel.value
        com.example.data.api.GeminiStoryService.generateStory(
            prompt = prompt,
            userCustomGroqApiKey = groqKey,
            groqModel = model
        )
    }

    suspend fun saveGeneratedStory(
        context: android.content.Context,
        title: String,
        text: String,
        language: String,
        level: String,
        genre: String
    ): Int = withContext(defaultDispatcher) {
        val author = "$genre ($level)"
        val cleanTitle = title.ifBlank { "Сгенерированный рассказ" }
        val parsedBook = BookParser.parseStory(
            title = cleanTitle,
            author = author,
            text = text
        )

        // Save physical txt file
        val txtFileName = "story_${System.currentTimeMillis()}.txt"
        val storageFile = java.io.File(context.filesDir, txtFileName)
        try {
            storageFile.writeText(text, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val bookId = repository.insertBookWithChapters(
            parsedBook = parsedBook,
            language = language,
            filePath = storageFile.absolutePath
        ).toInt()

        val chapters = repository.getBookChaptersList(bookId)
        val fullText = chapters.joinToString("\n") { it.content }
        val tokens = LinguisticEngine.tokenizeText(fullText)

        val currentCache = _bookCache.value.toMutableMap()
        currentCache[bookId] = createBookCacheData(tokens, chapters)
        _bookCache.value = currentCache

        bookId
    }

    private val _storyGenerationState = MutableStateFlow(StoryGenerationState())
    val storyGenerationState: StateFlow<StoryGenerationState> = _storyGenerationState.asStateFlow()

    private val _isGenerateStoryActive = MutableStateFlow(false)
    val isGenerateStoryActive: StateFlow<Boolean> = _isGenerateStoryActive.asStateFlow()

    private var storyGenerationJob: kotlinx.coroutines.Job? = null

    fun setGenerateStoryActive(active: Boolean) {
        _isGenerateStoryActive.value = active
    }

    fun dismissStoryGenerationBanner() {
        _storyGenerationState.update { it.copy(showBanner = false) }
    }

    fun clearStoryGenerationState() {
        _storyGenerationState.value = StoryGenerationState()
    }

    fun cancelStoryGeneration() {
        storyGenerationJob?.cancel()
        _storyGenerationState.value = StoryGenerationState(
            isGenerating = false,
            errorMessage = "Генерация отменена",
            showBanner = false
        )
    }

    fun startStoryGeneration(
        context: android.content.Context,
        promptText: String,
        languageCode: String,
        level: CefrLevel,
        genre: StoryGenre,
        storyLength: StoryLength,
        selectedPercentage: Double?,
        includeVocabInPrompt: Boolean,
        displayStudyWords: List<String>,
        displayKnownWords: List<String>,
        customUserNotes: String,
        customTargetWords: String
    ) {
        if (_storyGenerationState.value.isGenerating) return
        val appContext = context.applicationContext

        _isGenerateStoryActive.value = true

        storyGenerationJob?.cancel()
        storyGenerationJob = viewModelScope.launch {
            _storyGenerationState.value = StoryGenerationState(
                isGenerating = true,
                progressStage = "Отправка запроса на AI сервер...",
                language = languageCode,
                levelCode = level.code,
                genreTitleRu = genre.titleRu,
                showBanner = true
            )

            try {
                var story = generateAiStory(promptText)

                // Check how many chapters were generated in the initial response
                val chapterHeaderRegex = Regex("(?i)##\\s*(?:[a-zа-яёí]+\\s*)?(\\d+)", RegexOption.IGNORE_CASE)
                var detectedChapters = chapterHeaderRegex
                    .findAll(story)
                    .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                    .toList()
                var lastChapterNum = detectedChapters.maxOrNull() ?: 1

                // If fewer chapters were generated than requested, continue writing them with the exact same vocabulary rules
                var continuePass = 0
                val maxContinuePasses = 3
                while (lastChapterNum < storyLength.chaptersCount && continuePass < maxContinuePasses) {
                    continuePass++
                    val fromChapter = lastChapterNum + 1
                    val toChapter = storyLength.chaptersCount
                    _storyGenerationState.update {
                        it.copy(progressStage = "Дописывание глав ($fromChapter–$toChapter) с сохранением простоты...")
                    }

                    val continuationPrompt = buildContinuationPrompt(
                        languageCode = languageCode,
                        level = level,
                        genre = genre,
                        storyLength = storyLength,
                        fromChapter = fromChapter,
                        toChapter = toChapter,
                        previousStoryEnding = story,
                        unknownWordsRatio = UnknownWordsRatio.PERCENT_5_0,
                        learningWords = displayStudyWords,
                        knownWords = displayKnownWords,
                        customUserNotes = customUserNotes,
                        customTargetWords = customTargetWords,
                        includeKnownWordsInPrompt = includeVocabInPrompt,
                        customUnknownPercentage = selectedPercentage
                    )

                    try {
                        val continuation = generateAiStory(continuationPrompt)
                        val lines = continuation.lines()
                        val firstHeaderIdx = lines.indexOfFirst { it.trim().startsWith("## ") }
                        val cleanContinuation = if (firstHeaderIdx >= 0) {
                            lines.subList(firstHeaderIdx, lines.size).joinToString("\n")
                        } else {
                            lines.dropWhile { it.startsWith("# ") || it.isBlank() }.joinToString("\n")
                        }

                        if (cleanContinuation.isNotBlank()) {
                            story = story.trimEnd() + "\n\n" + cleanContinuation.trimStart()
                        }

                        val newDetected = chapterHeaderRegex
                            .findAll(story)
                            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                            .toList()
                        val newLast = newDetected.maxOrNull() ?: lastChapterNum
                        if (newLast <= lastChapterNum) {
                            break
                        }
                        lastChapterNum = newLast
                    } catch (e: Exception) {
                        break
                    }
                }

                _storyGenerationState.update {
                    it.copy(progressStage = "Сохранение рассказа в библиотеку...")
                }

                val parsedLines = story.lines().filter { it.isNotBlank() }
                val firstLine = parsedLines.firstOrNull() ?: ""
                val title = formatAiStoryTitle(
                    rawFirstLineOrTitle = firstLine,
                    levelCode = level.code,
                    fallbackGenre = genre.titleRu
                )

                val savedId = saveGeneratedStory(
                    context = appContext,
                    title = title,
                    text = story,
                    language = languageCode,
                    level = level.code,
                    genre = genre.titleRu
                )

                _storyGenerationState.value = StoryGenerationState(
                    isGenerating = false,
                    progressStage = "Готово",
                    storyTitle = title,
                    storyText = story,
                    savedBookId = savedId,
                    language = languageCode,
                    levelCode = level.code,
                    genreTitleRu = genre.titleRu,
                    showBanner = true
                )

                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        appContext,
                        "Рассказ «$title» успешно добавлен в библиотеку!",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                _storyGenerationState.value = StoryGenerationState(
                    isGenerating = false,
                    errorMessage = e.message ?: "Не удалось сгенерировать рассказ",
                    showBanner = true
                )
            }
        }
    }
}

class MainViewModelFactory(private val repository: BookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
