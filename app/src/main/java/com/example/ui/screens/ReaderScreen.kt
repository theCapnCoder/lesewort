package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.ui.theme.ThemeSettings
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.LinguisticEngine
import com.example.ui.MainViewModel
import com.example.ui.ScreenTab
import com.example.ui.TranslationUiState
import com.example.ui.VariantsUiState
import com.example.ui.ChapterStatsData
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.DialogProperties
import java.io.File

enum class WordClickMode(val displayName: String) {
    TRANSLATE("Перевод и объяснение"),
    QUICK_ADD("Быстрое добавление"),
    PRONOUNCE("Только произношение")
}

enum class ParagraphMode(val displayName: String) {
    OFF("Выключен"),
    FULL_PARAGRAPH("Перевод абзаца целиком"),
    UNKNOWN_WORDS_INLINE("Перевод незнакомых слов в тексте"),
    ADD_TO_MATCH_GAME("Добавление незнакомых слов в «Найди пару»")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    LaunchedEffect(Unit) {
        viewModel.initTts(context)
    }
    val currentBook by viewModel.currentBook.collectAsState()
    val currentChapters by viewModel.currentChapters.collectAsState()
    val activeChapter by viewModel.activeChapter.collectAsState()
    val activeChapterIndex by viewModel.activeChapterIndex.collectAsState()
    val activeBookStats by viewModel.activeBookStats.collectAsState()
    val activeBookStatsData by viewModel.activeBookStatsData.collectAsState()
    val knownWordsSet by viewModel.knownWordsSet.collectAsState()
    val learningWordsSet by viewModel.learningWordsSet.collectAsState()
    val readingFontSize by viewModel.readingFontSize.collectAsState()
    val learningColorCode by viewModel.learningColor.collectAsState()
    val learnedColorCode by viewModel.learnedColor.collectAsState()
    val libraryItems by viewModel.libraryItems.collectAsState()
    val bookCache by viewModel.bookCache.collectAsState()
    val activeChapterStats by viewModel.activeChapterStats.collectAsState()
    val studyWords by viewModel.studyWords.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()

    val modelShortName = remember(selectedModel) {
        selectedModel
            .substringBefore(" (")
            .trim()
            .split('-', '/', ' ', '_')
            .lastOrNull { it.isNotBlank() } ?: "AI"
    }

    val studyWordsSet = remember(studyWords, knownWordsSet, learningWordsSet) {
        studyWords
            .map { LinguisticEngine.tokenizeWord(it.word) }
            .filter { clean -> !knownWordsSet.contains(clean) && !learningWordsSet.contains(clean) }
            .toSet()
    }

    val currentBookUiItem = remember(libraryItems, currentBook) {
        libraryItems.find { it.book.id == currentBook?.id }
    }

