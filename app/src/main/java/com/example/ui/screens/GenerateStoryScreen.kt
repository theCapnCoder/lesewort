package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.data.engine.LinguisticEngine
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.ScreenTab
import com.example.ui.components.AppLanguage
import com.example.ui.components.LanguageFlagIcon
import com.example.ui.components.getLanguageNameRu
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val STORY_GENERATION_LANGUAGES = listOf(
    AppLanguage("de", "Немецкий", "Deutsch", "🇩🇪"),
    AppLanguage("en", "Английский", "English", "🇬🇧"),
    AppLanguage("fr", "Французский", "Français", "🇫🇷")
)

enum class CefrLevel(
    val code: String,
    val titleRu: String,
    val description: String,
    val minWords: Int,
    val maxWords: Int
) {
    A1("A1", "Начальный (A1)", "Базовые фразы, простые предложения, настоящее время", 0, 150),
    A2("A2", "Элементарный (A2)", "Простые связные рассказы, базовые времена и диалоги", 151, 500),
    B1("B1", "Средний (B1)", "Развернутые описания, чувства, разные временные конструкции", 501, 1500),
    B2("B2", "Выше среднего (B2)", "Сложные сюжетные линии, богатые синонимы и идиомы", 1501, 3500),
    C1("C1", "Продвинутый (C1)", "Глубокая стилистика, метафоры, абстрактные рассуждения", 3501, 7000),
    C2("C2", "В совершенстве (C2)", "Свободное владение стилем уровня классической литературы", 7001, 999999)
}

enum class StoryGenre(
    val id: String,
    val titleRu: String,
    val iconName: String,
    val description: String
) {
    FICTION("fiction", "Художественная литература", "📖", "Живой сюжет о героях, их судьбе, переживаниях и диалогах"),
    DETECTIVE("detective", "Детектив и тайна", "🔍", "Загадочное происшествие, улики, интрига и поиск истины"),
    TRAVEL("travel", "Путешествия и приключения", "🧭", "Дорожные приключения, новые города, экспедиции и открытия"),
    SCI_FI("sci_fi", "Научная фантастика", "🚀", "Технологии будущего, космос, роботы и смелые открытия"),
    FANTASY("fantasy", "Фэнтези и сказка", "🧙", "Магический мир, древние легенды, мифические существа и волшебство"),
    ROMANCE("romance", "Романтика и чувства", "💖", "Любовная история, искренние эмоции, встречи и симпатии"),
    DAILY_LIFE("daily_life", "Повседневная жизнь и диалоги", "☕", "Реалистичные ситуации: кафе, покупки, работа, хобби и друзья"),
    HISTORICAL("historical", "Исторический рассказ", "🏛️", "Атмосфера прошлых веков, старинные обычаи и памятные события"),
    MYSTERY("mystery", "Мистика и триллер", "🌑", "Таинственные совпадения, ночные тайны и интригующее напряжение"),
    HUMOR("humor", "Юмор и комедия", "😄", "Забавные курьезы, добрые шутки, ирония и позитивные диалоги")
}

enum class StoryLength(
    val id: String,
    val titleRu: String,
    val subtitleRu: String,
    val chaptersCount: Int,
    val minWords: Int,
    val targetWords: String
) {
    NOVELLA("novella", "Повесть (рекомендуется)", "5 глав • 1500–2500 слов (несколько страниц)", 5, 1500, "1500–2500"),
    EPIC("epic", "Большая книга / Роман", "7 глав • 3000–4500 слов (длинное чтение)", 7, 3000, "3000–4500"),
    STANDARD("standard", "Развернутый рассказ", "4 главы • 1000–1500 слов", 4, 1000, "1000–1500")
}

enum class UnknownWordsRatio(
    val percentage: Double,
    val label: String,
    val descriptionRu: String
) {
    PERCENT_2_5(2.5, "2.5%", "Очень легкое чтение (97.5% знакомых слов)"),
    PERCENT_5_0(5.0, "5%", "Комфортный баланс (95% знакомых слов)"),
    PERCENT_7_5(7.5, "7.5%", "Умеренная сложность (92.5% знакомых слов)"),
    PERCENT_10_0(10.0, "10%", "Интенсивное обучение (90% знакомых слов)")
}

fun calculateRecommendedLevel(totalVocabCount: Int): CefrLevel {
    return when {
        totalVocabCount <= 150 -> CefrLevel.A1
        totalVocabCount <= 500 -> CefrLevel.A2
        totalVocabCount <= 1500 -> CefrLevel.B1
        totalVocabCount <= 3500 -> CefrLevel.B2
        totalVocabCount <= 7000 -> CefrLevel.C1
        else -> CefrLevel.C2
    }
}

