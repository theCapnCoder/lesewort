package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.ThemeSettings
import com.example.ui.MainViewModel
import com.example.ui.ScreenTab
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val knownWords by viewModel.knownWords.collectAsState()
    val studyWords by viewModel.studyWords.collectAsState()
    val portionSize by viewModel.studyWordPortionSize.collectAsState()
    val isGenerateStoryActive by viewModel.isGenerateStoryActive.collectAsState()
    var isWordStatsActive by remember { mutableStateOf(false) }
    var isGameModeActive by remember { mutableStateOf(false) }
    var isStudyListActive by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (isGenerateStoryActive) {
            viewModel.setGenerateStoryActive(false)
        } else if (isWordStatsActive) {
            isWordStatsActive = false
        } else if (isGameModeActive) {
            isGameModeActive = false
        } else if (isStudyListActive) {
            isStudyListActive = false
        } else {
            onNavigateToTab(ScreenTab.LIBRARY)
        }
    }
    val learningColorCode by viewModel.learningColor.collectAsState()
    val learnedColorCode by viewModel.learnedColor.collectAsState()
    
    val currentLang by viewModel.currentLanguage.collectAsState()
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val currentDateStr = remember { sdf.format(java.util.Date()) }

    val exportLearnedFileName = remember(currentLang, currentDateStr) {
        "words_${currentLang}_learned_${currentDateStr}.txt"
    }
    val exportLearningFileName = remember(currentLang, currentDateStr) {
        "words_${currentLang}_for_learning_${currentDateStr}.txt"
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var manualWordInput by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableStateOf(0) } // 0 = Все, 1 = На изучение, 2 = Выученные, 3 = Изучаемые (Study)
    var addStatus by remember { mutableStateOf(2) } // default 2 (Выученные)

    // Initialize TTS on launch
    LaunchedEffect(Unit) {
        viewModel.initTts(context)
    }

    // Filter study words dynamically
    val filteredStudyWords = remember(studyWords, searchQuery) {
        if (searchQuery.isBlank()) {
            studyWords
        } else {
            studyWords.filter { it.word.contains(searchQuery.trim().lowercase(), ignoreCase = true) }
        }
    }

    // Filter words dynamically on search and status
    val filteredWords = remember(knownWords, searchQuery, selectedFilterIndex) {
        val baseList = when (selectedFilterIndex) {
            1 -> knownWords.filter { it.status == 1 }
            2 -> knownWords.filter { it.status == 2 }
            else -> knownWords
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter { it.word.contains(searchQuery.trim().lowercase(), ignoreCase = true) }
        }
    }

    // 1. Export Learned Launcher
    val exportLearnedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val learnedWords = knownWords.filter { it.status == 2 }
                        val text = learnedWords.joinToString("\n") { it.word }
                        output.write(text.toByteArray(Charsets.UTF_8))
                    }
                    val count = knownWords.count { it.status == 2 }
                    Toast.makeText(context, "Выгружено выученных слов: $count", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка выгрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    // 2. Export Learning Launcher
    val exportLearningLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val learningWords = knownWords.filter { it.status == 1 }
                        val text = learningWords.joinToString("\n") { it.word }
                        output.write(text.toByteArray(Charsets.UTF_8))
                    }
                    val count = knownWords.count { it.status == 1 }
                    Toast.makeText(context, "Выгружено слов на изучение: $count", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка выгрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    // 3. Import Learned Launcher
    val importLearnedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val text = input.bufferedReader().readText()
                        val importedCount = viewModel.importDictionaryWords(text, status = 2)
                        Toast.makeText(context, "Загружено выученных слов: $importedCount", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    // 4. Import Learning Launcher
    val importLearningLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val text = input.bufferedReader().readText()
                        val importedCount = viewModel.importDictionaryWords(text, status = 1)
                        Toast.makeText(context, "Загружено слов на изучение: $importedCount", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val coroutineScope = rememberCoroutineScope()

    // 5. Export All Launcher (Directory selection)
    val exportAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        val databaseWords = viewModel.getAllKnownWordsList()
                        val languagesToExport = mutableSetOf("de", "en")
                        languagesToExport.addAll(databaseWords.map { it.language }.filter { it.isNotEmpty() })
                        
                        val dirFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                        if (dirFile != null && dirFile.isDirectory) {
                            var successCount = 0
                            languagesToExport.forEach { lang ->
                                // status 1: на изучении
                                val learningFileName = "words_${lang}_for_learning.txt"
                                val learningWords = databaseWords.filter { it.language == lang && it.status == 1 }
                                val learningText = learningWords.joinToString("\n") { it.word }
                                
                                var fileLearning = dirFile.findFile(learningFileName)
                                if (fileLearning == null) {
                                    fileLearning = dirFile.createFile("text/plain", learningFileName)
                                }
                                fileLearning?.let { file ->
                                    context.contentResolver.openOutputStream(file.uri)?.use { output ->
                                        output.write(learningText.toByteArray(Charsets.UTF_8))
                                        successCount++
                                    }
                                }

                                // status 2: выученные
                                val learnedFileName = "words_${lang}_learned.txt"
                                val learnedWords = databaseWords.filter { it.language == lang && it.status == 2 }
                                val learnedText = learnedWords.joinToString("\n") { it.word }
                                
                                var fileLearned = dirFile.findFile(learnedFileName)
                                if (fileLearned == null) {
                                    fileLearned = dirFile.createFile("text/plain", learnedFileName)
                                }
                                fileLearned?.let { file ->
                                    context.contentResolver.openOutputStream(file.uri)?.use { output ->
                                        output.write(learnedText.toByteArray(Charsets.UTF_8))
                                        successCount++
                                    }
                                }
                            }
                            Toast.makeText(context, "Резервная копия создана! Сохранено файлов: $successCount", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Не удалось открыть выбранную папку", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    // Helper to extract filename from URI
    fun getUriFileName(ctx: android.content.Context, uri: android.net.Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name ?: "unknown.txt"
    }

    // 6. Import All Launcher (Multiple TXT file selection)
    val importAllLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (!uris.isNullOrEmpty()) {
                coroutineScope.launch {
                    try {
                        var totalFiles = 0
                        var totalWords = 0
                        
                        val regexLearned = Regex("words_([a-zA-Z0-9]+)_learned(?:_.*)?\\.txt", RegexOption.IGNORE_CASE)
                        val regexLearning = Regex("words_([a-zA-Z0-9]+)_for_learning(?:_.*)?\\.txt", RegexOption.IGNORE_CASE)
                        
                        uris.forEach { uri ->
                            val fileName = getUriFileName(context, uri)
                            
                            val isLearned = regexLearned.matchEntire(fileName)
                            val isLearning = regexLearning.matchEntire(fileName)
                            
                            val lang = when {
                                isLearned != null -> isLearned.groupValues[1].lowercase()
                                isLearning != null -> isLearning.groupValues[1].lowercase()
                                else -> null
                            }
                            val status = when {
                                isLearned != null -> 2
                                isLearning != null -> 1
                                else -> null
                            }
                            
                            if (lang != null && status != null) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    val text = input.bufferedReader().readText()
                                    val count = viewModel.importDictionaryWordsForLanguage(text, language = lang, status = status)
                                    totalWords += count
                                    totalFiles++
                                }
                            }
                        }
                        
                        if (totalFiles > 0) {
                            Toast.makeText(context, "Импорт завершен! Обработано файлов: $totalFiles, загружено слов: $totalWords", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Ни один выбранный файл не соответствовал шаблону 'words_[язык]_learned.txt' или 'words_[язык]_for_learning.txt'", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isGenerateStoryActive) {
                TopAppBar(
                    title = {
                        Text(
                            "В словаре: ${knownWords.size} слов",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        if (isGenerateStoryActive) {
            GenerateStoryScreen(
                viewModel = viewModel,
                onBack = { viewModel.setGenerateStoryActive(false) },
                onNavigateToTab = onNavigateToTab,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isWordStatsActive) {
            WordStatsView(
                viewModel = viewModel,
                onClose = { isWordStatsActive = false },
                modifier = Modifier.fillMaxSize()
            )
        } else if (isGameModeActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                FindAPairGameView(
                    studyWords = studyWords,
                    portionSize = portionSize,
                    onClose = { isGameModeActive = false },
                    viewModel = viewModel
                )
            }
        } else if (isStudyListActive) {
            // Dedicated full sub-view for study words list
            var isDeleteModeEnabled by remember { mutableStateOf(false) }
            var isSortByMastery by remember { mutableStateOf(false) } // false = по времени добавления, true = по тому на сколько выучено

            val currentLangStudyWords = remember(studyWords, isSortByMastery) {
                if (isSortByMastery) {
                    studyWords.sortedWith(
                        compareByDescending<com.example.data.db.StudyWord> { it.correctCount }
                            .thenByDescending { it.addedAt }
                    )
                } else {
                    studyWords.sortedByDescending { it.addedAt }
                }
            }
            val untouchedCount = remember(studyWords) { studyWords.count { it.correctCount == 0 } }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isStudyListActive = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        text = "Желаемые слова: ${studyWords.size} (не затронуто: $untouchedCount)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9333EA),
                        modifier = Modifier.weight(1f)
                    )

                    // Sort toggle icon
                    IconButton(
                        onClick = {
                            isSortByMastery = !isSortByMastery
                            val msg = if (isSortByMastery) "Сортировка: По уровню изучения" else "Сортировка: По времени добавления"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = if (isSortByMastery) Icons.Default.BarChart else Icons.Default.Schedule,
                            contentDescription = if (isSortByMastery) "По уровню изучения" else "По времени добавления",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Copy icon
                    if (studyWords.isNotEmpty()) {
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        IconButton(
                            onClick = {
                                val text = studyWords.joinToString("\n") { "${it.word} - ${it.translation}" }
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                                Toast.makeText(context, "Список слов скопирован", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Копировать все слова"
                            )
                        }
                    }

                    // Delete All icon (visible only in delete mode)
                    if (isDeleteModeEnabled && studyWords.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.deleteAllStudyWords()
                                isDeleteModeEnabled = false
                                Toast.makeText(context, "Все желаемые слова удалены", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Удалить все",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(
                        onClick = { isDeleteModeEnabled = !isDeleteModeEnabled },
                        modifier = Modifier.testTag("toggle_delete_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isDeleteModeEnabled) Icons.Default.Close else Icons.Default.Delete,
                            contentDescription = if (isDeleteModeEnabled) "Выйти из режима удаления" else "Режим удаления",
                            tint = if (isDeleteModeEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (currentLangStudyWords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Список желаемых слов пуст.\nДобавляйте новые слова в желаемое кнопкой '+' при чтении книги или в режиме для игры «Найди пару»!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("study_words_list"),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentLangStudyWords, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("study_word_item_${item.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.word,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(item.language.uppercase()) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.translation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StudyWordProgressSpeaker(
                                            word = item.word,
                                            language = item.language,
                                            correctCount = item.correctCount,
                                            viewModel = viewModel
                                        )
                                        
                                        if (isDeleteModeEnabled) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteStudyWord(item.id)
                                                    Toast.makeText(context, "Слово удалено", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Удалить слово",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AI Story Generation Hero Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("ai_story_generation_banner_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Генерация текста (AI)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "Создайте полноценную книгу из ваших выученных и желаемых слов. Настройте уровень сложности, жанр, язык и редактируйте промпт перед отправкой.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Button(
                                onClick = { viewModel.setGenerateStoryActive(true) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("generate_story_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Сгенерировать текст",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Game & Study List Buttons Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isGameModeActive = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_game_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Найди пару",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { isStudyListActive = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("view_study_words_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9333EA).copy(alpha = 0.15f),
                                contentColor = Color(0xFF9333EA)
                            )
                        ) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF9333EA))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Желаемые (${studyWords.size})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Word Statistics Button
                item {
                    Button(
                        onClick = { isWordStatsActive = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("word_stats_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Статистика слов",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Bulk Operations Card
                item {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Импорт-Экспорт",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = "Загружайте и выгружайте списки слов в формате .txt раздельно по статусам.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Section 1: Загрузка слов
                            Text(
                                "Загрузить слова из файла (Импорт):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { importLearnedLauncher.launch("text/plain") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_learned_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ThemeSettings.getLearnedHighlightColor(learnedColorCode).copy(alpha = 0.2f),
                                        contentColor = ThemeSettings.getLearnedBadgeColor(learnedColorCode)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Выученные", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = { importLearningLauncher.launch("text/plain") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_learning_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ThemeSettings.getLearningHighlightColor(learningColorCode).copy(alpha = 0.2f),
                                        contentColor = ThemeSettings.getLearningBadgeColor(learningColorCode)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("На изучение", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Section 2: Выгрузка слов
                            Text(
                                "Сохранить слова в файл (Экспорт):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { exportLearnedLauncher.launch(exportLearnedFileName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_learned_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Выученные", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = { exportLearningLauncher.launch(exportLearningFileName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_learning_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("На изучение", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            // Раздел 3: Резервное копирование всех языков
                            Text(
                                "Все словари (Масштабируемый бэкап всех языков):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { importAllLauncher.launch(arrayOf("text/plain")) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("import_all_languages_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Загрузить всё", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = { exportAllLauncher.launch(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("export_all_languages_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Сохранить всё", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Add Manual Word Section Card
                item {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Быстрое добавление нового слова",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Choice of status for new word
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Тип:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = addStatus == 1,
                                        onClick = { addStatus = 1 },
                                        label = { Text("На изучение (1)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ThemeSettings.getLearningHighlightColor(learningColorCode).copy(alpha = 0.25f),
                                            selectedLabelColor = ThemeSettings.getLearningBadgeColor(learningColorCode)
                                        )
                                    )
                                    FilterChip(
                                        selected = addStatus == 2,
                                        onClick = { addStatus = 2 },
                                        label = { Text("Выучено (2)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ThemeSettings.getLearnedHighlightColor(learnedColorCode).copy(alpha = 0.25f),
                                            selectedLabelColor = ThemeSettings.getLearnedBadgeColor(learnedColorCode)
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = manualWordInput,
                                    onValueChange = { manualWordInput = it },
                                    placeholder = { Text("Введите слово...") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("manual_word_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                                Button(
                                    onClick = {
                                        val trimmed = manualWordInput.trim()
                                        if (trimmed.isNotEmpty()) {
                                            if (addStatus == 1) {
                                                viewModel.addWordToLearning(trimmed)
                                            } else {
                                                viewModel.addWordToLearned(trimmed)
                                            }
                                            manualWordInput = ""
                                            Toast.makeText(context, "Добавлено!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .testTag("manual_word_add_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                                }
                            }
                        }
                    }
                }

                // Word List Status Filters
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Фильтр:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        listOf("Все", "На изучение", "Выученные").forEachIndexed { index, label ->
                            val isSelected = selectedFilterIndex == index
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilterIndex = index },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when(index) {
                                        1 -> ThemeSettings.getLearningHighlightColor(learningColorCode).copy(alpha = 0.25f)
                                        2 -> ThemeSettings.getLearnedHighlightColor(learnedColorCode).copy(alpha = 0.25f)
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    },
                                    selectedLabelColor = when(index) {
                                        1 -> ThemeSettings.getLearningBadgeColor(learningColorCode)
                                        2 -> ThemeSettings.getLearnedBadgeColor(learnedColorCode)
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                ),
                                modifier = Modifier.testTag("filter_chip_$index")
                            )
                        }
                    }
                }

                // Search Filter TF
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Поиск по словарю") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Очистить")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .testTag("dictionary_search_input")
                    )
                }

                // Dictionary lists inside the outer LazyColumn!
                if (filteredWords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Слово не найдено" else "Словарь пока пуст.\nДобавляйте слова вручную или нажимайте на них прямо в тексте при чтении книги!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                } else {
                    items(filteredWords, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dict_word_item_${item.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                              ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.word,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Status Badge
                                    val isLearning = item.status == 1
                                    Text(
                                        text = if (isLearning) "На изучении" else "Выучено",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLearning) ThemeSettings.getLearningBadgeColor(learningColorCode) else ThemeSettings.getLearnedBadgeColor(learnedColorCode)
                                    )
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.status == 1) {
                                        IconButton(
                                            onClick = {
                                                viewModel.addWordToLearned(item.word)
                                                Toast.makeText(context, "Изучено!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Отметить как выученное",
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                viewModel.addWordToLearning(item.word)
                                                Toast.makeText(context, "Перенесено в изучение", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = "Перенести в изучение",
                                                tint = Color(0xFFFF9800),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            viewModel.removeKnownWord(item.id)
                                            Toast.makeText(context, "Слово удалено", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить слово",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FindAPairGameView(
    studyWords: List<com.example.data.db.StudyWord>,
    portionSize: Int,
    onClose: () -> Unit,
    viewModel: MainViewModel
) {
    // Game session states
    var gameMode by remember { mutableStateOf("Shuffle") } // "Shuffle", "Easy", "Hard"
    var isPortionStarted by remember { mutableStateOf(false) }
    var isSessionEnded by remember { mutableStateOf(false) }
    val showLimitSetting by viewModel.gameRepetitionsLimit.collectAsState()
    
    var currentSessionWords by remember { mutableStateOf(emptyList<com.example.data.db.StudyWord>()) }
    var totalMatchesCompleted by remember { mutableStateOf(0) }
    var fullyLearnedWordsInSession by remember { mutableStateOf(emptySet<com.example.data.db.StudyWord>()) }
    
    // Active round states (displays up to 5 pairs)
    var currentRoundWords by remember { mutableStateOf(emptyList<com.example.data.db.StudyWord>()) }
    var leftColumnShuffled by remember { mutableStateOf(emptyList<com.example.data.db.StudyWord>()) }
    var rightColumnShuffled by remember { mutableStateOf(emptyList<com.example.data.db.StudyWord>()) }
    
    var selectedLeftWord by remember { mutableStateOf<com.example.data.db.StudyWord?>(null) }
    var selectedRightWord by remember { mutableStateOf<com.example.data.db.StudyWord?>(null) }
    var matchedWordIdsInRound by remember { mutableStateOf(setOf<Int>()) }
    
    var errorLeftWord by remember { mutableStateOf<com.example.data.db.StudyWord?>(null) }
    var errorRightWord by remember { mutableStateOf<com.example.data.db.StudyWord?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Active vocabulary pool filtered from studyWords so that deleted words are excluded dynamically
    val activeSessionWords = remember(studyWords, currentSessionWords) {
        currentSessionWords.mapNotNull { sessionWord ->
            studyWords.find { it.id == sessionWord.id }
        }
    }
    
    // Start session and round helper
    val startSession: () -> Unit = {
        val sortedList = when (gameMode) {
            "Easy" -> studyWords.sortedWith(compareByDescending<com.example.data.db.StudyWord> { it.correctCount }.thenBy { it.word.length })
            "Hard" -> studyWords.sortedWith(compareBy<com.example.data.db.StudyWord> { it.correctCount }.thenByDescending { it.word.length })
            else -> studyWords.shuffled()
        }
        val portion = sortedList.take(portionSize)
        currentSessionWords = portion
        totalMatchesCompleted = 0
        fullyLearnedWordsInSession = emptySet()
        isPortionStarted = true
        isSessionEnded = false
        
        val roundSize = minOf(5, portion.size)
        val round = when (gameMode) {
            "Easy" -> portion.sortedWith(compareByDescending<com.example.data.db.StudyWord> { it.correctCount }.thenBy { it.word.length }).take(roundSize)
            "Hard" -> portion.sortedWith(compareBy<com.example.data.db.StudyWord> { it.correctCount }.thenByDescending { it.word.length }).take(roundSize)
            else -> portion.shuffled().take(roundSize)
        }
        currentRoundWords = round
        leftColumnShuffled = round.shuffled()
        rightColumnShuffled = round.shuffled()
        matchedWordIdsInRound = emptySet()
        selectedLeftWord = null
        selectedRightWord = null
        errorLeftWord = null
        errorRightWord = null
    }
    
    // Auto transition to the next round of 5 words without intermediate popup!
    LaunchedEffect(matchedWordIdsInRound, currentRoundWords) {
        if (currentRoundWords.isNotEmpty() && matchedWordIdsInRound.size == currentRoundWords.size) {
            val newlyCompletedCount = totalMatchesCompleted + currentRoundWords.size
            
            // Get latest active words (excluding deleted) with fresh database values
            val activeWords = currentSessionWords.mapNotNull { sessionWord ->
                studyWords.find { it.id == sessionWord.id }
            }
            
            if (newlyCompletedCount >= showLimitSetting || activeWords.isEmpty()) {
                kotlinx.coroutines.delay(400)
                totalMatchesCompleted = newlyCompletedCount
                isSessionEnded = true
            } else {
                kotlinx.coroutines.delay(400)
                totalMatchesCompleted = newlyCompletedCount
                
                val roundSize = minOf(5, activeWords.size)
                val nextRound = when (gameMode) {
                    "Easy" -> activeWords.sortedWith(compareByDescending<com.example.data.db.StudyWord> { it.correctCount }.thenBy { it.word.length }).take(roundSize)
                    "Hard" -> activeWords.sortedWith(compareBy<com.example.data.db.StudyWord> { it.correctCount }.thenByDescending { it.word.length }).take(roundSize)
                    else -> activeWords.shuffled().take(roundSize)
                }
                currentRoundWords = nextRound
                leftColumnShuffled = nextRound.shuffled()
                rightColumnShuffled = nextRound.shuffled()
                matchedWordIdsInRound = emptySet()
                selectedLeftWord = null
                selectedRightWord = null
                errorLeftWord = null
                errorRightWord = null
            }
        }
    }
    
    val checkMatch: (com.example.data.db.StudyWord, com.example.data.db.StudyWord) -> Unit = { left, right ->
        if (left == right) {
            matchedWordIdsInRound = matchedWordIdsInRound + left.id
            selectedLeftWord = null
            selectedRightWord = null
            viewModel.incrementStudyWordCorrectCount(left)
            val targetCount = viewModel.targetCorrectCount.value
            if (left.correctCount + 1 >= targetCount) {
                fullyLearnedWordsInSession = fullyLearnedWordsInSession + left
            }
        } else {
            errorLeftWord = left
            errorRightWord = right
            viewModel.decrementStudyWordCorrectCount(left)
            viewModel.decrementStudyWordCorrectCount(right)
            scope.launch {
                kotlinx.coroutines.delay(800)
                selectedLeftWord = null
                selectedRightWord = null
                errorLeftWord = null
                errorRightWord = null
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
            }
            
            if (studyWords.isNotEmpty() && isPortionStarted) {
                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val progress = if (showLimitSetting > 0) totalMatchesCompleted.toFloat() / showLimitSetting else 0f
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$totalMatchesCompleted/$showLimitSetting",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            if (isPortionStarted) {
                IconButton(onClick = { 
                    fullyLearnedWordsInSession = emptySet()
                    startSession() 
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Начать заново")
                }
            }
        }
        
        if (studyWords.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Нет слов на изучение",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "При чтении книги нажимайте на слова и добавляйте их кнопкой '+' в список на изучение. Тогда они появятся здесь для тренировки!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onClose) {
                        Text("Вернуться в словарь")
                    }
                }
            }
        } else if (!isPortionStarted) {
            // Setup Screen!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val untouchedCount = remember(studyWords) { studyWords.count { it.correctCount == 0 } }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Слов: ${studyWords.size} (не затронуто: $untouchedCount)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    
                    // Compact side-by-side selectors for learning portions & reps limit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Column: Portion Size selector
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Сколько учим слов:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                OutlinedIconButton(
                                    onClick = {
                                        if (portionSize > 5) {
                                            viewModel.setStudyWordPortionSize(context, portionSize - 5)
                                        }
                                    },
                                    enabled = portionSize > 5,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Меньше", modifier = Modifier.size(14.dp))
                                }
                                
                                Text(
                                    text = "$portionSize",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                
                                OutlinedIconButton(
                                    onClick = {
                                        viewModel.setStudyWordPortionSize(context, portionSize + 5)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Больше", modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Right Column: repetitions limit selector
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Повторений в игре:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                OutlinedIconButton(
                                    onClick = {
                                        if (showLimitSetting > 5) {
                                            viewModel.setGameRepetitionsLimit(context, showLimitSetting - 5)
                                        }
                                    },
                                    enabled = showLimitSetting > 5,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Меньше", modifier = Modifier.size(14.dp))
                                }
                                
                                Text(
                                    text = "$showLimitSetting",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                
                                OutlinedIconButton(
                                    onClick = {
                                        viewModel.setGameRepetitionsLimit(context, showLimitSetting + 5)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Больше", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Mode Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Режим:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "Shuffle" to "Микс",
                                "Easy" to "Простой",
                                "Hard" to "Сложный"
                            ).forEach { (modeCode, modeLabel) ->
                                val isSelected = gameMode == modeCode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { gameMode = modeCode },
                                    label = { Text(modeLabel, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Button(
                        onClick = {
                            fullyLearnedWordsInSession = emptySet()
                            startSession()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Начать тренировку", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Game Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "Shuffle" to "Вперемешку",
                    "Easy" to "Легкий",
                    "Hard" to "Сложный"
                ).forEach { (modeCode, modeLabel) ->
                    val isSelected = gameMode == modeCode
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            gameMode = modeCode
                            startSession()
                        },
                        label = { Text(modeLabel, style = MaterialTheme.typography.bodySmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
            
            if (isSessionEnded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "🎉 Отличный результат!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Вы завершили тренировку сессии! Выполнено повторений: $totalMatchesCompleted из $showLimitSetting.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val targetCount = viewModel.targetCorrectCount.collectAsState().value
                                Text(
                                    text = "Полностью выученные слова ($targetCount/$targetCount):",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (fullyLearnedWordsInSession.isEmpty()) {
                                    Text(
                                        text = "В этой сессии нет полностью выученных слов (слово считается выученным после $targetCount правильных ответов). Продолжайте заниматься!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    fullyLearnedWordsInSession.forEach { word ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(word.word, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            Text(word.translation, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                        
                        val untouchedCount = remember(studyWords) { studyWords.count { it.correctCount == 0 } }
                        Text(
                            text = "Всего слов осталось на изучение в списке: ${studyWords.size} (не затронуто: $untouchedCount)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        
                        Button(
                            onClick = {
                                Toast.makeText(context, "Тренировка порции завершена!", Toast.LENGTH_SHORT).show()
                                onClose()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Завершить тренировку")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        leftColumnShuffled.forEach { word ->
                            val isMatched = matchedWordIdsInRound.contains(word.id)
                            
                            if (isMatched) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .background(Color.Transparent)
                                )
                            } else {
                                val isSelected = selectedLeftWord == word
                                val isError = errorLeftWord == word
                                
                                val containerColor = when {
                                    isError -> Color(0xFFEF9A9A)
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                                val borderColor = when {
                                    isError -> Color.Red
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent
                                }
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .clickable {
                                            if (errorLeftWord == null && errorRightWord == null) {
                                                viewModel.speak(word.word, word.language)
                                                selectedLeftWord = word
                                                selectedRightWord?.let { right ->
                                                    checkMatch(word, right)
                                                }
                                            }
                                        },
                                    border = BorderStroke(1.5.dp, borderColor),
                                    colors = CardDefaults.cardColors(containerColor = containerColor)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = word.word,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rightColumnShuffled.forEach { word ->
                            val isMatched = matchedWordIdsInRound.contains(word.id)
                            
                            if (isMatched) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .background(Color.Transparent)
                                )
                            } else {
                                val isSelected = selectedRightWord == word
                                val isError = errorRightWord == word
                                
                                val containerColor = when {
                                    isError -> Color(0xFFEF9A9A)
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                                val borderColor = when {
                                    isError -> Color.Red
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent
                                }
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .clickable {
                                            if (errorLeftWord == null && errorRightWord == null) {
                                                selectedRightWord = word
                                                selectedLeftWord?.let { left ->
                                                    checkMatch(left, word)
                                                }
                                            }
                                        },
                                    border = BorderStroke(1.5.dp, borderColor),
                                    colors = CardDefaults.cardColors(containerColor = containerColor)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = word.translation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudyWordProgressSpeaker(
    word: String,
    language: String,
    correctCount: Int,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 3.dp.toPx()
            val sizeMin = size.minDimension
            val radius = (sizeMin - strokeWidthPx) / 2f
            val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)

            val segments = 8
            val gapAngle = 6f
            val segmentAngle = (360f / segments) - gapAngle

            for (i in 0 until segments) {
                val startAngle = -90f + (i * (360f / segments)) + (gapAngle / 2f)
                val isCompleted = i < correctCount
                val color = if (isCompleted) {
                    val fraction = correctCount / 8f
                    androidx.compose.ui.graphics.Color(
                        red = (1f - fraction) * 0.96f + fraction * 0.3f,
                        green = (1f - fraction) * 0.32f + fraction * 0.69f,
                        blue = (1f - fraction) * 0.32f + fraction * 0.31f,
                        alpha = 1f
                    )
                } else {
                    androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.4f)
                }

                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = segmentAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidthPx,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }

        IconButton(
            onClick = {
                viewModel.speak(word, language)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Произнести слово",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