    var clickedWordDetails by remember { mutableStateOf<String?>(null) }
    var wordClickMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(WordClickMode.TRANSLATE) }
    val isQuickAddModeEnabled = wordClickMode == WordClickMode.QUICK_ADD
    var paragraphMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ParagraphMode.OFF) }
    var clickedWordRaw by remember { mutableStateOf("") }
    var clickedWordSentence by remember { mutableStateOf("") }
    var chapterDropdownExpanded by remember { mutableStateOf(false) }

    val sharedPrefs = remember(context) {
        context.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)
    }

    var showChaptersList by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showChaptersList) {
            showChaptersList = false
        } else {
            onNavigateToTab(ScreenTab.LIBRARY)
        }
    }

    LaunchedEffect(currentBook?.id) {
        val book = currentBook
        if (book != null) {
            val opened = sharedPrefs.getStringSet("opened_books", emptySet()) ?: emptySet()
            if (opened.contains(book.id.toString()) || book.currentChapterIndex > 0 || book.currentScrollPosition > 0) {
                showChaptersList = false
                if (!opened.contains(book.id.toString())) {
                    val newOpened = opened.toMutableSet().apply { add(book.id.toString()) }
                    sharedPrefs.edit().putStringSet("opened_books", newOpened).apply()
                }
            } else {
                showChaptersList = true
            }
        }
    }

    val markBookAsOpened = remember(currentBook?.id) {
        {
            val bookId = currentBook?.id
            if (bookId != null) {
                val opened = sharedPrefs.getStringSet("opened_books", emptySet()) ?: emptySet()
                if (!opened.contains(bookId.toString())) {
                    val newOpened = opened.toMutableSet().apply { add(bookId.toString()) }
                    sharedPrefs.edit().putStringSet("opened_books", newOpened).apply()
                }
            }
        }
    }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isRestoringScroll by remember { mutableStateOf(false) }

    // Whenever chapter changes, restore scroll progress or reset
    LaunchedEffect(activeChapterIndex, currentBook?.id) {
        val book = currentBook
        if (book != null) {
            isRestoringScroll = true
            val key = "scroll_pos_book_${book.id}_chapter_${activeChapterIndex}"
            val savedIndex = if (sharedPrefs.contains(key)) {
                sharedPrefs.getInt(key, 0)
            } else {
                if (activeChapterIndex == book.currentChapterIndex) {
                    book.currentScrollPosition
                } else {
                    0
                }
            }
            if (savedIndex > 0) {
                lazyListState.scrollToItem(savedIndex)
            } else {
                lazyListState.scrollToItem(0)
            }
            kotlinx.coroutines.delay(50L)
            isRestoringScroll = false
        }
    }

    // Auto save scroll position periodically, or if the user leaves
    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
        val book = currentBook
        if (book != null && currentChapters.isNotEmpty() && !isRestoringScroll) {
            val scrollPos = lazyListState.firstVisibleItemIndex
            val key = "scroll_pos_book_${book.id}_chapter_${activeChapterIndex}"
            sharedPrefs.edit().putInt(key, scrollPos).apply()
            viewModel.saveProgress(activeChapterIndex, scrollPos)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (currentBook != null) {
                        Column {
                            if (showChaptersList) {
                                Text(
                                    text = "Оглавление",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = currentBook!!.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = currentBook!!.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isTablet && currentBook!!.author.isNotBlank()) {
                                        Text(
                                            text = currentBook!!.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    val newDisplay = activeBookStatsData?.newCount?.toString() ?: currentBook!!.newWordsCount.takeIf { it > 0 }?.toString() ?: "--"
                                    val learningDisplay = activeBookStatsData?.learningCount?.toString() ?: currentBook!!.learningWordsCount.takeIf { it > 0 }?.toString() ?: "--"
                                    val learnedDisplay = activeBookStatsData?.learnedCount?.toString() ?: currentBook!!.learnedWordsCount.takeIf { it > 0 }?.toString() ?: "--"
                                    val compPercentVal = activeBookStatsData?.stats?.comprehensibleVolumePercent ?: currentBook!!.comprehensionPercent.takeIf { it > 0.0 }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = newDisplay,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFF2196F3)
                                        )
                                        Text(
                                            text = "/",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = learningDisplay,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = ThemeSettings.getLearningBadgeColor(learningColorCode)
                                        )
                                        Text(
                                            text = "/",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = learnedDisplay,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color(0xFF4CAF50)
                                        )
                                    }

                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = if (compPercentVal != null && compPercentVal > 0.0) "%.1f%%".format(compPercentVal) else "--%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = studyWordsSet.size.toString(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFBA68C8),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Читалка")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showChaptersList || currentBook == null) {
                                onNavigateToTab(ScreenTab.LIBRARY)
                            } else {
                                showChaptersList = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (currentBook != null && currentChapters.isNotEmpty() && !showChaptersList) {
                        // Word click interaction mode
                        IconButton(
                            onClick = {
                                wordClickMode = when (wordClickMode) {
                                    WordClickMode.TRANSLATE -> WordClickMode.QUICK_ADD
                                    WordClickMode.QUICK_ADD -> WordClickMode.PRONOUNCE
                                    WordClickMode.PRONOUNCE -> WordClickMode.TRANSLATE
                                }
                                Toast.makeText(context, "Режим нажатия: ${wordClickMode.displayName}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = when (wordClickMode) {
                                    WordClickMode.TRANSLATE -> Icons.Default.Book
                                    WordClickMode.QUICK_ADD -> Icons.Default.Bookmark
                                    WordClickMode.PRONOUNCE -> Icons.Default.VolumeUp
                                },
                                contentDescription = "Режим взаимодействия со словами: ${wordClickMode.displayName}",
                                tint = if (wordClickMode != WordClickMode.TRANSLATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Paragraph translation toggle mode
                        IconButton(
                            onClick = {
                                paragraphMode = when (paragraphMode) {
                                    ParagraphMode.OFF -> ParagraphMode.FULL_PARAGRAPH
                                    ParagraphMode.FULL_PARAGRAPH -> ParagraphMode.UNKNOWN_WORDS_INLINE
                                    ParagraphMode.UNKNOWN_WORDS_INLINE -> ParagraphMode.ADD_TO_MATCH_GAME
                                    ParagraphMode.ADD_TO_MATCH_GAME -> ParagraphMode.OFF
                                }
                                if (paragraphMode == ParagraphMode.OFF) {
                                    viewModel.clearParagraphTranslations()
                                    viewModel.clearInlineParagraphTranslations()
                                }
                                Toast.makeText(context, "Режим абзацев: ${paragraphMode.displayName}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = when (paragraphMode) {
                                    ParagraphMode.OFF -> Icons.Default.MenuBook
                                    ParagraphMode.FULL_PARAGRAPH -> Icons.Default.MenuBook
                                    ParagraphMode.UNKNOWN_WORDS_INLINE -> Icons.Default.Translate
                                    ParagraphMode.ADD_TO_MATCH_GAME -> Icons.Default.Style
                                },
                                contentDescription = "Режим перевода абзацев: ${paragraphMode.displayName}",
                                tint = if (paragraphMode != ParagraphMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            IconButton(onClick = { chapterDropdownExpanded = true }) {
                                Icon(Icons.Default.List, contentDescription = "Оглавление")
                            }
                            DropdownMenu(
                                expanded = chapterDropdownExpanded,
                                onDismissRequest = { chapterDropdownExpanded = false }
                            ) {
                                currentChapters.forEachIndexed { index, chapter ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = chapter.title,
                                                fontWeight = if (index == activeChapterIndex) FontWeight.Bold else FontWeight.Normal,
                                                color = if (index == activeChapterIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            val savedScrollPos = currentBook?.let { book ->
                                                sharedPrefs.getInt("scroll_pos_book_${book.id}_chapter_${index}", 0)
                                            } ?: 0
                                            viewModel.selectChapter(index, savedScrollPos)
                                            markBookAsOpened()
                                            chapterDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentBook == null || currentChapters.isEmpty() || activeChapter == null) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Text(
                        "Книга не выбрана",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Выберите книгу на вкладке «Библиотека», чтобы начать комфортное чтение с разбором слов.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { onNavigateToTab(ScreenTab.LIBRARY) }) {
                        Icon(Icons.Default.Book, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Перейти в Библиотеку")
                    }
                }
            } else if (showChaptersList) {
                // Table of Contents list
                val book = currentBook!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chapters_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header item: Book details card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Cover image display with format-aware placeholder
                                com.example.ui.components.BookCoverView(
                                    bookId = book.id,
                                    title = book.title,
                                    author = book.author,
                                    filePath = book.filePath,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(150.dp)
                                )

                                // Information details
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = book.author,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Всего слов: ${book.totalWords.takeIf { it > 0 }?.toString() ?: "--"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Уникальных слов: ${book.uniqueWordsCount.takeIf { it > 0 }?.toString() ?: "--"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    val drawerCompPercent = activeBookStatsData?.stats?.comprehensibleVolumePercent
                                        ?: activeBookStats?.comprehensibleVolumePercent
                                        ?: book.comprehensionPercent.takeIf { it > 0.0 }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Понятно по объему: ${if (drawerCompPercent != null && drawerCompPercent > 0.0) "%.1f%%".format(drawerCompPercent) else "--%"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Local progression count
                                    if (currentBookUiItem != null) {
                                        Text(
                                            text = "Прочитано: ${currentBookUiItem.currentPage} из ${currentBookUiItem.totalPages} стр. (${(currentBookUiItem.currentPage.toFloat() / currentBookUiItem.totalPages * 100).toInt()}%)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Глава ${book.currentChapterIndex + 1} из ${currentChapters.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "Текущий прогресс: глава ${book.currentChapterIndex + 1} из ${currentChapters.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Dedicated Book Vocabulary Statistics Block (Always displayed)
                    item {
                        val cardNewDisplay = activeBookStatsData?.newCount?.toString()
                            ?: book.newWordsCount.takeIf { it > 0 }?.toString()
                            ?: "--"
                        val cardLearningDisplay = activeBookStatsData?.learningCount?.toString()
                            ?: book.learningWordsCount.takeIf { it > 0 }?.toString()
                            ?: "--"
                        val cardLearnedDisplay = activeBookStatsData?.learnedCount?.toString()
                            ?: book.learnedWordsCount.takeIf { it > 0 }?.toString()
                            ?: "--"
                        val cardCompPercent = activeBookStatsData?.stats?.comprehensibleVolumePercent
                            ?: activeBookStats?.comprehensibleVolumePercent
                            ?: book.comprehensionPercent.takeIf { it > 0.0 }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Статистика слов книги",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (cardCompPercent != null && cardCompPercent > 0.0) "%.1f%%".format(cardCompPercent) else "--%",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // 3 word status badges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // New words badge
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2196F3).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = cardNewDisplay,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1976D2)
                                            )
                                            Text(
                                                text = "Новые",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Learning / Желаемое words badge
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = ThemeSettings.getLearningHighlightColor(learningColorCode).copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, ThemeSettings.getLearningHighlightColor(learningColorCode).copy(alpha = 0.35f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = cardLearningDisplay,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = ThemeSettings.getLearningBadgeColor(learningColorCode)
                                            )
                                            Text(
                                                text = "На изучении",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Learned words badge
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = cardLearnedDisplay,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                            Text(
                                                text = "Выученные",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section header
                    item {
                        Text(
                            text = "Главы книги",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    // Chapters list items
                    itemsIndexed(currentChapters) { index, chapter ->
                        val isActive = index == activeChapterIndex
                        val isLastRead = index == book.currentChapterIndex

                        val chapterStats = activeChapterStats[index] ?: ChapterStatsData(0.0, 0, 0, 0, 0)
                        val (complexityPercent, newCount, learningCount, learnedCount, uniqueCount) = chapterStats

                        OutlinedCard(
                            onClick = {
                                val savedScrollPos = currentBook?.let { b ->
                                    sharedPrefs.getInt("scroll_pos_book_${b.id}_chapter_${index}", 0)
                                } ?: 0
                                viewModel.selectChapter(index, savedScrollPos)
                                markBookAsOpened()
                                showChaptersList = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Circular index badge
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = if (isActive) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                },
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            }
                                        )
                                    }

                                    // Chapter details
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        
                                        val secondaryText = if (isActive) {
                                            "Читается сейчас"
                                        } else if (isLastRead) {
                                            "Остановлено здесь"
                                        } else {
                                            null
                                        }
                                        
                                        if (secondaryText != null) {
                                            Text(
                                                text = secondaryText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    // Right-side action/status icon
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = "Читается сейчас",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else if (isLastRead) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Последнее место",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Открыть главу",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Border line for statistics
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                        )
                                )

                                // Minimalist Stats Bar bottom sub-row
                                java.lang.Object().run {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Row 1: Difficulty Level Badge & Total Unique words description
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val (complexityLabel, complexityColor) = when {
                                                uniqueCount == 0 -> Pair("Сложность: --%", MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                                complexityPercent < 15.0 -> Pair("Легкая глава (%.1f%%)".format(complexityPercent), Color(0xFF2E7D32))
                                                complexityPercent < 30.0 -> Pair("Средняя сложность (%.1f%%)".format(complexityPercent), Color(0xFFE65100))
                                                else -> Pair("Сложная глава (%.1f%%)".format(complexityPercent), Color(0xFFC62828))
                                            }
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(complexityColor, shape = CircleShape)
                                                )
                                                Text(
                                                    text = complexityLabel,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Text(
                                                text = if (uniqueCount > 0) "Словарь: $uniqueCount уник." else "Словарь: -- уник.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }

                                        // Row 2: Segmented Proportions Progress Bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            if (uniqueCount > 0) {
                                                if (newCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(newCount.toFloat())
                                                            .fillMaxHeight()
                                                            .background(Color(0xFF2196F3))
                                                    )
                                                }
                                                if (learningCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(learningCount.toFloat())
                                                            .fillMaxHeight()
                                                            .background(Color(0xFFFFC107))
                                                    )
                                                }
                                                if (learnedCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(learnedCount.toFloat())
                                                            .fillMaxHeight()
                                                            .background(Color(0xFF4CAF50))
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                )
                                            }
                                        }

                                        // Row 3: Legend with exact values
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // New counter
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(5.dp).background(Color(0xFF2196F3), shape = CircleShape))
                                                Text(
                                                    text = if (uniqueCount > 0) "$newCount нов." else "-- нов.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            // Learning counter
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(5.dp).background(Color(0xFFFFC107), shape = CircleShape))
                                                Text(
                                                    text = if (uniqueCount > 0) "$learningCount учу" else "-- учу",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Learned counter
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Box(modifier = Modifier.size(5.dp).background(Color(0xFF4CAF50), shape = CircleShape))
                                                Text(
                                                    text = if (uniqueCount > 0) "$learnedCount знаю" else "-- знаю",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Reading View
                val chapter = activeChapter!!

                // Split chapter content into paragraphs/lines based on double-newline boundaries for proper book-like prose
                val paragraphs = remember(chapter.content) {
                    chapter.content.split(Regex("\\n\\s*\\n"))
                        .map { paragraph ->
                            paragraph.replace(Regex("\\r?\\n"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                        }
                        .filter { it.isNotEmpty() }
                        .map { "\u2003\u2003$it" }
                }

                val onSurfaceColor = MaterialTheme.colorScheme.onSurface

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Reading area lazy loaded column
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Chapter Title
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // 1.5 Batch chapter addition for Match Game
                        if (paragraphMode == ParagraphMode.ADD_TO_MATCH_GAME) {
                            item {
                                val chapterBatchState by viewModel.chapterBatchStudyState.collectAsState()
                                
                                val chapterUnknownWordsCount = remember(paragraphs, knownWordsSet, learningWordsSet, studyWordsSet) {
                                    val wordRegex = Regex("""(\p{L}+(?:[-'’`]\p{L}+)*)""")
                                    val set = mutableSetOf<String>()
                                    paragraphs.forEach { para ->
                                        wordRegex.findAll(para).forEach { match ->
                                            val clean = LinguisticEngine.tokenizeWord(match.value)
                                            if (clean.length > 1 &&
                                                !knownWordsSet.contains(clean) &&
                                                !learningWordsSet.contains(clean) &&
                                                !studyWordsSet.contains(clean)
                                            ) {
                                                set.add(clean)
                                            }
                                        }
                                    }
                                    set.size
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF9333EA).copy(alpha = 0.12f)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF9333EA).copy(alpha = 0.35f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Незнакомых в главе: $chapterUnknownWordsCount",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "В «Желаемое» («Найди пару»): ${studyWordsSet.size}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF9333EA)
                                            )
                                        }

                                        if (chapterBatchState.isProcessing) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                LinearProgressIndicator(
                                                    progress = {
                                                        if (chapterBatchState.totalWords > 0) {
                                                            chapterBatchState.processedWords.toFloat() / chapterBatchState.totalWords.toFloat()
                                                        } else 0f
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = Color(0xFF9333EA)
                                                )
                                                Text(
                                                    text = "Обработка пакета ${chapterBatchState.currentBatch} из ${chapterBatchState.totalBatches} (${chapterBatchState.processedWords} из ${chapterBatchState.totalWords} слов)...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF9333EA),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        } else if (chapterBatchState.isDone) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "✓ Добавлено ${chapterBatchState.processedWords} слов в «Желаемое»!",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF9333EA)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.resetChapterBatchStudyState() },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                                                }
                                            }
                                        } else if (chapterBatchState.errorMessage != null) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Ошибка: ${chapterBatchState.errorMessage}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                IconButton(
                                                    onClick = { viewModel.resetChapterBatchStudyState() },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Закрыть")
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        viewModel.addChapterWordsToStudy(context, paragraphs)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                                ) {
                                                    Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Вся глава в «Желаемое» ($chapterUnknownWordsCount)", color = Color.White)
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.addChapterWordsToStudy(context, paragraphs, startIndex = 0, paragraphCount = 10)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                                ) {
                                                    Text("След. 10 абз.")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Parsed & styled paragraphs (lazy loaded as they enter viewport!)
                        items(paragraphs.size) { index ->
                            val paragraphText = paragraphs[index]
                            val translationKey = "${activeChapter?.id ?: 0}_$index"
                            val paragraphTranslationsState by viewModel.paragraphTranslations.collectAsState()
                            val inlineParagraphTranslationsState by viewModel.inlineParagraphTranslations.collectAsState()
                            val fullTranslationState = paragraphTranslationsState[translationKey]
                            val inlineTranslationState = inlineParagraphTranslationsState[translationKey]
                            val primaryColor = MaterialTheme.colorScheme.primary

                            val paragraphAnnotatedString = remember(
                                paragraphText, knownWordsSet, learningWordsSet, studyWordsSet,
                                onSurfaceColor, learningColorCode, learnedColorCode,
                                paragraphMode, inlineTranslationState, primaryColor
                            ) {
                                buildAnnotatedString {
                                    val regex = Regex("""(\p{L}+(?:[-'’`]\p{L}+)*)""")
                                    var lastIndex = 0

                                    val inlineMap = if ((paragraphMode == ParagraphMode.UNKNOWN_WORDS_INLINE || paragraphMode == ParagraphMode.ADD_TO_MATCH_GAME) && inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Success) {
                                        inlineTranslationState.wordTranslations
                                    } else null

                                    regex.findAll(paragraphText).forEach { match ->
                                        // Append leading punctuation/spacing
                                        if (match.range.first > lastIndex) {
                                            append(paragraphText.substring(lastIndex, match.range.first))
                                        }
                                        
                                        val word = match.value
                                        val clean = LinguisticEngine.tokenizeWord(word)
                                        val isLearned = knownWordsSet.contains(clean)
                                        val isLearning = learningWordsSet.contains(clean)
                                        val isStudying = studyWordsSet.contains(clean)
                                        val isUnknown = !isLearned && !isLearning && !isStudying

                                        pushStringAnnotation(tag = "word", annotation = clean)
                                        
                                        withStyle(
                                            style = SpanStyle(
                                                color = when {
                                                    isLearned -> ThemeSettings.getLearnedHighlightColor(learnedColorCode)
                                                    isLearning -> ThemeSettings.getLearningHighlightColor(learningColorCode)
                                                    isStudying -> Color(0xFFBA68C8) // Gentle soft purple for Match Game
                                                    else -> onSurfaceColor
                                                },
                                                fontWeight = if (isLearned) FontWeight.Normal else FontWeight.Bold
                                            )
                                        ) {
                                            append(word)
                                        }
                                        pop()

                                        if (isUnknown && inlineMap != null) {
                                            val tr = inlineMap[clean] ?: inlineMap[clean.lowercase()] ?: inlineMap[word.lowercase()]
                                            if (!tr.isNullOrBlank()) {
                                                withStyle(
                                                    style = SpanStyle(
                                                        color = primaryColor,
                                                        fontSize = (readingFontSize * 0.82f).sp,
                                                        fontWeight = FontWeight.Medium,
                                                        fontStyle = FontStyle.Italic
                                                    )
                                                ) {
                                                    append(" [$tr]")
                                                }
                                            }
                                        }

                                        lastIndex = match.range.last + 1
                                    }
                                    if (lastIndex < paragraphText.length) {
                                        append(paragraphText.substring(lastIndex))
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    ClickableText(
                                        text = paragraphAnnotatedString,
                                        onClick = { offset ->
                                            paragraphAnnotatedString.getStringAnnotations(tag = "word", start = offset, end = offset)
                                                .firstOrNull()?.let { annotation ->
                                                    val cleanWord = annotation.item
                                                    if (wordClickMode == WordClickMode.PRONOUNCE || paragraphMode == ParagraphMode.UNKNOWN_WORDS_INLINE || paragraphMode == ParagraphMode.ADD_TO_MATCH_GAME) {
                                                        viewModel.speak(cleanWord, currentBook?.language ?: "de")
                                                    } else {
                                                        clickedWordDetails = cleanWord
                                                        val sentence = findSentenceAtOffset(paragraphText, offset)
                                                        val rawWord = findWordAtOffset(paragraphText, offset)
                                                        clickedWordRaw = rawWord
                                                        clickedWordSentence = sentence
                                                        if (isQuickAddModeEnabled) {
                                                            viewModel.fetchWordTranslationVariants(context, cleanWord, sentence)
                                                        } else {
                                                            viewModel.translateWordComplete(context, cleanWord, rawWord, sentence)
                                                        }
                                                    }
                                                }
                                        },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = Color(0xFF7CA17B), // Muted green for punctuation
                                            fontSize = readingFontSize.sp,
                                            lineHeight = (readingFontSize * 1.55f).sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("chapter_clickable_text")
                                    )

                                    if (paragraphMode == ParagraphMode.FULL_PARAGRAPH && fullTranslationState != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        when (fullTranslationState) {
                                            is com.example.ui.ParagraphTranslationUiState.Loading -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                }
                                            }
                                            is com.example.ui.ParagraphTranslationUiState.Success -> {
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 4.dp),
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                                ) {
                                                    Text(
                                                        text = fullTranslationState.translation,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = (readingFontSize * 0.9f).sp,
                                                            lineHeight = (readingFontSize * 1.35f).sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(12.dp)
                                                    )
                                                }
                                            }
                                            is com.example.ui.ParagraphTranslationUiState.Error -> {
                                                Text(
                                                    text = "Ошибка: ${fullTranslationState.message}",
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    } else if ((paragraphMode == ParagraphMode.UNKNOWN_WORDS_INLINE || paragraphMode == ParagraphMode.ADD_TO_MATCH_GAME) && inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Error) {
                                        Text(
                                            text = "Ошибка: ${inlineTranslationState.message}",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (paragraphMode == ParagraphMode.FULL_PARAGRAPH) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            if (fullTranslationState == null) {
                                                viewModel.translateParagraph(context, translationKey, paragraphText)
                                            } else {
                                                viewModel.toggleParagraphTranslation(translationKey)
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (fullTranslationState is com.example.ui.ParagraphTranslationUiState.Success) Icons.Default.Close else Icons.Default.Translate,
                                            contentDescription = "Перевести абзац",
                                            tint = if (fullTranslationState is com.example.ui.ParagraphTranslationUiState.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else if (paragraphMode == ParagraphMode.UNKNOWN_WORDS_INLINE) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Loading) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .size(36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                if (inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Success) {
                                                    viewModel.toggleInlineParagraphTranslation(translationKey)
                                                } else {
                                                    val wordRegex = Regex("""(\p{L}+(?:[-'’`]\p{L}+)*)""")
                                                    val unknownWords = wordRegex.findAll(paragraphText)
                                                        .map { LinguisticEngine.tokenizeWord(it.value) }
                                                        .filter { clean ->
                                                            clean.length > 1 &&
                                                            !knownWordsSet.contains(clean) &&
                                                            !learningWordsSet.contains(clean) &&
                                                            !studyWordsSet.contains(clean)
                                                        }
                                                        .distinct()
                                                        .toList()

                                                    if (unknownWords.isEmpty()) {
                                                        Toast.makeText(context, "В этом абзаце нет незнакомых слов", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        viewModel.translateParagraphUnknownWords(context, translationKey, paragraphText, unknownWords)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Success) Icons.Default.Close else Icons.Default.Translate,
                                                contentDescription = "Перевести незнакомые слова",
                                                tint = if (inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else if (paragraphMode == ParagraphMode.ADD_TO_MATCH_GAME) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Loading) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .size(36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                val wordRegex = Regex("""(\p{L}+(?:[-'’`]\p{L}+)*)""")
                                                val unknownWords = wordRegex.findAll(paragraphText)
                                                    .map { LinguisticEngine.tokenizeWord(it.value) }
                                                    .filter { clean ->
                                                        clean.length > 1 &&
                                                        !knownWordsSet.contains(clean) &&
                                                        !learningWordsSet.contains(clean) &&
                                                        !studyWordsSet.contains(clean)
                                                    }
                                                    .distinct()
                                                    .toList()

                                                if (unknownWords.isEmpty()) {
                                                    Toast.makeText(context, "В этом абзаце нет незнакомых слов", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    viewModel.addParagraphUnknownWordsToStudy(context, translationKey, paragraphText, unknownWords)
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Success) Icons.Default.Check else Icons.Default.Translate,
                                                contentDescription = "Добавить незнакомые слова в «Найди пару»",
                                                tint = if (inlineTranslationState is com.example.ui.InlineParagraphTranslationUiState.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Navigation controls at the end of chapter
                        item {
                            Spacer(modifier = Modifier.height(24.dp))

                            if (activeChapterIndex < currentChapters.size - 1) {
                                Button(
                                    onClick = {
                                        val nextIdx = activeChapterIndex + 1
                                        val savedScrollPos = currentBook?.let { b ->
                                            sharedPrefs.getInt("scroll_pos_book_${b.id}_chapter_${nextIdx}", 0)
                                        } ?: 0
                                        viewModel.selectChapter(nextIdx, savedScrollPos)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                        .testTag("next_chapter_button"),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Text("Следующая глава", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showChaptersList = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                        .testTag("back_to_chapters_button"),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Icon(Icons.Default.List, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Вернуться к оглавлению", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }

                    // Elegant micro-footer at the bottom of the reader screen
                    if (currentBookUiItem != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = if (currentBookUiItem.totalPages > 0) {
                                    currentBookUiItem.currentPage.toFloat() / currentBookUiItem.totalPages
                                } else 0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Страница ${currentBookUiItem.currentPage} из ${currentBookUiItem.totalPages} (${(currentBookUiItem.currentPage.toFloat() / currentBookUiItem.totalPages * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Глава ${activeChapterIndex + 1}/${currentChapters.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive word details dialog
    clickedWordDetails?.let { word ->
        val isLearned = knownWordsSet.contains(word)
        val isLearning = learningWordsSet.contains(word)
        val isNew = !isLearned && !isLearning

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isTablet = configuration.screenWidthDp >= 480

        androidx.compose.ui.window.Popup(
            alignment = if (isTablet) Alignment.Center else Alignment.BottomCenter,
            onDismissRequest = { 
                clickedWordDetails = null 
                viewModel.clearTranslationState()
            },
            properties = androidx.compose.ui.window.PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            val widthModifier = if (isTablet) {
                Modifier.widthIn(max = 480.dp).padding(24.dp)
            } else {
                Modifier.fillMaxWidth().padding(16.dp)
            }

            val maxHeight = (configuration.screenHeightDp * 0.75f).dp

            Box(
                modifier = Modifier
                    .then(if (!isTablet) Modifier.navigationBarsPadding() else Modifier)
                    .then(widthModifier)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .testTag("word_details_dialog"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = if (isTablet) BorderStroke(2.dp, Color(0xFF1976D2)) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        var showAddStudyWordFlow by remember { mutableStateOf(isQuickAddModeEnabled) }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = word,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            viewModel.speak(word, currentBook?.language ?: "de")
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Озвучить слово",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                if (!isQuickAddModeEnabled) {
                                    IconButton(
                                        onClick = { 
                                            showAddStudyWordFlow = !showAddStudyWordFlow
                                            if (showAddStudyWordFlow) {
                                                viewModel.fetchWordTranslationVariants(context, word, clickedWordSentence)
                                            } else {
                                                viewModel.clearVariantsTranslationState()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (showAddStudyWordFlow) Icons.Default.Close else Icons.Default.Add,
                                            contentDescription = "Добавить слово в желаемое («Найди пару»)",
                                            tint = Color(0xFF9333EA)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            clickedWordDetails = null
                                            viewModel.clearTranslationState()
                                            viewModel.clearVariantsTranslationState()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Закрыть",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            if (showAddStudyWordFlow) {
                                val variantsState by viewModel.variantsTranslationState.collectAsState()
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF9333EA).copy(alpha = 0.12f)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF9333EA).copy(alpha = 0.35f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Добавление в «Желаемое» («Найди пару»)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF9333EA)
                                        )
                                        
                                        when (val state = variantsState) {
                                            is VariantsUiState.Idle -> {
                                                Text(
                                                    text = "Инициализация...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            is VariantsUiState.Loading -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Color(0xFF9333EA)
                                                    )
                                                    Text(
                                                        text = "Загружаем варианты перевода...",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                            is VariantsUiState.Success -> {
                                                if (state.variants.isEmpty()) {
                                                    Text(
                                                        text = "Не удалось найти варианты перевода.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                } else {
                                                    var selectedVariants by remember { mutableStateOf(setOf<String>()) }
                                                    
                                                    Text(
                                                        text = "Выберите один или несколько вариантов:",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    
                                                    // Wrap of chip items dynamically using FlowRow
                                                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                                    androidx.compose.foundation.layout.FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        state.variants.forEach { variant ->
                                                            val isSelected = selectedVariants.contains(variant)
                                                            FilterChip(
                                                                selected = isSelected,
                                                                onClick = {
                                                                    selectedVariants = if (isSelected) {
                                                                        selectedVariants - variant
                                                                    } else {
                                                                        selectedVariants + variant
                                                                    }
                                                                },
                                                                label = { Text(variant) },
                                                                colors = FilterChipDefaults.filterChipColors(
                                                                    selectedContainerColor = Color(0xFF9333EA).copy(alpha = 0.2f),
                                                                    selectedLabelColor = Color(0xFF9333EA)
                                                                )
                                                            )
                                                        }
                                                    }
                                                    
                                                    Button(
                                                        onClick = {
                                                            if (selectedVariants.isNotEmpty()) {
                                                                viewModel.addStudyWord(word, selectedVariants.toList())
                                                                Toast.makeText(context, "Слово '$word' сохранено в «Желаемое» («Найди пару»)!", Toast.LENGTH_SHORT).show()
                                                                if (isQuickAddModeEnabled) {
                                                                    clickedWordDetails = null
                                                                    viewModel.clearTranslationState()
                                                                    viewModel.clearVariantsTranslationState()
                                                                } else {
                                                                    showAddStudyWordFlow = false
                                                                    viewModel.clearVariantsTranslationState()
                                                                }
                                                            } else {
                                                                Toast.makeText(context, "Выберите хотя бы один перевод", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                                    ) {
                                                        Text("Сохранить в «Желаемое»", color = Color.White)
                                                    }
                                                }
                                            }
                                            is VariantsUiState.Error -> {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = state.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                    Button(
                                                        onClick = { viewModel.fetchWordTranslationVariants(context, word, clickedWordSentence) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                                    ) {
                                                        Text("Повторить", color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        if (!isQuickAddModeEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Simple, clean and highly aesthetic horizontal status togglers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // New / "Новое" pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isNew) Color(0xFFE3F2FD) 
                                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        )
                                        .clickable {
                                            if (!isNew) {
                                                viewModel.removeKnownWordByValue(word)
                                            }
                                            clickedWordDetails = null
                                            viewModel.clearTranslationState()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                 ) {
                                     Text(
                                         text = "Новое",
                                         style = MaterialTheme.typography.labelMedium,
                                         fontWeight = FontWeight.Bold,
                                         color = if (isNew) Color(0xFF1976D2) 
                                                 else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                     )
                                 }

                                // Learning / "На изучении" pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isLearning) ThemeSettings.getLearningHighlightColor(learningColorCode).copy(alpha = 0.2f) 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        )
                                        .clickable {
                                            if (!isLearning) {
                                                viewModel.addWordToLearning(word)
                                            }
                                            clickedWordDetails = null
                                            viewModel.clearTranslationState()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "На изучении",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLearning) ThemeSettings.getLearningBadgeColor(learningColorCode) 
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }

                                // Learned / "Выучено" pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isLearned) ThemeSettings.getLearnedHighlightColor(learnedColorCode).copy(alpha = 0.2f) 
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        )
                                        .clickable {
                                            if (!isLearned) {
                                                viewModel.addWordToLearned(word)
                                            }
                                            clickedWordDetails = null
                                            viewModel.clearTranslationState()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "Выучено",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLearned) ThemeSettings.getLearnedBadgeColor(learnedColorCode) 
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val translationState by viewModel.translationState.collectAsState()
                            
                            when (val state = translationState) {
                                is TranslationUiState.Idle -> {
                                    Text(
                                        text = "Ожидание перевода...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                is TranslationUiState.Loading -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Переводим через $modelShortName...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                is TranslationUiState.Success -> {
                                    val translationFontSize by viewModel.translationFontSize.collectAsState()
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Dynamic main translation
                                        Text(
                                            text = state.wordTranslation,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontSize = (translationFontSize + 4f).sp,
                                                lineHeight = ((translationFontSize + 4f) * 1.3f).sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        
                                        // Deep AI integration context explanation
                                        if (state.explanation.isNotBlank() && state.explanation != "Объяснение недоступно") {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "Значение в контексте:",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = (translationFontSize - 2f).coerceAtLeast(10f).sp
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = state.explanation,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = translationFontSize.sp,
                                                        lineHeight = (translationFontSize * 1.4f).sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Context sentence
                                        if (state.sentenceTranslation.isNotBlank() && state.sentenceTranslation != "Перевод недоступен") {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "Перевод предложения:",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = (translationFontSize - 2f).coerceAtLeast(10f).sp
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Text(
                                                    text = state.sentenceTranslation,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = (translationFontSize + 2f).sp,
                                                        lineHeight = ((translationFontSize + 2f) * 1.4f).sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                                is TranslationUiState.Error -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        TextButton(
                                            onClick = {
                                                viewModel.translateWordComplete(context, word, clickedWordRaw, clickedWordSentence)
                                            },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Повторить запрос")
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
    }
}

// Private helper functions for interactive translation text lookups
private fun findSentenceAtOffset(text: String, offset: Int): String {
    if (text.isEmpty() || offset < 0 || offset >= text.length) return ""
    
    // Find previous boundary
    var start = offset
    val sentenceEndChars = setOf('.', '!', '?', '\n')
    while (start > 0) {
        if (sentenceEndChars.contains(text[start - 1])) {
            break
        }
        start--
    }
    
    // Find next boundary
    var end = offset
    while (end < text.length - 1) {
        if (sentenceEndChars.contains(text[end])) {
            end++
            break
        }
        end++
    }
    
    // Clean and return
    return text.substring(start, end).trim()
        .replace(Regex("\\s+"), " ")
}

private fun findWordAtOffset(text: String, offset: Int): String {
    if (text.isEmpty() || offset < 0 || offset >= text.length) return ""
    val regex = Regex("""\p{L}+(?:[-'’`]\p{L}+)*""")
    for (match in regex.findAll(text)) {
        if (offset in match.range) {
            return match.value
        }
    }
    return ""
}