fun formatAiStoryTitle(rawFirstLineOrTitle: String, levelCode: String, fallbackGenre: String = "Story"): String {
    // 1. Remove markdown '#' prefix and whitespace
    var title = rawFirstLineOrTitle.trim().removePrefix("#").trim()

    // 2. Remove any existing "AI*." / "AI*" / "AI." prefixes and CEFR level tags to re-apply the exact canonical format
    // Matches patterns like "AI*.A1. ", "AI*. A2: ", "AI* B1 - ", "AI. ", "AI*."
    val prefixRegex = Regex("""^AI\*?\s*[\.:\-]?\s*([A-Za-z]\d)?\s*[\.:\-]?\s*""", RegexOption.IGNORE_CASE)
    val cleanedTitle = title.replace(prefixRegex, "").trim()

    val baseTitle = if (cleanedTitle.isNotBlank() && cleanedTitle.length <= 100) {
        cleanedTitle
    } else {
        fallbackGenre
    }

    return "AI*.$levelCode. $baseTitle"
}

fun formatPercentageLabel(pct: Double): String {
    return if (pct % 1.0 == 0.0) "${pct.toInt()}%" else "$pct%"
}

fun formatPercentageDescription(pct: Double): String {
    val known = (100.0 - pct).let {
        if (it % 1.0 == 0.0) "${it.toInt()}" else String.format(java.util.Locale.US, "%.1f", it)
    }
    return when {
        pct <= 3.0 -> "Очень легкое чтение (~$known% знакомых слов)"
        pct <= 6.0 -> "Комфортный баланс (~$known% знакомых слов)"
        pct <= 8.5 -> "Умеренная сложность (~$known% знакомых слов)"
        pct <= 12.0 -> "Интенсивное обучение (~$known% знакомых слов)"
        else -> "Высокая сложность (~$known% знакомых слов)"
    }
}

