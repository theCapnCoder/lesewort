package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DayWordStats
import com.example.ui.MainViewModel
import com.example.ui.MonthWordStats
import com.example.ui.YearWordStats
import java.text.SimpleDateFormat
import java.util.*

enum class StatsPeriod(val title: String, val icon: ImageVector) {
    DAYS("Дни", Icons.Default.Today),
    MONTHS("Месяцы", Icons.Default.CalendarMonth),
    YEARS("Годы", Icons.Default.DateRange)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordStatsView(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayStats by viewModel.dayWordStats.collectAsState()
    val monthStats by viewModel.monthWordStats.collectAsState()
    val yearStats by viewModel.yearWordStats.collectAsState()

    val currentLang by viewModel.currentLanguage.collectAsState()
    val statsFilterAllLanguages by viewModel.statsFilterAllLanguages.collectAsState()
    val learningColorCode by viewModel.learningColor.collectAsState()
    val learnedColorCode by viewModel.learnedColor.collectAsState()

    val learningColor = remember(learningColorCode) {
        try {
            Color(android.graphics.Color.parseColor("#$learningColorCode"))
        } catch (e: Exception) {
            Color(0xFFE65100)
        }
    }

    val learnedColor = remember(learnedColorCode) {
        try {
            Color(android.graphics.Color.parseColor("#$learnedColorCode"))
        } catch (e: Exception) {
            Color(0xFF4CAF50)
        }
    }

    // Selected period: Days, Months, Years
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.DAYS) }

    // Collapsed by default - no auto-expansion on entry! Words only open upon tap.
    var expandedDayKeys by remember { mutableStateOf(setOf<String>()) }
    var expandedMonthKeys by remember { mutableStateOf(setOf<String>()) }
    var expandedYearKeys by remember { mutableStateOf(setOf<String>()) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Today / Current Month / Current Year Keys
    val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val currentMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }
    val currentYearKey = remember { SimpleDateFormat("yyyy", Locale.getDefault()).format(Date()) }

    val todayStats = remember(dayStats, todayKey) {
        dayStats.find { it.dateKey == todayKey }
    }
    val currentMonthStats = remember(monthStats, currentMonthKey) {
        monthStats.find { it.monthKey == currentMonthKey }
    }
    val currentYearStats = remember(yearStats, currentYearKey) {
        yearStats.find { it.yearKey == currentYearKey }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Статистика слов",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (statsFilterAllLanguages) "Все языки" else "Язык: ${currentLang.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("word_stats_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    // Filter: Current Language vs All Languages
                    FilterChip(
                        selected = !statsFilterAllLanguages,
                        onClick = { viewModel.setStatsFilterAllLanguages(!statsFilterAllLanguages) },
                        label = {
                            Text(
                                text = if (!statsFilterAllLanguages) currentLang.uppercase() else "Все",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.padding(end = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (dayStats.any { it.totalCount > 0 }) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_word_stats_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Очистить статистику",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Period Selector: Дни | Месяцы | Годы
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
            ) {
                StatsPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsPeriod.entries.size),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = period.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = period.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Minimalist Summary Card - dynamically reflects the chosen period (resets per day/month/year)
            PeriodSummaryCard(
                period = selectedPeriod,
                todayStats = todayStats,
                monthStats = currentMonthStats,
                yearStats = currentYearStats,
                learningColor = learningColor,
                learnedColor = learnedColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Main Content depending on Selected Period
            when (selectedPeriod) {
                StatsPeriod.DAYS -> {
                    if (dayStats.isEmpty()) {
                        EmptyStatsPlaceholder(message = "Добавляйте слова на изучение или отмечайте выученными — здесь появится статистика по дням.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                        ) {
                            items(dayStats, key = { it.dateKey }) { day ->
                                val isExpanded = expandedDayKeys.contains(day.dateKey)
                                DayWordStatsCard(
                                    day = day,
                                    isExpanded = isExpanded,
                                    learningColor = learningColor,
                                    learnedColor = learnedColor,
                                    onToggleExpand = {
                                        expandedDayKeys = if (isExpanded) {
                                            expandedDayKeys - day.dateKey
                                        } else {
                                            expandedDayKeys + day.dateKey
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                StatsPeriod.MONTHS -> {
                    if (monthStats.isEmpty()) {
                        EmptyStatsPlaceholder(message = "Статистика активности за месяцы появится по мере чтения и добавления слов.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                        ) {
                            items(monthStats, key = { it.monthKey }) { month ->
                                val isExpanded = expandedMonthKeys.contains(month.monthKey)
                                MonthWordStatsCard(
                                    month = month,
                                    isExpanded = isExpanded,
                                    expandedDayKeys = expandedDayKeys,
                                    learningColor = learningColor,
                                    learnedColor = learnedColor,
                                    onToggleExpand = {
                                        expandedMonthKeys = if (isExpanded) {
                                            expandedMonthKeys - month.monthKey
                                        } else {
                                            expandedMonthKeys + month.monthKey
                                        }
                                    },
                                    onToggleDayExpand = { dayKey ->
                                        expandedDayKeys = if (expandedDayKeys.contains(dayKey)) {
                                            expandedDayKeys - dayKey
                                        } else {
                                            expandedDayKeys + dayKey
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                StatsPeriod.YEARS -> {
                    if (yearStats.isEmpty()) {
                        EmptyStatsPlaceholder(message = "Статистика активности за годы появится по мере изучения слов.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                        ) {
                            items(yearStats, key = { it.yearKey }) { year ->
                                val isExpanded = expandedYearKeys.contains(year.yearKey)
                                YearWordStatsCard(
                                    year = year,
                                    isExpanded = isExpanded,
                                    expandedMonthKeys = expandedMonthKeys,
                                    expandedDayKeys = expandedDayKeys,
                                    learningColor = learningColor,
                                    learnedColor = learnedColor,
                                    onToggleExpand = {
                                        expandedYearKeys = if (isExpanded) {
                                            expandedYearKeys - year.yearKey
                                        } else {
                                            expandedYearKeys + year.yearKey
                                        }
                                    },
                                    onToggleMonthExpand = { monthKey ->
                                        expandedMonthKeys = if (expandedMonthKeys.contains(monthKey)) {
                                            expandedMonthKeys - monthKey
                                        } else {
                                            expandedMonthKeys + monthKey
                                        }
                                    },
                                    onToggleDayExpand = { dayKey ->
                                        expandedDayKeys = if (expandedDayKeys.contains(dayKey)) {
                                            expandedDayKeys - dayKey
                                        } else {
                                            expandedDayKeys + dayKey
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

    // Confirmation dialog for clearing word stats history
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(text = "Очистить статистику слов?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Журнал добавления и изучения слов будет очищен. Сами слова останутся в словаре.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearWordActivityHistory()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Top Summary Card that resets per day, month, or year depending on the active period view.
 */
@Composable
private fun PeriodSummaryCard(
    period: StatsPeriod,
    todayStats: DayWordStats?,
    monthStats: MonthWordStats?,
    yearStats: YearWordStats?,
    learningColor: Color,
    learnedColor: Color,
    modifier: Modifier = Modifier
) {
    val periodTitle = when (period) {
        StatsPeriod.DAYS -> "Сводка за сегодня"
        StatsPeriod.MONTHS -> "Сводка за этот месяц"
        StatsPeriod.YEARS -> "Сводка за этот год"
    }

    val periodSubtitle = when (period) {
        StatsPeriod.DAYS -> {
            val fullDateFormatter = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
            fullDateFormatter.format(Date())
        }
        StatsPeriod.MONTHS -> {
            monthStats?.monthLabel ?: SimpleDateFormat("LLLL yyyy", Locale("ru")).format(Date()).replaceFirstChar { it.uppercase() }
        }
        StatsPeriod.YEARS -> {
            yearStats?.yearLabel ?: "${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())} год"
        }
    }

    val metric1Label = when (period) {
        StatsPeriod.DAYS -> "Активность"
        StatsPeriod.MONTHS -> "Активных дней"
        StatsPeriod.YEARS -> "Активных дней"
    }

    val metric1Value = when (period) {
        StatsPeriod.DAYS -> if ((todayStats?.totalCount ?: 0) > 0) "Активен" else "0 слов"
        StatsPeriod.MONTHS -> "${monthStats?.activeDaysCount ?: 0}"
        StatsPeriod.YEARS -> "${yearStats?.activeDaysCount ?: 0}"
    }

    val learningCount = when (period) {
        StatsPeriod.DAYS -> todayStats?.learningCount ?: 0
        StatsPeriod.MONTHS -> monthStats?.learningCount ?: 0
        StatsPeriod.YEARS -> yearStats?.learningCount ?: 0
    }

    val learnedCount = when (period) {
        StatsPeriod.DAYS -> todayStats?.learnedCount ?: 0
        StatsPeriod.MONTHS -> monthStats?.learnedCount ?: 0
        StatsPeriod.YEARS -> yearStats?.learnedCount ?: 0
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = periodTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = periodSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryMetricItem(
                    label = metric1Label,
                    value = metric1Value,
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = if (period == StatsPeriod.DAYS) Icons.Default.TrendingUp else Icons.Default.CalendarToday
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(34.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                SummaryMetricItem(
                    label = "На изучение",
                    value = if (learningCount > 0) "+$learningCount" else "0",
                    accentColor = learningColor,
                    icon = Icons.Default.School
                )

                VerticalDivider(
                    modifier = Modifier
                        .height(34.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                SummaryMetricItem(
                    label = "Выучено",
                    value = if (learnedCount > 0) "+$learnedCount" else "0",
                    accentColor = learnedColor,
                    icon = Icons.Default.CheckCircle
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricItem(
    label: String,
    value: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Day Card - Collapsed by default. Tapping reveals word chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayWordStatsCard(
    day: DayWordStats,
    isExpanded: Boolean,
    learningColor: Color,
    learnedColor: Color,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_day_rotation"
    )

    val formattedDateLabel = remember(day.dateKey, day.timestamp) {
        formatDayLabel(day.dateKey, day.timestamp)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Date and Expand indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formattedDateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (day.totalCount > 0) "+${day.totalCount} слов" else "0 слов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotationState),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stats Badges Row: Learning and Learned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge: Learning words
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(learningColor.copy(alpha = 0.12f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = learningColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = if (day.learningCount > 0) "+${day.learningCount}" else "0",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = learningColor
                            )
                            Text(
                                text = "На изучение",
                                style = MaterialTheme.typography.labelSmall,
                                color = learningColor.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Badge: Learned words
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(learnedColor.copy(alpha = 0.12f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = learnedColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = if (day.learnedCount > 0) "+${day.learnedCount}" else "0",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = learnedColor
                            )
                            Text(
                                text = "Выучено",
                                style = MaterialTheme.typography.labelSmall,
                                color = learnedColor.copy(alpha = 0.85f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Expanded content: Words chips
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    if (day.totalCount == 0) {
                        Text(
                            text = "В этот день новые слова пока не добавлялись",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Learning words list
                    if (day.learningWords.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Добавлено на изучение (${day.learningWords.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = learningColor
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                day.learningWords.forEach { word ->
                                    WordChip(
                                        word = word,
                                        backgroundColor = learningColor.copy(alpha = 0.12f),
                                        textColor = learningColor
                                    )
                                }
                            }
                        }
                    }

                    // Learned words list
                    if (day.learnedWords.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Выучено слов (${day.learnedWords.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = learnedColor
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                day.learnedWords.forEach { word ->
                                    WordChip(
                                        word = word,
                                        backgroundColor = learnedColor.copy(alpha = 0.12f),
                                        textColor = learnedColor
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

/**
 * Month Card - Collapsed by default.
 * Tapping reveals nested list of active days and month word summary.
 */
@Composable
fun MonthWordStatsCard(
    month: MonthWordStats,
    isExpanded: Boolean,
    expandedDayKeys: Set<String>,
    learningColor: Color,
    learnedColor: Color,
    onToggleExpand: () -> Unit,
    onToggleDayExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_month_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Month Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = month.monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatDaysCount(month.activeDaysCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotationState),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Month Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge: Active days
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Column {
                        Text(
                            text = "${month.activeDaysCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Активных дней",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Badge: Learning words
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(learningColor.copy(alpha = 0.12f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Column {
                        Text(
                            text = if (month.learningCount > 0) "+${month.learningCount}" else "0",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = learningColor
                        )
                        Text(
                            text = "На изучение",
                            style = MaterialTheme.typography.labelSmall,
                            color = learningColor.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Badge: Learned words
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(learnedColor.copy(alpha = 0.12f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Column {
                        Text(
                            text = if (month.learnedCount > 0) "+${month.learnedCount}" else "0",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = learnedColor
                        )
                        Text(
                            text = "Выучено",
                            style = MaterialTheme.typography.labelSmall,
                            color = learnedColor.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Expanded: Nested Days List
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    val activeDays = month.days.filter { it.totalCount > 0 }
                    if (activeDays.isEmpty()) {
                        Text(
                            text = "В этом месяце пока нет дней с добавленными словами",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Дни с активностью (${activeDays.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Nested Days
                        activeDays.forEach { day ->
                            val isDayExpanded = expandedDayKeys.contains(day.dateKey)
                            DayWordStatsCard(
                                day = day,
                                isExpanded = isDayExpanded,
                                learningColor = learningColor,
                                learnedColor = learnedColor,
                                onToggleExpand = { onToggleDayExpand(day.dateKey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Year Card - Collapsed by default.
 * Tapping reveals nested list of months and yearly totals.
 */
@Composable
fun YearWordStatsCard(
    year: YearWordStats,
    isExpanded: Boolean,
    expandedMonthKeys: Set<String>,
    expandedDayKeys: Set<String>,
    learningColor: Color,
    learnedColor: Color,
    onToggleExpand: () -> Unit,
    onToggleMonthExpand: (String) -> Unit,
    onToggleDayExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_year_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Year Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = year.yearLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatDaysCount(year.activeDaysCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotationState),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Year Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge: Active days
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Column {
                        Text(
                            text = "${year.activeDaysCount}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Активных дней",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Badge: Learning words
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(learningColor.copy(alpha = 0.12f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Column {
                        Text(
                            text = if (year.learningCount > 0) "+${year.learningCount}" else "0",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = learningColor
                        )
                        Text(
                            text = "На изучение",
                            style = MaterialTheme.typography.labelSmall,
                            color = learningColor.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Badge: Learned words
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(learnedColor.copy(alpha = 0.12f))
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Column {
                        Text(
                            text = if (year.learnedCount > 0) "+${year.learnedCount}" else "0",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = learnedColor
                        )
                        Text(
                            text = "Выучено",
                            style = MaterialTheme.typography.labelSmall,
                            color = learnedColor.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Expanded: Nested Months List
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    val activeMonths = year.months.filter { it.totalCount > 0 }
                    if (activeMonths.isEmpty()) {
                        Text(
                            text = "В этом году пока нет месяцев с добавленными словами",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Месяцы с активностью (${activeMonths.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Nested Months
                        activeMonths.forEach { month ->
                            val isMonthExpanded = expandedMonthKeys.contains(month.monthKey)
                            MonthWordStatsCard(
                                month = month,
                                isExpanded = isMonthExpanded,
                                expandedDayKeys = expandedDayKeys,
                                learningColor = learningColor,
                                learnedColor = learnedColor,
                                onToggleExpand = { onToggleMonthExpand(month.monthKey) },
                                onToggleDayExpand = onToggleDayExpand
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordChip(
    word: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = word,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun EmptyStatsPlaceholder(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Пока нет статистики",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

private fun formatDayLabel(dateKey: String, timestamp: Long): String {
    val todaySdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayKey = todaySdf.format(Date())

    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayKey = todaySdf.format(cal.time)

    val fullDateFormatter = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    val dayOfWeekFormatter = SimpleDateFormat("EEEE", Locale("ru"))
    val dateObj = if (timestamp > 0L) Date(timestamp) else {
        try {
            todaySdf.parse(dateKey) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }
    val formattedDate = fullDateFormatter.format(dateObj)
    val dayOfWeek = dayOfWeekFormatter.format(dateObj).replaceFirstChar { it.uppercase() }

    return when (dateKey) {
        todayKey -> "Сегодня, $formattedDate"
        yesterdayKey -> "Вчера, $formattedDate"
        else -> "$formattedDate ($dayOfWeek)"
    }
}

private fun formatDaysCount(count: Int): String {
    val remainder10 = count % 10
    val remainder100 = count % 100
    val word = when {
        remainder100 in 11..19 -> "дней"
        remainder10 == 1 -> "день"
        remainder10 in 2..4 -> "дня"
        else -> "дней"
    }
    return "$count $word"
}
