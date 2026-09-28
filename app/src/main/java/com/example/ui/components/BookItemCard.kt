package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BookUiItem

private data class DifficultyTier(
    val levelName: String,
    val cefr: String,
    val activeSegments: Int,
    val color: Color
)

private fun getDifficultyTier(percent: Double): DifficultyTier {
    return when {
        percent <= 20.0 -> DifficultyTier("Очень легкая", "A1–A2", 1, Color(0xFF10B981))
        percent <= 35.0 -> DifficultyTier("Легкая", "A2–B1", 2, Color(0xFF84CC16))
        percent <= 55.0 -> DifficultyTier("Средняя", "B1", 3, Color(0xFFF59E0B))
        percent <= 75.0 -> DifficultyTier("Повышенная", "B2", 4, Color(0xFFF97316))
        else -> DifficultyTier("Высокая", "C1+", 5, Color(0xFFEF4444))
    }
}

private fun formatNumber(count: Int): String {
    return if (count >= 1000) {
        val kValue = count / 1000.0
        if (kValue >= 10.0) "%.0fk".format(kValue) else "%.1fk".format(kValue)
    } else {
        count.toString()
    }
}

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
    val complexityPercent = item.stats?.complexityVolumePercent ?: 0.0
    val tier = remember(complexityPercent) { getDifficultyTier(complexityPercent) }

    val lexicalDensity = if (book.totalWords > 0) {
        (book.uniqueWordsCount.toDouble() / book.totalWords) * 100.0
    } else 0.0

    val progress = if (item.totalPages > 0) {
        (item.currentPage.toFloat() / item.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val progressPercent = (progress * 100).toInt()

    val surfaceCard = Color(0xFF12141A)
    val surfaceCardMuted = Color(0xFF171A22)
    val surfacePill = Color(0xFF1E222D)
    val surfaceBorder = Color(0x1FFFFFFF)
    val widgetBorder = Color(0x40FFFFFF) // Crisp light border as seen in the design mockup

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceCard)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else surfaceBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("book_item_${book.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Group indicator strip on the left edge if grouped
            if (leftBorderColor != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(leftBorderColor)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Top Row: Title, Author & Bookmark action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 20.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = book.author.ifBlank { "Автор не указан" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.BookmarkBorder,
                        contentDescription = "Сохранить",
                        tint = Color(0xFF64748B),
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Middle Row: Cover & Complexity Widget Side by Side (Balanced Heights: 96x144 dp, 2:3 ratio)
                val coverWidth = 96.dp
                val coverHeight = 144.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(coverHeight),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left: Book Cover Graphic Mockup (Standard 2:3 book ratio)
                    Box(
                        modifier = Modifier
                            .width(coverWidth)
                            .height(coverHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                    ) {
                        BookCoverView(
                            bookId = book.id,
                            title = book.title,
                            author = book.author,
                            filePath = book.filePath,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Right: Complexity & Vocabulary metrics + 3 Frequency Badges
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(coverHeight),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Metrics unified widget box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceCardMuted.copy(alpha = 0.7f))
                                .border(1.dp, widgetBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Complexity
                                    Column {
                                        Text(
                                            text = "СЛОЖНОСТЬ",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF94A3B8),
                                                letterSpacing = 0.5.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "%.1f%%".format(complexityPercent),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                color = tier.color
                                            )
                                        )
                                    }

                                    // Lexical density
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "ЛЕКСИКА",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF94A3B8),
                                                letterSpacing = 0.5.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "%.1f%%".format(lexicalDensity),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF34D399)
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // 5-segment CEFR difficulty meter
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (i in 1..5) {
                                        val isActive = i <= tier.activeSegments
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (isActive) tier.color else surfacePill)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Frequency badges: новые / Изучаю / Знаю
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. Новые (Blue)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1A3B82F6))
                                    .border(1.dp, Color(0x403B82F6), RoundedCornerShape(10.dp))
                                    .padding(vertical = 5.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF3B82F6), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = formatNumber(item.newWordsCount),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF93C5FD)
                                            )
                                        )
                                    }
                                    Text(
                                        text = "новые",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xCC60A5FA)
                                        )
                                    )
                                }
                            }

                            // 2. Изучаю (Amber)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1AFFB300))
                                    .border(1.dp, Color(0x40FFB300), RoundedCornerShape(10.dp))
                                    .padding(vertical = 5.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFFF59E0B), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = formatNumber(item.learningWordsCount),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFFFCD34D)
                                            )
                                        )
                                    }
                                    Text(
                                        text = "Изучаю",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xCCFBBF24)
                                        )
                                    )
                                }
                            }

                            // 3. Знаю (Emerald)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x1A10B981))
                                    .border(1.dp, Color(0x4010B981), RoundedCornerShape(10.dp))
                                    .padding(vertical = 5.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF10B981), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = formatNumber(item.learnedWordsCount),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF6EE7B7)
                                            )
                                        )
                                    }
                                    Text(
                                        text = "Знаю",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xCC34D399)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom words strip: 🔤 2.1k уник. • 📖 13.3k всего | 11 / 80 стр.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceCardMuted)
                        .border(1.dp, widgetBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔤", fontSize = 11.sp)
                            Text(
                                text = formatNumber(book.uniqueWordsCount),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "уник.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            )

                            Text(
                                text = "•",
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            Text("📖", fontSize = 11.sp)
                            Text(
                                text = formatNumber(book.totalWords),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "всего",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${item.currentPage}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = " / ${item.totalPages} стр.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Reading Progress Row & Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Прогресс чтения книги",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.3.sp
                        )
                    )
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B82F6)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(surfacePill)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF3B82F6))
                    )
                }
            }
        }
    }
}