fun buildDefaultPrompt(
    languageCode: String,
    level: CefrLevel,
    genre: StoryGenre,
    storyLength: StoryLength = StoryLength.NOVELLA,
    unknownWordsRatio: UnknownWordsRatio = UnknownWordsRatio.PERCENT_5_0,
    learningWords: List<String> = emptyList(),
    knownWords: List<String> = emptyList(),
    customUserNotes: String = "",
    customTargetWords: String = "",
    includeKnownWordsInPrompt: Boolean = true,
    customUnknownPercentage: Double? = null
): String {
    val langNameRu = getLanguageNameRu(languageCode)
    val langNameEn = when (languageCode.lowercase()) {
        "de" -> "German"
        "en" -> "English"
        "fr" -> "French"
        "es" -> "Spanish"
        "it" -> "Italian"
        "ru" -> "Russian"
        else -> languageCode
    }

    val chapterWord = when (languageCode.lowercase()) {
        "de" -> "Kapitel"
        "en" -> "Chapter"
        "fr" -> "Chapitre"
        "es" -> "Capítulo"
        "it" -> "Capitolo"
        "ru" -> "Глава"
        else -> "Chapter"
    }

    val cleanedCustomTargetWords = customTargetWords
        .split(",", ";", "\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")

    val effectivePercentage = customUnknownPercentage ?: unknownWordsRatio.percentage
    val effectivePercentageLabel = customUnknownPercentage?.let { formatPercentageLabel(it) } ?: unknownWordsRatio.label

    val fullStudyWords = learningWords.joinToString(", ")
    val fullKnownWords = knownWords.joinToString(", ")

    val perChapterMinWords = storyLength.minWords / storyLength.chaptersCount

    return buildString {
        appendLine("TASK: You are an expert author writing a COMPLETE, HIGH-VOLUME, MULTI-CHAPTER LITERARY NOVELLA in $langNameEn ($langNameRu) for language learners.")
        appendLine("CEFR Level: ${level.code} (${level.titleRu})")
        appendLine("Genre / Style: ${genre.titleRu} (${genre.description})")
        appendLine("Target Length: EXACTLY ${storyLength.chaptersCount} Chapters, at least ${storyLength.minWords} to ${storyLength.targetWords} words total (minimum $perChapterMinWords words per chapter).")
        if (includeKnownWordsInPrompt) {
            appendLine("Target Ratio of New/Unknown Words: $effectivePercentageLabel ($effectivePercentage%)")
        }
        appendLine()
        appendLine("MANDATORY RULES & VOLUME REQUIREMENTS (DO NOT TRUNCATE, DO NOT WRITE SHORT SUMMARIES):")
        appendLine("1. LANGUAGE: Write 100% strictly in $langNameEn.")
        appendLine("2. BOOK TITLE (FIRST LINE): The first line MUST be the markdown title in this strict format:")
        appendLine("   # AI*.${level.code}. [Book Title in $langNameEn]")
        appendLine("   Example: # AI*.${level.code}. Das Geheimnis der alten Bibliothek")
        appendLine()
        appendLine("3. STRICT HIGH-VOLUME REQUIREMENT (EXTREMELY IMPORTANT):")
        appendLine("   - The entire story MUST contain AT LEAST ${storyLength.minWords} words in total (Target: ${storyLength.targetWords} words).")
        appendLine("   - Each of the ${storyLength.chaptersCount} chapters MUST contain AT LEAST $perChapterMinWords words of full literary prose.")
        appendLine("   - DO NOT provide a brief outline, short summary, or condensed story. Write fully developed scenes with extensive descriptions, realistic dialogues, interactions, sensory details, and actions.")
        appendLine("   - Even if the language level is ${level.code}, DO NOT shorten the text! Instead, use simpler grammatical structures and accessible vocabulary suitable for ${level.code}, but write spacious, immersive, long paragraphs with many descriptive details and rich conversations.")
        appendLine()
        appendLine("4. MANDATORY CHAPTER STRUCTURE:")
        appendLine("   - You MUST write all ${storyLength.chaptersCount} chapters. Do NOT stop after 1 or 2 chapters.")
        appendLine("   - Format each chapter header exactly as:")
        for (i in 1..storyLength.chaptersCount) {
            appendLine("     ## $chapterWord $i: [Chapter Title in $langNameEn]")
        }
        appendLine("   - In EVERY chapter, write at least 6 to 8 substantial, detailed paragraphs with extensive dialogue, vivid setting descriptions, and character interactions to reach at least $perChapterMinWords words per chapter.")
        appendLine()
        if (includeKnownWordsInPrompt) {
            appendLine("5. VOCABULARY INTEGRATION & UNKNOWN WORDS RATIO:")
            appendLine("   - STRICT NEW / UNKNOWN WORDS PERCENTAGE: Exactly around $effectivePercentageLabel ($effectivePercentage%) of the total vocabulary in the story should consist of new/unfamiliar words appropriate for CEFR ${level.code}, while the remaining ~${100.0 - effectivePercentage}% must be familiar, accessible base words.")
            if (cleanedCustomTargetWords.isNotBlank()) {
                appendLine("   - MANDATORY TARGET WORDS TO LEARN & REPEAT AS OFTEN AS POSSIBLE:")
                appendLine("     Target Words: $cleanedCustomTargetWords")
                appendLine("     RULE: The student specifically wants to learn and master these exact words! You MUST deliberately weave them into the narrative and dialogues AS FREQUENTLY AS POSSIBLE across all chapters, scenes, and situations, utilizing different grammatical forms (tenses, conjugations, declensions, plural) so the learner encounters them many times and remembers them.")
            }
            if (fullStudyWords.isNotBlank()) {
                appendLine("   - WORDS TO PRACTICE (prioritize weaving these into plot and dialogues): $fullStudyWords")
            }
            if (fullKnownWords.isNotBlank()) {
                appendLine("   - KNOWN VOCABULARY BASE: $fullKnownWords")
            }
            appendLine("   - Use natural repetitions of key vocabulary across varied sentences, contexts, and grammatical forms (conjugations, tenses, plurals).")
            appendLine("   - If the student's word list is small, freely employ standard, common level-appropriate words to build an extensive world and vibrant narrative.")
        } else {
            appendLine("5. VOCABULARY & GRAMMAR ADAPTATION FOR CEFR LEVEL ${level.code}:")
            appendLine("   - Strictly write using vocabulary, idioms, sentence lengths, and grammatical patterns appropriate for CEFR ${level.code}.")
            appendLine("   - Do NOT use overly complex sentence structures, rare literary archaisms, or advanced grammar beyond level ${level.code}.")
            appendLine("   - The text must feel completely natural, engaging, and easy to understand for a learner at level ${level.code}.")
            if (cleanedCustomTargetWords.isNotBlank()) {
                appendLine("   - MANDATORY TARGET WORDS TO LEARN & REPEAT AS OFTEN AS POSSIBLE:")
                appendLine("     Target Words: $cleanedCustomTargetWords")
                appendLine("     RULE: The student specifically wants to learn and master these exact words! You MUST deliberately weave them into the narrative and dialogues AS FREQUENTLY AS POSSIBLE across all chapters, scenes, and situations, utilizing different grammatical forms so the learner encounters them many times.")
            }
        }

        if (customUserNotes.isNotBlank()) {
            appendLine()
            appendLine("ADDITIONAL USER PLOT WISHES:")
            appendLine(customUserNotes.trim())
        }
    }
}

