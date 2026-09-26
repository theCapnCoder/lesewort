package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.data.parser.BookParser
import com.example.ui.BookUiItem
import com.example.ui.MainViewModel
import com.example.ui.ScreenTab
import com.example.ui.SortType
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (ScreenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryItems by viewModel.libraryItems.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val currentSort by viewModel.sortBy.collectAsState()
    val isStatsRefreshing by viewModel.isStatsRefreshing.collectAsState()

    var showDeleteDialogForBook by remember { mutableStateOf<BookUiItem?>(null) }
    var selectedBookIds by remember { mutableStateOf(emptySet<Int>()) }
    var showMultiDeleteDialog by remember { mutableStateOf(false) }
    var showGroupDeleteDialogForBooks by remember { mutableStateOf<List<BookUiItem>?>(null) }
    var groupDeleteName by remember { mutableStateOf("") }
    var sortingMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedBookIds.isNotEmpty()) {
        selectedBookIds = emptySet()
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            viewModel.importMultipleBooks(context, uris) { success, failed ->
                val message = if (failed == 0) {
                    if (success == 1) "Книга успешно импортирована!" else "Успешно импортировано книг: %d!".format(success)
                } else if (success == 0) {
                    "Не удалось импортировать книги (%d ошибок).".format(failed)
                } else {
                    "Импорт завершен: успешно %d, ошибок %d.".format(success, failed)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selectedBookIds.isNotEmpty()) {
                        IconButton(onClick = { selectedBookIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить выбор")
                        }
                    }
                },
                title = {
                    if (selectedBookIds.isNotEmpty()) {
                        Text(
                            "Выбрано: ${selectedBookIds.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Column {
                            Text(
                                "Моя библиотека",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Всего книг: ${libraryItems.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (selectedBookIds.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                showMultiDeleteDialog = true
                            },
                            modifier = Modifier.testTag("delete_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить выбранные книги",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        val currentLang by viewModel.currentLanguage.collectAsState()
                        IconButton(
                            onClick = {
                                val targetLang = when (currentLang) {
                                    "de" -> "en"
                                    "en" -> "fr"
                                    else -> "de"
                                }
                                viewModel.setCurrentLanguage(context, targetLang)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("lang_toggle_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            ) {
                                when (currentLang) {
                                    "de" -> GermanFlagIcon()
                                    "fr" -> FrenchFlagIcon()
                                    else -> EnglishFlagIcon()
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { sortingMenuExpanded = true },
                                modifier = Modifier.testTag("sort_menu_button")
                            ) {
                                Icon(Icons.Default.List, contentDescription = "Сортировка")
                            }
                            if (sortingMenuExpanded) {
                                AlertDialog(
                                    onDismissRequest = { sortingMenuExpanded = false },
                                    title = {
                                        Text(
                                            text = "Сортировка и группировка",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    text = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "Порядок сортировки:",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                SortType.values().forEach { sortType ->
                                                    val isSelected = currentSort == sortType
                                                    val icon = when (sortType) {
                                                        SortType.NEWEST -> Icons.Default.Schedule
                                                        SortType.COMPLEXITY_ASC -> Icons.Default.ArrowUpward
                                                        SortType.COMPLEXITY_DESC -> Icons.Default.ArrowDownward
                                                        SortType.DENSITY_ASC -> Icons.Default.ArrowUpward
                                                        SortType.DENSITY_DESC -> Icons.Default.ArrowDownward
                                                        SortType.WORDS_ASC -> Icons.Default.ArrowUpward
                                                        SortType.WORDS_DESC -> Icons.Default.ArrowDownward
                                                    }
                                                    Surface(
                                                        onClick = { viewModel.changeSort(sortType) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isSelected) 
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                                        else 
                                                            Color.Transparent,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = null,
                                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = sortType.displayName,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            if (isSelected) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = "Выбрано",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            )

                                            Text(
                                                text = "Группировка:",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            val isGrouped by viewModel.groupByNewWords.collectAsState()
                                            Surface(
                                                onClick = { viewModel.setGroupByNewWords(context, !isGrouped) },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                color = if (isGrouped) 
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
                                                else 
                                                    MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Layers,
                                                            contentDescription = null,
                                                            tint = if (isGrouped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Column {
                                                            Text(
                                                                text = "Группировать по новым словам",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = "По числу незнакомых слов в книге",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Switch(
                                                        checked = isGrouped,
                                                        onCheckedChange = { viewModel.setGroupByNewWords(context, it) }
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { sortingMenuExpanded = false }
                                        ) {
                                            Text("Готово", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                )
                            }
                        }
                }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { fileImportLauncher.launch("*/*") },
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("import_book_fab"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Импортировать книгу")
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isStatsRefreshing,
            onRefresh = { viewModel.refreshBookStatistics() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val columns = when {
                    maxWidth < 500.dp -> 1
                    maxWidth < 840.dp -> 2
                    else -> 3
                }
                if (isStatsRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .combinedClickable(
                            enabled = true,
                            onClick = {},
                            onLongClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(24.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Импорт книг",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val progress = importProgress
                            if (progress != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = progress.currentBookTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Text(
                                    text = "Успешно: ${progress.successfulCount} | Ошибок: ${progress.failedCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                val fraction = if (progress.totalBooks > 0) {
                                    progress.currentBookIndex.toFloat() / progress.totalBooks.toFloat()
                                } else {
                                    0f
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Обработка страницы/файла: ${progress.currentBookIndex} из ${progress.totalBooks}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${(fraction * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = fraction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                )

                                Text(
                                    text = progress.stage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                CircularProgressIndicator()
                                Text(
                                    text = "Синтаксический анализ и занесение в базу данных...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (libraryItems.isEmpty()) {
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
                        "Ваша библиотека пуста",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Нажмите кнопку «+» внизу экрана, чтобы импортировать файл книги (.epub или .txt).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                val groupByNewWords by viewModel.groupByNewWords.collectAsState()
                val recentlyOpened = remember(libraryItems) {
                    libraryItems
                        .filter { it.book.lastOpened > 0L }
                        .sortedByDescending { it.book.lastOpened }
                        .take(3)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("books_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (recentlyOpened.isNotEmpty()) {
                        item(key = "recently_opened_books_section", span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Продолжить чтение",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(recentlyOpened, key = { "recent_${it.book.id}" }) { item ->
                                        Card(
                                            modifier = Modifier
                                                .width(180.dp)
                                                .height(105.dp),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                            ),
                                            onClick = {
                                                viewModel.selectBook(item.book.id)
                                                onNavigateToTab(ScreenTab.READER)
                                            }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = item.book.title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = item.book.author,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    
                                                    Text(
                                                        text = "Страница ${item.currentPage}/${item.totalPages}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                                    )
                                                }
                                                
                                                val progress = if (item.totalPages > 0) {
                                                    item.currentPage.toFloat() / item.totalPages
                                                } else 0f
                                                
                                                LinearProgressIndicator(
                                                    progress = progress,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .align(Alignment.BottomCenter),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                    if (groupByNewWords) {
                        val grouped = libraryItems.groupBy { item ->
                            WORD_GROUPS.first { item.newWordsCount >= it.min && item.newWordsCount <= it.max }
                        }.toSortedMap(compareBy { it.min })

                        grouped.forEach { (group, booksInGroup) ->
                            item(key = "group_header_${group.min}", span = { GridItemSpan(maxLineSpan) }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (selectedBookIds.isNotEmpty()) {
                                                    IconButton(
                                                        onClick = {
                                                            showGroupDeleteDialogForBooks = booksInGroup
                                                            groupDeleteName = group.name
                                                        },
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .testTag("delete_group_button_${group.min}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Удалить группу",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                     }
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${booksInGroup.size}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = group.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            gridItems(booksInGroup, key = { "grouped_${group.min}_${it.book.id}" }) { item ->
                                val isSelected = selectedBookIds.contains(item.book.id)
                                val leftBorderColor = when (group.min) {
                                    0 -> Color(0xFF4CAF50)
                                    201 -> Color(0xFF8BC34A)
                                    401 -> Color(0xFFFFC107)
                                    601 -> Color(0xFFFF9800)
                                    801 -> Color(0xFFFF5722)
                                    1001 -> Color(0xFFF44336)
                                    2001 -> Color(0xFFE91E63)
                                    else -> Color(0xFF9C27B0)
                                }
                                
                                val cardBgColor = when (group.min) {
                                    0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                                    201 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.08f)
                                    401 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.08f)
                                    601 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    801 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
                                    1001 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.14f)
                                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                }

                                BookItemCard(
                                    item = item,
                                    isSelected = isSelected,
                                    context = context,
                                    leftBorderColor = leftBorderColor,
                                    cardBgColor = cardBgColor,
                                    columns = columns,
                                    onClick = {
                                        if (selectedBookIds.isNotEmpty()) {
                                            selectedBookIds = if (isSelected) {
                                                selectedBookIds - item.book.id
                                            } else {
                                                selectedBookIds + item.book.id
                                            }
                                        } else {
                                            viewModel.selectBook(item.book.id)
                                            onNavigateToTab(ScreenTab.READER)
                                        }
                                    },
                                    onLongClick = {
                                        selectedBookIds = if (isSelected) {
                                            selectedBookIds - item.book.id
                                        } else {
                                            selectedBookIds + item.book.id
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        gridItems(libraryItems, key = { it.book.id }) { item ->
                            val isSelected = selectedBookIds.contains(item.book.id)
                            BookItemCard(
                                item = item,
                                isSelected = isSelected,
                                context = context,
                                leftBorderColor = null,
                                cardBgColor = MaterialTheme.colorScheme.surface,
                                columns = columns,
                                onClick = {
                                    if (selectedBookIds.isNotEmpty()) {
                                        selectedBookIds = if (isSelected) {
                                            selectedBookIds - item.book.id
                                        } else {
                                            selectedBookIds + item.book.id
                                        }
                                    } else {
                                        viewModel.selectBook(item.book.id)
                                        onNavigateToTab(ScreenTab.READER)
                                    }
                                },
                                onLongClick = {
                                    selectedBookIds = if (isSelected) {
                                        selectedBookIds - item.book.id
                                    } else {
                                        selectedBookIds + item.book.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Delete confirmation dialog
    showDeleteDialogForBook?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogForBook = null },
            title = { Text("Удалить книгу") },
            text = { Text("Вы действительно хотите удалить книгу «${item.book.title}» из вашей библиотеки? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(item.book.id, context)
                        showDeleteDialogForBook = null
                        Toast.makeText(context, "Книга удалена", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogForBook = null }) {
                    Text("Отмена")
                }
            },
            modifier = Modifier.testTag("delete_book_dialog")
        )
    }

    // Multiple books delete confirmation dialog
    if (showMultiDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteDialog = false },
            title = { Text("Удалить книги") },
            text = { Text("Вы действительно хотите удалить выбранные книги (${selectedBookIds.size} шт.) из вашей библиотеки? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBooks(selectedBookIds, context)
                        selectedBookIds = emptySet()
                        showMultiDeleteDialog = false
                        Toast.makeText(context, "Выбранные книги удалены", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteDialog = false }) {
                    Text("Отмена")
                }
            },
            modifier = Modifier.testTag("multi_delete_book_dialog")
        )
    }

    // Group delete confirmation dialog
    showGroupDeleteDialogForBooks?.let { itemsToDelete ->
        AlertDialog(
            onDismissRequest = { showGroupDeleteDialogForBooks = null },
            title = { Text("Удалить группу") },
            text = { Text("Вы действительно хотите удалить все книги в группе «$groupDeleteName» (${itemsToDelete.size} шт.)? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = itemsToDelete.map { it.book.id }.toSet()
                        viewModel.deleteBooks(idsToDelete, context)
                        selectedBookIds = selectedBookIds - idsToDelete
                        showGroupDeleteDialogForBooks = null
                        Toast.makeText(context, "Группа книг удалена", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить всё")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDeleteDialogForBooks = null }) {
                    Text("Отмена")
                }
            },
            modifier = Modifier.testTag("group_delete_book_dialog")
        )
    }
}

@Composable
fun GermanFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF000000)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFF0000)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFFCC00)))
        }
    }
}

@Composable
fun EnglishFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // 1. Blue background
            drawRect(color = Color(0xFF00247D))
            
            // 2. White diagonals
            val diagStroke = h * 0.15f
            val redDiagStroke = h * 0.06f
            
            drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, h), strokeWidth = diagStroke)
            drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(0f, h), end = androidx.compose.ui.geometry.Offset(w, 0f), strokeWidth = diagStroke)
            
            // 3. Red diagonals
            drawLine(Color(0xFFCF142B), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(w, h), strokeWidth = redDiagStroke)
            drawLine(Color(0xFFCF142B), start = androidx.compose.ui.geometry.Offset(0f, h), end = androidx.compose.ui.geometry.Offset(w, 0f), strokeWidth = redDiagStroke)
            
            // 4. White cross
            val crossWidth = w * 0.22f
            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset((w - crossWidth) / 2f, 0f), size = androidx.compose.ui.geometry.Size(crossWidth, h))
            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(0f, (h - crossWidth * (h/w)) / 2f), size = androidx.compose.ui.geometry.Size(w, crossWidth * (h/w)))
            
            // 5. Red cross
            val redCrossWidth = w * 0.12f
            drawRect(Color(0xFFCF142B), topLeft = androidx.compose.ui.geometry.Offset((w - redCrossWidth) / 2f, 0f), size = androidx.compose.ui.geometry.Size(redCrossWidth, h))
            drawRect(Color(0xFFCF142B), topLeft = androidx.compose.ui.geometry.Offset(0f, (h - redCrossWidth * (h/w)) / 2f), size = androidx.compose.ui.geometry.Size(w, redCrossWidth * (h/w)))
        }
    }
}

@Composable
fun FrenchFlagIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(26.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF002395)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFFFFF)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFED2939)))
        }
    }
}

data class WordGroup(
    val min: Int,
    val max: Int,
    val name: String,
    val description: String
)

val WORD_GROUPS = listOf(
    WordGroup(0, 200, "До 200 новых слов", "Простой уровень, отлично для разминки 🟢"),
    WordGroup(201, 400, "201 – 400 новых слов", "Легкий уровень, комфортное чтение 🟢"),
    WordGroup(401, 600, "401 – 600 новых слов", "Средний уровень, умеренная сложность 🟡"),
    WordGroup(601, 800, "601 – 800 новых слов", "Умеренно сложный уровень, новые фразы 🟡"),
    WordGroup(801, 1000, "801 – 1000 новых слов", "Продвинутый уровень, требует внимания 🟠"),
    WordGroup(1001, 2000, "1001 – 2000 новых слов", "Высокая сложность, большой объем новой лексики 🟠"),
    WordGroup(2001, 5000, "2001 – 5000 новых слов", "Очень высокий барьер, серьезное испытание 🔴"),
    WordGroup(5001, Int.MAX_VALUE, "Более 5000 новых слов", "Профессиональный уровень, максимум новой лексики 🟣")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItemCard(
    item: BookUiItem,
    isSelected: Boolean,
    context: android.content.Context,
    leftBorderColor: Color?,
    cardBgColor: Color,
    columns: Int = 1,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val book = item.book
    val complexityText = item.stats?.let {
        "Сложность: %.1f%%".format(it.complexityVolumePercent)
    } ?: "Вычисление сложности..."

    val comprehensibilityText = item.stats?.let {
        "Понятно по объему: %.1f%%".format(it.comprehensibleVolumePercent)
    } ?: ""

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_item_${book.id}")
            .then(
                if (isSelected) {
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else cardBgColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (leftBorderColor != null) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(leftBorderColor)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(if (columns > 1) 8.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (columns > 1) 10.dp else 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left Side: Cover Image with bottom statistics overlays
                val coverWidth = if (columns > 1) 100.dp else 110.dp
                val coverHeight = if (columns > 1) 150.dp else 165.dp
                Column(
                    modifier = Modifier.width(coverWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.ui.components.BookCoverView(
                        bookId = book.id,
                        title = book.title,
                        author = book.author,
                        filePath = book.filePath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(coverHeight)
                    )

                    // Statistics card rendered below the book cover for ALL devices (mobile and tablet)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Blue Circle: count of new words
                                val blueCount = item.newWordsCount
                                val isDoubleBlue = blueCount >= 100
                                val blueText = if (blueCount > 999) "${blueCount / 100 / 10.0}k" else "$blueCount"
                                
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                        .background(
                                            Color(0xFF2196F3), 
                                            shape = if (isDoubleBlue) RoundedCornerShape(10.dp) else CircleShape
                                        )
                                        .padding(horizontal = if (isDoubleBlue) 4.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = blueText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }

                                // Yellow Circle: count of learning words
                                val yellowCount = item.learningWordsCount
                                val isDoubleYellow = yellowCount >= 100
                                val yellowText = if (yellowCount > 999) "${yellowCount / 100 / 10.0}k" else "$yellowCount"
                                
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                        .background(
                                            Color(0xFFFFC107), 
                                            shape = if (isDoubleYellow) RoundedCornerShape(10.dp) else CircleShape
                                        )
                                        .padding(horizontal = if (isDoubleYellow) 4.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = yellowText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.Black,
                                        maxLines = 1
                                    )
                                }

                                // Green Circle: count of learned words
                                val greenCount = item.learnedWordsCount
                                val isDoubleGreen = greenCount >= 100
                                val greenText = if (greenCount > 999) "${greenCount / 100 / 10.0}k" else "$greenCount"
                                
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                        .background(
                                            Color(0xFF4CAF50), 
                                            shape = if (isDoubleGreen) RoundedCornerShape(10.dp) else CircleShape
                                        )
                                        .padding(horizontal = if (isDoubleGreen) 4.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = greenText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }

                            val complexityPercent = item.stats?.complexityVolumePercent ?: 0.0
                            Text(
                                text = "Сложность: %.1f%%".format(complexityPercent),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Right Side: Meta information and content description
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = book.title,
                            style = if (columns > 1) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = book.author,
                            style = if (columns > 1) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val unfamiliarPercent = if (book.uniqueWordsCount > 0) {
                        (item.newWordsCount.toDouble() / book.uniqueWordsCount) * 100.0
                    } else 0.0

                    Text(
                        text = "Незнакомые слова: %.1f%%".format(unfamiliarPercent),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (comprehensibilityText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Понимание книги: ${comprehensibilityText.replace("Понятно по объему: ", "")}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Уникальных слов: ${book.uniqueWordsCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Всего слов: ${book.totalWords}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val lexicalDensity = if (book.totalWords > 0) {
                        (book.uniqueWordsCount.toDouble() / book.totalWords) * 100.0
                    } else 0.0

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Лексическая плотность: %.1f%%".format(lexicalDensity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Indicator rows ( swapped order: "Прочитано" then "Глава" )
                    val progress = if (item.totalPages > 0) {
                        item.currentPage.toFloat() / item.totalPages
                    } else 0f
                    val progressPercent = (progress * 100).toInt()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "Прочитано: $progressPercent%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = if (item.totalChapters > 0) {
                                (book.currentChapterIndex + 1).toFloat() / item.totalChapters
                            } else 0f,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "Страница ${item.currentPage}/${item.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
