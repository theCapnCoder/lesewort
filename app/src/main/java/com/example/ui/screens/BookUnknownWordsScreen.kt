package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BookEntity
import com.example.ui.BookUnknownWord
import com.example.ui.MainViewModel
import com.example.ui.theme.ThemeSettings

enum class UnknownWordsSortType(val title: String) {
    FREQUENCY_DESC("Частота ↓"),
    FREQUENCY_ASC("Частота ↑"),
    ALPHABETICAL("Алфавит А-Я")
}

private fun formatFrequencyRu(count: Int): String {
    val lastTwo = count % 100
    val lastOne = count % 10
    return when {
        lastTwo in 11..19 -> "$count раз"
        lastOne in 2..4 -> "$count раза"
        else -> "$count раз"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookUnknownWordsScreen(
    book: BookEntity,
    unknownWords: List<BookUnknownWord>,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val learningColorCode by viewModel.learningColor.collectAsState()
    val highlightByFrequency by viewModel.highlightUnknownByFrequency.collectAsState()
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var searchQuery by remember { mutableStateOf("") }
    var sortType by remember { mutableStateOf(UnknownWordsSortType.FREQUENCY_DESC) }
    val listState = rememberLazyListState()

    BackHandler(enabled = true) {
        onBack()
    }

    // Filter and sort words
    val filteredWords = remember(unknownWords, searchQuery, sortType) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isEmpty()) {
            unknownWords
        } else {
            unknownWords.filter { it.word.lowercase().contains(query) || it.cleanWord.contains(query) }
        }

        when (sortType) {
            UnknownWordsSortType.FREQUENCY_DESC -> list.sortedByDescending { it.count }
            UnknownWordsSortType.FREQUENCY_ASC -> list.sortedBy { it.count }
            UnknownWordsSortType.ALPHABETICAL -> list.sortedBy { it.word.lowercase() }
        }
    }

    val totalOccurrences = remember(unknownWords) {
        unknownWords.sumOf { it.count }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Неизвестные слова",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${book.title} • ${unknownWords.size} слов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_unknown_words_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад к оглавлению"
                        )
                    }
                },
                actions = {
                    // Quick Toggle for frequency highlighting
                    IconButton(
                        onClick = {
                            val next = !highlightByFrequency
                            viewModel.setHighlightUnknownByFrequency(context, next)
                            Toast.makeText(
                                context,
                                if (next) "Подсветка по частоте включена" else "Подсветка по частоте выключена",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("btn_toggle_frequency_highlight_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gradient,
                            contentDescription = "Подсветка по частоте",
                            tint = if (highlightByFrequency) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("btn_clear_search_top")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить поиск")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("input_unknown_words_search"),
                placeholder = { Text("Поиск слова по книге...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("btn_clear_search_field")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            // Frequency Highlight Toggle Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("card_toggle_frequency_highlight"),
                colors = CardDefaults.cardColors(
                    containerColor = if (highlightByFrequency) {
                        Color(0xFF2196F3).copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (highlightByFrequency) Color(0xFF1976D2).copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextState = !highlightByFrequency
                            viewModel.setHighlightUnknownByFrequency(context, nextState)
                            Toast.makeText(
                                context,
                                if (nextState) "Подсветка по частоте включена в книге" else "Подсветка по частоте выключена",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (highlightByFrequency) Color(0xFF1976D2) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Gradient,
                                    contentDescription = null,
                                    tint = if (highlightByFrequency) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Подсветка по частотности",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (highlightByFrequency) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1976D2).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "ВКЛ",
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1976D2)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "1 раз — стандартный цвет, 2 раза — светло-голубой, 10+ раз — синий",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }
                    }

                    Switch(
                        checked = highlightByFrequency,
                        onCheckedChange = { nextState ->
                            viewModel.setHighlightUnknownByFrequency(context, nextState)
                            Toast.makeText(
                                context,
                                if (nextState) "Подсветка по частоте включена в книге" else "Подсветка по частоте выключена",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("switch_frequency_highlight"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1976D2)
                        )
                    )
                }
            }

            // Statistics summary & Sorting Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Count info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2196F3).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Найдено: ${filteredWords.size}" else "${unknownWords.size} новых",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2)
                        )
                    }

                    if (searchQuery.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "$totalOccurrences вхождений",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Sort options
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UnknownWordsSortType.entries.forEach { type ->
                        val isSelected = sortType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { sortType = type },
                            label = {
                                Text(
                                    text = type.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("sort_chip_${type.name}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main List or Empty State
            if (unknownWords.isEmpty()) {
                // Empty state: All words learned or known
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Все слова книги изучены!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "В этой книге нет неизвестных слов. Все слова либо находятся на изучении, либо уже выучены.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_empty_back_to_toc")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Вернуться в оглавление")
                        }
                    }
                }
            } else if (filteredWords.isEmpty()) {
                // Search returned no matches
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Слово не найдено",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "По запросу «$searchQuery» новых слов не найдено.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { searchQuery = "" }) {
                            Text("Сбросить поиск")
                        }
                    }
                }
            } else {
                // List of unknown words
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("unknown_words_lazy_column"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredWords,
                        key = { it.cleanWord }
                    ) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_unknown_word_${item.cleanWord}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Word and occurrence pill
                                val freqColor = if (highlightByFrequency) {
                                    ThemeSettings.getUnknownWordFrequencyColor(item.count, isDarkTheme)
                                } else null

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = item.word,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = freqColor ?: MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Occurrence frequency count badge
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = freqColor?.copy(alpha = 0.15f) ?: Color(0xFF2196F3).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, freqColor?.copy(alpha = 0.4f) ?: Color(0xFF2196F3).copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = formatFrequencyRu(item.count),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = freqColor ?: Color(0xFF1976D2)
                                        )
                                    }
                                }

                                // Fast action buttons
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Pronounce TTS button
                                    IconButton(
                                        onClick = {
                                            viewModel.speak(item.word, book.language)
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("btn_speak_${item.cleanWord}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Озвучить ${item.word}",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Mark as Learning (Status = 1)
                                    IconButton(
                                        onClick = {
                                            viewModel.addWordToLearning(item.cleanWord, book.language)
                                            Toast.makeText(
                                                context,
                                                "«${item.word}» перенесено в «На изучении»",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("btn_learning_${item.cleanWord}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkAdd,
                                            contentDescription = "Добавить «${item.word}» на изучение",
                                            tint = ThemeSettings.getLearningBadgeColor(learningColorCode),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Mark as Learned (Status = 2)
                                    IconButton(
                                        onClick = {
                                            viewModel.addWordToLearned(item.cleanWord, book.language)
                                            Toast.makeText(
                                                context,
                                                "«${item.word}» отмечено как выученное",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("btn_learned_${item.cleanWord}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircleOutline,
                                            contentDescription = "Отметить «${item.word}» как выученное",
                                            tint = Color(0xFF2E7D32),
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