fun buildContinuationPrompt(
    languageCode: String,
    level: CefrLevel,
    genre: StoryGenre,
    storyLength: StoryLength,
    fromChapter: Int,
    toChapter: Int,
    previousStoryEnding: String,
    unknownWordsRatio: UnknownWordsRatio = UnknownWordsRatio.PERCENT_5_0,
    learningWords: List<String> = emptyList(),
    knownWords: List<String> = emptyList(),
    customUserNotes: String = "",
    customTargetWords: String = "",
    includeKnownWordsInPrompt: Boolean = true,
    customUnknownPercentage: Double? = null
): String {
    val langNameRu = getLanguageNameRu(languageCode)
    val langNameEn = when (languageCode.lowercase()) {
        "de" -> "German"
        "en" -> "English"
        "fr" -> "French"
        "es" -> "Spanish"
        "it" -> "Italian"
        "ru" -> "Russian"
        else -> languageCode
    }

    val chapterWord = when (languageCode.lowercase()) {
        "de" -> "Kapitel"
        "en" -> "Chapter"
        "fr" -> "Chapitre"
        "es" -> "Capítulo"
        "it" -> "Capitolo"
        "ru" -> "Глава"
        else -> "Chapter"
    }

    val cleanedCustomTargetWords = customTargetWords
        .split(",", ";", "\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")

    val effectivePercentage = customUnknownPercentage ?: unknownWordsRatio.percentage
    val effectivePercentageLabel = customUnknownPercentage?.let { formatPercentageLabel(it) } ?: unknownWordsRatio.label

    val fullStudyWords = learningWords.joinToString(", ")
    val fullKnownWords = knownWords.joinToString(", ")

    val perChapterMinWords = storyLength.minWords / storyLength.chaptersCount

    return buildString {
        appendLine("TASK: You are continuing an ongoing graded reader novella in $langNameEn ($langNameRu) for language learners.")
        appendLine("CEFR Level: ${level.code} (${level.titleRu}) — ABSOLUTE VOCABULARY & SIMPLICITY CONSISTENCY REQUIRED!")
        appendLine("Genre / Style: ${genre.titleRu} (${genre.description})")
        appendLine("Target Chapters to write now: Chapters $fromChapter to $toChapter (at least $perChapterMinWords words per chapter).")
        if (includeKnownWordsInPrompt) {
            appendLine("Target Ratio of New/Unknown Words: $effectivePercentageLabel ($effectivePercentage%)")
        }
        appendLine()
        appendLine("CRITICAL INSTRUCTION — CONSISTENT DIFFICULTY & COMFORTABLE READING (MUST BE IDENTICAL TO CHAPTER 1):")
        appendLine("1. IDENTICAL LEVEL OF EASE: Chapters $fromChapter to $toChapter MUST be just as easy and accessible to read as Chapter 1! Under NO circumstances should the difficulty increase, nor should you switch to advanced literary vocabulary, obscure words, complex idioms, or sophisticated syntax.")
        appendLine("2. ABSOLUTELY NO VOCABULARY OVERLOAD: It must NEVER be that every second word is unknown or complex. Keep the text gentle, accessible, and natural for a CEFR ${level.code} student.")
        appendLine("3. LANGUAGE: Write 100% strictly in $langNameEn.")
        appendLine("4. CHAPTER HEADERS: Do NOT output the book title (# ...). Start IMMEDIATELY with Chapter $fromChapter header:")
        for (i in fromChapter..toChapter) {
            appendLine("   ## $chapterWord $i: [Chapter Title in $langNameEn]")
        }
        appendLine("5. VOLUME PER CHAPTER: In each chapter ($fromChapter to $toChapter), write full, vivid scenes with natural dialogue and descriptive paragraphs reaching at least $perChapterMinWords words per chapter. Do NOT write summaries.")
        appendLine()
        if (includeKnownWordsInPrompt) {
            appendLine("6. VOCABULARY RESTRICTIONS & UNKNOWN WORDS RATIO (EXTREMELY IMPORTANT):")
            appendLine("   - STRICT NEW / UNKNOWN WORDS PERCENTAGE: Exactly around $effectivePercentageLabel ($effectivePercentage%) of the vocabulary in Chapters $fromChapter–$toChapter should consist of new/unfamiliar words appropriate for CEFR ${level.code}.")
            appendLine("   - The remaining ~${100.0 - effectivePercentage}% MUST be familiar, accessible base words from the student's known vocabulary.")
            appendLine("   - The student must encounter almost entirely familiar words (~${100.0 - effectivePercentage}%), with only occasional new vocabulary (~$effectivePercentageLabel) that is easy to understand from context.")
            if (cleanedCustomTargetWords.isNotBlank()) {
                appendLine("   - MANDATORY TARGET WORDS TO LEARN & REPEAT IN DIALOGUES & SCENES:")
                appendLine("     Target Words: $cleanedCustomTargetWords")
                appendLine("     RULE: Weave these target words into Chapters $fromChapter–$toChapter AS FREQUENTLY AS POSSIBLE across conversations, actions, and scenes in different grammatical forms!")
            }
            if (fullStudyWords.isNotBlank()) {
                appendLine("   - WORDS TO PRACTICE (prioritize weaving these into plot and dialogues): $fullStudyWords")
            }
            if (fullKnownWords.isNotBlank()) {
                appendLine("   - KNOWN VOCABULARY BASE (draw heavily from these familiar words): $fullKnownWords")
            }
            appendLine("   - Use natural repetitions of basic vocabulary across varied sentences and simple sentence structures.")
        } else {
            appendLine("6. VOCABULARY & GRAMMAR ADAPTATION FOR CEFR LEVEL ${level.code}:")
            appendLine("   - Strictly write using simple vocabulary, short sentences, and grammatical patterns appropriate for CEFR ${level.code}.")
            appendLine("   - DO NOT use complex sentence structures, rare literary archaisms, or advanced grammar beyond level ${level.code}.")
            appendLine("   - Chapters $fromChapter–$toChapter must feel completely natural, engaging, and just as easy to read as Chapter 1.")
            if (cleanedCustomTargetWords.isNotBlank()) {
                appendLine("   - MANDATORY TARGET WORDS TO LEARN & REPEAT AS OFTEN AS POSSIBLE:")
                appendLine("     Target Words: $cleanedCustomTargetWords")
                appendLine("     RULE: Weave these target words into dialogues and actions across Chapters $fromChapter–$toChapter.")
            }
        }
        if (customUserNotes.isNotBlank()) {
            appendLine()
            appendLine("ADDITIONAL USER PLOT WISHES:")
            appendLine(customUserNotes.trim())
        }
        appendLine()
        appendLine("PREVIOUS STORY CONTEXT (The story so far ends with):")
        appendLine("\"\"\"")
        appendLine(previousStoryEnding.takeLast(1200).trim())
        appendLine("\"\"\"")
        appendLine()
        appendLine("Now continue the story starting directly with:")
        appendLine("## $chapterWord $fromChapter: [Chapter Title in $langNameEn]")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateStoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToTab: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("lesewort_prefs", android.content.Context.MODE_PRIVATE) }
    var includeStudyWordsAsKnown by remember {
        mutableStateOf(prefs.getBoolean("story_include_study_words_as_known", false))
    }

    val currentGlobalLang by viewModel.currentLanguage.collectAsState()
    var selectedLanguage by remember {
        mutableStateOf(
            if (STORY_GENERATION_LANGUAGES.any { it.code.equals(currentGlobalLang, ignoreCase = true) }) {
                currentGlobalLang
            } else {
                "de"
            }
        )
    }

    // Collect all known words from repository for the selected language
    val allKnownWordsList by viewModel.knownWords.collectAsState()
    val allStudyWordsList by viewModel.studyWords.collectAsState()

    // Words filtered by selectedLanguage
    var targetLanguageKnownWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var targetLanguageStudyWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var targetLanguageBookLearningWords by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(selectedLanguage) {
        val rawKnown = viewModel.getAllKnownWordsList().filter { it.language.equals(selectedLanguage, ignoreCase = true) }
        val rawStudy = viewModel.getAllStudyWordsList().filter { it.language.equals(selectedLanguage, ignoreCase = true) }
        targetLanguageKnownWords = rawKnown.filter { it.status == 2 }.map { it.word }
        targetLanguageBookLearningWords = rawKnown.filter { it.status == 1 }.map { it.word }
        // Mini-game words (StudyWordDao: study_words table)
        targetLanguageStudyWords = rawStudy.map { it.word }
    }

    // When the radiobutton is enabled, words with status "изучение" from the book database (status = 1 in known_words)
    // are excluded from study words and counted as known/learned words.
    val displayStudyWords = remember(targetLanguageStudyWords, targetLanguageBookLearningWords, includeStudyWordsAsKnown) {
        if (includeStudyWordsAsKnown) {
            val bookLearningSet = targetLanguageBookLearningWords.map { LinguisticEngine.tokenizeWord(it) }.toSet()
            targetLanguageStudyWords.filter { word ->
                !bookLearningSet.contains(LinguisticEngine.tokenizeWord(word))
            }
        } else {
            targetLanguageStudyWords
        }
    }

    val displayKnownWords = remember(targetLanguageKnownWords, targetLanguageBookLearningWords, includeStudyWordsAsKnown) {
        if (includeStudyWordsAsKnown) {
            (targetLanguageKnownWords + targetLanguageBookLearningWords).distinct()
        } else {
            targetLanguageKnownWords
        }
    }

    val totalVocabCount = displayKnownWords.size + displayStudyWords.size
    val recommendedLevel = remember(totalVocabCount) { calculateRecommendedLevel(totalVocabCount) }

    var selectedLevel by remember { mutableStateOf(recommendedLevel) }
    var selectedGenre by remember { mutableStateOf(StoryGenre.FICTION) }
    var selectedLength by remember { mutableStateOf(StoryLength.NOVELLA) }
    // Vocabulary and percentage inclusion in prompt
    var includeVocabInPrompt by remember {
        mutableStateOf(prefs.getBoolean("story_include_vocab_in_prompt", true))
    }

    // Custom percentages list from SharedPreferences
    val savedPercentagesStr = prefs.getString("story_custom_percentages_list", "2.5,5.0,7.5,10.0") ?: "2.5,5.0,7.5,10.0"
    var percentageList by remember {
        val parsed = savedPercentagesStr.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
            .filter { it in 0.1..99.9 }
            .distinct()
            .sorted()
        mutableStateOf(if (parsed.isNotEmpty()) parsed else listOf(2.5, 5.0, 7.5, 10.0))
    }

    val savedSelectedPct = prefs.getFloat("story_selected_unknown_percentage", 5.0f).toDouble()
    var selectedPercentage by remember {
        mutableStateOf(
            if (percentageList.contains(savedSelectedPct)) savedSelectedPct else (percentageList.firstOrNull() ?: 5.0)
        )
    }

    var isDeletePercentMode by remember { mutableStateOf(false) }
    var showAddPercentDialog by remember { mutableStateOf(false) }

    var customUserNotes by remember { mutableStateOf("") }
    var customTargetWords by remember { mutableStateOf("") }
    var isEditingPromptManually by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf("") }

    // Update prompt when parameters change unless user is manually typing
    LaunchedEffect(
        selectedLanguage,
        selectedLevel,
        selectedGenre,
        selectedLength,
        selectedPercentage,
        includeVocabInPrompt,
        displayStudyWords,
        displayKnownWords,
        customUserNotes,
        customTargetWords
    ) {
        if (!isEditingPromptManually) {
            promptText = buildDefaultPrompt(
                languageCode = selectedLanguage,
                level = selectedLevel,
                genre = selectedGenre,
                storyLength = selectedLength,
                unknownWordsRatio = UnknownWordsRatio.PERCENT_5_0,
                learningWords = displayStudyWords,
                knownWords = displayKnownWords,
                customUserNotes = customUserNotes,
                customTargetWords = customTargetWords,
                includeKnownWordsInPrompt = includeVocabInPrompt,
                customUnknownPercentage = selectedPercentage
            )
        }
    }

    val storyGenState by viewModel.storyGenerationState.collectAsState()
    val isGenerating = storyGenState.isGenerating
    val generationProgressStage = storyGenState.progressStage
    val errorMessage = storyGenState.errorMessage
    var showLanguageDialog by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("story_generator_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Text(
                        "Генерация рассказа",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Language Switcher Badge with Flag
                    Card(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { showLanguageDialog = true }
                            .testTag("story_language_selector_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LanguageFlagIcon(selectedLanguage)
                            Text(
                                text = selectedLanguage.uppercase(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = {
                    Text(
                        "Выберите язык рассказа",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        STORY_GENERATION_LANGUAGES.forEach { lang ->
                            val isSelected = selectedLanguage.equals(lang.code, ignoreCase = true)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLanguage = lang.code
                                        showLanguageDialog = false
                                        isEditingPromptManually = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LanguageFlagIcon(lang.code)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lang.nameRu,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = lang.nativeName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text("Закрыть")
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Vocabulary & Recommended Level Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Ваш словарный запас (${getLanguageNameRu(selectedLanguage)})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            LanguageFlagIcon(selectedLanguage)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${displayKnownWords.size}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                    Text(
                                        "Выучено",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF9333EA).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF9333EA).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${displayStudyWords.size}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9333EA)
                                    )
                                    Text(
                                        "Желаемое",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$totalVocabCount",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Всего слов",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Radiobutton option: Count study words as known
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val newValue = !includeStudyWordsAsKnown
                                    includeStudyWordsAsKnown = newValue
                                    prefs.edit().putBoolean("story_include_study_words_as_known", newValue).apply()
                                    isEditingPromptManually = false
                                }
                                .testTag("radio_study_words_toggle"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (includeStudyWordsAsKnown) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (includeStudyWordsAsKnown) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RadioButton(
                                        selected = includeStudyWordsAsKnown,
                                        onClick = {
                                            val newValue = !includeStudyWordsAsKnown
                                            includeStudyWordsAsKnown = newValue
                                            prefs.edit().putBoolean("story_include_study_words_as_known", newValue).apply()
                                            isEditingPromptManually = false
                                        },
                                        modifier = Modifier.testTag("radio_study_words_button")
                                    )
                                     Column {
                                        Text(
                                            text = "Учитывать слова из книг со статусом «на изучении» как выученные",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (includeStudyWordsAsKnown) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (includeStudyWordsAsKnown)
                                                "Слова со статусом «на изучении» из книг исключены из списка отработки и добавлены в известный запас"
                                            else
                                                "Исключить слова со статусом «на изучении» из книг из списка отработки",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Number of words with status "на изучении" from books opposite the radiobutton
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFA000).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${targetLanguageBookLearningWords.size}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                        Text(
                                            text = "в книгах",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                            }
                        }

                        // Radiobutton option: Include known words in prompt
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val newValue = !includeVocabInPrompt
                                    includeVocabInPrompt = newValue
                                    prefs.edit().putBoolean("story_include_vocab_in_prompt", newValue).apply()
                                    isEditingPromptManually = false
                                }
                                .testTag("radio_include_vocab_toggle"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (includeVocabInPrompt) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            border = BorderStroke(
                                1.dp,
                                if (includeVocabInPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RadioButton(
                                    selected = includeVocabInPrompt,
                                    onClick = {
                                        val newValue = !includeVocabInPrompt
                                        includeVocabInPrompt = newValue
                                        prefs.edit().putBoolean("story_include_vocab_in_prompt", newValue).apply()
                                        isEditingPromptManually = false
                                    },
                                    modifier = Modifier.testTag("radio_include_vocab_button")
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Добавлять выученные слова в промпт",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (includeVocabInPrompt) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (includeVocabInPrompt)
                                            "Весь список выученных слов и процент незнакомых слов передаются в промпт"
                                        else
                                            "Исключить слова из промпта. Генератор ориентируется строго на правила уровня",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Recommendation banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Recommend,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Column {
                                    Text(
                                        text = "Рекомендуемый уровень: ${recommendedLevel.titleRu}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Ориентир перехода: A1 (0–150), A2 (151–500), B1 (501–1500), B2 (1501–3500), C1 (3501–7000), C2 (7000+).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. CEFR Level Selector (A1 - C2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. Выберите уровень текста (A1–C2):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CefrLevel.values()) { level ->
                            val isSelected = selectedLevel == level
                            val isRecommended = recommendedLevel == level

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedLevel = level
                                    isEditingPromptManually = false
                                },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = level.code,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isRecommended) {
                                            Text(
                                                "★",
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("level_chip_${level.code}")
                            )
                        }
                    }

                    Text(
                        text = "${selectedLevel.titleRu}: ${selectedLevel.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // 3. 10 Styles / Genres Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "2. Выберите жанр / стиль (10 вариантов):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StoryGenre.values().forEach { genre ->
                            val isSelected = selectedGenre == genre

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedGenre = genre
                                        isEditingPromptManually = false
                                    }
                                    .testTag("genre_card_${genre.id}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(genre.iconName, fontSize = 20.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = genre.titleRu,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = genre.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Story Length / Scope Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "3. Выберите масштаб / объем книги:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StoryLength.values().forEach { len ->
                            val isSelected = selectedLength == len

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLength = len
                                        isEditingPromptManually = false
                                    }
                                    .testTag("length_card_${len.id}"),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = when (len) {
                                            StoryLength.EPIC -> Icons.Default.AutoStories
                                            StoryLength.NOVELLA -> Icons.Default.MenuBook
                                            StoryLength.STANDARD -> Icons.Default.Article
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = len.titleRu,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (len == StoryLength.NOVELLA) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ) {
                                                    Text(
                                                        "ХИТ",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = len.subtitleRu,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Unknown Words Percentage Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header with title and active toggle badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "4. Процент незнакомых слов в тексте:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (includeVocabInPrompt) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = formatPercentageLabel(selectedPercentage),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    text = "Отключено",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Radio button: Include known words & percentage in prompt
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val newValue = !includeVocabInPrompt
                                includeVocabInPrompt = newValue
                                prefs.edit().putBoolean("story_include_vocab_in_prompt", newValue).apply()
                                isEditingPromptManually = false
                            }
                            .testTag("radio_include_vocab_in_section4"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (includeVocabInPrompt) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (includeVocabInPrompt) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = includeVocabInPrompt,
                                onClick = {
                                    val newValue = !includeVocabInPrompt
                                    includeVocabInPrompt = newValue
                                    prefs.edit().putBoolean("story_include_vocab_in_prompt", newValue).apply()
                                    isEditingPromptManually = false
                                },
                                modifier = Modifier.testTag("radio_include_vocab_button_section4")
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Учитывать изученные слова и процент в промпте",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (includeVocabInPrompt) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (includeVocabInPrompt)
                                        "В промпт передается база выученных слов и задается желаемая доля неизвестных слов ($selectedPercentage%)"
                                    else
                                        "Отключено: слова исключены, текст строится строго по общим правилам уровня ${selectedLevel.code}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (includeVocabInPrompt) {
                        // Action bar: Delete mode toggle and Add button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Radiobutton / Toggle for deletion mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isDeletePercentMode = !isDeletePercentMode }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                    .testTag("toggle_delete_percent_mode")
                            ) {
                                RadioButton(
                                    selected = isDeletePercentMode,
                                    onClick = { isDeletePercentMode = !isDeletePercentMode },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Режим удаления кнопок",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isDeletePercentMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isDeletePercentMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            // Add custom percentage button
                            OutlinedButton(
                                onClick = { showAddPercentDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp).testTag("add_custom_percent_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Добавить %", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // FlowRow of percentage buttons
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            percentageList.forEach { pct ->
                                val isSelected = !isDeletePercentMode && selectedPercentage == pct
                                val pctLabel = formatPercentageLabel(pct)

                                Card(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 72.dp)
                                        .clickable {
                                            if (isDeletePercentMode) {
                                                // Remove percentage
                                                val updatedList = percentageList.filter { it != pct }
                                                if (updatedList.isNotEmpty()) {
                                                    percentageList = updatedList
                                                    prefs.edit().putString(
                                                        "story_custom_percentages_list",
                                                        updatedList.joinToString(",")
                                                    ).apply()
                                                    if (selectedPercentage == pct) {
                                                        selectedPercentage = updatedList.first()
                                                        prefs.edit().putFloat("story_selected_unknown_percentage", selectedPercentage.toFloat()).apply()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Нельзя удалить последний процент", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                selectedPercentage = pct
                                                prefs.edit().putFloat("story_selected_unknown_percentage", pct.toFloat()).apply()
                                                isEditingPromptManually = false
                                            }
                                        }
                                        .testTag("unknown_words_percent_${pctLabel.replace("%", "").replace(".", "_")}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isDeletePercentMode -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isDeletePercentMode -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isDeletePercentMode) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Удалить $pctLabel",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = pctLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isDeletePercentMode -> MaterialTheme.colorScheme.error
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Description of current selection
                        if (isDeletePercentMode) {
                            Text(
                                text = "Нажмите на любую кнопку процента выше, чтобы удалить её из списка.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = formatPercentageDescription(selectedPercentage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Explanatory note when disabled
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Так как известные слова не передаются в промпт, процент незнакомых слов не используется. Модель будет адаптировать весь словарный запас исключительно под выбранный уровень (${selectedLevel.code}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // 5. Custom Plot Instructions Field
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "5. Дополнительные пожелания к сюжету (необязательно):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = customUserNotes,
                        onValueChange = {
                            customUserNotes = it
                            isEditingPromptManually = false
                        },
                        placeholder = { Text("Например: добавить собаку в сюжет, действие происходит в старом замке...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_plot_notes_field"),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3
                    )
                }
            }

            // 6. Custom Target Words Field (Words to learn & repeat as often as possible)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "6. Слова для частого повторения в тексте (через запятую):",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Укажите слова, которые вы хотите выучить. AI будет использовать их в сюжете и диалогах как можно чаще в разных грамматических формах, чтобы вы их надежно усвоили.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customTargetWords,
                        onValueChange = {
                            customTargetWords = it
                            isEditingPromptManually = false
                        },
                        placeholder = { Text("Например: die Sehnsucht, entdecken, plötzlich, das Geheimnis...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_target_words_field"),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3
                    )
                }
            }

            // 7. Live Prompt Preview & Editable Field
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Промпт для AI-сервера",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Вы можете отредактировать промпт перед отправкой",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isEditingPromptManually) {
                                TextButton(
                                    onClick = {
                                        isEditingPromptManually = false
                                        promptText = buildDefaultPrompt(
                                            languageCode = selectedLanguage,
                                            level = selectedLevel,
                                            genre = selectedGenre,
                                            storyLength = selectedLength,
                                            unknownWordsRatio = UnknownWordsRatio.PERCENT_5_0,
                                            learningWords = displayStudyWords,
                                            knownWords = displayKnownWords,
                                            customUserNotes = customUserNotes,
                                            customTargetWords = customTargetWords,
                                            includeKnownWordsInPrompt = includeVocabInPrompt,
                                            customUnknownPercentage = selectedPercentage
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Сбросить")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = promptText,
                            onValueChange = {
                                promptText = it
                                isEditingPromptManually = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 300.dp)
                                .testTag("editable_prompt_field"),
                            shape = RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                        )
                    }
                }
            }

            // Error display
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = errorMessage ?: "Произошла ошибка",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Generate Button
            item {
                Button(
                    onClick = {
                        if (isGenerating) return@Button
                        viewModel.startStoryGeneration(
                            context = context,
                            promptText = promptText,
                            languageCode = selectedLanguage,
                            level = selectedLevel,
                            genre = selectedGenre,
                            storyLength = selectedLength,
                            selectedPercentage = selectedPercentage,
                            includeVocabInPrompt = includeVocabInPrompt,
                            displayStudyWords = displayStudyWords,
                            displayKnownWords = displayKnownWords,
                            customUserNotes = customUserNotes,
                            customTargetWords = customTargetWords
                        )
                    },
                    enabled = !isGenerating && promptText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_story_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = generationProgressStage.ifBlank { "Генерация в фоне..." },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сгенерировать текст",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                if (isGenerating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelStoryGeneration() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отменить генерацию")
                    }
                }
            }
        }
    }

    if (showAddPercentDialog) {
        var inputPercentageText by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddPercentDialog = false },
            title = {
                Text("Добавить свой процент", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Укажите процент незнакомых слов в тексте (например: 3, 12, 15):",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = inputPercentageText,
                        onValueChange = {
                            inputPercentageText = it.replace(",", ".")
                            inputError = null
                        },
                        label = { Text("Процент") },
                        trailingIcon = { Text("%", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = inputError != null,
                        supportingText = inputError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth().testTag("add_percent_input_field")
                    )

                    Text(
                        "Быстрый выбор:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(3.0, 12.0, 15.0, 20.0).forEach { preset ->
                            SuggestionChip(
                                onClick = {
                                    inputPercentageText = if (preset % 1.0 == 0.0) preset.toInt().toString() else preset.toString()
                                    inputError = null
                                },
                                label = { Text(formatPercentageLabel(preset)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = inputPercentageText.toDoubleOrNull()
                        if (parsed == null || parsed <= 0.0 || parsed >= 100.0) {
                            inputError = "Введите число от 0.1 до 99"
                        } else {
                            val rounded = (parsed * 10).roundToInt() / 10.0
                            val updatedList = (percentageList + rounded).distinct().sorted()
                            percentageList = updatedList
                            selectedPercentage = rounded
                            prefs.edit()
                                .putString("story_custom_percentages_list", updatedList.joinToString(","))
                                .putFloat("story_selected_unknown_percentage", rounded.toFloat())
                                .apply()
                            isEditingPromptManually = false
                            showAddPercentDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_percent_button")
                ) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPercentDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
